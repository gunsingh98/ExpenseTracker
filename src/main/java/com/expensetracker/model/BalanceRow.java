package com.expensetracker.model;

import java.time.LocalDate;

/**
 * One row from the Excel "Balances" sheet (a dated account-balance snapshot).
 * {@code id} is the 1-based Excel row number. {@code balance} is signed: loans
 * and credit-card dues are entered as negative amounts, matching the sheet's
 * Net-Worth formulas.
 */
public record BalanceRow(
        int id,
        LocalDate asOnDate,
        String owner,
        String accountType,
        String institution,
        double balance,
        String notes
) {
    public int year() {
        return asOnDate != null ? asOnDate.getYear() : 0;
    }

    public int month() {
        return asOnDate != null ? asOnDate.getMonthValue() : 0;
    }
}
