package com.expensetracker.data;

import com.expensetracker.model.BalanceInput;
import com.expensetracker.model.BalanceRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises {@link BalanceDao} against a real SQLite file in a {@link TempDir}. */
class BalanceDaoTest {

    private BalanceDao dao;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + tempDir.resolve("tracker.db"));
        dao = new BalanceDao(new JdbcTemplate(ds));
        dao.initSchema();
    }

    @Test
    void insertRoundTripsIncludingNegativeLoan() {
        int id = dao.insert(new BalanceInput(
                LocalDate.of(2026, 3, 31), "Joint", "Loan (Liability)", "Home loan", -2500000.0, "EMI"));
        BalanceRow row = dao.findById(id);
        assertThat(row.asOnDate()).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(row.owner()).isEqualTo("Joint");
        assertThat(row.accountType()).isEqualTo("Loan (Liability)");
        assertThat(row.institution()).isEqualTo("Home loan");
        assertThat(row.balance()).isEqualTo(-2500000.0);
    }

    @Test
    void updateDeleteAndOrdering() {
        int older = dao.insert(input(LocalDate.of(2026, 1, 31)));
        int newer = dao.insert(input(LocalDate.of(2026, 2, 28)));
        assertThat(dao.findAll()).extracting(BalanceRow::id).containsExactly(newer, older);

        dao.update(older, new BalanceInput(
                LocalDate.of(2026, 1, 31), "Me", "Cash", "wallet", 500.0, "z"));
        assertThat(dao.findById(older).accountType()).isEqualTo("Cash");

        dao.delete(newer);
        assertThat(dao.findAll()).extracting(BalanceRow::id).containsExactly(older);
    }

    private static BalanceInput input(LocalDate date) {
        return new BalanceInput(date, "Me", "Bank Account", "HDFC", 150000.0, "");
    }
}
