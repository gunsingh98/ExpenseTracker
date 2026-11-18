package com.expensetracker.data;

import com.expensetracker.model.InvestmentInput;
import com.expensetracker.model.InvestmentRow;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * JDBC access to the {@code investment} table. {@code current_value} is nullable
 * (not yet valued). The fund convention — a fund's current value lives on exactly
 * one of its contribution rows so the net-worth sum does not double-count — is
 * enforced via {@link #setFundCurrentValue} / {@link #deleteFund}.
 */
@Repository
public class InvestmentDao {

    private static final RowMapper<InvestmentRow> MAPPER = (rs, n) -> {
        double current = rs.getDouble("current_value");
        Double currentValue = rs.wasNull() ? null : current;
        return new InvestmentRow(
                rs.getInt("id"),
                LocalDate.parse(rs.getString("date")),
                rs.getString("owner"),
                rs.getString("type"),
                rs.getString("name"),
                rs.getString("account"),
                rs.getDouble("amount_invested"),
                currentValue,
                rs.getString("notes"));
    };

    /** A fund's identity: its trimmed name (NULL as empty), as in {@code InvestmentService}. */
    private static final String FUND_KEY = Aggregates.key("name");

    private final JdbcTemplate jdbc;

    public InvestmentDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void initSchema() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS investment (
                    id              INTEGER PRIMARY KEY AUTOINCREMENT,
                    date            TEXT    NOT NULL,
                    owner           TEXT,
                    type            TEXT,
                    name            TEXT,
                    account         TEXT,
                    amount_invested REAL    NOT NULL,
                    current_value   REAL,
                    notes           TEXT
                )
                """);
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_investment_date ON investment(date)");
        // Expression index matching findFund's WHERE, so a fund lookup is a seek, not a scan.
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_investment_fund ON investment(" + FUND_KEY + ")");
    }

    /** Every row of the fund whose trimmed name is {@code fundKey}, most recent first. */
    public List<InvestmentRow> findFund(String fundKey) {
        return jdbc.query("SELECT * FROM investment WHERE " + FUND_KEY + " = ? ORDER BY date DESC, id DESC",
                MAPPER, fundKey);
    }

    /**
     * All-time totals per (type, fund, owner); blank labels read "Other".
     * {@code valued} is whether any row in the bucket carries a current value.
     */
    public record FundGroup(String type, String name, String owner, double invested,
                            double currentValue, boolean valued) {
    }

    public List<FundGroup> sumByFund() {
        return jdbc.query("SELECT " + Aggregates.label("type") + " AS type, "
                        + Aggregates.label("name") + " AS name, " + Aggregates.label("owner") + " AS owner,"
                        + " SUM(amount_invested) AS invested, TOTAL(current_value) AS current_value,"
                        + " COUNT(current_value) AS valued_rows FROM investment GROUP BY 1, 2, 3",
                (rs, n) -> new FundGroup(rs.getString("type"), rs.getString("name"), rs.getString("owner"),
                        rs.getDouble("invested"), rs.getDouble("current_value"), rs.getInt("valued_rows") > 0));
    }

    /** {@code year}'s contributions per month, indexed 1-12. */
    public double[] sumInvestedByMonth(int year) {
        return Aggregates.sumByMonth(jdbc, "investment", "amount_invested", year);
    }

    /** Distinct years that have investments. */
    public List<Integer> findYears() {
        return jdbc.queryForList(Aggregates.distinctYears("investment", "date"), Integer.class);
    }

    /** All investments, most recent first. */
    public List<InvestmentRow> findAll() {
        return jdbc.query("SELECT * FROM investment ORDER BY date DESC, id DESC", MAPPER);
    }

    public InvestmentRow findById(int id) {
        return jdbc.query("SELECT * FROM investment WHERE id = ?", MAPPER, id)
                .stream().findFirst().orElse(null);
    }

    public int insert(InvestmentInput in) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO investment (date, owner, type, name, account, amount_invested, current_value, notes)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            bind(ps, in);
            return ps;
        }, keys);
        return Objects.requireNonNull(keys.getKey(), "no generated key").intValue();
    }

    public void update(int id, InvestmentInput in) {
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    UPDATE investment SET date = ?, owner = ?, type = ?, name = ?, account = ?,
                        amount_invested = ?, current_value = ?, notes = ?
                    WHERE id = ?
                    """);
            bind(ps, in);
            ps.setInt(9, id);
            return ps;
        });
    }

    public void delete(int id) {
        jdbc.update("DELETE FROM investment WHERE id = ?", id);
    }

    /**
     * Sets the current value of a fund onto a single target row, clearing it from
     * every other row of that fund so the value is counted exactly once.
     *
     * @param targetId the row that should carry the current value
     * @param value    the fund's current value (may be {@code null} to unset)
     * @param clearIds other rows of the same fund whose current value must be nulled
     */
    public void setFundCurrentValue(int targetId, Double value, List<Integer> clearIds) {
        jdbc.update("UPDATE investment SET current_value = ? WHERE id = ?", value, targetId);
        for (int id : clearIds) {
            jdbc.update("UPDATE investment SET current_value = NULL WHERE id = ?", id);
        }
    }

    /** Deletes every row belonging to a fund. */
    public void deleteFund(List<Integer> ids) {
        if (ids.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", ids.stream().map(i -> "?").toList());
        jdbc.update("DELETE FROM investment WHERE id IN (" + placeholders + ")", ids.toArray());
    }

    private static void bind(PreparedStatement ps, InvestmentInput in) throws SQLException {
        ps.setString(1, in.date().toString());
        ps.setString(2, in.owner());
        ps.setString(3, in.type());
        ps.setString(4, in.name());
        ps.setString(5, in.account());
        ps.setDouble(6, in.amountInvested());
        if (in.currentValue() == null) {
            ps.setNull(7, Types.REAL);
        } else {
            ps.setDouble(7, in.currentValue());
        }
        ps.setString(8, in.notes());
    }
}
