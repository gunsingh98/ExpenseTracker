package com.expensetracker.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Payload for creating/updating an income row. */
public record IncomeInput(
        @NotNull LocalDate date,
        @NotNull @Size(max = 100) String earner,
        @NotNull @Size(max = 100) String incomeType,
        @Size(max = 1000) String source,
        @PositiveOrZero double amount,
        @Size(max = 100) String receivedIn,
        @Size(max = 2000) String notes
) {
}
