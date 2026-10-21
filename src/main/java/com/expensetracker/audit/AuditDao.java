package com.expensetracker.audit;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC access to the {@code audit_log} table in the shared SQLite DB. Owns the
 * schema (created idempotently at startup).
 */
@Repository
public class AuditDao {

    private static final RowMapper<AuditEntry> ROW_MAPPER = AuditDao::mapRow;

    private final JdbcTemplate jdbc;

    public AuditDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    void initSchema() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS audit_log (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    ts          TEXT    NOT NULL,
                    username    TEXT    NOT NULL,
                    action      TEXT    NOT NULL,
                    entity_type TEXT    NOT NULL,
                    entity_id   TEXT,
                    old_value   TEXT,
                    new_value   TEXT
                )
                """);
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_audit_ts ON audit_log(ts)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_audit_entity_type ON audit_log(entity_type)");
    }

    /** Appends one audit entry. The {@code id} is assigned by SQLite. */
    public void insert(AuditEntry entry) {
        jdbc.update("""
                INSERT INTO audit_log (ts, username, action, entity_type, entity_id, old_value, new_value)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                entry.timestamp().toString(),
                entry.username(),
                entry.action(),
                entry.entityType(),
                entry.entityId(),
                entry.oldValue(),
                entry.newValue());
    }

    /**
     * Returns audit entries newest-first. {@code entityType} filters by entity
     * when non-null; {@code limit}/{@code offset} page the results.
     */
    public List<AuditEntry> query(String entityType, int limit, int offset) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, ts, username, action, entity_type, entity_id, old_value, new_value FROM audit_log");
        List<Object> args = new ArrayList<>();
        if (entityType != null) {
            sql.append(" WHERE entity_type = ?");
            args.add(entityType);
        }
        sql.append(" ORDER BY id DESC LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbc.query(sql.toString(), ROW_MAPPER, args.toArray());
    }

    private static AuditEntry mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new AuditEntry(
                rs.getLong("id"),
                Instant.parse(rs.getString("ts")),
                rs.getString("username"),
                rs.getString("action"),
                rs.getString("entity_type"),
                rs.getString("entity_id"),
                rs.getString("old_value"),
                rs.getString("new_value"));
    }
}
