package com.expensetracker.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Payload for creating/updating a balance snapshot. {@code balance} is
 * intentionally unconstrained in sign: loans and credit-card dues are entered
 * as negative amounts, so no {@code @PositiveOrZero} here.
 */
public record BalanceInput(
        @NotNull LocalDate asOnDate,
        @NotNull @Size(max = 100) String owner,
        @NotNull @Size(max = 100) String accountType,
        @Size(max = 200) String institution,
        @NotNull Double balance,
        @Size(max = 2000) String notes
) {
}
