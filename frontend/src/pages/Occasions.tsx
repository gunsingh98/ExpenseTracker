import { useEffect, useMemo, useState } from "react";
import { api } from "../api/client";
import { EmptyState, ErrorBanner, Loading, PageHeader } from "../components/ui";
import { formatDate, formatMoney } from "../format";
import { useAsync } from "../hooks";
import type { ExpenseRow } from "../types";
import { ExpenseModal } from "./Expenses";

/**
 * Trip / occasion view: the same expenses as the Expenses page, but scoped to a
 * single occasion tag. Adding here pre-fills the occasion so the new row is
 * tagged automatically; it is otherwise an ordinary expense.
 */
export default function Occasions() {
  const expensesQ = useAsync(() => api.expenses(), []);
  const listsQ = useAsync(() => api.lists(), []);
  const [selected, setSelected] = useState<string>("");
  const [editing, setEditing] = useState<ExpenseRow | "new" | null>(null);

  const rows = expensesQ.data ?? [];

  // Offer every occasion that is either configured in Lists or already used on a
  // row, so imported/legacy tags remain selectable even if not in the list.
  const occasions = useMemo(() => {
    const set = new Set<string>(listsQ.data?.occasions ?? []);
    for (const r of rows) if (r.occasion) set.add(r.occasion);
    return [...set].sort((a, b) => a.localeCompare(b));
  }, [rows, listsQ.data]);

  // Keep a valid selection as data loads / changes.
  useEffect(() => {
    if (occasions.length && !occasions.includes(selected)) setSelected(occasions[0]);
  }, [occasions, selected]);

  const filtered = useMemo(
    () => rows.filter((r) => r.occasion === selected),
    [rows, selected],
  );
  const total = filtered.reduce((s, r) => s + r.amount, 0);

  if (expensesQ.loading && !expensesQ.data) return <Loading />;
  if (expensesQ.error) return <ErrorBanner message={expensesQ.error} onRetry={expensesQ.reload} />;

  return (
    <div className="page">
      <PageHeader
        title="Occasions & trips"
        subtitle={
          selected ? `${selected} · ${filtered.length} entries · ${formatMoney(total)}` : undefined
        }
        actions={
          occasions.length > 0 ? (
            <button className="btn primary" onClick={() => setEditing("new")}>
              + Add expense
            </button>
          ) : undefined
        }
      />

      {occasions.length === 0 ? (
        <EmptyState message="No occasions yet. Add one under Lists → Occasions / trips, then tag expenses to it." />
      ) : (
        <>
          <div className="filters">
            <select
              value={selected}
              onChange={(e) => setSelected(e.target.value)}
              aria-label="Occasion"
            >
              {occasions.map((o) => (
                <option key={o} value={o}>
                  {o}
                </option>
              ))}
            </select>
          </div>

          {filtered.length === 0 ? (
            <EmptyState message="No expenses tagged to this occasion yet." />
          ) : (
            <ul className="row-list">
              {filtered.map((r) => (
                <li key={r.id} className="row-item" onClick={() => setEditing(r)}>
                  <div className="row-main">
                    <span className="row-title">{r.description || r.category}</span>
                    <span className="row-sub">
                      {formatDate(r.date)} · {r.category} · {r.paidBy}
                      {r.shared ? " · shared" : ""}
                    </span>
                  </div>
                  <span className="row-amount spend">{formatMoney(r.amount, true)}</span>
                </li>
              ))}
            </ul>
          )}
        </>
      )}

      {editing && listsQ.data && (
        <ExpenseModal
          lists={listsQ.data}
          row={editing === "new" ? null : editing}
          defaultOccasion={selected}
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
