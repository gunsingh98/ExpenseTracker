package com.expensetracker.model;

/** Row count and amount sum over a filtered set of expenses (not just one page). */
public record ExpenseTotals(int count, double total) {
}
