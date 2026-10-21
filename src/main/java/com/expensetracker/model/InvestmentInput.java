package com.expensetracker.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Payload for creating/updating an investment row. {@code currentValue} optional. */
public record InvestmentInput(
        @NotNull LocalDate date,
        @NotNull @Size(max = 100) String owner,
        @NotNull @Size(max = 100) String type,
        @Size(max = 200) String name,
        @Size(max = 100) String account,
        @PositiveOrZero double amountInvested,
        Double currentValue,
        @Size(max = 2000) String notes
) {
}
