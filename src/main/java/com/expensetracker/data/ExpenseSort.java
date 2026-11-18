package com.expensetracker.data;

import com.expensetracker.model.ExpenseRow;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Orderings for the keyset-paginated expense list. Each is {@code (column, id)} in one
 * direction, so {@code id} breaks ties and the cursor stays a single row-value seek.
 */
public enum ExpenseSort {
    DATE_DESC("date", true),
    DATE_ASC("date", false),
    AMOUNT_DESC("amount", true),
    AMOUNT_ASC("amount", false);

    private final String column;
    private final boolean descending;

    ExpenseSort(String column, boolean descending) {
        this.column = column;
        this.descending = descending;
    }

    String orderBy() {
        String dir = descending ? " DESC" : " ASC";
        return " ORDER BY " + column + dir + ", id" + dir;
    }

    /** The WHERE clause selecting rows strictly after a cursor in this order. */
    String seek() {
        return "(" + column + ", id) " + (descending ? "<" : ">") + " (?, ?)";
    }

    /** The cursor key for {@code row}: its sort-column value as text. */
    public String keyOf(ExpenseRow row) {
        return isDate() ? row.date().toString() : Double.toString(row.amount());
    }

    /** Parses a cursor key into a bind value; throws {@link IllegalArgumentException} if it isn't one. */
    Object bindKey(String key) {
        if (!isDate()) {
            return Double.parseDouble(key); // NumberFormatException is an IllegalArgumentException
        }
        try {
            return LocalDate.parse(key).toString();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Malformed cursor", e);
        }
    }

    private boolean isDate() {
        return column.equals("date");
    }
}
