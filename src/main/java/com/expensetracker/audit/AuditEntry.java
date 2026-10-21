package com.expensetracker.audit;

import java.time.Instant;

/**
 * One recorded change: who did what, when, to which entity, with JSON snapshots
 * of the before/after state. {@code oldValue} is null for a CREATE and
 * {@code newValue} is null for a DELETE. {@code entityId} is the affected row id
 * (as text) or, for a LIST replace, the column name; it is null for a CREATE
 * (the new workbook row id is not known at write time).
 */
public record AuditEntry(
        long id,
        Instant timestamp,
        String username,
        String action,
        String entityType,
        String entityId,
        String oldValue,
        String newValue
) {
}
