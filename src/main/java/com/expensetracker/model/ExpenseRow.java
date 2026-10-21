package com.expensetracker.model;

import java.time.LocalDate;

/**
 * One row from the Excel "Expenses" sheet. {@code id} is the 1-based Excel row
 * number, used as a stable handle for edit/delete within a loaded workbook.
 *
 * <p>{@code occasion} tags the expense to a trip/occasion (e.g. "Goa Trip 2026");
 * blank/null means an ordinary, untagged expense.
 */
public record ExpenseRow(
        int id,
        LocalDate date,
        String paidBy,
        String category,
        String description,
        double amount,
        String paymentMethod,
        boolean shared,
        String notes,
        String occasion
) {
    public int year() {
        return date != null ? date.getYear() : 0;
    }

    public int month() {
        return date != null ? date.getMonthValue() : 0;
    }
}
