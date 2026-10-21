package com.expensetracker.model;

import java.time.LocalDate;

/**
 * One row from the Excel "Income" sheet. {@code id} is the 1-based Excel row
 * number, used as a stable handle for edit/delete within a loaded workbook.
 */
public record IncomeRow(
        int id,
        LocalDate date,
        String earner,
        String incomeType,
        String source,
        double amount,
        String receivedIn,
        String notes
) {
    public int year() {
        return date != null ? date.getYear() : 0;
    }

    public int month() {
        return date != null ? date.getMonthValue() : 0;
    }
}
