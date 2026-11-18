package com.expensetracker.data;

import com.expensetracker.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Owns the single SQLite {@link DataSource} + {@link JdbcTemplate} backing the
 * whole app — the financial tables (expense/income/investment/balance/list_value)
 * and the audit log all live in one database file at {@code app.data.db-path}.
 *
 * <p>Defining the {@code DataSource} here makes Spring Boot's
 * {@code DataSourceAutoConfiguration} / {@code JdbcTemplateAutoConfiguration} back
 * off (they only act when no such bean exists), so no server-DB datasource is
 * attempted. WAL journal mode lets reads run while a write is in flight; a
 * busy-timeout turns brief write contention into a short wait rather than a
 * {@code SQLITE_BUSY} error.
 */
@Configuration
public class DatabaseConfig {

    @Bean
    public DataSource dataSource(AppProperties props) {
        String dbPath = props.getData().getDbPath();
        createParentDir(dbPath);

        SQLiteConfig config = new SQLiteConfig();
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setBusyTimeout(5000);

        SQLiteDataSource ds = new SQLiteDataSource(config);
        ds.setUrl("jdbc:sqlite:" + dbPath);
        return ds;
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /** Ensure the directory holding the DB file (and its WAL sidecars) exists. */
    private static void createParentDir(String dbPath) {
        Path parent = Path.of(dbPath).toAbsolutePath().getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create data DB directory: " + parent, e);
        }
    }
}
