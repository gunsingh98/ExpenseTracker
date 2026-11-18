import { useState, type FormEvent } from "react";
import { useSearchParams } from "react-router-dom";
import { api } from "../api/client";
import { EmptyState, ErrorBanner, Field, Loading, Modal, PageHeader } from "../components/ui";
import { formatDate, formatMoney, todayIso } from "../format";
import { useAsync, useCursorList, useInView } from "../hooks";
import type { ExpenseFilters, ExpenseInput, ExpenseRow, ExpenseSort, Lists } from "../types";

const ALL = "__all__";

type FilterKey = "month" | "category" | "paidBy" | "paymentMethod";
const FILTER_KEYS: FilterKey[] = ["month", "category", "paidBy", "paymentMethod"];

const SORTS: { value: ExpenseSort; label: string }[] = [
  { value: "DATE_DESC", label: "Newest first" },
  { value: "DATE_ASC", label: "Oldest first" },
  { value: "AMOUNT_DESC", label: "Amount: high to low" },
  { value: "AMOUNT_ASC", label: "Amount: low to high" },
];
const DEFAULT_SORT: ExpenseSort = "DATE_DESC";

export default function Expenses() {
  // Filters and sort live in the URL so a view survives reloads and can be shared.
  const [params, setParams] = useSearchParams();
  const month = params.get("month") ?? "";
  const category = params.get("category") ?? "";
  const paidBy = params.get("paidBy") ?? "";
  const paymentMethod = params.get("paymentMethod") ?? "";
  const sortParam = params.get("sort");
  const sort = SORTS.some((s) => s.value === sortParam) ? (sortParam as ExpenseSort) : DEFAULT_SORT;
  const filters: ExpenseFilters = {
    month: month || undefined,
    category: category || undefined,
    paidBy: paidBy || undefined,
    paymentMethod: paymentMethod || undefined,
  };
  const filterDeps = [month, category, paidBy, paymentMethod];
  const anyFilter = filterDeps.some(Boolean);

  const list = useCursorList((cursor) => api.expensePage(filters, sort, cursor), [...filterDeps, sort]);
  const totalsQ = useAsync(() => api.expenseTotals(filters), filterDeps);
  const monthsQ = useAsync(() => api.expenseMonths(), []);
  const listsQ = useAsync(() => api.lists(), []);
  const [editing, setEditing] = useState<ExpenseRow | "new" | null>(null);
  const sentinel = useInView(list.loadMore);

  function setParam(key: FilterKey | "sort", value: string) {
    setParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        if (value === ALL || (key === "sort" && value === DEFAULT_SORT)) next.delete(key);
        else next.set(key, value);
        return next;
      },
      { replace: true },
    );
  }

  function clearFilters() {
    setParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        FILTER_KEYS.forEach((k) => next.delete(k));
        return next;
      },
      { replace: true },
    );
  }

  if (list.loading && list.items.length === 0 && !list.error) return <Loading />;
  if (list.error && list.items.length === 0)
    return <ErrorBanner message={list.error} onRetry={list.reload} />;

  const totals = totalsQ.data;

  return (
    <div className="page">
      <PageHeader
        title="Expenses"
        subtitle={totals ? `${totals.count} entries · ${formatMoney(totals.total)}` : " "}
        actions={
          <button className="btn primary" onClick={() => setEditing("new")}>
            + Add expense
          </button>
        }
      />

      <div className="filters">
        <select value={month || ALL} onChange={(e) => setParam("month", e.target.value)} aria-label="Month">
          <option value={ALL}>All months</option>
          {(monthsQ.data ?? []).map((m) => (
            <option key={m} value={m}>
              {formatMonth(m)}
            </option>
          ))}
        </select>
        <select
          value={category || ALL}
          onChange={(e) => setParam("category", e.target.value)}
          aria-label="Category"
        >
          <option value={ALL}>All categories</option>
          {(listsQ.data?.categories ?? []).map((c) => (
            <option key={c} value={c}>
              {c}
            </option>
          ))}
        </select>
        <select value={paidBy || ALL} onChange={(e) => setParam("paidBy", e.target.value)} aria-label="Paid by">
          <option value={ALL}>Anyone</option>
          {(listsQ.data?.people ?? []).map((p) => (
            <option key={p} value={p}>
              {p}
            </option>
          ))}
        </select>
        <select
          value={paymentMethod || ALL}
          onChange={(e) => setParam("paymentMethod", e.target.value)}
          aria-label="Payment method"
        >
          <option value={ALL}>All payment methods</option>
          {(listsQ.data?.paymentMethods ?? []).map((m) => (
            <option key={m} value={m}>
              {m}
            </option>
          ))}
        </select>
        <select value={sort} onChange={(e) => setParam("sort", e.target.value)} aria-label="Sort by">
          {SORTS.map((s) => (
            <option key={s.value} value={s.value}>
              Sort: {s.label}
            </option>
          ))}
        </select>
        {anyFilter && (
          <button className="btn" onClick={clearFilters}>
            Clear filters
          </button>
        )}
      </div>

      {list.items.length === 0 && !list.loading ? (
        <EmptyState message="No expenses match these filters." />
      ) : (
        <ul className="row-list" aria-busy={list.loading || list.loadingMore}>
          {list.items.map((r) => (
            <li key={r.id} className="row-item" onClick={() => setEditing(r)}>
              <div className="row-main">
                <span className="row-title">{r.description || r.category}</span>
                <span className="row-sub">
                  {formatDate(r.date)} · {r.category} · {r.paidBy}
                  {r.shared ? " · shared" : ""}
                  {r.occasion ? ` · 🧳 ${r.occasion}` : ""}
                </span>
              </div>
              <span className="row-amount spend">{formatMoney(r.amount, true)}</span>
            </li>
          ))}
        </ul>
      )}

      {list.error && list.items.length > 0 && (
        <ErrorBanner message={list.error} onRetry={list.loadMore} />
      )}
      {list.hasMore && !list.error && (
        // Auto-loads when scrolled near; the button is the keyboard/no-observer fallback.
        <div ref={sentinel} className="load-more">
          <button className="btn" onClick={list.loadMore} disabled={list.loadingMore}>
            {list.loadingMore ? "Loading…" : "Load more"}
          </button>
        </div>
      )}

      {editing && listsQ.data && (
        <ExpenseModal
          lists={listsQ.data}
          row={editing === "new" ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            list.reload();
            totalsQ.reload();
            monthsQ.reload();
          }}
        />
      )}
    </div>
  );
}

function formatMonth(ym: string): string {
  const [y, m] = ym.split("-");
  return new Date(Number(y), Number(m) - 1, 1).toLocaleDateString("en-IN", {
    month: "long",
    year: "numeric",
  });
}

export function ExpenseModal({
  lists,
  row,
  defaultOccasion,
  onClose,
  onSaved,
}: {
  lists: Lists;
  row: ExpenseRow | null;
  /** Pre-selected occasion for a new expense (e.g. opened from the Occasions page). */
  defaultOccasion?: string;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [form, setForm] = useState<ExpenseInput>(() => ({
    date: row?.date ?? todayIso(),
    paidBy: row?.paidBy ?? lists.people[0] ?? "",
    category: row?.category ?? lists.categories[0] ?? "",
    description: row?.description ?? "",
    amount: row?.amount ?? 0,
    paymentMethod: row?.paymentMethod ?? lists.paymentMethods[0] ?? "",
    shared: row?.shared ?? false,
    notes: row?.notes ?? "",
    occasion: row?.occasion ?? defaultOccasion ?? "",
  }));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function set<K extends keyof ExpenseInput>(key: K, value: ExpenseInput[K]) {
    setForm((f) => ({ ...f, [key]: value }));
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      if (row) await api.updateExpense(row.id, form);
      else await api.addExpense(form);
      onSaved();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save.");
      setBusy(false);
    }
  }

  async function onDelete() {
    if (!row || !confirm("Delete this expense?")) return;
    setBusy(true);
    setError(null);
    try {
      await api.deleteExpense(row.id);
      onSaved();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not delete.");
      setBusy(false);
    }
  }

  return (
    <Modal title={row ? "Edit expense" : "Add expense"} onClose={onClose}>
      <form className="form" onSubmit={onSubmit}>
        <div className="form-grid">
          <Field label="Date">
            <input type="date" value={form.date} onChange={(e) => set("date", e.target.value)} required />
          </Field>
          <Field label="Amount (₹)">
            <input
              type="number"
              min="0"
              step="0.01"
              inputMode="decimal"
              value={form.amount}
              onChange={(e) => set("amount", Number(e.target.value))}
              required
            />
          </Field>
          <Field label="Paid by">
            <select value={form.paidBy} onChange={(e) => set("paidBy", e.target.value)}>
              {lists.people.map((p) => (
                <option key={p}>{p}</option>
              ))}
            </select>
          </Field>
          <Field label="Category">
            <select value={form.category} onChange={(e) => set("category", e.target.value)}>
              {lists.categories.map((c) => (
                <option key={c}>{c}</option>
              ))}
            </select>
          </Field>
          <Field label="Payment method">
            <select value={form.paymentMethod} onChange={(e) => set("paymentMethod", e.target.value)}>
              {lists.paymentMethods.map((m) => (
                <option key={m}>{m}</option>
              ))}
            </select>
          </Field>
          <Field label="Occasion / trip">
            <select value={form.occasion} onChange={(e) => set("occasion", e.target.value)}>
              <option value="">— None —</option>
              {lists.occasions.map((o) => (
                <option key={o}>{o}</option>
              ))}
            </select>
          </Field>
          <label className="field checkbox">
            <input
              type="checkbox"
              checked={form.shared}
              onChange={(e) => set("shared", e.target.checked)}
            />
            <span>Shared expense</span>
          </label>
        </div>

        <Field label="Description">
          <input value={form.description} onChange={(e) => set("description", e.target.value)} />
        </Field>
        <Field label="Notes">
          <textarea value={form.notes} onChange={(e) => set("notes", e.target.value)} rows={2} />
        </Field>

        {error && <div className="banner error">{error}</div>}

        <div className="form-actions">
          {row && (
            <button type="button" className="btn danger" onClick={onDelete} disabled={busy}>
              Delete
            </button>
          )}
          <div className="spacer" />
          <button type="button" className="btn" onClick={onClose} disabled={busy}>
            Cancel
          </button>
          <button type="submit" className="btn primary" disabled={busy}>
            {busy ? "Saving…" : "Save"}
          </button>
        </div>
      </form>
    </Modal>
  );
}
