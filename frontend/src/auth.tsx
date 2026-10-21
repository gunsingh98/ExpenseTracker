import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { api, ApiError, setAuthToken, setOnAuthExpired } from "./api/client";

interface AuthState {
  username: string | null;
  loading: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthState | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [username, setUsername] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  // On a mid-session 401 that even a refresh can't fix, drop state → show login.
  useEffect(() => {
    setOnAuthExpired(() => setUsername(null));
    return () => setOnAuthExpired(null);
  }, []);

  // On load (including a full reload, which wipes the in-memory access token),
  // try to restore the session silently from the HttpOnly refresh cookie.
  useEffect(() => {
    api
      .refresh()
      .then((res) => {
        setAuthToken(res.token);
        setUsername(res.username);
      })
      .catch((e) => {
        if (!(e instanceof ApiError && e.status === 401)) {
          console.error("Failed to restore session", e);
        }
        setUsername(null);
      })
      .finally(() => setLoading(false));
  }, []);

  const login = useCallback(async (user: string, password: string) => {
    const res = await api.login(user, password);
    setAuthToken(res.token);
    setUsername(res.username);
  }, []);

  // Revoke the refresh token server-side (best-effort), then clear local state.
  const logout = useCallback(async () => {
    try {
      await api.logout();
    } catch {
      /* even if the server call fails, clear the client session below */
    }
    setAuthToken(null);
    setUsername(null);
  }, []);

  const value = useMemo(
    () => ({ username, loading, login, logout }),
    [username, loading, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
