package com.expensetracker.data;

import com.expensetracker.model.InvestmentInput;
import com.expensetracker.model.InvestmentRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises {@link InvestmentDao}, including the nullable current value and fund operations. */
class InvestmentDaoTest {

    private InvestmentDao dao;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + tempDir.resolve("tracker.db"));
        jdbc = new JdbcTemplate(ds);
        dao = new InvestmentDao(jdbc);
        dao.initSchema();
    }

    @Test
    void insertPreservesNullCurrentValue() {
        int id = dao.insert(input(LocalDate.of(2026, 1, 1), "PPFAS", null));
        assertThat(dao.findById(id).currentValue()).isNull();
    }

    @Test
    void insertRoundTripsValuedRow() {
        int id = dao.insert(input(LocalDate.of(2026, 1, 1), "PPFAS", 8000.0));
        InvestmentRow row = dao.findById(id);
        assertThat(row.currentValue()).isEqualTo(8000.0);
        assertThat(row.gainLoss()).isEqualTo(3000.0); // 8000 - 5000
    }

    @Test
    void updateCanClearCurrentValue() {
        int id = dao.insert(input(LocalDate.of(2026, 1, 1), "PPFAS", 8000.0));
        dao.update(id, input(LocalDate.of(2026, 1, 1), "PPFAS", null));
        assertThat(dao.findById(id).currentValue()).isNull();
    }

    @Test
    void setFundCurrentValueKeepsValueOnTargetAndClearsOthers() {
        int jan = dao.insert(input(LocalDate.of(2026, 1, 10), "PPFAS", 8000.0));
        int feb = dao.insert(input(LocalDate.of(2026, 2, 10), "PPFAS", null));

        dao.setFundCurrentValue(feb, 20000.0, List.of(jan));

        assertThat(dao.findById(feb).currentValue()).isEqualTo(20000.0);
        assertThat(dao.findById(jan).currentValue()).isNull();
    }

    @Test
    void deleteFundRemovesAllGivenIds() {
        int jan = dao.insert(input(LocalDate.of(2026, 1, 10), "PPFAS", null));
        int feb = dao.insert(input(LocalDate.of(2026, 2, 10), "PPFAS", null));
        int gold = dao.insert(input(LocalDate.of(2026, 2, 10), "Gold", 3000.0));

        dao.deleteFund(List.of(jan, feb));

        assertThat(dao.findAll()).extracting(InvestmentRow::id).containsExactly(gold);
    }

    @Test
    void deleteFundWithEmptyListIsNoOp() {
        int id = dao.insert(input(LocalDate.of(2026, 1, 1), "PPFAS", null));
        dao.deleteFund(List.of());
        assertThat(dao.findById(id)).isNotNull();
    }

    @Test
    void findFundMatchesTrimmedNameNewestFirstViaIndex() {
        int jan = dao.insert(input(LocalDate.of(2026, 1, 1), "PPFAS", null));
        int feb = dao.insert(input(LocalDate.of(2026, 2, 1), "  PPFAS ", 8000.0));
        dao.insert(input(LocalDate.of(2026, 2, 1), "Gold", 3000.0));
        int unnamed = dao.insert(input(LocalDate.of(2026, 3, 1), null, null));

        assertThat(dao.findFund("PPFAS")).extracting(InvestmentRow::id).containsExactly(feb, jan);
        assertThat(dao.findFund("")).extracting(InvestmentRow::id).containsExactly(unnamed);
        assertThat(dao.findFund("Nope")).isEmpty();

        String plan = jdbc.queryForList("EXPLAIN QUERY PLAN SELECT * FROM investment WHERE "
                + Aggregates.key("name") + " = 'PPFAS'").toString();
        assertThat(plan).contains("idx_investment_fund");
    }

    @Test
    void sumByFundGroupsTrimmedLabelsAndFlagsValuedFunds() {
        dao.insert(input(LocalDate.of(2026, 1, 1), "PPFAS", null));
        dao.insert(input(LocalDate.of(2026, 2, 1), " PPFAS ", 8000.0));
        dao.insert(input(LocalDate.of(2026, 2, 1), "", null));

        assertThat(dao.sumByFund()).containsExactlyInAnyOrder(
                new InvestmentDao.FundGroup("Mutual Funds", "PPFAS", "Me", 10000, 8000, true),
                new InvestmentDao.FundGroup("Mutual Funds", "Other", "Me", 5000, 0, false));
    }

    private static InvestmentInput input(LocalDate date, String name, Double currentValue) {
        return new InvestmentInput(date, "Me", "Mutual Funds", name, "Brokerage", 5000, currentValue, "");
    }
}
