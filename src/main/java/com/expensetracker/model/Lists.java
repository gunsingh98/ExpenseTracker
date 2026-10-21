package com.expensetracker.model;

import java.util.List;

/**
 * Dropdown source values from the Excel "Lists" sheet.
 */
public record Lists(
        List<String> categories,
        List<String> paymentMethods,
        List<String> people,
        List<String> investmentTypes,
        List<String> accounts,
        List<String> incomeTypes,
        List<String> accountTypes,
        List<String> occasions
) {
}
