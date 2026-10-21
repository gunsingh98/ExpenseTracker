import { useMemo, useState, type FormEvent } from "react";
import { api } from "../api/client";
import { EmptyState, ErrorBanner, Field, Loading, Modal, PageHeader } from "../components/ui";
import { formatDate, formatMoney, todayIso } from "../format";
import { useAsync } from "../hooks";
import type { InvestmentInput, InvestmentRow, Lists } from "../types";

/**
 * A fund groups every contribution that shares the same investment name, so a
 * recurring monthly investment is tracked as one position instead of one row per
 * month. {@code currentValue} is the fund's market value as a whole — the sum of
 * whichever rows carry a value (ideally one), or {@code null} when none is set yet.
 */
interface Fund {
  name: string;
  rows: InvestmentRow[]; // contributions, most recent first
  invested: number;
  currentValue: number | null;
  gain: number | null;
  types: string[];
  owners: string[];
  accounts: string[];
  latest: InvestmentRow; // target for "add contribution" prefill
}

function uniq(values: string[]): string[] {
  return [...new Set(values.filter((v) => v && v.trim()))];
}

/** Groups named investments into funds; rows without a name stay as one-offs. */
function groupFunds(rows: InvestmentRow[]): { funds: Fund[]; oneOffs: InvestmentRow[] } {
  const byName = new Map<string, InvestmentRow[]>();
  const oneOffs: InvestmentRow[] = [];
  for (const r of rows) {
    const name = r.name?.trim() ?? "";
    if (!name) {
      oneOffs.push(r);
      continue;
    }
    (byName.get(name) ?? byName.set(name, []).get(name)!).push(r);
  }

  const funds: Fund[] = [...byName.entries()].map(([name, group]) => {
    const contributions = [...group].sort((a, b) => b.date.localeCompare(a.date) || b.id - a.id);
    const invested = contributions.reduce((s, r) => s + r.amountInvested, 0);
    const valued = contributions.filter((r) => r.currentValue != null);
    const currentValue = valued.length
      ? valued.reduce((s, r) => s + (r.currentValue ?? 0), 0)
      : null;
    return {
      name,
      rows: contributions,
      invested,
      currentValue,
      gain: currentValue == null ? null : currentValue - invested,
      types: uniq(contributions.map((r) => r.type)),
      owners: uniq(contributions.map((r) => r.owner)),
      accounts: uniq(contributions.map((r) => r.account)),
      latest: contributions[0],
    };
  });
  // Biggest positions first; one-offs newest first (matches the API's ordering).
  funds.sort((a, b) => (b.currentValue ?? b.invested) - (a.currentValue ?? a.invested));
  return { funds, oneOffs };
}

/** A fund's value for totals: its set market value, or its invested amount if unvalued. */
function fundValue(f: Fund): number {
  return f.currentValue ?? f.invested;
}

export default function Investments() {
  const investmentsQ = useAsync(() => api.investments(), []);
  const listsQ = useAsync(() => api.lists(), []);
  const [editing, setEditing] = useState<InvestmentRow | "new" | null>(null);
  const [contributingTo, setContributingTo] = useState<Fund | null>(null);
  const [valuing, setValuing] = useState<Fund | null>(null);
  const [expanded, setExpanded] = useState<Set<string>>(new Set());
  const [deleting, setDeleting] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const rows = investmentsQ.data ?? [];
  const { funds, oneOffs } = useMemo(() => groupFunds(rows), [rows]);

  const invested = rows.reduce((s, r) => s + r.amountInvested, 0);
  const current =
    funds.reduce((s, f) => s + fundValue(f), 0) +
    oneOffs.reduce((s, r) => s + (r.currentValue ?? r.amountInvested), 0);

  function toggle(name: string) {
    setExpanded((prev) => {
      const next = new Set(prev);
      next.has(name) ? next.delete(name) : next.add(name);
      return next;
    });
  }

  function reload() {
    setEditing(null);
    setContributingTo(null);
    setValuing(null);
    investmentsQ.reload();
  }

  async function deleteFund(f: Fund) {
    const n = f.rows.length;
    const confirmed = confirm(
      `Delete the entire "${f.name}" fund and all ${n} contribution${n === 1 ? "" : "s"}? This cannot be undone.`,
    );
    if (!confirmed) return;
    setDeleting(f.name);
    setActionError(null);
    try {
      await api.deleteFund(f.name);
      investmentsQ.reload();
    } catch (err) {
      setActionError(err instanceof Error ? err.message : "Could not delete fund.");
    } finally {
      setDeleting(null);
    }
  }

  if (investmentsQ.loading && !investmentsQ.data) return <Loading />;
  if (investmentsQ.error)
    return <ErrorBanner message={investmentsQ.error} onRetry={investmentsQ.reload} />;

  return (
    <div className="page">
      <PageHeader
        title="Investments"
        subtitle={`Invested ${formatMoney(invested)} · Now ${formatMoney(current)}`}
        actions={
          <button className="btn primary" onClick={() => setEditing("new")}>
            + Add investment
          </button>
        }
      />

      {actionError && <div className="banner error">{actionError}</div>}

      {rows.length === 0 ? (
        <EmptyState message="No investments recorded yet." />
      ) : (
        <ul className="fund-list">
          {funds.map((f) => {
            const open = expanded.has(f.name);
            const meta = [
              f.types.join(", "),
              f.owners.join(", "),
              `${f.rows.length} contribution${f.rows.length === 1 ? "" : "s"}`,
            ]
              .filter(Boolean)
              .join(" · ");
            return (
              <li key={f.name} className="fund">
                <div className="fund-head" onClick={() => toggle(f.name)}>
                  <span className={`fund-caret ${open ? "open" : ""}`} aria-hidden>
                    ▸
                  </span>
                  <div className="row-main">
                    <span className="row-title">{f.name}</span>
                    <span className="row-sub">{meta}</span>
                  </div>
                  <div className="row-figures">
                    <span className="row-amount">{formatMoney(fundValue(f))}</span>
                    {f.gain != null && (
                      <span className={`row-gain ${f.gain >= 0 ? "gain" : "loss"}`}>
                        {f.gain >= 0 ? "+" : ""}
                        {formatMoney(f.gain)}
                      </span>
                    )}
                  </div>
                </div>

                <div className="fund-actions">
                  <span className="fund-invested">
                    Invested {formatMoney(f.invested)}
                    {f.currentValue == null && " · not yet valued"}
                  </span>
                  <div className="spacer" />
                  <button className="btn-link" onClick={() => setValuing(f)}>
                    Set current value
                  </button>
                  <button className="btn-link" onClick={() => setContributingTo(f)}>
                    + Add contribution
                  </button>
                  <button
                    className="btn-link danger"
                    onClick={() => deleteFund(f)}
                    disabled={deleting === f.name}
                  >
                    {deleting === f.name ? "Deleting…" : "Delete fund"}
                  </button>
                </div>

                {open && (
                  <ul className="fund-contribs">
                    {f.rows.map((r) => (
                      <li key={r.id} className="contrib-item" onClick={() => setEditing(r)}>
                        <span className="contrib-when">
                          {formatDate(r.date)}
                          {r.account ? ` · ${r.account}` : ""}
                        </span>
                        <span className="contrib-amt">
                          {formatMoney(r.amountInvested)}
                          {r.currentValue != null && (
                            <em className="contrib-val"> → now {formatMoney(r.currentValue)}</em>
                          )}
                        </span>
                      </li>
                    ))}
                  </ul>
                )}
              </li>
            );
          })}

          {oneOffs.length > 0 && (
            <>
              <li className="fund-section-label">Unnamed / one-off</li>
              {oneOffs.map((r) => {
                const gain = r.currentValue == null ? null : r.currentValue - r.amountInvested;
                return (
                  <li key={r.id} className="row-item" onClick={() => setEditing(r)}>
                    <div className="row-main">
                      <span className="row-title">{r.type || "Investment"}</span>
                      <span className="row-sub">
                        {formatDate(r.date)} · {r.owner}
                        {r.account ? ` · ${r.account}` : ""}
                      </span>
                    </div>
                    <div className="row-figures">
                      <span className="row-amount">
                        {formatMoney(r.currentValue ?? r.amountInvested)}
                      </span>
                      {gain != null && (
                        <span className={`row-gain ${gain >= 0 ? "gain" : "loss"}`}>
                          {gain >= 0 ? "+" : ""}
                          {formatMoney(gain)}
                        </span>
                      )}
                    </div>
                  </li>
                );
              })}
            </>
          )}
        </ul>
      )}

      {editing && listsQ.data && (
        <InvestmentModal
          lists={listsQ.data}
          row={editing === "new" ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={reload}
        />
      )}

      {contributingTo && listsQ.data && (
        <ContributionModal
          lists={listsQ.data}
          fund={contributingTo}
          onClose={() => setContributingTo(null)}
          onSaved={reload}
        />
      )}

      {valuing && (
        <FundValueModal fund={valuing} onClose={() => setValuing(null)} onSaved={reload} />
      )}
    </div>
  );
}

function ContributionModal({
  lists,
  fund,
  onClose,
  onSaved,
}: {
  lists: Lists;
  fund: Fund;
  onClose: () => void;
  onSaved: () => void;
}) {
  // Prefill from the fund's most recent contribution so a monthly top-up is just
  // a date and an amount. A new contribution carries its own invested amount as
  // its current value, so adding it raises the fund's current value by exactly
  // that amount (current value is summed across the fund's rows). The market
  // value can still be corrected later via "Set current value".
  const [form, setForm] = useState<InvestmentInput>(() => ({
    date: todayIso(),
    owner: fund.latest.owner || lists.people[0] || "",
    type: fund.latest.type || lists.investmentTypes[0] || "",
    name: fund.name,
    account: fund.latest.account || lists.accounts[0] || "",
    amountInvested: 0,
    currentValue: null,
    notes: "",
  }));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function set<K extends keyof InvestmentInput>(key: K, value: InvestmentInput[K]) {
    setForm((f) => ({ ...f, [key]: value }));
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      // Seed the contribution's current value with its invested amount so the
      // fund's current value goes up by exactly what was just added.
      await api.addInvestment({ ...form, currentValue: form.amountInvested });
      onSaved();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save.");
      setBusy(false);
    }
  }

  return (
    <Modal title={`Add contribution — ${fund.name}`} onClose={onClose}>
      <form className="form" onSubmit={onSubmit}>
        <div className="form-grid">
          <Field label="Date">
            <input type="date" value={form.date} onChange={(e) => set("date", e.target.value)} required />
          </Field>
          <Field label="Amount invested (₹)">
            <input
              type="number"
              min="0"
              step="0.01"
              inputMode="decimal"
              value={form.amountInvested}
              onChange={(e) => set("amountInvested", Number(e.target.value))}
              required
              autoFocus
            />
          </Field>
          <Field label="Owner">
            <select value={form.owner} onChange={(e) => set("owner", e.target.value)}>
              {lists.people.map((p) => (
                <option key={p}>{p}</option>
              ))}
            </select>
          </Field>
          <Field label="Type">
            <select value={form.type} onChange={(e) => set("type", e.target.value)}>
              {lists.investmentTypes.map((t) => (
                <option key={t}>{t}</option>
              ))}
            </select>
          </Field>
          <Field label="Account / platform">
            <select value={form.account} onChange={(e) => set("account", e.target.value)}>
              {lists.accounts.map((a) => (
                <option key={a}>{a}</option>
              ))}
            </select>
          </Field>
        </div>

        <Field label="Notes">
          <textarea value={form.notes} onChange={(e) => set("notes", e.target.value)} rows={2} />
        </Field>

        <p className="form-hint">
          Adds to <strong>{fund.name}</strong> and raises its current value by this amount. Correct
          the market value any time with “Set current value”.
        </p>

        {error && <div className="banner error">{error}</div>}

        <div className="form-actions">
          <div className="spacer" />
          <button type="button" className="btn" onClick={onClose} disabled={busy}>
            Cancel
          </button>
          <button type="submit" className="btn primary" disabled={busy}>
            {busy ? "Saving…" : "Add contribution"}
          </button>
        </div>
      </form>
    </Modal>
  );
}

function FundValueModal({
  fund,
  onClose,
  onSaved,
}: {
  fund: Fund;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [value, setValue] = useState<string>(fund.currentValue != null ? String(fund.currentValue) : "");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await api.setFundCurrentValue(fund.name, value === "" ? null : Number(value));
      onSaved();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save.");
      setBusy(false);
    }
  }

  const preview = value === "" ? null : Number(value) - fund.invested;

  return (
    <Modal title={`Current value — ${fund.name}`} onClose={onClose}>
      <form className="form" onSubmit={onSubmit}>
        <p className="form-hint">
          The whole fund's market value today. Invested so far: {formatMoney(fund.invested)} across{" "}
          {fund.rows.length} contribution{fund.rows.length === 1 ? "" : "s"}.
        </p>
        <Field label="Current value (₹)">
          <input
            type="number"
            min="0"
            step="0.01"
            inputMode="decimal"
            value={value}
            placeholder="Leave blank to clear"
            onChange={(e) => setValue(e.target.value)}
            autoFocus
          />
        </Field>
        {preview != null && (
          <p className={`form-hint ${preview >= 0 ? "gain" : "loss"}`}>
            Gain/loss: {preview >= 0 ? "+" : ""}
            {formatMoney(preview)}
          </p>
        )}

        {error && <div className="banner error">{error}</div>}

        <div className="form-actions">
          <div className="spacer" />
          <button type="button" className="btn" onClick={onClose} disabled={busy}>
            Cancel
          </button>
          <button type="submit" className="btn primary" disabled={busy}>
            {busy ? "Saving…" : "Save value"}
          </button>
        </div>
      </form>
    </Modal>
  );
}

function InvestmentModal({
  lists,
  row,
  onClose,
  onSaved,
}: {
  lists: Lists;
  row: InvestmentRow | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [form, setForm] = useState<InvestmentInput>(() => ({
    date: row?.date ?? todayIso(),
    owner: row?.owner ?? lists.people[0] ?? "",
    type: row?.type ?? lists.investmentTypes[0] ?? "",
    name: row?.name ?? "",
    account: row?.account ?? lists.accounts[0] ?? "",
    amountInvested: row?.amountInvested ?? 0,
    currentValue: row?.currentValue ?? null,
    notes: row?.notes ?? "",
  }));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function set<K extends keyof InvestmentInput>(key: K, value: InvestmentInput[K]) {
    setForm((f) => ({ ...f, [key]: value }));
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      if (row) await api.updateInvestment(row.id, form);
      else await api.addInvestment(form);
      onSaved();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save.");
      setBusy(false);
    }
  }

  async function onDelete() {
    if (!row || !confirm("Delete this investment?")) return;
    setBusy(true);
    setError(null);
    try {
      await api.deleteInvestment(row.id);
      onSaved();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not delete.");
      setBusy(false);
    }
  }

  return (
    <Modal title={row ? "Edit investment" : "Add investment"} onClose={onClose}>
      <form className="form" onSubmit={onSubmit}>
        <div className="form-grid">
          <Field label="Date">
            <input type="date" value={form.date} onChange={(e) => set("date", e.target.value)} required />
          </Field>
          <Field label="Owner">
            <select value={form.owner} onChange={(e) => set("owner", e.target.value)}>
              {lists.people.map((p) => (
                <option key={p}>{p}</option>
              ))}
            </select>
          </Field>
          <Field label="Type">
            <select value={form.type} onChange={(e) => set("type", e.target.value)}>
              {lists.investmentTypes.map((t) => (
                <option key={t}>{t}</option>
              ))}
            </select>
          </Field>
          <Field label="Account / platform">
            <select value={form.account} onChange={(e) => set("account", e.target.value)}>
              {lists.accounts.map((a) => (
                <option key={a}>{a}</option>
              ))}
            </select>
          </Field>
          <Field label="Amount invested (₹)">
            <input
              type="number"
              min="0"
              step="0.01"
              inputMode="decimal"
              value={form.amountInvested}
              onChange={(e) => set("amountInvested", Number(e.target.value))}
              required
            />
          </Field>
          <Field label="Current value (₹)">
            <input
              type="number"
              min="0"
              step="0.01"
              inputMode="decimal"
              value={form.currentValue ?? ""}
              placeholder="Optional"
              onChange={(e) => set("currentValue", e.target.value === "" ? null : Number(e.target.value))}
            />
          </Field>
        </div>

        <Field label="Investment / fund name">
          <input
            value={form.name}
            onChange={(e) => set("name", e.target.value)}
            placeholder="Group monthly contributions under one name"
          />
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
