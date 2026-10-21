package com.expensetracker.service;

import com.expensetracker.data.BalanceDao;
import com.expensetracker.data.ExpenseDao;
import com.expensetracker.data.IncomeDao;
import com.expensetracker.data.InvestmentDao;
import com.expensetracker.model.BalanceInput;
import com.expensetracker.model.DashboardSummary;
import com.expensetracker.model.ExpenseInput;
import com.expensetracker.model.IncomeInput;
import com.expensetracker.model.InvestmentInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Verifies the SUMIFS-mirroring aggregation against hand-computed totals, driven
 * by real SQLite-backed DAOs (data inserted via the normal {@code *Input} path).
 */
class DashboardServiceTest {

    private ExpenseDao expenseDao;
    private InvestmentDao investmentDao;
    private IncomeDao incomeDao;
    private BalanceDao balanceDao;
    private DashboardService service;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + tempDir.resolve("tracker.db"));
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        expenseDao = new ExpenseDao(jdbc);
        investmentDao = new InvestmentDao(jdbc);
        incomeDao = new IncomeDao(jdbc);
        balanceDao = new BalanceDao(jdbc);
        expenseDao.initSchema();
        investmentDao.initSchema();
        incomeDao.initSchema();
        balanceDao.initSchema();
        service = new DashboardService(expenseDao, investmentDao, incomeDao, balanceDao);
    }

    @Test
    void aggregatesExpensesAndInvestmentsForTheRequestedYear() {
        expense(LocalDate.of(2026, 1, 10), "Me", "Groceries", 1000);
        expense(LocalDate.of(2026, 1, 20), "Husband", "Groceries", 500);
        expense(LocalDate.of(2026, 3, 5), "Me", "Rent", 2000);
        expense(LocalDate.of(2025, 12, 31), "Me", "Rent", 9999); // different year, excluded

        investment(LocalDate.of(2026, 2, 1), "Joint", "Mutual Funds", 50000, 62000.0);
        investment(LocalDate.of(2026, 2, 1), "Me", "Stocks", 20000, null); // not yet valued

        DashboardSummary s = service.summary(2026);

        assertThat(s.year()).isEqualTo(2026);
        assertThat(s.availableYears()).containsExactly(2026, 2025);
        assertThat(s.expensesThisYear()).isEqualTo(3500.0); // 1000 + 500 + 2000
        assertThat(s.investedThisYear()).isEqualTo(70000.0); // 50000 + 20000
        assertThat(s.currentInvestmentValue()).isEqualTo(62000.0); // only the valued one
        assertThat(s.investmentGainLoss()).isEqualTo(12000.0); // 62000 - 50000

        assertThat(s.expenseByCategory())
                .extracting(DashboardSummary.NamedAmount::name, DashboardSummary.NamedAmount::amount)
                .containsExactly( // sorted by amount desc
                        org.assertj.core.groups.Tuple.tuple("Rent", 2000.0),
                        org.assertj.core.groups.Tuple.tuple("Groceries", 1500.0));

        assertThat(s.expenseByOwner())
                .extracting(DashboardSummary.NamedAmount::name, DashboardSummary.NamedAmount::amount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Me", 3000.0),
                        org.assertj.core.groups.Tuple.tuple("Husband", 500.0));
    }

    @Test
    void aggregatesExpensesByOccasionExcludingUntaggedRows() {
        occasionExpense(LocalDate.of(2026, 1, 5), "Travel / Vacation", 15000, "Goa Trip");
        occasionExpense(LocalDate.of(2026, 1, 6), "Eating Out", 5000, "Goa Trip");
        occasionExpense(LocalDate.of(2026, 2, 1), "Gifting", 8000, "Diwali 2026");
        expense(LocalDate.of(2026, 1, 10), "Me", "Groceries", 1000); // untagged, excluded
        occasionExpense(LocalDate.of(2025, 12, 1), "Eating Out", 9999, "Goa Trip"); // other year, excluded

        DashboardSummary s = service.summary(2026);

        assertThat(s.expenseByOccasion())
                .extracting(DashboardSummary.NamedAmount::name, DashboardSummary.NamedAmount::amount)
                .containsExactly( // sorted by amount desc; untagged "Groceries" absent
                        org.assertj.core.groups.Tuple.tuple("Goa Trip", 20000.0),
                        org.assertj.core.groups.Tuple.tuple("Diwali 2026", 8000.0));
    }

    @Test
    void groupsFundsWithinEachInvestmentType() {
        // Two funds under Mutual Funds (one with two monthly contributions), one under Stocks.
        namedInvestment(LocalDate.of(2026, 1, 1), "Mutual Funds", "PPFAS", 5000, 6000.0);
        namedInvestment(LocalDate.of(2026, 2, 1), "Mutual Funds", "PPFAS", 5000, 7000.0);
        namedInvestment(LocalDate.of(2026, 1, 1), "Mutual Funds", "Nippon", 3000, 3500.0);
        namedInvestment(LocalDate.of(2026, 1, 1), "Stocks", "Infy", 2000, 2500.0);

        DashboardSummary s = service.summary(2026);

        assertThat(s.investmentFundsByType())
                .extracting(DashboardSummary.TypeFunds::type)
                .containsExactly("Mutual Funds", "Stocks"); // types ordered by total current value desc

        DashboardSummary.TypeFunds mf = s.investmentFundsByType().get(0);
        assertThat(mf.funds())
                .extracting(DashboardSummary.Allocation::name, DashboardSummary.Allocation::currentValue)
                .containsExactly( // funds ordered by current value desc; PPFAS contributions summed
                        org.assertj.core.groups.Tuple.tuple("PPFAS", 13000.0),
                        org.assertj.core.groups.Tuple.tuple("Nippon", 3500.0));

        DashboardSummary.TypeFunds stocks = s.investmentFundsByType().get(1);
        assertThat(stocks.funds())
                .extracting(DashboardSummary.Allocation::name, DashboardSummary.Allocation::currentValue)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Infy", 2500.0));
    }

    @Test
    void monthlyTrendHasTwelvePointsWithCorrectBuckets() {
        expense(LocalDate.of(2026, 1, 10), "Me", "Groceries", 1000);
        expense(LocalDate.of(2026, 3, 5), "Me", "Rent", 2000);
        investment(LocalDate.of(2026, 3, 1), "Me", "Stocks", 5000, null);
        income(LocalDate.of(2026, 1, 1), "Me", "Salary", 8000);
        income(LocalDate.of(2026, 3, 1), "Me", "Salary", 8000);

        DashboardSummary s = service.summary(2026);

        assertThat(s.monthlyTrend()).hasSize(12);
        assertThat(s.monthlyTrend().get(0).label()).isEqualTo("Jan");
        assertThat(s.monthlyTrend().get(0).expenses()).isEqualTo(1000.0);
        assertThat(s.monthlyTrend().get(0).income()).isEqualTo(8000.0);
        assertThat(s.monthlyTrend().get(0).savings()).isEqualTo(7000.0); // 8000 - 1000
        assertThat(s.monthlyTrend().get(2).label()).isEqualTo("Mar");
        assertThat(s.monthlyTrend().get(2).expenses()).isEqualTo(2000.0);
        assertThat(s.monthlyTrend().get(2).invested()).isEqualTo(5000.0);
        assertThat(s.monthlyTrend().get(2).savings()).isEqualTo(6000.0); // 8000 - 2000
        assertThat(s.monthlyTrend().get(5).expenses()).isZero(); // June, empty
    }

    @Test
    void aggregatesIncomeAndSavingsForTheYear() {
        expense(LocalDate.of(2026, 1, 10), "Me", "Rent", 30000);
        income(LocalDate.of(2026, 1, 1), "Me", "Salary", 100000);
        income(LocalDate.of(2025, 1, 1), "Me", "Salary", 99999); // other year, excluded

        DashboardSummary s = service.summary(2026);

        assertThat(s.incomeThisYear()).isEqualTo(100000.0);
        assertThat(s.savingsThisYear()).isEqualTo(70000.0); // 100000 - 30000
        assertThat(s.savingsRate()).isCloseTo(0.70, within(1e-9));
    }

    @Test
    void netWorthUsesOnlyLatestSnapshotAndSubtractsLoans() {
        investment(LocalDate.of(2026, 1, 1), "Me", "Stocks", 50000, 60000.0);
        // older snapshot — must be ignored entirely
        balance(LocalDate.of(2026, 1, 31), "Me", "Bank Account", 10000);
        // latest snapshot (2026-02-28)
        balance(LocalDate.of(2026, 2, 28), "Me", "Bank Account", 40000);
        balance(LocalDate.of(2026, 2, 28), "Joint", "Cash", 5000);
        balance(LocalDate.of(2026, 2, 28), "Me", "Other Asset", 20000);
        balance(LocalDate.of(2026, 2, 28), "Me", "Loan (Liability)", -15000);

        DashboardSummary s = service.summary(2026);

        DashboardSummary.NetWorth nw = s.netWorth();
        assertThat(nw.asOnDate()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(nw.bankAndCash()).isEqualTo(45000.0); // 40000 + 5000 (old 10000 ignored)
        assertThat(nw.otherAssets()).isEqualTo(20000.0);
        assertThat(nw.investments()).isEqualTo(60000.0); // current value
        assertThat(nw.loans()).isEqualTo(-15000.0);
        assertThat(nw.total()).isEqualTo(110000.0); // 45000 + 20000 + 60000 - 15000
    }

    @Test
    void defaultsToMostRecentYearWhenNoneRequested() {
        expense(LocalDate.of(2024, 1, 1), "Me", "Rent", 100);
        expense(LocalDate.of(2026, 1, 1), "Me", "Rent", 200);

        DashboardSummary s = service.summary(null);

        assertThat(s.year()).isEqualTo(2026);
        assertThat(s.expensesThisYear()).isEqualTo(200.0);
    }

    private void expense(LocalDate date, String paidBy, String category, double amount) {
        expenseDao.insert(new ExpenseInput(date, paidBy, category, "", amount, "UPI", false, "", null));
    }

    private void occasionExpense(LocalDate date, String category, double amount, String occasion) {
        expenseDao.insert(new ExpenseInput(date, "Me", category, "", amount, "UPI", false, "", occasion));
    }

    private void investment(LocalDate date, String owner, String type, double invested, Double current) {
        investmentDao.insert(new InvestmentInput(
                date, owner, type, type + " holding", "Brokerage", invested, current, ""));
    }

    private void namedInvestment(LocalDate date, String type, String name, double invested, Double current) {
        investmentDao.insert(new InvestmentInput(
                date, "Me", type, name, "Brokerage", invested, current, ""));
    }

    private void income(LocalDate date, String earner, String type, double amount) {
        incomeDao.insert(new IncomeInput(date, earner, type, "", amount, "Bank Account", ""));
    }

    private void balance(LocalDate date, String owner, String accountType, double balance) {
        balanceDao.insert(new BalanceInput(date, owner, accountType, "", balance, ""));
    }
}
