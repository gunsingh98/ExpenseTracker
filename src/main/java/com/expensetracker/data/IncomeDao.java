package com.expensetracker.data;

import com.expensetracker.model.IncomeInput;
import com.expensetracker.model.IncomeRow;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** JDBC access to the {@code income} table. Owns its schema (created at startup). */
@Repository
public class IncomeDao {

    private static final RowMapper<IncomeRow> MAPPER = (rs, n) -> new IncomeRow(
            rs.getInt("id"),
            LocalDate.parse(rs.getString("date")),
            rs.getString("earner"),
            rs.getString("income_type"),
            rs.getString("source"),
            rs.getDouble("amount"),
            rs.getString("received_in"),
            rs.getString("notes"));

    private final JdbcTemplate jdbc;

    public IncomeDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void initSchema() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS income (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    date        TEXT    NOT NULL,
                    earner      TEXT,
                    income_type TEXT,
                    source      TEXT,
                    amount      REAL    NOT NULL,
                    received_in TEXT,
                    notes       TEXT
                )
                """);
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_income_date ON income(date)");
    }

    /** All income entries, most recent first. */
    public List<IncomeRow> findAll() {
        return jdbc.query("SELECT * FROM income ORDER BY date DESC, id DESC", MAPPER);
    }

    /** {@code year}'s income per month, indexed 1-12. */
    public double[] sumByMonth(int year) {
        return Aggregates.sumByMonth(jdbc, "income", "amount", year);
    }

    /** Distinct years that have income. */
    public List<Integer> findYears() {
        return jdbc.queryForList(Aggregates.distinctYears("income", "date"), Integer.class);
    }

    public IncomeRow findById(int id) {
        return jdbc.query("SELECT * FROM income WHERE id = ?", MAPPER, id)
                .stream().findFirst().orElse(null);
    }

    public int insert(IncomeInput in) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO income (date, earner, income_type, source, amount, received_in, notes)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            bind(ps, in);
            return ps;
        }, keys);
        return Objects.requireNonNull(keys.getKey(), "no generated key").intValue();
    }

    public void update(int id, IncomeInput in) {
        jdbc.update("""
                UPDATE income SET date = ?, earner = ?, income_type = ?, source = ?,
                    amount = ?, received_in = ?, notes = ?
                WHERE id = ?
                """,
                in.date().toString(), in.earner(), in.incomeType(), in.source(),
                in.amount(), in.receivedIn(), in.notes(), id);
    }

    public void delete(int id) {
        jdbc.update("DELETE FROM income WHERE id = ?", id);
    }

    private static void bind(PreparedStatement ps, IncomeInput in) throws SQLException {
        ps.setString(1, in.date().toString());
        ps.setString(2, in.earner());
        ps.setString(3, in.incomeType());
        ps.setString(4, in.source());
        ps.setDouble(5, in.amount());
        ps.setString(6, in.receivedIn());
        ps.setString(7, in.notes());
    }
}
