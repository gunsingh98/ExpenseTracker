package com.expensetracker.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the DAO against a real SQLite database in a {@link TempDir} file
 * (not {@code :memory:}, which is per-connection and would appear empty on a
 * second connection).
 */
class AuditDaoTest {

    private AuditDao dao;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + tempDir.resolve("audit.db"));
        dao = new AuditDao(new JdbcTemplate(ds));
        dao.initSchema();
    }

    @Test
    void initSchemaIsIdempotent() {
        // A second call must not fail (CREATE ... IF NOT EXISTS).
        dao.initSchema();
        assertThat(dao.query(null, 10, 0)).isEmpty();
    }

    @Test
    void insertsAndReturnsEntriesNewestFirst() {
        dao.insert(entry("alice", "CREATE", "EXPENSE", "1", null, "{\"amount\":100}"));
        dao.insert(entry("bob", "UPDATE", "EXPENSE", "1", "{\"amount\":100}", "{\"amount\":150}"));

        List<AuditEntry> all = dao.query(null, 10, 0);

        assertThat(all).hasSize(2);
        assertThat(all.get(0).username()).isEqualTo("bob");   // newest first (id desc)
        assertThat(all.get(0).action()).isEqualTo("UPDATE");
        assertThat(all.get(0).oldValue()).isEqualTo("{\"amount\":100}");
        assertThat(all.get(0).newValue()).isEqualTo("{\"amount\":150}");
        assertThat(all.get(1).username()).isEqualTo("alice");
        assertThat(all.get(1).oldValue()).isNull();
    }

    @Test
    void filtersByEntityType() {
        dao.insert(entry("alice", "CREATE", "EXPENSE", "1", null, "{}"));
        dao.insert(entry("alice", "CREATE", "INVESTMENT", "2", null, "{}"));
        dao.insert(entry("alice", "REPLACE", "LIST", "CATEGORIES", "[\"a\"]", "[\"a\",\"b\"]"));

        assertThat(dao.query("INVESTMENT", 10, 0))
                .singleElement()
                .satisfies(e -> assertThat(e.entityId()).isEqualTo("2"));
        assertThat(dao.query("LIST", 10, 0)).singleElement();
        assertThat(dao.query("EXPENSE", 10, 0)).singleElement();
    }

    @Test
    void appliesLimitAndOffset() {
        for (int i = 0; i < 5; i++) {
            dao.insert(entry("alice", "CREATE", "EXPENSE", String.valueOf(i), null, "{}"));
        }
        List<AuditEntry> page = dao.query(null, 2, 1);
        assertThat(page).hasSize(2);
        // Newest first: ids 4,3,2,1,0 → offset 1 limit 2 => entity ids "3","2".
        assertThat(page).extracting(AuditEntry::entityId).containsExactly("3", "2");
    }

    @Test
    void roundTripsTimestamp() {
        Instant ts = Instant.parse("2026-10-15T09:30:00.123Z");
        dao.insert(new AuditEntry(0, ts, "alice", "CREATE", "EXPENSE", "1", null, "{}"));
        assertThat(dao.query(null, 1, 0).getFirst().timestamp()).isEqualTo(ts);
    }

    private static AuditEntry entry(String user, String action, String type, String id,
                                    String oldJson, String newJson) {
        return new AuditEntry(0, Instant.now(), user, action, type, id, oldJson, newJson);
    }
}
