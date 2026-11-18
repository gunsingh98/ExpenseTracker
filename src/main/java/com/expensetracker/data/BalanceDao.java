package com.expensetracker.data;

import com.expensetracker.model.BalanceInput;
import com.expensetracker.model.BalanceRow;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * JDBC access to the {@code balance} table (dated account-balance snapshots).
 * {@code balance} is signed — loans/credit-card dues are stored negative.
 */
@Repository
public class BalanceDao {

    private static final RowMapper<BalanceRow> MAPPER = (rs, n) -> new BalanceRow(
            rs.getInt("id"),
            LocalDate.parse(rs.getString("as_on_date")),
            rs.getString("owner"),
            rs.getString("account_type"),
            rs.getString("institution"),
            rs.getDouble("balance"),
            rs.getString("notes"));

    private final JdbcTemplate jdbc;

    public BalanceDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void initSchema() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS balance (
                    id           INTEGER PRIMARY KEY AUTOINCREMENT,
                    as_on_date   TEXT    NOT NULL,
                    owner        TEXT,
                    account_type TEXT,
                    institution  TEXT,
                    balance      REAL    NOT NULL,
                    notes        TEXT
                )
                """);
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_balance_date ON balance(as_on_date)");
    }

    /** All balance snapshots, most recent first. */
    public List<BalanceRow> findAll() {
        return jdbc.query("SELECT * FROM balance ORDER BY as_on_date DESC, id DESC", MAPPER);
    }

    /** The most recent as-on date, or {@code null} with no balances (a lookup on the date index). */
    public LocalDate findLatestDate() {
        String latest = jdbc.queryForObject("SELECT MAX(as_on_date) FROM balance", String.class);
        return latest == null ? null : LocalDate.parse(latest);
    }

    /** Balances on {@code asOnDate} summed per trimmed account type (NULL as empty). */
    public Map<String, Double> sumByAccountType(LocalDate asOnDate) {
        Map<String, Double> totals = new HashMap<>();
        jdbc.query("SELECT " + Aggregates.key("account_type") + " AS account_type, SUM(balance) AS balance"
                        + " FROM balance WHERE as_on_date = ? GROUP BY 1",
                (RowCallbackHandler) rs -> totals.put(rs.getString("account_type"), rs.getDouble("balance")),
                asOnDate.toString());
        return totals;
    }

    public BalanceRow findById(int id) {
        return jdbc.query("SELECT * FROM balance WHERE id = ?", MAPPER, id)
                .stream().findFirst().orElse(null);
    }

    public int insert(BalanceInput in) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO balance (as_on_date, owner, account_type, institution, balance, notes)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            bind(ps, in);
            return ps;
        }, keys);
        return Objects.requireNonNull(keys.getKey(), "no generated key").intValue();
    }

    public void update(int id, BalanceInput in) {
        jdbc.update("""
                UPDATE balance SET as_on_date = ?, owner = ?, account_type = ?,
                    institution = ?, balance = ?, notes = ?
                WHERE id = ?
                """,
                in.asOnDate().toString(), in.owner(), in.accountType(),
                in.institution(), in.balance(), in.notes(), id);
    }

    public void delete(int id) {
        jdbc.update("DELETE FROM balance WHERE id = ?", id);
    }

    private static void bind(PreparedStatement ps, BalanceInput in) throws SQLException {
        ps.setString(1, in.asOnDate().toString());
        ps.setString(2, in.owner());
        ps.setString(3, in.accountType());
        ps.setString(4, in.institution());
        ps.setDouble(5, in.balance());
        ps.setString(6, in.notes());
    }
}
