package com.expensetracker.audit;

/** The kind of change recorded in an audit entry. */
public enum AuditAction {
    CREATE,
    UPDATE,
    DELETE,
    REPLACE
}
