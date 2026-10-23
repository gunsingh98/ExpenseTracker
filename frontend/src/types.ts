// Mirrors the backend JSON models (com.expensetracker.model.*).

export interface ExpenseRow {
  id: number;
  date: string; // ISO yyyy-MM-dd
  paidBy: string;
  category: string;
  description: string;
  amount: number;
  paymentMethod: string;
  shared: boolean;
  notes: string;
  occasion: string; // trip/occasion tag; "" for an ordinary expense
}

export interface InvestmentRow {
  id: number;
  date: string; // ISO yyyy-MM-dd
  owner: string;
  type: string;
  name: string;
  account: string;
  amountInvested: number;
  currentValue: number | null;
  notes: string;
}

export interface IncomeRow {
  id: number;
  date: string; // ISO yyyy-MM-dd
  earner: string;
  incomeType: string;
  source: string;
  amount: number;
  receivedIn: string;
  notes: string;
}

export interface BalanceRow {
  id: number;
  asOnDate: string; // ISO yyyy-MM-dd
  owner: string;
  accountType: string;
  institution: string;
  balance: number; // signed — loans / credit-card dues are negative
  notes: string;
}

export interface Lists {
  categories: string[];
  paymentMethods: string[];
  people: string[];
  investmentTypes: string[];
  accounts: string[];
  incomeTypes: string[];
  accountTypes: string[];
  occasions: string[];
}

export interface ExpenseInput {
  date: string;
  paidBy: string;
  category: string;
  description: string;
  amount: number;
  paymentMethod: string;
  shared: boolean;
  notes: string;
  occasion: string; // "" for an ordinary (non-occasion) expense
}

export interface InvestmentInput {
  date: string;
  owner: string;
  type: string;
  name: string;
  account: string;
  amountInvested: number;
  currentValue: number | null;
  notes: string;
}

export interface FundValueInput {
  name: string;
  currentValue: number | null;
}

export interface IncomeInput {
  date: string;
  earner: string;
  incomeType: string;
  source: string;
  amount: number;
  receivedIn: string;
  notes: string;
}

export interface BalanceInput {
  asOnDate: string;
  owner: string;
  accountType: string;
  institution: string;
  balance: number; // may be negative (loans / credit-card dues)
  notes: string;
}

export interface AuditRow {
  id: number;
  timestamp: string; // ISO-8601 instant, e.g. 2026-10-15T09:30:00.123Z
  username: string;
  action: "CREATE" | "UPDATE" | "DELETE" | "REPLACE";
  entityType: "EXPENSE" | "INVESTMENT" | "INCOME" | "BALANCE" | "LIST";
  entityId: string | null;
  oldValue: string | null; // JSON snapshot, null for CREATE
  newValue: string | null; // JSON snapshot, null for DELETE
}

export interface NamedAmount {
  name: string;
  amount: number;
}

export interface Allocation {
  name: string;
  invested: number;
  currentValue: number;
  gainLoss: number;
}

export interface TypeFunds {
  type: string;
  funds: Allocation[];
}

export interface MonthPoint {
  month: number;
  label: string;
  expenses: number;
  invested: number;
  income: number;
  savings: number;
  expenseByCategory: NamedAmount[];
}

export interface NetWorth {
  asOnDate: string | null; // ISO yyyy-MM-dd, null when no balances recorded
  bankAndCash: number;
  otherAssets: number;
  investments: number;
  loans: number; // negative
  total: number;
}

export interface DashboardSummary {
  year: number;
  availableYears: number[];
  expensesThisYear: number;
  investedThisYear: number;
  currentInvestmentValue: number;
  investmentGainLoss: number;
  incomeThisYear: number;
  savingsThisYear: number;
  savingsRate: number; // 0..1
  expenseByCategory: NamedAmount[];
  expenseByOwner: NamedAmount[];
  expenseByOccasion: NamedAmount[];
  investmentByType: Allocation[];
  investmentByOwner: Allocation[];
  investmentFundsByType: TypeFunds[];
  monthlyTrend: MonthPoint[];
  netWorth: NetWorth;
}

// The list dropdown columns, matching the backend's ListColumn enum (kebab in the URL).
export type ListColumn =
  | "categories"
  | "payment-methods"
  | "people"
  | "investment-types"
  | "accounts"
  | "income-types"
  | "account-types"
  | "occasions";
