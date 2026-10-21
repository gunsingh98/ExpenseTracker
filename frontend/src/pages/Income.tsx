import { useMemo, useState, type FormEvent } from "react";
import { api } from "../api/client";
import { EmptyState, ErrorBanner, Field, Loading, Modal, PageHeader } from "../components/ui";
import { formatDate, formatMoney, todayIso } from "../format";
import { useAsync } from "../hooks";
import type { IncomeInput, IncomeRow, Lists } from "../types";

const ALL = "__all__";

export default function Income() {
  const incomeQ = useAsync(() => api.income(), []);
  const listsQ = useAsync(() => api.lists(), []);
  const [editing, setEditing] = useState<IncomeRow | "new" | null>(null);
  const [monthFilter, setMonthFilter] = useState<string>(ALL);
  const [typeFilter, setTypeFilter] = useState<string>(ALL);

  const rows = incomeQ.data ?? [];

  const months = useMemo(() => {
    const set = new Set(rows.map((r) => r.date.slice(0, 7)));
    return [...set].sort().reverse();
  }, [rows]);

  const filtered = useMemo(
    () =>
      rows.filter(
        (r) =>
          (monthFilter === ALL || r.date.startsWith(monthFilter)) &&
          (typeFilter === ALL || r.incomeType === typeFilter),
      ),
    [rows, monthFilter, typeFilter],
  );

  const total = filtered.reduce((s, r) => s + r.amount, 0);

  if (incomeQ.loading && !incomeQ.data) return <Loading />;
  if (incomeQ.error) return <ErrorBanner message={incomeQ.error} onRetry={incomeQ.reload} />;

  return (
    <div className="page">
      <PageHeader
        title="Income"
        subtitle={`${filtered.length} entries · ${formatMoney(total)}`}
        actions={
          <button className="btn primary" onClick={() => setEditing("new")}>
            + Add income
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
        <select value={typeFilter} onChange={(e) => setTypeFilter(e.target.value)} aria-label="Income type">
          <option value={ALL}>All types</option>
          {(listsQ.data?.incomeTypes ?? []).map((t) => (
            <option key={t} value={t}>
              {t}
            </option>
          ))}
        </select>
      </div>

      {filtered.length === 0 ? (
        <EmptyState message="No income matches these filters." />
      ) : (
        <ul className="row-list">
          {filtered.map((r) => (
            <li key={r.id} className="row-item" onClick={() => setEditing(r)}>
              <div className="row-main">
                <span className="row-title">{r.source || r.incomeType}</span>
                <span className="row-sub">
                  {formatDate(r.date)} · {r.incomeType} · {r.earner}
                  {r.receivedIn ? ` · ${r.receivedIn}` : ""}
                </span>
              </div>
              <span className="row-amount gain">{formatMoney(r.amount, true)}</span>
            </li>
          ))}
        </ul>
      )}

      {editing && listsQ.data && (
        <IncomeModal
          lists={listsQ.data}
          row={editing === "new" ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            incomeQ.reload();
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

function IncomeModal({
  lists,
  row,
  onClose,
  onSaved,
}: {
  lists: Lists;
  row: IncomeRow | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [form, setForm] = useState<IncomeInput>(() => ({
    date: row?.date ?? todayIso(),
    earner: row?.earner ?? lists.people[0] ?? "",
    incomeType: row?.incomeType ?? lists.incomeTypes[0] ?? "",
    source: row?.source ?? "",
    amount: row?.amount ?? 0,
    receivedIn: row?.receivedIn ?? lists.accountTypes[0] ?? "",
    notes: row?.notes ?? "",
  }));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function set<K extends keyof IncomeInput>(key: K, value: IncomeInput[K]) {
    setForm((f) => ({ ...f, [key]: value }));
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      if (row) await api.updateIncome(row.id, form);
      else await api.addIncome(form);
      onSaved();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save.");
      setBusy(false);
    }
  }

  async function onDelete() {
    if (!row || !confirm("Delete this income entry?")) return;
    setBusy(true);
    setError(null);
    try {
      await api.deleteIncome(row.id);
      onSaved();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not delete.");
      setBusy(false);
    }
  }

  return (
    <Modal title={row ? "Edit income" : "Add income"} onClose={onClose}>
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
          <Field label="Earner">
            <select value={form.earner} onChange={(e) => set("earner", e.target.value)}>
              {lists.people.map((p) => (
                <option key={p}>{p}</option>
              ))}
            </select>
          </Field>
          <Field label="Income type">
            <select value={form.incomeType} onChange={(e) => set("incomeType", e.target.value)}>
              {lists.incomeTypes.map((t) => (
                <option key={t}>{t}</option>
              ))}
            </select>
          </Field>
          <Field label="Received in">
            <select value={form.receivedIn} onChange={(e) => set("receivedIn", e.target.value)}>
              {lists.accountTypes.map((a) => (
                <option key={a}>{a}</option>
              ))}
            </select>
          </Field>
        </div>

        <Field label="Source">
          <input value={form.source} onChange={(e) => set("source", e.target.value)} />
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
