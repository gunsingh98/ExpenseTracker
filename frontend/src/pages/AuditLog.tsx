import { useMemo, useState } from "react";
import { api } from "../api/client";
import { EmptyState, ErrorBanner, Loading, PageHeader } from "../components/ui";
import { useAsync } from "../hooks";
import type { AuditRow } from "../types";

const ALL = "__all__";

const ENTITY_LABELS: Record<AuditRow["entityType"], string> = {
  EXPENSE: "Expense",
  INVESTMENT: "Investment",
  INCOME: "Income",
  BALANCE: "Balance",
  LIST: "List",
};

const ACTION_LABELS: Record<AuditRow["action"], string> = {
  CREATE: "Created",
  UPDATE: "Updated",
  DELETE: "Deleted",
  REPLACE: "Replaced",
};

export default function AuditLog() {
  const auditQ = useAsync(() => api.audit(), []);
  const [entityFilter, setEntityFilter] = useState<string>(ALL);

  const rows = auditQ.data ?? [];
  const filtered = useMemo(
    () => (entityFilter === ALL ? rows : rows.filter((r) => r.entityType === entityFilter)),
    [rows, entityFilter],
  );

  if (auditQ.loading && !auditQ.data) return <Loading />;
  if (auditQ.error) return <ErrorBanner message={auditQ.error} onRetry={auditQ.reload} />;

  return (
    <div className="page">
      <PageHeader
        title="Audit Log"
        subtitle={`${filtered.length} ${filtered.length === 1 ? "change" : "changes"} · who changed what, and when`}
      />

      <div className="filters">
        <select
          value={entityFilter}
          onChange={(e) => setEntityFilter(e.target.value)}
          aria-label="Entity type"
        >
          <option value={ALL}>All types</option>
          <option value="EXPENSE">Expenses</option>
          <option value="INVESTMENT">Investments</option>
          <option value="LIST">Lists</option>
        </select>
      </div>

      {filtered.length === 0 ? (
        <EmptyState message="No audit entries yet." />
      ) : (
        <ul className="row-list">
          {filtered.map((r) => (
            <li key={r.id} className="row-item audit-item">
              <div className="row-main">
                <span className="row-title">
                  {ACTION_LABELS[r.action]} {ENTITY_LABELS[r.entityType]}
                  {r.entityId ? ` #${r.entityId}` : ""}
                </span>
                <span className="row-sub">
                  {formatTimestamp(r.timestamp)} · {r.username}
                </span>
                <ChangeDetails action={r.action} oldValue={r.oldValue} newValue={r.newValue} />
              </div>
              <span className={`badge action-${r.action.toLowerCase()}`}>{r.action}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function ChangeDetails({
  action,
  oldValue,
  newValue,
}: {
  action: AuditRow["action"];
  oldValue: string | null;
  newValue: string | null;
}) {
  const oldObj = parse(oldValue);
  const newObj = parse(newValue);

  // LIST replace: both sides are string arrays — show what was added/removed.
  if (Array.isArray(oldObj) || Array.isArray(newObj)) {
    const before = (oldObj as unknown[] | null)?.map(String) ?? [];
    const after = (newObj as unknown[] | null)?.map(String) ?? [];
    const added = after.filter((v) => !before.includes(v));
    const removed = before.filter((v) => !after.includes(v));
    if (added.length === 0 && removed.length === 0) {
      return <div className="audit-change muted small">Reordered ({after.length} items)</div>;
    }
    return (
      <div className="audit-change">
        {removed.length > 0 && (
          <div className="small">
            <span className="muted">Removed:</span> <span className="change-old">{removed.join(", ")}</span>
          </div>
        )}
        {added.length > 0 && (
          <div className="small">
            <span className="muted">Added:</span> <span className="change-new">{added.join(", ")}</span>
          </div>
        )}
      </div>
    );
  }

  // UPDATE: show only the fields whose value changed.
  if (action === "UPDATE" && isRecord(oldObj) && isRecord(newObj)) {
    const keys = fieldKeys(oldObj, newObj);
    const changed = keys.filter((k) => display(oldObj[k]) !== display(newObj[k]));
    if (changed.length === 0) {
      return <div className="audit-change muted small">No field values changed.</div>;
    }
    return (
      <div className="audit-change">
        {changed.map((k) => (
          <div key={k} className="small">
            <span className="muted">{k}:</span> <span className="change-old">{display(oldObj[k])}</span>{" "}
            → <span className="change-new">{display(newObj[k])}</span>
          </div>
        ))}
      </div>
    );
  }

  // CREATE / DELETE: show a snapshot of the relevant side.
  const snapshot = isRecord(newObj) ? newObj : isRecord(oldObj) ? oldObj : null;
  if (!snapshot) return null;
  return (
    <div className="audit-change">
      {fieldKeys(snapshot).map((k) => (
        <div key={k} className="small">
          <span className="muted">{k}:</span> {display(snapshot[k])}
        </div>
      ))}
    </div>
  );
}

function formatTimestamp(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString("en-IN", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function parse(json: string | null): unknown {
  if (json == null) return null;
  try {
    return JSON.parse(json);
  } catch {
    return null;
  }
}

function isRecord(v: unknown): v is Record<string, unknown> {
  return v != null && typeof v === "object" && !Array.isArray(v);
}

/** Field keys to display, skipping the internal row id. */
function fieldKeys(...objs: Record<string, unknown>[]): string[] {
  const keys = new Set<string>();
  for (const o of objs) for (const k of Object.keys(o)) if (k !== "id") keys.add(k);
  return [...keys];
}

function display(v: unknown): string {
  if (v == null || v === "") return "—";
  if (typeof v === "boolean") return v ? "Yes" : "No";
  return String(v);
}
