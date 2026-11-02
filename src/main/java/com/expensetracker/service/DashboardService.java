package com.expensetracker.service;

import com.expensetracker.data.BalanceDao;
import com.expensetracker.data.ExpenseDao;
import com.expensetracker.data.ExpenseDao.MonthlyGroup;
import com.expensetracker.data.IncomeDao;
import com.expensetracker.data.InvestmentDao;
import com.expensetracker.data.InvestmentDao.FundGroup;
import com.expensetracker.model.DashboardSummary;
import com.expensetracker.model.DashboardSummary.Allocation;
import com.expensetracker.model.DashboardSummary.MonthPoint;
import com.expensetracker.model.DashboardSummary.NamedAmount;
import com.expensetracker.model.DashboardSummary.NetWorth;
import com.expensetracker.model.DashboardSummary.TypeFunds;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * Computes dashboard aggregates, mirroring the workbook's SUMIFS formulas. The
 * database does the summing ({@code GROUP BY} queries in the DAOs) and this class
 * folds the small grouped results into the summary, so a dashboard load costs a
 * few grouped scans of one year's rows rather than reading every table in full.
 */
@Service
public class DashboardService {

    // Account-type literals from the Balances sheet's Net-Worth SUMIFS formulas.
    // Keep in sync with the "Account / Balance Types" list; unknown types are excluded.
    private static final Set<String> BANK_AND_CASH_TYPES = Set.of("Bank Account", "Savings Account", "Cash");
    private static final Set<String> OTHER_ASSET_TYPES = Set.of("Other Asset");
    private static final Set<String> LOAN_TYPES = Set.of("Loan (Liability)", "Credit Card (Liability)");

    /** Largest current value first; ties by name so order never depends on query row order. */
    private static final Comparator<Allocation> BY_CURRENT_VALUE =
            Comparator.comparingDouble(Allocation::currentValue).reversed().thenComparing(Allocation::name);

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
        List<Integer> years = availableYears();
        int year = requestedYear != null ? requestedYear
                : (years.isEmpty() ? LocalDate.now().getYear() : years.get(0));

        List<MonthlyGroup> expenses = expenseDao.sumByMonthCategoryPayer(year);
        double[] investedByMonth = investmentDao.sumInvestedByMonth(year);
        double[] incomeByMonth = incomeDao.sumByMonth(year);
        List<FundGroup> funds = investmentDao.sumByFund();

        double expensesThisYear = expenses.stream().mapToDouble(MonthlyGroup::amount).sum();
        double investedThisYear = Arrays.stream(investedByMonth).sum();
        double incomeThisYear = Arrays.stream(incomeByMonth).sum();
        double savingsThisYear = incomeThisYear - expensesThisYear;
        double savingsRate = incomeThisYear > 0 ? savingsThisYear / incomeThisYear : 0;
        double currentValue = funds.stream().mapToDouble(FundGroup::currentValue).sum();

        return new DashboardSummary(
                year,
                years,
                expensesThisYear,
                investedThisYear,
                currentValue,
                gainLoss(funds),
                incomeThisYear,
                savingsThisYear,
                savingsRate,
                sumBy(expenses, MonthlyGroup::category, MonthlyGroup::amount),
                sumBy(expenses, MonthlyGroup::paidBy, MonthlyGroup::amount),
                sorted(expenseDao.sumByOccasion(year)),
                allocationBy(funds, FundGroup::type),
                allocationBy(funds, FundGroup::owner),
                fundsByType(funds),
                monthlyTrend(expenses, investedByMonth, incomeByMonth),
                netWorth(currentValue));
    }

    /** Years with any expense, investment or income, newest first. */
    private List<Integer> availableYears() {
        TreeSet<Integer> years = new TreeSet<>(Comparator.reverseOrder());
        years.addAll(expenseDao.findYears());
        years.addAll(investmentDao.findYears());
        years.addAll(incomeDao.findYears());
        years.remove(0);
        return new ArrayList<>(years);
    }

    /**
     * Unrealised gain/loss across the funds that have a market value. A fund's value
     * sits on one row (its latest contribution) while its contributions span many, so
     * the gain is taken per fund, value minus everything invested in it, never per
     * row (which would subtract only the valued row's contribution). A fund is its
     * trimmed name, even if its rows differ in type or owner.
     */
    private double gainLoss(List<FundGroup> funds) {
        Map<String, double[]> byFund = new HashMap<>(); // name -> [invested, current, valued?]
        for (FundGroup f : funds) {
            double[] a = byFund.computeIfAbsent(f.name(), x -> new double[3]);
            a[0] += f.invested();
            a[1] += f.currentValue();
            if (f.valued()) {
                a[2] = 1;
            }
        }
        return byFund.values().stream().filter(a -> a[2] == 1).mapToDouble(a -> a[1] - a[0]).sum();
    }

    private <T> List<NamedAmount> sumBy(List<T> rows, Function<T, String> key, ToDoubleFunction<T> value) {
        Map<String, Double> totals = new LinkedHashMap<>();
        for (T row : rows) {
            totals.merge(key.apply(row), value.applyAsDouble(row), Double::sum);
        }
        return sorted(totals);
    }

    /** Largest first; ties by name so the order does not depend on query row order. */
    private static List<NamedAmount> sorted(Map<String, Double> totals) {
        return totals.entrySet().stream()
                .map(e -> new NamedAmount(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingDouble(NamedAmount::amount).reversed()
                        .thenComparing(NamedAmount::name))
                .toList();
    }

    private List<Allocation> allocationBy(List<FundGroup> funds, Function<FundGroup, String> key) {
        Map<String, double[]> acc = new LinkedHashMap<>(); // [invested, current]
        for (FundGroup f : funds) {
            double[] a = acc.computeIfAbsent(key.apply(f), x -> new double[2]);
            a[0] += f.invested();
            a[1] += f.currentValue();
        }
        return toAllocations(acc);
    }

    /**
     * Groups investments by type and, within each type, by fund name, so the
     * dashboard can offer a type dropdown and draw a pie of the funds under the
     * selected type. Funds and types are each ordered by current value, largest
     * first (matching {@link #allocationBy}), and unnamed rows fall under "Other".
     */
    private List<TypeFunds> fundsByType(List<FundGroup> funds) {
        Map<String, Map<String, double[]>> byType = new LinkedHashMap<>(); // type -> name -> [invested, current]
        for (FundGroup f : funds) {
            double[] a = byType.computeIfAbsent(f.type(), x -> new LinkedHashMap<>())
                    .computeIfAbsent(f.name(), x -> new double[2]);
            a[0] += f.invested();
            a[1] += f.currentValue();
        }
        return byType.entrySet().stream()
                .map(e -> new TypeFunds(e.getKey(), toAllocations(e.getValue())))
                .sorted(Comparator.comparingDouble(
                                (TypeFunds t) -> t.funds().stream().mapToDouble(Allocation::currentValue).sum())
                        .reversed().thenComparing(TypeFunds::type))
                .toList();
    }

    private static List<Allocation> toAllocations(Map<String, double[]> acc) {
        return acc.entrySet().stream()
                .map(e -> new Allocation(e.getKey(), e.getValue()[0], e.getValue()[1],
                        e.getValue()[1] - e.getValue()[0]))
                .sorted(BY_CURRENT_VALUE)
                .toList();
    }

    private List<MonthPoint> monthlyTrend(List<MonthlyGroup> expenses, double[] invested, double[] income) {
        List<List<MonthlyGroup>> byMonth = new ArrayList<>(13);
        for (int m = 0; m <= 12; m++) {
            byMonth.add(new ArrayList<>());
        }
        double[] spent = new double[13];
        for (MonthlyGroup g : expenses) {
            byMonth.get(g.month()).add(g);
            spent[g.month()] += g.amount();
        }
        List<MonthPoint> points = new ArrayList<>(12);
        for (int m = 1; m <= 12; m++) {
            String label = Month.of(m).getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            List<MonthlyGroup> month = byMonth.get(m);
            points.add(new MonthPoint(m, label, spent[m], invested[m], income[m], income[m] - spent[m],
                    sumBy(month, MonthlyGroup::category, MonthlyGroup::amount),
                    sumBy(month, MonthlyGroup::paidBy, MonthlyGroup::amount)));
        }
        return points;
    }

    /**
     * Net worth from the latest balance snapshot plus current investment value,
     * mirroring the Balances sheet: only rows on the most recent as-on date count,
     * bucketed by account type. Loans/credit cards are stored negative and sum in
     * as-is. {@code investmentCurrentValue} matches {@code SUM(Investments!H)}.
     */
    private NetWorth netWorth(double investmentCurrentValue) {
        LocalDate latest = balanceDao.findLatestDate();
        double bankAndCash = 0, otherAssets = 0, loans = 0;
        if (latest != null) {
            for (Map.Entry<String, Double> e : balanceDao.sumByAccountType(latest).entrySet()) {
                if (BANK_AND_CASH_TYPES.contains(e.getKey())) {
                    bankAndCash += e.getValue();
                } else if (OTHER_ASSET_TYPES.contains(e.getKey())) {
                    otherAssets += e.getValue();
                } else if (LOAN_TYPES.contains(e.getKey())) {
                    loans += e.getValue();
                }
            }
        }
        double total = bankAndCash + otherAssets + investmentCurrentValue + loans;
        return new NetWorth(latest, bankAndCash, otherAssets, investmentCurrentValue, loans, total);
    }
}
