package com.expensetracker.model;

import java.time.LocalDate;

/**
 * One row from the Excel "Investments" sheet. {@code currentValue} may be null
 * (not yet valued); {@code gainLoss} mirrors the sheet's auto column (H - G).
 */
public record InvestmentRow(
        int id,
        LocalDate date,
        String owner,
        String type,
        String name,
        String account,
        double amountInvested,
        Double currentValue,
        String notes
) {
    public Double gainLoss() {
        return currentValue == null ? null : currentValue - amountInvested;
    }

    public int year() {
        return date != null ? date.getYear() : 0;
    }

    public int month() {
        return date != null ? date.getMonthValue() : 0;
    }
}
