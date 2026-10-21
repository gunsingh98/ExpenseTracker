package com.expensetracker.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Payload for creating/updating an expense row. */
public record ExpenseInput(
        @NotNull LocalDate date,
        @NotNull @Size(max = 100) String paidBy,
        @NotNull @Size(max = 100) String category,
        @Size(max = 1000) String description,
        @PositiveOrZero double amount,
        @Size(max = 100) String paymentMethod,
        boolean shared,
        @Size(max = 2000) String notes,
        /** Optional trip/occasion tag; blank/null means an ordinary expense. */
        @Size(max = 100) String occasion
) {
}
