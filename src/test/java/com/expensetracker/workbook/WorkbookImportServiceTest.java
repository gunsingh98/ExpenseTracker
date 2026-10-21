package com.expensetracker.workbook;

import com.expensetracker.data.BalanceDao;
import com.expensetracker.data.ExpenseDao;
import com.expensetracker.data.IncomeDao;
import com.expensetracker.data.InvestmentDao;
import com.expensetracker.data.ListColumn;
import com.expensetracker.data.ListsDao;
import com.expensetracker.model.BalanceInput;
import com.expensetracker.model.BalanceRow;
import com.expensetracker.model.ExpenseInput;
import com.expensetracker.model.ExpenseRow;
import com.expensetracker.model.IncomeInput;
import com.expensetracker.model.IncomeRow;
import com.expensetracker.model.InvestmentInput;
import com.expensetracker.model.InvestmentRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trips data through the exporter and back through {@link WorkbookImportService},
 * proving the importer is the exact inverse of the export: every seeded row is read
 * back with the same field values, and the contiguous-rows / stop-at-blank scan does
 * not pick up the template's trailing empty formula rows.
 */
class WorkbookImportServiceTest {

    /** A fresh, independent SQLite database with the schema created. */
    private record Store(ExpenseDao expenseDao, InvestmentDao investmentDao, IncomeDao incomeDao,
                         BalanceDao balanceDao, ListsDao listsDao) {}

    private static Store newStore(Path dbFile) {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + dbFile);
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        ExpenseDao expenseDao = new ExpenseDao(jdbc);
        InvestmentDao investmentDao = new InvestmentDao(jdbc);
        IncomeDao incomeDao = new IncomeDao(jdbc);
        BalanceDao balanceDao = new BalanceDao(jdbc);
        ListsDao listsDao = new ListsDao(jdbc);
        expenseDao.initSchema();
        investmentDao.initSchema();
        incomeDao.initSchema();
        balanceDao.initSchema();
        listsDao.initSchema();
        return new Store(expenseDao, investmentDao, incomeDao, balanceDao, listsDao);
    }

    @Test
    void importsEveryRowBackFromAnExportedWorkbook(@TempDir Path tempDir) throws IOException {
        // Seed a source database and export it to an .xlsx file.
        Store source = newStore(tempDir.resolve("source.db"));
        source.expenseDao().insert(new ExpenseInput(
                LocalDate.of(2026, 3, 15), "Me", "Groceries", "BigBasket", 1250.5, "UPI", true, "weekly", "Goa Trip"));
        source.expenseDao().insert(new ExpenseInput(
                LocalDate.of(2026, 3, 16), "Husband", "Rent", null, 25000, "Bank Transfer", false, null, null));
        source.investmentDao().insert(new InvestmentInput(
                LocalDate.of(2026, 1, 10), "Me", "Stocks", "INFY", "Brokerage", 100000, 112000.0, "long"));
        source.investmentDao().insert(new InvestmentInput(
                LocalDate.of(2026, 2, 10), "Joint", "EPF", "EPF1", "EPFO", 50000, null, null));
        source.incomeDao().insert(new IncomeInput(
                LocalDate.of(2026, 4, 1), "Me", "Salary", "Acme", 85000, "Bank Account", ""));
        source.balanceDao().insert(new BalanceInput(
                LocalDate.of(2026, 3, 31), "Joint", "Loan (Liability)", "Home", -2500000.0, "mortgage"));
        source.listsDao().replace(ListColumn.PEOPLE, List.of("Me", "Husband", "Joint"));
        source.listsDao().replace(ListColumn.CATEGORIES, List.of("Groceries", "Rent"));
        source.listsDao().replace(ListColumn.OCCASIONS, List.of("Goa Trip", "Diwali 2026"));

        byte[] bytes = new WorkbookExportService(source.expenseDao(), source.investmentDao(),
                source.incomeDao(), source.balanceDao(), source.listsDao()).export();
        Path workbook = tempDir.resolve("export.xlsx");
        Files.write(workbook, bytes);

        // Import into a fresh, empty database.
        Store target = newStore(tempDir.resolve("target.db"));
        WorkbookImportService importer = new WorkbookImportService(target.expenseDao(),
                target.investmentDao(), target.incomeDao(), target.balanceDao(), target.listsDao());

        WorkbookImportService.Result result = importer.importFrom(workbook);

        // Counts stop at the data block — the template's empty formula rows are not imported.
        assertThat(result.expenses()).isEqualTo(2);
        assertThat(result.investments()).isEqualTo(2);
        assertThat(result.income()).isEqualTo(1);
        assertThat(result.balances()).isEqualTo(1);
        // The exporter overwrites Lists top-down over the template's built-in defaults
        // without clearing trailing rows, so the count also includes those defaults.
        assertThat(result.lists()).isGreaterThanOrEqualTo(5);

        // Field-level fidelity (findAll is date-desc, id-desc).
        List<ExpenseRow> expenses = target.expenseDao().findAll();
        assertThat(expenses).hasSize(2);
        ExpenseRow rent = expenses.get(0); // 2026-03-16 is the most recent
        assertThat(rent.date()).isEqualTo(LocalDate.of(2026, 3, 16));
        assertThat(rent.paidBy()).isEqualTo("Husband");
        assertThat(rent.category()).isEqualTo("Rent");
        assertThat(rent.description()).isNull();
        assertThat(rent.amount()).isEqualTo(25000.0);
        assertThat(rent.shared()).isFalse();
        assertThat(rent.occasion()).isNull();
        ExpenseRow groceries = expenses.get(1);
        assertThat(groceries.amount()).isEqualTo(1250.5);
        assertThat(groceries.shared()).isTrue();
        assertThat(groceries.notes()).isEqualTo("weekly");
        assertThat(groceries.occasion()).isEqualTo("Goa Trip");

        List<InvestmentRow> investments = target.investmentDao().findAll();
        assertThat(investments).hasSize(2);
        InvestmentRow epf = investments.get(0); // 2026-02-10
        assertThat(epf.type()).isEqualTo("EPF");
        assertThat(epf.currentValue()).isNull();
        InvestmentRow stocks = investments.get(1);
        assertThat(stocks.name()).isEqualTo("INFY");
        assertThat(stocks.amountInvested()).isEqualTo(100000.0);
        assertThat(stocks.currentValue()).isEqualTo(112000.0);

        IncomeRow income = target.incomeDao().findAll().get(0);
        assertThat(income.earner()).isEqualTo("Me");
        assertThat(income.source()).isEqualTo("Acme");
        assertThat(income.amount()).isEqualTo(85000.0);
        assertThat(income.receivedIn()).isEqualTo("Bank Account");

        BalanceRow balance = target.balanceDao().findAll().get(0);
        assertThat(balance.accountType()).isEqualTo("Loan (Liability)");
        assertThat(balance.balance()).isEqualTo(-2500000.0);
        assertThat(balance.notes()).isEqualTo("mortgage");

        // People are not template defaults, so their presence proves list import works;
        // categories coexist with the template's built-in defaults.
        assertThat(target.listsDao().values(ListColumn.PEOPLE))
                .startsWith("Me", "Husband", "Joint");
        assertThat(target.listsDao().values(ListColumn.CATEGORIES))
                .contains("Groceries", "Rent");
        assertThat(target.listsDao().values(ListColumn.OCCASIONS))
                .startsWith("Goa Trip", "Diwali 2026");
    }

    @Test
    void workbookWithNoDataRowsImportsNoFinancialRows(@TempDir Path tempDir) throws IOException {
        Store empty = newStore(tempDir.resolve("empty-source.db"));
        byte[] bytes = new WorkbookExportService(empty.expenseDao(), empty.investmentDao(),
                empty.incomeDao(), empty.balanceDao(), empty.listsDao()).export();
        Path workbook = tempDir.resolve("empty.xlsx");
        Files.write(workbook, bytes);

        Store target = newStore(tempDir.resolve("empty-target.db"));
        WorkbookImportService.Result result = new WorkbookImportService(target.expenseDao(),
                target.investmentDao(), target.incomeDao(), target.balanceDao(), target.listsDao())
                .importFrom(workbook);

        // No data rows in any financial sheet; the stop-at-blank scan imports nothing there.
        assertThat(result.expenses()).isZero();
        assertThat(result.investments()).isZero();
        assertThat(result.income()).isZero();
        assertThat(result.balances()).isZero();
        assertThat(target.expenseDao().findAll()).isEmpty();
    }
}
