package com.expensetracker.data;

import com.expensetracker.model.ExpenseInput;
import com.expensetracker.model.ExpenseRow;
import com.expensetracker.model.ExpenseTotals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises {@link ExpenseDao} against a real SQLite file in a {@link TempDir}. */
class ExpenseDaoTest {

    private ExpenseDao dao;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + tempDir.resolve("tracker.db"));
        jdbc = new JdbcTemplate(ds);
        dao = new ExpenseDao(jdbc);
        dao.initSchema();
    }

    @Test
    void initSchemaIsIdempotent() {
        dao.initSchema();
        assertThat(dao.findAll()).isEmpty();
    }

    @Test
    void insertAssignsIdAndRoundTrips() {
        int id = dao.insert(new ExpenseInput(
                LocalDate.of(2026, 3, 15), "Me", "Groceries", "Milk", 150.5, "UPI", true, "note", "Goa Trip"));
        assertThat(id).isPositive();

        ExpenseRow row = dao.findById(id);
        assertThat(row).isNotNull();
        assertThat(row.date()).isEqualTo(LocalDate.of(2026, 3, 15));
        assertThat(row.paidBy()).isEqualTo("Me");
        assertThat(row.category()).isEqualTo("Groceries");
        assertThat(row.description()).isEqualTo("Milk");
        assertThat(row.amount()).isEqualTo(150.5);
        assertThat(row.paymentMethod()).isEqualTo("UPI");
        assertThat(row.shared()).isTrue();
        assertThat(row.notes()).isEqualTo("note");
        assertThat(row.occasion()).isEqualTo("Goa Trip");
    }

    @Test
    void findAllOrdersByDateThenIdDescending() {
        int older = dao.insert(input(LocalDate.of(2026, 1, 1)));
        int newer = dao.insert(input(LocalDate.of(2026, 2, 1)));
        int sameDay = dao.insert(input(LocalDate.of(2026, 2, 1)));

        assertThat(dao.findAll()).extracting(ExpenseRow::id)
                .containsExactly(sameDay, newer, older);
    }

    @Test
    void updateChangesFields() {
        int id = dao.insert(input(LocalDate.of(2026, 1, 1)));
        dao.update(id, new ExpenseInput(
                LocalDate.of(2026, 1, 2), "Husband", "Rent", "flat", 2000, "Cash", false, "z", null));

        ExpenseRow row = dao.findById(id);
        assertThat(row.category()).isEqualTo("Rent");
        assertThat(row.amount()).isEqualTo(2000);
        assertThat(row.shared()).isFalse();
        assertThat(row.occasion()).isNull();
    }

    @Test
    void deleteRemovesRow() {
        int id = dao.insert(input(LocalDate.of(2026, 1, 1)));
        dao.delete(id);
        assertThat(dao.findById(id)).isNull();
        assertThat(dao.findAll()).isEmpty();
    }

    @Test
    void findByIdReturnsNullWhenMissing() {
        assertThat(dao.findById(999)).isNull();
    }

    @Test
    void pagesWalkEveryRowOnceInFindAllOrder() {
        // Several rows per date, so the id tie-breaker in the cursor matters.
        for (int i = 0; i < 23; i++) {
            dao.insert(input(LocalDate.of(2026, 1, 1 + i % 5)));
        }
        List<Integer> walked = walk(4, ExpenseFilter.NONE, ExpenseSort.DATE_DESC);
        assertThat(walked).containsExactlyElementsOf(dao.findAll().stream().map(ExpenseRow::id).toList());
    }

    @Test
    void insertMidWalkNeitherSkipsNorRepeats() {
        for (int i = 0; i < 10; i++) {
            dao.insert(input(LocalDate.of(2026, 1, 10 + i)));
        }
        List<ExpenseRow> first = dao.findPage(null, 4, ExpenseFilter.NONE, ExpenseSort.DATE_DESC);
        dao.insert(input(LocalDate.of(2026, 2, 1))); // newer than everything already seen

        List<Integer> seen = new ArrayList<>(first.stream().map(ExpenseRow::id).toList());
        ExpenseRow last = first.getLast();
        seen.addAll(dao.findPage(new PageCursor(last.date().toString(), last.id()), 100, ExpenseFilter.NONE, ExpenseSort.DATE_DESC)
                .stream().map(ExpenseRow::id).toList());

        assertThat(seen).hasSize(10).doesNotHaveDuplicates();
    }

    @Test
    void pageAndTotalsApplyMonthAndCategoryFilters() {
        dao.insert(input(LocalDate.of(2026, 2, 28)));
        int marchGroceries = dao.insert(input(LocalDate.of(2026, 3, 1)));
        dao.insert(new ExpenseInput(LocalDate.of(2026, 3, 31), "Me", "Rent", "x", 900, "UPI", false, "", null));
        dao.insert(input(LocalDate.of(2026, 4, 1)));

        assertThat(dao.findPage(null, 10, new ExpenseFilter(YearMonth.of(2026, 3), "Groceries", null, null), ExpenseSort.DATE_DESC))
                .extracting(ExpenseRow::id).containsExactly(marchGroceries);
        assertThat(dao.totals(new ExpenseFilter(YearMonth.of(2026, 3), null, null, null))).isEqualTo(new ExpenseTotals(2, 1000));
        assertThat(dao.totals(new ExpenseFilter(null, "Groceries", null, null))).isEqualTo(new ExpenseTotals(3, 300));
        assertThat(dao.totals(new ExpenseFilter(YearMonth.of(2025, 1), null, null, null))).isEqualTo(new ExpenseTotals(0, 0));
    }

    @Test
    void findMonthsListsDistinctMonthsNewestFirst() {
        dao.insert(input(LocalDate.of(2026, 1, 5)));
        dao.insert(input(LocalDate.of(2026, 3, 5)));
        dao.insert(input(LocalDate.of(2026, 3, 9)));
        assertThat(dao.findMonths()).containsExactly(YearMonth.of(2026, 3), YearMonth.of(2026, 1));
    }

    @Test
    void cursorRoundTripsAndRejectsGarbage() {
        PageCursor c = new PageCursor("2026-03-01", 42);
        assertThat(PageCursor.decode(c.encode())).isEqualTo(c);
        assertThatThrownBy(() -> PageCursor.decode("not-a-cursor")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PageCursor.decode("!!")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pageAndTotalsApplyPayerAndPaymentMethodFilters() {
        dao.insert(input(LocalDate.of(2026, 3, 1)));
        int herCard = dao.insert(new ExpenseInput(LocalDate.of(2026, 3, 2), "Her", "Groceries", "x", 250, "Card",
                false, "", null));
        dao.insert(new ExpenseInput(LocalDate.of(2026, 3, 3), "Her", "Groceries", "x", 50, "UPI", false, "", null));

        ExpenseFilter filter = new ExpenseFilter(null, " ", "Her", "Card");
        assertThat(dao.findPage(null, 10, filter, ExpenseSort.DATE_DESC))
                .extracting(ExpenseRow::id).containsExactly(herCard);
        assertThat(dao.totals(new ExpenseFilter(null, null, "Her", null))).isEqualTo(new ExpenseTotals(2, 300));
    }

    @Test
    void everySortWalksEveryRowOnceInItsOrder() {
        // Repeated dates and amounts, so the id tie-breaker in the cursor matters.
        for (int i = 0; i < 17; i++) {
            dao.insert(new ExpenseInput(LocalDate.of(2026, 1, 1 + i % 4), "Me", "Groceries", "x",
                    10.5 * (i % 3), "UPI", false, "", null));
        }
        Comparator<ExpenseRow> byDate = Comparator.comparing(ExpenseRow::date).thenComparing(ExpenseRow::id);
        Comparator<ExpenseRow> byAmount = Comparator.comparingDouble(ExpenseRow::amount)
                .thenComparing(ExpenseRow::id);
        Map<ExpenseSort, Comparator<ExpenseRow>> expected = Map.of(
                ExpenseSort.DATE_ASC, byDate, ExpenseSort.DATE_DESC, byDate.reversed(),
                ExpenseSort.AMOUNT_ASC, byAmount, ExpenseSort.AMOUNT_DESC, byAmount.reversed());
        expected.forEach((sort, order) -> assertThat(walk(4, ExpenseFilter.NONE, sort)).as(sort.name())
                .containsExactlyElementsOf(dao.findAll().stream().sorted(order).map(ExpenseRow::id).toList()));
    }

    @Test
    void cursorKeyMustFitTheSort() {
        assertThatThrownBy(() -> dao.findPage(new PageCursor("2026-03-01", 1), 10, ExpenseFilter.NONE,
                ExpenseSort.AMOUNT_DESC)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> dao.findPage(new PageCursor("12.5", 1), 10, ExpenseFilter.NONE,
                ExpenseSort.DATE_DESC)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pageQueriesSeekAnIndexWithoutSorting() {
        PageCursor cursor = new PageCursor("2026-03-01", 5);
        assertThat(plan(cursor, ExpenseFilter.NONE, ExpenseSort.DATE_DESC))
                .contains("USING INDEX idx_expense_date").doesNotContain("TEMP B-TREE");
        assertThat(plan(cursor, ExpenseFilter.NONE, ExpenseSort.DATE_ASC))
                .contains("USING INDEX idx_expense_date").doesNotContain("TEMP B-TREE");
        assertThat(plan(cursor, new ExpenseFilter(null, "Rent", null, null), ExpenseSort.DATE_DESC))
                .contains("USING INDEX idx_expense_category_date").doesNotContain("TEMP B-TREE");
        assertThat(plan(cursor, new ExpenseFilter(YearMonth.of(2026, 3), null, null, null), ExpenseSort.DATE_DESC))
                .contains("USING INDEX").doesNotContain("TEMP B-TREE");
        PageCursor amount = new PageCursor("120.0", 5);
        assertThat(plan(amount, ExpenseFilter.NONE, ExpenseSort.AMOUNT_DESC))
                .contains("USING INDEX idx_expense_amount").doesNotContain("TEMP B-TREE");
    }

    @Test
    void dashboardAggregatesReadOnlyTheCoveringIndex() {
        String groups = jdbc.queryForList("EXPLAIN QUERY PLAN SELECT " + Aggregates.month("date") + ", "
                + Aggregates.label("category") + ", " + Aggregates.label("paid_by") + ", SUM(amount) FROM expense"
                + " WHERE " + Aggregates.inYear("date") + " GROUP BY 1, 2, 3", (Object[]) Aggregates.yearBounds(2026))
                .toString();
        String occasions = jdbc.queryForList("EXPLAIN QUERY PLAN SELECT " + Aggregates.key("occasion")
                + ", SUM(amount) FROM expense WHERE " + Aggregates.inYear("date") + " AND "
                + Aggregates.key("occasion") + " <> '' GROUP BY 1", (Object[]) Aggregates.yearBounds(2026))
                .toString();
        assertThat(groups).contains("COVERING INDEX idx_expense_dashboard");
        assertThat(occasions).contains("COVERING INDEX idx_expense_dashboard");
    }

    /** Ids from walking every page of {@code size} via the cursor. */
    private List<Integer> walk(int size, ExpenseFilter filter, ExpenseSort sort) {
        List<Integer> ids = new ArrayList<>();
        PageCursor after = null;
        while (true) {
            List<ExpenseRow> page = dao.findPage(after, size, filter, sort);
            page.forEach(r -> ids.add(r.id()));
            if (page.size() < size) {
                return ids;
            }
            ExpenseRow last = page.getLast();
            after = new PageCursor(sort.keyOf(last), last.id());
        }
    }

    private String plan(PageCursor after, ExpenseFilter filter, ExpenseSort sort) {
        List<Object> args = new ArrayList<>();
        String sql = "EXPLAIN QUERY PLAN SELECT * FROM expense" + ExpenseDao.where(after, sort, filter, args)
                + sort.orderBy() + " LIMIT 50";
        return jdbc.queryForList(sql, args.toArray()).stream()
                .map(row -> String.valueOf(row.get("detail"))).collect(Collectors.joining("\n"));
    }

    private static ExpenseInput input(LocalDate date) {
        return new ExpenseInput(date, "Me", "Groceries", "x", 100, "UPI", false, "", null);
    }
}
