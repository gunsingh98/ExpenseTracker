import type {
  AuditRow,
  BalanceInput,
  BalanceRow,
  DashboardSummary,
  ExpenseInput,
  ExpenseRow,
  IncomeInput,
  IncomeRow,
  InvestmentInput,
  InvestmentRow,
  ListColumn,
  Lists,
} from "../types";

/** Thrown for non-2xx responses; `status` lets callers special-case 401. */
export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
    this.name = "ApiError";
  }
}

/**
 * The JWT bearer token for the current session. Kept in module memory (not
 * localStorage) so it is not reachable by injected scripts; it is lost on a
 * full page reload, which triggers re-login via the auth provider.
 */
let authToken: string | null = null;

export function setAuthToken(token: string | null): void {
  authToken = token;
}

/**
 * Called when the session is unrecoverable (a 401 that even a refresh could not
 * fix). The auth provider registers this to drop its state and show the login
 * screen, so a mid-session token expiry bounces the user to login instead of
 * leaving error panels on the page.
 */
let onAuthExpired: (() => void) | null = null;

export function setOnAuthExpired(handler: (() => void) | null): void {
  onAuthExpired = handler;
}

/**
 * In-flight refresh, shared so that several requests failing with 401 at once
 * trigger only one /api/refresh round-trip. Resolves to true if a new access
 * token was obtained.
 */
let refreshInFlight: Promise<boolean> | null = null;

function tryRefresh(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      try {
        const res = await fetch("/api/refresh", {
          method: "POST",
          credentials: "same-origin", // send the HttpOnly refresh cookie
        });
        if (!res.ok) return false;
        const data = (await res.json()) as { token: string };
        authToken = data.token;
        return true;
      } catch {
        return false;
      }
    })();
    void refreshInFlight.finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

async function request<T>(
  method: string,
  path: string,
  body?: unknown,
  allowRefresh = true,
): Promise<T> {
  const headers: Record<string, string> = {};
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (authToken) headers["Authorization"] = `Bearer ${authToken}`;

  const res = await fetch(path, {
    method,
    headers: Object.keys(headers).length ? headers : undefined,
    body: body !== undefined ? JSON.stringify(body) : undefined,
    credentials: "same-origin", // allow the refresh cookie to flow on auth calls
  });

  // A 401 on a normal API call: try a silent refresh once, then replay the
  // request. The refresh/login endpoints themselves are excluded to avoid loops.
  if (
    res.status === 401 &&
    allowRefresh &&
    path !== "/api/refresh" &&
    path !== "/api/login"
  ) {
    const refreshed = await tryRefresh();
    if (refreshed) {
      return request<T>(method, path, body, false);
    }
    // Refresh failed: the session is over. Clear the token and let the auth
    // provider bounce to login, then surface the error to the caller.
    authToken = null;
    onAuthExpired?.();
  }

  if (!res.ok) {
    let message = res.statusText;
    try {
      const data = await res.json();
      message = (data && (data.detail || data.message)) || message;
    } catch {
      /* non-JSON error body */
    }
    throw new ApiError(res.status, message);
  }

  if (res.status === 204) return undefined as T;
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

/**
 * Fetches a binary response (the Excel export) as a Blob, carrying the bearer
 * token and reusing the same silent-refresh-and-replay flow as {@link request}.
 */
async function requestBlob(path: string, allowRefresh = true): Promise<Blob> {
  const headers: Record<string, string> = {};
  if (authToken) headers["Authorization"] = `Bearer ${authToken}`;

  const res = await fetch(path, {
    method: "GET",
    headers: Object.keys(headers).length ? headers : undefined,
    credentials: "same-origin",
  });

  if (res.status === 401 && allowRefresh) {
    const refreshed = await tryRefresh();
    if (refreshed) return requestBlob(path, false);
    authToken = null;
    onAuthExpired?.();
  }

  if (!res.ok) {
    throw new ApiError(res.status, res.statusText);
  }
  return res.blob();
}

export const api = {
  // --- auth ---
  me: () => request<{ username: string }>("GET", "/api/me"),
  login: (username: string, password: string) =>
    request<{ token: string; username: string }>("POST", "/api/login", { username, password }),
  /** Exchange the HttpOnly refresh cookie for a fresh access token (silent re-auth). */
  refresh: () => request<{ token: string; username: string }>("POST", "/api/refresh"),
  /** Revoke the refresh token server-side and clear the cookie. */
  logout: () => request<void>("POST", "/api/logout"),

  // --- dashboard ---
  dashboard: (year?: number) =>
    request<DashboardSummary>(
      "GET",
      year ? `/api/dashboard?year=${year}` : "/api/dashboard",
    ),

  // --- expenses ---
  expenses: () => request<ExpenseRow[]>("GET", "/api/expenses"),
  addExpense: (input: ExpenseInput) => request<void>("POST", "/api/expenses", input),
  updateExpense: (id: number, input: ExpenseInput) =>
    request<void>("PUT", `/api/expenses/${id}`, input),
  deleteExpense: (id: number) => request<void>("DELETE", `/api/expenses/${id}`),

  // --- investments ---
  investments: () => request<InvestmentRow[]>("GET", "/api/investments"),
  addInvestment: (input: InvestmentInput) =>
    request<void>("POST", "/api/investments", input),
  updateInvestment: (id: number, input: InvestmentInput) =>
    request<void>("PUT", `/api/investments/${id}`, input),
  deleteInvestment: (id: number) => request<void>("DELETE", `/api/investments/${id}`),
  /** Set a whole fund's market value in one call (kept on a single row). */
  setFundCurrentValue: (name: string, currentValue: number | null) =>
    request<void>("PUT", "/api/investments/funds/current-value", { name, currentValue }),
  /** Delete a whole fund — every contribution sharing the name — in one call. */
  deleteFund: (name: string) =>
    request<void>("DELETE", `/api/investments/funds?name=${encodeURIComponent(name)}`),

  // --- income ---
  income: () => request<IncomeRow[]>("GET", "/api/income"),
  addIncome: (input: IncomeInput) => request<void>("POST", "/api/income", input),
  updateIncome: (id: number, input: IncomeInput) =>
    request<void>("PUT", `/api/income/${id}`, input),
  deleteIncome: (id: number) => request<void>("DELETE", `/api/income/${id}`),

  // --- balances ---
  balances: () => request<BalanceRow[]>("GET", "/api/balances"),
  addBalance: (input: BalanceInput) => request<void>("POST", "/api/balances", input),
  updateBalance: (id: number, input: BalanceInput) =>
    request<void>("PUT", `/api/balances/${id}`, input),
  deleteBalance: (id: number) => request<void>("DELETE", `/api/balances/${id}`),

  // --- lists ---
  lists: () => request<Lists>("GET", "/api/lists"),
  updateList: (column: ListColumn, values: string[]) =>
    request<void>("PUT", `/api/lists/${column}`, { values }),

  // --- audit log ---
  audit: () => request<AuditRow[]>("GET", "/api/audit"),

  // --- export ---
  /** Download the current data as an .xlsx workbook (also the Drive backup artifact). */
  exportWorkbook: () => requestBlob("/api/export"),
};
