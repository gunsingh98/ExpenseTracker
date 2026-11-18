package com.expensetracker.data;

/**
 * The dropdown lists stored in the {@code list_value} table. The names map to
 * the REST path segments (e.g. {@code payment-methods} → {@code PAYMENT_METHODS})
 * and are used as the {@code list_name} key for each list's rows.
 */
public enum ListColumn {
    CATEGORIES,
    PAYMENT_METHODS,
    PEOPLE,
    INVESTMENT_TYPES,
    ACCOUNTS,
    INCOME_TYPES,
    ACCOUNT_TYPES,
    OCCASIONS
}
