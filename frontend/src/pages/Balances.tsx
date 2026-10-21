import { useMemo, useState, type FormEvent } from "react";
import { api } from "../api/client";
import { EmptyState, ErrorBanner, Field, Loading, Modal, PageHeader } from "../components/ui";
import { formatDate, formatMoney, todayIso } from "../format";
import { useAsync } from "../hooks";
import type { BalanceInput, BalanceRow, Lists } from "../types";

const ALL = "__all__";

export default function Balances() {
  const balancesQ = useAsync(() => api.balances(), []);
  const listsQ = useAsync(() => api.lists(), []);
  const [editing, setEditing] = useState<BalanceRow | "new" | null>(null);
  const [dateFilter, setDateFilter] = useState<string>(ALL);
  const [typeFilter, setTypeFilter] = useState<string>(ALL);

  const rows = balancesQ.data ?? [];

  const dates = useMemo(() => {
    const set = new Set(rows.map((r) => r.asOnDate));
    return [...set].sort().reverse();
  }, [rows]);

  const filtered = useMemo(
    () =>
      rows.filter(
        (r) =>
          (dateFilter === ALL || r.asOnDate === dateFilter) &&
          (typeFilter === ALL || r.accountType === typeFilter),
      ),
    [rows, dateFilter, typeFilter],
  );

  const total = filtered.reduce((s, r) => s + r.balance, 0);

  if (balancesQ.loading && !balancesQ.data) return <Loading />;
  if (balancesQ.error) return <ErrorBanner message={balancesQ.error} onRetry={balancesQ.reload} />;

  return (
    <div className="page">
      <PageHeader
        title="Balances"
        subtitle={`${filtered.length} entries · net ${formatMoney(total)}`}
        actions={
          <button className="btn primary" onClick={() => setEditing("new")}>
            + Add balance
          </button>
        }
      />

      <div className="filters">
        <select value={dateFilter} onChange={(e) => setDateFilter(e.target.value)} aria-label="As-on date">
          <option value={ALL}>All dates</option>
          {dates.map((d) => (
            <option key={d} value={d}>
              {formatDate(d)}
            </option>
          ))}
        </select>
        <select value={typeFilter} onChange={(e) => setTypeFilter(e.target.value)} aria-label="Account type">
          <option value={ALL}>All account types</option>
          {(listsQ.data?.accountTypes ?? []).map((t) => (
            <option key={t} value={t}>
              {t}
            </option>
          ))}
        </select>
      </div>

      {filtered.length === 0 ? (
        <EmptyState message="No balances match these filters." />
      ) : (
        <ul className="row-list">
          {filtered.map((r) => (
            <li key={r.id} className="row-item" onClick={() => setEditing(r)}>
              <div className="row-main">
                <span className="row-title">{r.institution || r.accountType}</span>
                <span className="row-sub">
                  {formatDate(r.asOnDate)} · {r.accountType} · {r.owner}
                </span>
              </div>
              <span className={`row-amount ${r.balance < 0 ? "spend" : "gain"}`}>
                {formatMoney(r.balance, true)}
              </span>
            </li>
          ))}
        </ul>
      )}

      {editing && listsQ.data && (
        <BalanceModal
          lists={listsQ.data}
          row={editing === "new" ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            balancesQ.reload();
          }}
        />
      )}
    </div>
  );
}

function BalanceModal({
  lists,
  row,
  onClose,
  onSaved,
}: {
  lists: Lists;
  row: BalanceRow | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [form, setForm] = useState<BalanceInput>(() => ({
    asOnDate: row?.asOnDate ?? todayIso(),
    owner: row?.owner ?? lists.people[0] ?? "",
    accountType: row?.accountType ?? lists.accountTypes[0] ?? "",
    institution: row?.institution ?? "",
    balance: row?.balance ?? 0,
    notes: row?.notes ?? "",
  }));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function set<K extends keyof BalanceInput>(key: K, value: BalanceInput[K]) {
    setForm((f) => ({ ...f, [key]: value }));
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      if (row) await api.updateBalance(row.id, form);
      else await api.addBalance(form);
      onSaved();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save.");
      setBusy(false);
    }
  }

  async function onDelete() {
    if (!row || !confirm("Delete this balance entry?")) return;
    setBusy(true);
    setError(null);
    try {
      await api.deleteBalance(row.id);
      onSaved();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not delete.");
      setBusy(false);
    }
  }

  return (
    <Modal title={row ? "Edit balance" : "Add balance"} onClose={onClose}>
      <form className="form" onSubmit={onSubmit}>
        <div className="form-grid">
          <Field label="As-on date">
            <input
              type="date"
              value={form.asOnDate}
              onChange={(e) => set("asOnDate", e.target.value)}
              required
            />
          </Field>
          <Field label="Balance (₹)">
            <input
              type="number"
              step="0.01"
              inputMode="decimal"
              value={form.balance}
              onChange={(e) => set("balance", Number(e.target.value))}
              required
            />
          </Field>
          <Field label="Owner">
            <select value={form.owner} onChange={(e) => set("owner", e.target.value)}>
              {lists.people.map((p) => (
                <option key={p}>{p}</option>
              ))}
            </select>
          </Field>
          <Field label="Account type">
            <select value={form.accountType} onChange={(e) => set("accountType", e.target.value)}>
              {lists.accountTypes.map((a) => (
                <option key={a}>{a}</option>
              ))}
            </select>
          </Field>
        </div>

        <p className="muted small">Enter loans / credit-card dues as a negative amount.</p>

        <Field label="Institution">
          <input value={form.institution} onChange={(e) => set("institution", e.target.value)} />
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
