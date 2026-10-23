import { useState } from "react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ComposedChart,
  Legend,
  Line,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { api } from "../api/client";
import { ErrorBanner, Loading, PageHeader } from "../components/ui";
import { colorAt, formatDate, formatMoney } from "../format";
import { useAsync } from "../hooks";
import type { MonthPoint, NetWorth, TypeFunds } from "../types";

export default function Dashboard() {
  const [year, setYear] = useState<number | undefined>(undefined);
  const { data, loading, error, reload } = useAsync(() => api.dashboard(year), [year]);

  if (loading && !data) return <Loading />;
  if (error) return <ErrorBanner message={error} onRetry={reload} />;
  if (!data) return null;

  const compactMoney = (v: number) => formatMoney(v);

  return (
    <div className="page">
      <PageHeader
        title="Dashboard"
        subtitle={`Financial year ${data.year}`}
        actions={
          <select
            value={data.year}
            onChange={(e) => setYear(Number(e.target.value))}
            aria-label="Year"
          >
            {data.availableYears.map((y) => (
              <option key={y} value={y}>
                {y}
              </option>
            ))}
          </select>
        }
      />

      <section className="kpis">
        <Kpi label="Income this year" value={formatMoney(data.incomeThisYear)} tone="gain" />
        <Kpi label="Spent this year" value={formatMoney(data.expensesThisYear)} tone="spend" />
        <Kpi
          label="Savings rate"
          value={`${Math.round(data.savingsRate * 100)}%`}
          tone={data.savingsThisYear >= 0 ? "gain" : "loss"}
        />
        <Kpi label="Invested this year" value={formatMoney(data.investedThisYear)} tone="invest" />
        <Kpi label="Portfolio value" value={formatMoney(data.currentInvestmentValue)} tone="value" />
        <Kpi
          label="Gain / loss"
          value={formatMoney(data.investmentGainLoss)}
          tone={data.investmentGainLoss >= 0 ? "gain" : "loss"}
        />
        <Kpi
          label="Net worth"
          value={formatMoney(data.netWorth.total)}
          tone={data.netWorth.total >= 0 ? "value" : "loss"}
        />
      </section>

      <section className="chart-grid">
        <Card title="Income vs expenses vs savings">
          <ResponsiveContainer width="100%" height={260}>
            <ComposedChart data={data.monthlyTrend} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
              <XAxis dataKey="label" tick={{ fontSize: 12 }} />
              <YAxis tickFormatter={(v) => `${Math.round(v / 1000)}k`} tick={{ fontSize: 12 }} width={44} />
              <Tooltip formatter={(v: number) => compactMoney(v)} />
              <Legend />
              <Bar dataKey="income" name="Income" fill="#16a34a" radius={[4, 4, 0, 0]} />
              <Bar dataKey="expenses" name="Expenses" fill="#dc2626" radius={[4, 4, 0, 0]} />
              <Line
                type="monotone"
                dataKey="savings"
                name="Savings"
                stroke="#2563eb"
                strokeWidth={2}
                dot={false}
              />
            </ComposedChart>
          </ResponsiveContainer>
        </Card>

        <Card title="Monthly trend">
          <ResponsiveContainer width="100%" height={260}>
            <BarChart data={data.monthlyTrend} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
              <XAxis dataKey="label" tick={{ fontSize: 12 }} />
              <YAxis tickFormatter={(v) => `${Math.round(v / 1000)}k`} tick={{ fontSize: 12 }} width={44} />
              <Tooltip formatter={(v: number) => compactMoney(v)} />
              <Legend />
              <Bar dataKey="expenses" name="Expenses" fill="#dc2626" radius={[4, 4, 0, 0]} />
              <Bar dataKey="invested" name="Invested" fill="#16a34a" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </Card>

        <Card title="Net worth breakdown">
          <NetWorthBreakdown netWorth={data.netWorth} />
        </Card>

        <Card title="Expenses by category">
          <ExpenseBreakdown
            byCategory={data.expenseByCategory}
            monthly={data.monthlyTrend}
            total={data.expensesThisYear}
          />
        </Card>

        <Card title="Expenses by person">
          <CategoryPie data={data.expenseByOwner} empty="No expenses recorded this year." />
        </Card>

        <Card title="Expenses by occasion / trip">
          <CategoryPie
            data={data.expenseByOccasion}
            empty="No occasion-tagged expenses this year."
          />
        </Card>

        <Card title="Investment allocation">
          <AllocationList
            rows={data.investmentByType.map((a) => ({ name: a.name, value: a.currentValue }))}
            empty="No investments recorded this year."
          />
        </Card>

        <Card title="Investments by category">
          <FundsByTypePie data={data.investmentFundsByType} />
        </Card>

        <Card title="Investments by person">
          <CategoryPie
            data={data.investmentByOwner.map((a) => ({ name: a.name, amount: a.currentValue }))}
            empty="No investments recorded this year."
          />
        </Card>
      </section>
    </div>
  );
}

function Kpi({ label, value, tone }: { label: string; value: string; tone: string }) {
  return (
    <div className={`kpi kpi-${tone}`}>
      <span className="kpi-label">{label}</span>
      <span className="kpi-value">{value}</span>
    </div>
  );
}

function Card({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="card">
      <h3 className="card-title">{title}</h3>
      {children}
    </div>
  );
}

/**
 * A pie of the funds within a single investment type, with a dropdown to switch
 * types. Only types that have at least one valued fund are offered; the chosen
 * type's funds become the slices. Falls back to the first type if the current
 * selection disappears (e.g. after the data reloads).
 */
function FundsByTypePie({ data }: { data: TypeFunds[] }) {
  const types = data.filter((t) => t.funds.some((f) => f.currentValue > 0));
  const [selected, setSelected] = useState<string>("");

  if (types.length === 0) return <p className="muted">No investments recorded this year.</p>;

  const current = types.find((t) => t.type === selected) ?? types[0];
  const rows = current.funds
    .filter((f) => f.currentValue > 0)
    .map((f) => ({ name: f.name, amount: f.currentValue }));

  return (
    <>
      <div className="chart-control">
        <select
          value={current.type}
          onChange={(e) => setSelected(e.target.value)}
          aria-label="Investment type"
        >
          {types.map((t) => (
            <option key={t.type} value={t.type}>
              {t.type}
            </option>
          ))}
        </select>
      </div>
      <CategoryPie data={rows} empty="No funds in this category." />
    </>
  );
}

/**
 * Expenses by category, toggled between the whole year and a single month picked
 * from a dropdown. Both views draw a category pie.
 * The month defaults to the latest one that has spending.
 */
function ExpenseBreakdown({
  byCategory,
  monthly,
  total,
}: {
  byCategory: { name: string; amount: number }[];
  monthly: MonthPoint[];
  total: number;
}) {
  const [view, setView] = useState<"year" | "month">("year");
  const latestWithSpend = [...monthly].reverse().find((m) => m.expenses > 0)?.month ?? 1;
  const [selectedMonth, setSelectedMonth] = useState<number | null>(null);
  const current = monthly.find((m) => m.month === (selectedMonth ?? latestWithSpend)) ?? monthly[0];
  const showMonth = view === "month" && current != null;

  return (
    <>
      <div className="chart-control chart-control-row">
        <div className="segmented" role="group" aria-label="Expense period">
          <button
            type="button"
            className={view === "year" ? "active" : undefined}
            aria-pressed={view === "year"}
            onClick={() => setView("year")}
          >
            Year
          </button>
          <button
            type="button"
            className={view === "month" ? "active" : undefined}
            aria-pressed={view === "month"}
            onClick={() => setView("month")}
          >
            Month
          </button>
        </div>
        {showMonth && (
          <select
            value={current.month}
            onChange={(e) => setSelectedMonth(Number(e.target.value))}
            aria-label="Month"
          >
            {monthly.map((m) => (
              <option key={m.month} value={m.month}>
                {m.label}
              </option>
            ))}
          </select>
        )}
      </div>
      {showMonth ? (
        <>
          <CategoryPie
            data={current.expenseByCategory}
            empty={`No expenses recorded in ${current.label}.`}
          />
          <div className="alloc-head" style={{ marginTop: 8, fontWeight: 600 }}>
            <span>Total spent in {current.label}</span>
            <span>{formatMoney(current.expenses)}</span>
          </div>
        </>
      ) : (
        <>
          <CategoryPie data={byCategory} empty="No expenses recorded this year." />
          <div className="alloc-head" style={{ marginTop: 8, fontWeight: 600 }}>
            <span>Total spent this year</span>
            <span>{formatMoney(total)}</span>
          </div>
        </>
      )}
    </>
  );
}

function CategoryPie({ data, empty }: { data: { name: string; amount: number }[]; empty: string }) {
  const rows = data.filter((d) => d.amount > 0);
  if (rows.length === 0) return <p className="muted">{empty}</p>;
  return (
    <ResponsiveContainer width="100%" height={260}>
      <PieChart>
        <Pie
          data={rows}
          dataKey="amount"
          nameKey="name"
          cx="50%"
          cy="50%"
          outerRadius={90}
          innerRadius={48}
          paddingAngle={2}
        >
          {rows.map((_, i) => (
            <Cell key={i} fill={colorAt(i)} />
          ))}
        </Pie>
        <Tooltip formatter={(v: number) => formatMoney(v)} />
      </PieChart>
    </ResponsiveContainer>
  );
}

function NetWorthBreakdown({ netWorth }: { netWorth: NetWorth }) {
  if (netWorth.asOnDate == null) {
    return <p className="muted">No balances recorded yet. Add entries on the Balances tab.</p>;
  }
  // Sign-aware: assets are positive, loans negative. Bar widths scale to the
  // largest absolute component so a big loan is as readable as a big asset.
  const rows = [
    { name: "Bank & cash", value: netWorth.bankAndCash },
    { name: "Other assets", value: netWorth.otherAssets },
    { name: "Investments", value: netWorth.investments },
    { name: "Loans", value: netWorth.loans },
  ];
  const scale = Math.max(1, ...rows.map((r) => Math.abs(r.value)));
  return (
    <>
      <p className="muted small">As on {formatDate(netWorth.asOnDate)}</p>
      <ul className="alloc-list">
        {rows.map((r) => {
          const pct = Math.round((Math.abs(r.value) / scale) * 100);
          const negative = r.value < 0;
          return (
            <li key={r.name}>
              <div className="alloc-head">
                <span>{r.name}</span>
                <span className={negative ? "spend" : undefined}>{formatMoney(r.value)}</span>
              </div>
              <div className="alloc-bar">
                <div
                  className="alloc-fill"
                  style={{ width: `${pct}%`, background: negative ? "#dc2626" : "#16a34a" }}
                />
              </div>
            </li>
          );
        })}
      </ul>
      <div className="alloc-head" style={{ marginTop: 8, fontWeight: 600 }}>
        <span>Total net worth</span>
        <span className={netWorth.total < 0 ? "spend" : undefined}>{formatMoney(netWorth.total)}</span>
      </div>
    </>
  );
}

function AllocationList({
  rows,
  empty,
}: {
  rows: { name: string; value: number }[];
  empty: string;
}) {
  const filtered = rows.filter((r) => r.value > 0);
  if (filtered.length === 0) return <p className="muted">{empty}</p>;
  const total = filtered.reduce((s, r) => s + r.value, 0);
  return (
    <ul className="alloc-list">
      {filtered.map((r, i) => {
        const pct = total > 0 ? Math.round((r.value / total) * 100) : 0;
        return (
          <li key={r.name}>
            <div className="alloc-head">
              <span>{r.name}</span>
              <span>{formatMoney(r.value)}</span>
            </div>
            <div className="alloc-bar">
              <div
                className="alloc-fill"
                style={{ width: `${pct}%`, background: colorAt(i) }}
              />
            </div>
          </li>
        );
      })}
    </ul>
  );
}
