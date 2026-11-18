package com.expensetracker.data;

import com.expensetracker.model.IncomeInput;
import com.expensetracker.model.IncomeRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises {@link IncomeDao} against a real SQLite file in a {@link TempDir}. */
class IncomeDaoTest {

    private IncomeDao dao;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + tempDir.resolve("tracker.db"));
        dao = new IncomeDao(new JdbcTemplate(ds));
        dao.initSchema();
    }

    @Test
    void insertAndRoundTrip() {
        int id = dao.insert(new IncomeInput(
                LocalDate.of(2026, 4, 1), "Me", "Salary", "Acme", 85000, "Bank Account", "monthly"));
        IncomeRow row = dao.findById(id);
        assertThat(row.earner()).isEqualTo("Me");
        assertThat(row.incomeType()).isEqualTo("Salary");
        assertThat(row.source()).isEqualTo("Acme");
        assertThat(row.amount()).isEqualTo(85000);
        assertThat(row.receivedIn()).isEqualTo("Bank Account");
    }

    @Test
    void updateDeleteAndOrdering() {
        int older = dao.insert(input(LocalDate.of(2026, 1, 1)));
        int newer = dao.insert(input(LocalDate.of(2026, 2, 1)));
        assertThat(dao.findAll()).extracting(IncomeRow::id).containsExactly(newer, older);

        dao.update(older, new IncomeInput(
                LocalDate.of(2026, 1, 1), "Husband", "Bonus / Variable Pay", "y", 2000, "Cash", "z"));
        assertThat(dao.findById(older).incomeType()).isEqualTo("Bonus / Variable Pay");

        dao.delete(newer);
        assertThat(dao.findAll()).extracting(IncomeRow::id).containsExactly(older);
    }

    private static IncomeInput input(LocalDate date) {
        return new IncomeInput(date, "Me", "Salary", "x", 1000, "Bank Account", "");
    }
}
