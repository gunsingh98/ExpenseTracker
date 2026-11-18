package com.expensetracker.data;

import com.expensetracker.model.ExpenseInput;
import com.expensetracker.model.ExpenseRow;
import com.expensetracker.model.ExpenseTotals;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** JDBC access to the {@code expense} table. Owns its schema (created at startup). */
@Repository
public class ExpenseDao {

    private static final RowMapper<ExpenseRow> MAPPER = (rs, n) -> new ExpenseRow(
            rs.getInt("id"),
            LocalDate.parse(rs.getString("date")),
            rs.getString("paid_by"),
            rs.getString("category"),
            rs.getString("description"),
            rs.getDouble("amount"),
            rs.getString("payment_method"),
            rs.getInt("shared") != 0,
            rs.getString("notes"),
            rs.getString("occasion"));

    private final JdbcTemplate jdbc;

    public ExpenseDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void initSchema() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS expense (
                    id             INTEGER PRIMARY KEY AUTOINCREMENT,
                    date           TEXT    NOT NULL,
                    paid_by        TEXT,
                    category       TEXT,
                    description    TEXT,
                    amount         REAL    NOT NULL,
                    payment_method TEXT,
                    shared         INTEGER NOT NULL DEFAULT 0,
                    notes          TEXT,
                    occasion       TEXT
                )
                """);
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_expense_date ON expense(date)");
        // Serves the category-filtered page: equality on category, then seek/order by date (and rowid).
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_expense_category_date ON expense(category, date)");
        // Serves the page when sorted by amount.
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_expense_amount ON expense(amount)");
        addColumnIfMissing("occasion", "TEXT");
        // Covers the dashboard's year-scoped GROUP BYs, so they read only this index and never
        // visit the table rows (created after the occasion column, which older tables lack).
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_expense_dashboard"
                + " ON expense(date, category, paid_by, amount, occasion)");
    }

    /**
     * Adds a column to an already-created {@code expense} table if it is absent —
     * SQLite's {@code CREATE TABLE IF NOT EXISTS} won't alter an existing table, so
     * databases created before a column was added get it here (idempotent).
     */
    private void addColumnIfMissing(String column, String type) {
        boolean present = jdbc.queryForList("PRAGMA table_info(expense)").stream()
                .anyMatch(row -> column.equalsIgnoreCase(String.valueOf(row.get("name"))));
        if (!present) {
            jdbc.execute("ALTER TABLE expense ADD COLUMN " + column + " " + type);
        }
    }

    /** All expenses, most recent first. */
    public List<ExpenseRow> findAll() {
        return jdbc.query("SELECT * FROM expense ORDER BY date DESC, id DESC", MAPPER);
    }

    /**
     * Up to {@code limit} expenses strictly after {@code after} (or from the top when
     * {@code null}) in {@code sort} order, restricted by {@code filter}. A keyset seek:
     * cost depends on the page size, not on how deep into the list the page is. A
     * cursor whose key doesn't fit {@code sort} throws {@link IllegalArgumentException}.
     */
    public List<ExpenseRow> findPage(PageCursor after, int limit, ExpenseFilter filter, ExpenseSort sort) {
        List<Object> args = new ArrayList<>();
        String sql = "SELECT * FROM expense" + where(after, sort, filter, args) + sort.orderBy() + " LIMIT ?";
        args.add(limit);
        return jdbc.query(sql, MAPPER, args.toArray());
    }

    /** Count and sum over every expense matching the filter (all pages). */
    public ExpenseTotals totals(ExpenseFilter filter) {
        List<Object> args = new ArrayList<>();
        String sql = "SELECT COUNT(*) AS n, COALESCE(SUM(amount), 0) AS total FROM expense"
                + where(null, ExpenseSort.DATE_DESC, filter, args);
        return jdbc.queryForObject(sql, (rs, n) -> new ExpenseTotals(rs.getInt("n"), rs.getDouble("total")),
                args.toArray());
    }

    /** Distinct {@code yyyy-MM} months that have expenses, newest first (read from the date index). */
    public List<YearMonth> findMonths() {
        return jdbc.queryForList("SELECT DISTINCT substr(date, 1, 7) FROM expense ORDER BY 1 DESC", String.class)
                .stream().map(YearMonth::parse).toList();
    }

    /**
     * Builds the shared WHERE clause, appending bind values to {@code args}. The month
     * is a half-open date range (not {@code strftime} on the column) so the date index
     * is usable; the cursor is a row-value comparison SQLite resolves as an index seek.
     */
    static String where(PageCursor after, ExpenseSort sort, ExpenseFilter filter, List<Object> args) {
        List<String> clauses = new ArrayList<>();
        if (filter.month() != null) {
            clauses.add("date >= ? AND date < ?");
            args.add(filter.month().atDay(1).toString());
            args.add(filter.month().plusMonths(1).atDay(1).toString());
        }
        if (filter.category() != null) {
            clauses.add("category = ?");
            args.add(filter.category());
        }
        if (filter.paidBy() != null) {
            clauses.add("paid_by = ?");
            args.add(filter.paidBy());
        }
        if (filter.paymentMethod() != null) {
            clauses.add("payment_method = ?");
            args.add(filter.paymentMethod());
        }
        if (after != null) {
            clauses.add(sort.seek());
            args.add(sort.bindKey(after.key()));
            args.add(after.id());
        }
        return clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);
    }

    /** One (month, category, payer) bucket of a year's spend; blank labels read "Other". */
    public record MonthlyGroup(int month, String category, String paidBy, double amount) {
    }

    /**
     * {@code year}'s spend summed per (month, category, payer): the finest grain the
     * dashboard needs, so every expense KPI and pie is a fold over these few rows.
     */
    public List<MonthlyGroup> sumByMonthCategoryPayer(int year) {
        return jdbc.query("SELECT " + Aggregates.month("date") + " AS month, "
                        + Aggregates.label("category") + " AS category, "
                        + Aggregates.label("paid_by") + " AS paid_by, SUM(amount) AS amount"
                        + " FROM expense WHERE " + Aggregates.inYear("date") + " GROUP BY 1, 2, 3",
                (rs, n) -> new MonthlyGroup(rs.getInt("month"), rs.getString("category"),
                        rs.getString("paid_by"), rs.getDouble("amount")),
                Aggregates.yearBounds(year));
    }

    /** {@code year}'s spend per occasion (trimmed); untagged expenses are left out. */
    public Map<String, Double> sumByOccasion(int year) {
        String occasion = Aggregates.key("occasion");
        Map<String, Double> totals = new LinkedHashMap<>();
        jdbc.query("SELECT " + occasion + " AS occasion, SUM(amount) AS amount FROM expense"
                        + " WHERE " + Aggregates.inYear("date") + " AND " + occasion + " <> '' GROUP BY 1",
                (RowCallbackHandler) rs -> totals.put(rs.getString("occasion"), rs.getDouble("amount")),
                Aggregates.yearBounds(year));
        return totals;
    }

    /** Distinct years that have expenses. */
    public List<Integer> findYears() {
        return jdbc.queryForList(Aggregates.distinctYears("expense", "date"), Integer.class);
    }

    /** The row with {@code id}, or {@code null} if none. */
    public ExpenseRow findById(int id) {
        return jdbc.query("SELECT * FROM expense WHERE id = ?", MAPPER, id)
                .stream().findFirst().orElse(null);
    }

    /** Inserts a new expense, returning its generated id. */
    public int insert(ExpenseInput in) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO expense (date, paid_by, category, description, amount, payment_method, shared, notes, occasion)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            bind(ps, in);
            return ps;
        }, keys);
        return Objects.requireNonNull(keys.getKey(), "no generated key").intValue();
    }

    public void update(int id, ExpenseInput in) {
        jdbc.update("""
                UPDATE expense SET date = ?, paid_by = ?, category = ?, description = ?,
                    amount = ?, payment_method = ?, shared = ?, notes = ?, occasion = ?
                WHERE id = ?
                """,
                in.date().toString(), in.paidBy(), in.category(), in.description(),
                in.amount(), in.paymentMethod(), in.shared() ? 1 : 0, in.notes(), in.occasion(), id);
    }

    public void delete(int id) {
        jdbc.update("DELETE FROM expense WHERE id = ?", id);
    }

    private static void bind(PreparedStatement ps, ExpenseInput in) throws SQLException {
        ps.setString(1, in.date().toString());
        ps.setString(2, in.paidBy());
        ps.setString(3, in.category());
        ps.setString(4, in.description());
        ps.setDouble(5, in.amount());
        ps.setString(6, in.paymentMethod());
        ps.setInt(7, in.shared() ? 1 : 0);
        ps.setString(8, in.notes());
        ps.setString(9, in.occasion());
    }
}
