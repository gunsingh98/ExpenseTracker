package com.expensetracker.data;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

/**
 * SQL fragments shared by the DAOs' dashboard aggregate queries, so every table
 * groups and filters the same way.
 */
final class Aggregates {

    private Aggregates() {
    }

    /** Characters stripped from labels: space, tab, LF, CR (what Java's trim removes in practice). */
    private static final String TRIM_CHARS = "char(32, 9, 10, 13)";

    /** {@code column} trimmed, NULL as empty — the grouping key for a fund name or account type. */
    static String key(String column) {
        return "ifnull(trim(" + column + ", " + TRIM_CHARS + "), '')";
    }

    /** {@code column} trimmed, with NULL/blank shown as "Other" — a dashboard bucket label. */
    static String label(String column) {
        return "coalesce(nullif(trim(" + column + ", " + TRIM_CHARS + "), ''), 'Other')";
    }

    /** The month number (1-12) of an ISO {@code yyyy-MM-dd} date column. */
    static String month(String column) {
        return "CAST(substr(" + column + ", 6, 2) AS INTEGER)";
    }

    /**
     * {@code column} within {@code year}, as a half-open ISO range (two bind values,
     * from {@link #yearBounds}) rather than a function on the column, so the date
     * index stays usable.
     */
    static String inYear(String column) {
        return column + " >= ? AND " + column + " < ?";
    }

    static Object[] yearBounds(int year) {
        return new Object[] {year + "-01-01", (year + 1) + "-01-01"};
    }

    /** {@code SUM(amountColumn)} per month of {@code year}, indexed 1-12 (index 0 unused). */
    static double[] sumByMonth(JdbcTemplate jdbc, String table, String amountColumn, int year) {
        double[] totals = new double[13];
        jdbc.query("SELECT " + month("date") + " AS month, SUM(" + amountColumn + ") AS amount FROM " + table
                        + " WHERE " + inYear("date") + " GROUP BY 1",
                (RowCallbackHandler) rs -> totals[rs.getInt("month")] = rs.getDouble("amount"),
                yearBounds(year));
        return totals;
    }

    /** Distinct years present in a date column; a covering scan of its date index. */
    static String distinctYears(String table, String column) {
        return "SELECT DISTINCT CAST(substr(" + column + ", 1, 4) AS INTEGER) FROM " + table;
    }
}
