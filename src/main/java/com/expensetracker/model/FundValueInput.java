package com.expensetracker.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Payload for setting the market value of a whole fund (all investment rows that
 * share {@code name}). {@code currentValue} is optional — {@code null} clears the
 * fund's valuation. The value is stored on a single row so net-worth totals, which
 * sum current values across rows, are not inflated.
 */
public record FundValueInput(
        @NotNull @Size(max = 200) String name,
        @PositiveOrZero Double currentValue
) {
}
