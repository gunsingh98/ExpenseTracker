package com.expensetracker.data;

import com.expensetracker.model.Lists;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * JDBC access to the {@code list_value} table, which holds every dropdown list
 * keyed by {@link ListColumn#name()}. {@code position} preserves display order.
 */
@Repository
public class ListsDao {

    private final JdbcTemplate jdbc;

    public ListsDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void initSchema() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS list_value (
                    id        INTEGER PRIMARY KEY AUTOINCREMENT,
                    list_name TEXT    NOT NULL,
                    position  INTEGER NOT NULL,
                    value     TEXT    NOT NULL
                )
                """);
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_list_value_name ON list_value(list_name)");
    }

    /** Builds the full {@link Lists} record from the stored values. */
    public Lists readAll() {
        return new Lists(
                values(ListColumn.CATEGORIES),
                values(ListColumn.PAYMENT_METHODS),
                values(ListColumn.PEOPLE),
                values(ListColumn.INVESTMENT_TYPES),
                values(ListColumn.ACCOUNTS),
                values(ListColumn.INCOME_TYPES),
                values(ListColumn.ACCOUNT_TYPES),
                values(ListColumn.OCCASIONS));
    }

    /** The ordered values of a single list. */
    public List<String> values(ListColumn column) {
        return jdbc.queryForList(
                "SELECT value FROM list_value WHERE list_name = ? ORDER BY position",
                String.class, column.name());
    }

    /** Replaces a list's values wholesale, preserving the given order. */
    public void replace(ListColumn column, List<String> values) {
        jdbc.update("DELETE FROM list_value WHERE list_name = ?", column.name());
        List<Object[]> batch = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            batch.add(new Object[]{column.name(), i, values.get(i)});
        }
        jdbc.batchUpdate(
                "INSERT INTO list_value (list_name, position, value) VALUES (?, ?, ?)",
                batch);
    }
}
