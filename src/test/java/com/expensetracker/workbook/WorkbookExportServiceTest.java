package com.expensetracker.workbook;

import com.expensetracker.data.BalanceDao;
import com.expensetracker.data.ExpenseDao;
import com.expensetracker.data.IncomeDao;
import com.expensetracker.data.InvestmentDao;
import com.expensetracker.data.ListColumn;
import com.expensetracker.data.ListsDao;
import com.expensetracker.model.BalanceInput;
import com.expensetracker.model.ExpenseInput;
import com.expensetracker.model.IncomeInput;
import com.expensetracker.model.InvestmentInput;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Spliterators;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the export renders data into the template, preserving sheets and charts. */
class WorkbookExportServiceTest {

    private ExpenseDao expenseDao;
    private InvestmentDao investmentDao;
    private IncomeDao incomeDao;
    private BalanceDao balanceDao;
    private ListsDao listsDao;
    private WorkbookExportService exporter;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + tempDir.resolve("tracker.db"));
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        expenseDao = new ExpenseDao(jdbc);
        investmentDao = new InvestmentDao(jdbc);
        incomeDao = new IncomeDao(jdbc);
        balanceDao = new BalanceDao(jdbc);
        listsDao = new ListsDao(jdbc);
        expenseDao.initSchema();
        investmentDao.initSchema();
        incomeDao.initSchema();
        balanceDao.initSchema();
        listsDao.initSchema();
        exporter = new WorkbookExportService(expenseDao, investmentDao, incomeDao, balanceDao, listsDao);
    }

    @Test
    void exportPreservesSheetsAndCharts() throws IOException {
        byte[] bytes = exporter.export();
        try (XSSFWorkbook wb = open(bytes)) {
            assertThat(sheetNames(wb)).containsExactly(
                    "Summary", "Expenses", "Investments", "Income", "Balances", "Monthly Summary", "Lists");
        }
        assertThat(countCharts(bytes))
                .as("charts must survive the POI round-trip")
                .isGreaterThan(0);
    }

    @Test
    void exportWritesDataRowsIntoDataSheets() throws IOException {
        expenseDao.insert(new ExpenseInput(
                LocalDate.of(2026, 3, 15), "Me", "Groceries", "BigBasket", 1250.5, "UPI", true, "weekly", "Goa Trip"));
        investmentDao.insert(new InvestmentInput(
                LocalDate.of(2026, 1, 10), "Me", "Stocks", "INFY", "Brokerage", 100000, 112000.0, "long"));
        incomeDao.insert(new IncomeInput(
                LocalDate.of(2026, 4, 1), "Me", "Salary", "Acme", 85000, "Bank Account", ""));
        balanceDao.insert(new BalanceInput(
                LocalDate.of(2026, 3, 31), "Joint", "Loan (Liability)", "Home", -2500000.0, ""));
        listsDao.replace(ListColumn.PEOPLE, List.of("Me", "Husband", "Joint"));

        byte[] bytes = exporter.export();

        try (XSSFWorkbook wb = open(bytes)) {
            Row expense = dataRow(wb, "Expenses");
            assertThat(expense.getCell(3).getStringCellValue()).isEqualTo("Groceries");
            assertThat(expense.getCell(5).getNumericCellValue()).isEqualTo(1250.5);
            assertThat(expense.getCell(7).getStringCellValue()).isEqualTo("Yes");
            assertThat(expense.getCell(10).getStringCellValue()).isEqualTo("Goa Trip"); // Occasion (col K)

            Row investment = dataRow(wb, "Investments");
            assertThat(investment.getCell(4).getStringCellValue()).isEqualTo("INFY");
            assertThat(investment.getCell(6).getNumericCellValue()).isEqualTo(100000.0);
            assertThat(investment.getCell(7).getNumericCellValue()).isEqualTo(112000.0);

            Row income = dataRow(wb, "Income");
            assertThat(income.getCell(4).getStringCellValue()).isEqualTo("Acme");
            assertThat(income.getCell(5).getNumericCellValue()).isEqualTo(85000.0);

            Row balance = dataRow(wb, "Balances");
            assertThat(balance.getCell(2).getStringCellValue()).isEqualTo("Loan (Liability)");
            assertThat(balance.getCell(4).getNumericCellValue()).isEqualTo(-2500000.0);

            Row list = dataRow(wb, "Lists");
            assertThat(list.getCell(2).getStringCellValue()).isEqualTo("Me"); // people column (C)
        }
    }

    private static Row dataRow(XSSFWorkbook wb, String sheetName) {
        Sheet sheet = wb.getSheet(sheetName);
        return sheet.getRow(WorkbookExportService.FIRST_DATA_ROW);
    }

    private XSSFWorkbook open(byte[] bytes) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(bytes));
    }

    private List<String> sheetNames(XSSFWorkbook wb) {
        return StreamSupport
                .stream(Spliterators.spliteratorUnknownSize(wb.sheetIterator(), 0), false)
                .map(Sheet::getSheetName)
                .toList();
    }

    private int countCharts(byte[] bytes) throws IOException {
        try (XSSFWorkbook wb = open(bytes)) {
            int count = 0;
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                XSSFSheet sheet = wb.getSheetAt(i);
                XSSFDrawing drawing = sheet.getDrawingPatriarch();
                if (drawing != null) {
                    List<XSSFChart> charts = drawing.getCharts();
                    count += charts.size();
                }
            }
            return count;
        }
    }
}
