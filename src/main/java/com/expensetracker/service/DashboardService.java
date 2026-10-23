package com.expensetracker.service;

import com.expensetracker.model.BalanceRow;
import com.expensetracker.model.DashboardSummary;
import com.expensetracker.model.DashboardSummary.Allocation;
import com.expensetracker.model.DashboardSummary.MonthPoint;
import com.expensetracker.model.DashboardSummary.NamedAmount;
import com.expensetracker.model.DashboardSummary.NetWorth;
import com.expensetracker.model.ExpenseRow;
import com.expensetracker.model.IncomeRow;
import com.expensetracker.model.InvestmentRow;
import com.expensetracker.data.BalanceDao;
import com.expensetracker.data.ExpenseDao;
import com.expensetracker.data.IncomeDao;
import com.expensetracker.data.InvestmentDao;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Computes dashboard aggregates from the cached rows, mirroring the workbook's
 * SUMIFS formulas. All arithmetic is in Java so the UI never waits on Excel.
 */
@Service
public class DashboardService {

    // Account-type literals from the Balances sheet's Net-Worth SUMIFS formulas.
    // Keep in sync with the "Account / Balance Types" list; unknown types are excluded.
    private static final Set<String> BANK_AND_CASH_TYPES = Set.of("Bank Account", "Savings Account", "Cash");
    private static final Set<String> OTHER_ASSET_TYPES = Set.of("Other Asset");
    private static final Set<String> LOAN_TYPES = Set.of("Loan (Liability)", "Credit Card (Liability)");

    private final ExpenseDao expenseDao;
    private final InvestmentDao investmentDao;
    private final IncomeDao incomeDao;
    private final BalanceDao balanceDao;

    public DashboardService(ExpenseDao expenseDao, InvestmentDao investmentDao,
                            IncomeDao incomeDao, BalanceDao balanceDao) {
        this.expenseDao = expenseDao;
        this.investmentDao = investmentDao;
        this.incomeDao = incomeDao;
        this.balanceDao = balanceDao;
    }

    public DashboardSummary summary(Integer requestedYear) {
        List<ExpenseRow> expenses = expenseDao.findAll();
        List<InvestmentRow> investments = investmentDao.findAll();
        List<IncomeRow> income = incomeDao.findAll();
        List<BalanceRow> balances = balanceDao.findAll();

        List<Integer> years = availableYears(expenses, investments, income);
        int year = requestedYear != null ? requestedYear
                : (years.isEmpty() ? LocalDate.now().getYear() : years.get(0));

        List<ExpenseRow> yearExpenses = expenses.stream().filter(e -> e.year() == year).toList();
        List<InvestmentRow> yearInvestments = investments.stream().filter(i -> i.year() == year).toList();
        List<IncomeRow> yearIncome = income.stream().filter(i -> i.year() == year).toList();

        double expensesThisYear = yearExpenses.stream().mapToDouble(ExpenseRow::amount).sum();
        double investedThisYear = yearInvestments.stream().mapToDouble(InvestmentRow::amountInvested).sum();
        double incomeThisYear = yearIncome.stream().mapToDouble(IncomeRow::amount).sum();
        double savingsThisYear = incomeThisYear - expensesThisYear;
        double savingsRate = incomeThisYear > 0 ? savingsThisYear / incomeThisYear : 0;
        double currentValue = investments.stream()
                .filter(i -> i.currentValue() != null)
                .mapToDouble(InvestmentRow::currentValue).sum();
        double gainLoss = investments.stream()
                .filter(i -> i.currentValue() != null)
                .mapToDouble(i -> i.currentValue() - i.amountInvested()).sum();

        return new DashboardSummary(
                year,
                years,
                expensesThisYear,
                investedThisYear,
                currentValue,
                gainLoss,
                incomeThisYear,
                savingsThisYear,
                savingsRate,
                sumBy(yearExpenses, ExpenseRow::category, ExpenseRow::amount),
                sumBy(yearExpenses, ExpenseRow::paidBy, ExpenseRow::amount),
                sumBy(yearExpenses.stream()
                                .filter(e -> e.occasion() != null && !e.occasion().isBlank()).toList(),
                        ExpenseRow::occasion, ExpenseRow::amount),
                allocationBy(investments, InvestmentRow::type),
                allocationBy(investments, InvestmentRow::owner),
                fundsByType(investments),
                monthlyTrend(year, expenses, investments, income),
                netWorth(balances, currentValue));
    }

    private List<Integer> availableYears(List<ExpenseRow> expenses, List<InvestmentRow> investments,
                                         List<IncomeRow> income) {
        TreeSet<Integer> years = new TreeSet<>(Comparator.reverseOrder());
        expenses.forEach(e -> years.add(e.year()));
        investments.forEach(i -> years.add(i.year()));
        income.forEach(i -> years.add(i.year()));
        years.remove(0);
        return new ArrayList<>(years);
    }

    private <T> List<NamedAmount> sumBy(List<T> rows,
                                        java.util.function.Function<T, String> key,
                                        java.util.function.ToDoubleFunction<T> value) {
        Map<String, Double> totals = new LinkedHashMap<>();
        for (T row : rows) {
            String k = orOther(key.apply(row));
            totals.merge(k, value.applyAsDouble(row), Double::sum);
        }
        return totals.entrySet().stream()
                .map(e -> new NamedAmount(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingDouble(NamedAmount::amount).reversed())
                .collect(Collectors.toList());
    }

    private List<Allocation> allocationBy(List<InvestmentRow> rows,
                                          java.util.function.Function<InvestmentRow, String> key) {
        Map<String, double[]> acc = new LinkedHashMap<>(); // [invested, current]
        for (InvestmentRow row : rows) {
            String k = orOther(key.apply(row));
            double[] a = acc.computeIfAbsent(k, x -> new double[2]);
            a[0] += row.amountInvested();
            if (row.currentValue() != null) {
                a[1] += row.currentValue();
            }
        }
        return acc.entrySet().stream()
                .map(e -> new Allocation(e.getKey(), e.getValue()[0], e.getValue()[1],
                        e.getValue()[1] - e.getValue()[0]))
                .sorted(Comparator.comparingDouble(Allocation::currentValue).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Groups investments by type and, within each type, by fund name — so the
     * dashboard can offer a type dropdown and draw a pie of the funds under the
     * selected type. Funds and types are each ordered by current value, largest
     * first (matching {@link #allocationBy}), and unnamed rows fall under "Other".
     */
    private List<DashboardSummary.TypeFunds> fundsByType(List<InvestmentRow> rows) {
        Map<String, Map<String, double[]>> byType = new LinkedHashMap<>(); // type -> name -> [invested, current]
        for (InvestmentRow row : rows) {
            String type = orOther(row.type());
            String name = orOther(row.name());
            double[] a = byType.computeIfAbsent(type, x -> new LinkedHashMap<>())
                    .computeIfAbsent(name, x -> new double[2]);
            a[0] += row.amountInvested();
            if (row.currentValue() != null) {
                a[1] += row.currentValue();
            }
        }
        return byType.entrySet().stream()
                .map(e -> new DashboardSummary.TypeFunds(e.getKey(), e.getValue().entrySet().stream()
                        .map(f -> new Allocation(f.getKey(), f.getValue()[0], f.getValue()[1],
                                f.getValue()[1] - f.getValue()[0]))
                        .sorted(Comparator.comparingDouble(Allocation::currentValue).reversed())
                        .collect(Collectors.toList())))
                .sorted(Comparator.comparingDouble(
                        (DashboardSummary.TypeFunds t) ->
                                t.funds().stream().mapToDouble(Allocation::currentValue).sum()).reversed())
                .collect(Collectors.toList());
    }

    private List<MonthPoint> monthlyTrend(int year, List<ExpenseRow> expenses,
                                          List<InvestmentRow> investments, List<IncomeRow> income) {
        double[] exp = new double[13];
        double[] inv = new double[13];
        double[] inc = new double[13];
        expenses.stream().filter(e -> e.year() == year).forEach(e -> exp[e.month()] += e.amount());
        investments.stream().filter(i -> i.year() == year).forEach(i -> inv[i.month()] += i.amountInvested());
        income.stream().filter(i -> i.year() == year).forEach(i -> inc[i.month()] += i.amount());
        List<MonthPoint> points = new ArrayList<>(12);
        for (int m = 1; m <= 12; m++) {
            int month = m;
            String label = Month.of(m).getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            List<ExpenseRow> monthExpenses = expenses.stream()
                    .filter(e -> e.year() == year && e.month() == month).toList();
            points.add(new MonthPoint(m, label, exp[m], inv[m], inc[m], inc[m] - exp[m],
                    sumBy(monthExpenses, ExpenseRow::category, ExpenseRow::amount)));
        }
        return points;
    }

    /**
     * Net worth from the latest balance snapshot plus current investment value,
     * mirroring the Balances sheet: only rows on the most recent as-on date count,
     * bucketed by account type. Loans/credit cards are stored negative and sum in
     * as-is. {@code investmentCurrentValue} matches {@code SUM(Investments!H)}.
     */
    private NetWorth netWorth(List<BalanceRow> balances, double investmentCurrentValue) {
        LocalDate latest = balances.stream()
                .map(BalanceRow::asOnDate)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        double bankAndCash = 0, otherAssets = 0, loans = 0;
        if (latest != null) {
            for (BalanceRow b : balances) {
                if (!latest.equals(b.asOnDate())) {
                    continue;
                }
                String type = b.accountType() == null ? "" : b.accountType().trim();
                if (BANK_AND_CASH_TYPES.contains(type)) {
                    bankAndCash += b.balance();
                } else if (OTHER_ASSET_TYPES.contains(type)) {
                    otherAssets += b.balance();
                } else if (LOAN_TYPES.contains(type)) {
                    loans += b.balance();
                }
            }
        }
        double total = bankAndCash + otherAssets + investmentCurrentValue + loans;
        return new NetWorth(latest, bankAndCash, otherAssets, investmentCurrentValue, loans, total);
    }

    private String orOther(String s) {
        return (s == null || s.isBlank()) ? "Other" : s.trim();
    }
}
