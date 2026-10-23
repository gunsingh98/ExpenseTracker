package com.expensetracker.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Everything the dashboard renders for a selected year, computed in Java from
 * the raw rows (mirrors the workbook's SUMIFS logic, independent of Excel).
 */
public record DashboardSummary(
        int year,
        List<Integer> availableYears,
        double expensesThisYear,
        double investedThisYear,
        double currentInvestmentValue,
        double investmentGainLoss,
        double incomeThisYear,
        double savingsThisYear,
        double savingsRate,
        List<NamedAmount> expenseByCategory,
        List<NamedAmount> expenseByOwner,
        List<NamedAmount> expenseByOccasion,
        List<Allocation> investmentByType,
        List<Allocation> investmentByOwner,
        List<TypeFunds> investmentFundsByType,
        List<MonthPoint> monthlyTrend,
        NetWorth netWorth
) {
    public record NamedAmount(String name, double amount) {
    }

    public record Allocation(String name, double invested, double currentValue, double gainLoss) {
    }

    /** The funds (grouped by name) that make up one investment type, for the drill-down pie. */
    public record TypeFunds(String type, List<Allocation> funds) {
    }

    /** One month of the trend; {@code expenseByCategory} drives the dashboard's per-month category pie. */
    public record MonthPoint(int month, String label, double expenses, double invested,
                             double income, double savings, List<NamedAmount> expenseByCategory) {
    }

    /**
     * Net worth from the latest Balances snapshot plus current investment value,
     * mirroring the Balances sheet's Net-Worth block. {@code loans} is negative.
     */
    public record NetWorth(LocalDate asOnDate, double bankAndCash, double otherAssets,
                           double investments, double loans, double total) {
    }
}
