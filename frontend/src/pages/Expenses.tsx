import { useMemo, useState, type FormEvent } from "react";
import { api } from "../api/client";
import { EmptyState, ErrorBanner, Field, Loading, Modal, PageHeader } from "../components/ui";
import { formatDate, formatMoney, todayIso } from "../format";
import { useAsync } from "../hooks";
import type { ExpenseInput, ExpenseRow, Lists } from "../types";

const ALL = "__all__";

export default function Expenses() {
  const expensesQ = useAsync(() => api.expenses(), []);
  const listsQ = useAsync(() => api.lists(), []);
  const [editing, setEditing] = useState<ExpenseRow | "new" | null>(null);
  const [monthFilter, setMonthFilter] = useState<string>(ALL);
  const [categoryFilter, setCategoryFilter] = useState<string>(ALL);

  const rows = expensesQ.data ?? [];

  const months = useMemo(() => {
    const set = new Set(rows.map((r) => r.date.slice(0, 7)));
    return [...set].sort().reverse();
  }, [rows]);

  const filtered = useMemo(
    () =>
      rows.filter(
        (r) =>
          (monthFilter === ALL || r.date.startsWith(monthFilter)) &&
          (categoryFilter === ALL || r.category === categoryFilter),
      ),
    [rows, monthFilter, categoryFilter],
  );

  const total = filtered.reduce((s, r) => s + r.amount, 0);

  if (expensesQ.loading && !expensesQ.data) return <Loading />;
  if (expensesQ.error) return <ErrorBanner message={expensesQ.error} onRetry={expensesQ.reload} />;

  return (
    <div className="page">
      <PageHeader
        title="Expenses"
        subtitle={`${filtered.length} entries · ${formatMoney(total)}`}
        actions={
          <button className="btn primary" onClick={() => setEditing("new")}>
            + Add expense
          </button>
        }
      />

      <div className="filters">
        <select value={monthFilter} onChange={(e) => setMonthFilter(e.target.value)} aria-label="Month">
          <option value={ALL}>All months</option>
          {months.map((m) => (
            <option key={m} value={m}>
              {formatMonth(m)}
            </option>
          ))}
        </select>
        <select
          value={categoryFilter}
          onChange={(e) => setCategoryFilter(e.target.value)}
          aria-label="Category"
        >
          <option value={ALL}>All categories</option>
          {(listsQ.data?.categories ?? []).map((c) => (
            <option key={c} value={c}>
              {c}
            </option>
          ))}
        </select>
      </div>

      {filtered.length === 0 ? (
        <EmptyState message="No expenses match these filters." />
      ) : (
        <ul className="row-list">
          {filtered.map((r) => (
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

      {editing && listsQ.data && (
        <ExpenseModal
          lists={listsQ.data}
          row={editing === "new" ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            expensesQ.reload();
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
