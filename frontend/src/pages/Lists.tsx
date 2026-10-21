import { useEffect, useState } from "react";
import { api } from "../api/client";
import { ErrorBanner, Loading, PageHeader } from "../components/ui";
import { useAsync } from "../hooks";
import type { ListColumn, Lists } from "../types";

const COLUMNS: { key: ListColumn; field: keyof Lists; label: string; hint: string }[] = [
  { key: "categories", field: "categories", label: "Expense categories", hint: "Used in the expense form." },
  { key: "payment-methods", field: "paymentMethods", label: "Payment methods", hint: "Cash, UPI, Card…" },
  { key: "people", field: "people", label: "People", hint: "Who pays / owns (e.g. Me, Husband, Joint)." },
  { key: "investment-types", field: "investmentTypes", label: "Investment types", hint: "Stocks, Mutual Funds…" },
  { key: "accounts", field: "accounts", label: "Accounts / platforms", hint: "Brokerages, banks…" },
  { key: "income-types", field: "incomeTypes", label: "Income types", hint: "Salary, Bonus, Interest…" },
  {
    key: "account-types",
    field: "accountTypes",
    label: "Account / balance types",
    hint: "Bank Account, Cash, Loan (Liability)…",
  },
  {
    key: "occasions",
    field: "occasions",
    label: "Occasions / trips",
    hint: "Tag expenses to a trip or occasion (e.g. Goa Trip 2026).",
  },
];

export default function ListsPage() {
  const { data, loading, error, reload } = useAsync(() => api.lists(), []);

  if (loading && !data) return <Loading />;
  if (error) return <ErrorBanner message={error} onRetry={reload} />;
  if (!data) return null;

  return (
    <div className="page">
      <PageHeader
        title="Lists"
        subtitle="Edit the dropdown options used across the app. Changes are written back to the workbook."
      />
      <div className="lists-grid">
        {COLUMNS.map((col) => (
          <ListEditor
            key={col.key}
            column={col.key}
            label={col.label}
            hint={col.hint}
            values={data[col.field]}
            onSaved={reload}
          />
        ))}
      </div>
    </div>
  );
}

function ListEditor({
  column,
  label,
  hint,
  values,
  onSaved,
}: {
  column: ListColumn;
  label: string;
  hint: string;
  values: string[];
  onSaved: () => void;
}) {
  const [items, setItems] = useState<string[]>(values);
  const [draft, setDraft] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => setItems(values), [values]);

  const dirty = JSON.stringify(items) !== JSON.stringify(values);

  function add() {
    const v = draft.trim();
    if (!v || items.includes(v)) {
      setDraft("");
      return;
    }
    setItems([...items, v]);
    setDraft("");
  }

  function removeAt(i: number) {
    setItems(items.filter((_, idx) => idx !== i));
  }

  function move(i: number, dir: -1 | 1) {
    const j = i + dir;
    if (j < 0 || j >= items.length) return;
    const next = [...items];
    [next[i], next[j]] = [next[j], next[i]];
    setItems(next);
  }

  async function save() {
    setBusy(true);
    setError(null);
    try {
      await api.updateList(column, items);
      onSaved();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="card list-editor">
      <h3 className="card-title">{label}</h3>
      <p className="muted small">{hint}</p>

      <ul className="chips">
        {items.map((item, i) => (
          <li key={`${item}-${i}`} className="chip">
            <span className="chip-text">{item}</span>
            <span className="chip-actions">
              <button className="icon-btn tiny" onClick={() => move(i, -1)} aria-label="Move up" disabled={i === 0}>
                ↑
              </button>
              <button
                className="icon-btn tiny"
                onClick={() => move(i, 1)}
                aria-label="Move down"
                disabled={i === items.length - 1}
              >
                ↓
              </button>
              <button className="icon-btn tiny" onClick={() => removeAt(i)} aria-label="Remove">
                ✕
              </button>
            </span>
          </li>
        ))}
      </ul>

      <div className="add-row">
        <input
          value={draft}
          placeholder="Add an option…"
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              e.preventDefault();
              add();
            }
          }}
        />
        <button className="btn" onClick={add} type="button">
          Add
        </button>
      </div>

      {error && <div className="banner error">{error}</div>}

      <div className="form-actions">
        <div className="spacer" />
        <button className="btn primary" onClick={save} disabled={!dirty || busy || items.length === 0}>
          {busy ? "Saving…" : "Save"}
        </button>
      </div>
    </div>
  );
}
