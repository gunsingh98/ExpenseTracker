package com.expensetracker.data;

import java.time.YearMonth;

/**
 * Optional restrictions on the expense list: a month and exact matches on category,
 * payer and payment method. Blank strings mean "any" and are normalised to null.
 */
public record ExpenseFilter(YearMonth month, String category, String paidBy, String paymentMethod) {

    public static final ExpenseFilter NONE = new ExpenseFilter(null, null, null, null);

    public ExpenseFilter {
        category = blankToNull(category);
        paidBy = blankToNull(paidBy);
        paymentMethod = blankToNull(paymentMethod);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
