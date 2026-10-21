package com.expensetracker.workbook;

import com.expensetracker.data.BalanceDao;
import com.expensetracker.data.ExpenseDao;
import com.expensetracker.data.IncomeDao;
import com.expensetracker.data.InvestmentDao;
import com.expensetracker.data.ListsDao;
import com.expensetracker.model.BalanceRow;
import com.expensetracker.model.ExpenseRow;
import com.expensetracker.model.IncomeRow;
import com.expensetracker.model.InvestmentRow;
import com.expensetracker.model.Lists;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.List;

/**
 * Renders the current SQLite data to an {@code .xlsx} workbook for download and
 * for the periodic Drive backup. Write-only: it loads a bundled blank template
 * (the household workbook with its Summary / Monthly-Summary tabs, Net-Worth
 * block, formulas and charts intact but no data rows), appends every row from
 * the DAOs into the five data sheets, and serializes — flagging
 * {@code forceFormulaRecalculation} so Excel refreshes the summaries on open.
 */
@Service
public class WorkbookExportService {

    private static final String TEMPLATE = "templates/tracker-template.xlsx";

    static final String SHEET_EXPENSES = "Expenses";
    static final String SHEET_INVESTMENTS = "Investments";
    static final String SHEET_INCOME = "Income";
    static final String SHEET_BALANCES = "Balances";
    static final String SHEET_LISTS = "Lists";

    /** Data rows begin at Excel row 5 (0-based index 4); headers are on row 4. */
    static final int FIRST_DATA_ROW = 4;

    // Expenses columns (0-based). Columns B(1)=Month and J(9)=Year are auto formulas;
    // K(10)=Occasion sits after the Year formula so it does not disturb it.
    private static final int E_DATE = 0, E_PAID_BY = 2, E_CATEGORY = 3, E_DESC = 4,
            E_AMOUNT = 5, E_METHOD = 6, E_SHARED = 7, E_NOTES = 8, E_OCCASION = 10;

    // Investments columns (0-based). B(1)=Month and I(8)=Gain/Loss are auto formulas.
    private static final int I_DATE = 0, I_OWNER = 2, I_TYPE = 3, I_NAME = 4, I_ACCOUNT = 5,
            I_AMOUNT = 6, I_CURRENT = 7, I_NOTES = 9;

    // Income columns (0-based). B(1)=Month and I(8)=Year are auto formulas.
    private static final int IN_DATE = 0, IN_EARNER = 2, IN_TYPE = 3, IN_SOURCE = 4,
            IN_AMOUNT = 5, IN_RECEIVED = 6, IN_NOTES = 7;

    // Balances columns (0-based). The H3:I9 Net-Worth block (col 7+) is formula-driven.
    private static final int B_DATE = 0, B_OWNER = 1, B_ACCT_TYPE = 2, B_INSTITUTION = 3,
            B_BALANCE = 4, B_NOTES = 5;

    // Lists columns (0-based): A categories, B methods, C people, D types, E accounts,
    // F income types, G account/balance types, H occasions.
    private static final int L_CATEGORIES = 0, L_METHODS = 1, L_PEOPLE = 2, L_TYPES = 3,
            L_ACCOUNTS = 4, L_INCOME_TYPES = 5, L_ACCOUNT_TYPES = 6, L_OCCASIONS = 7;

    private final ExpenseDao expenseDao;
    private final InvestmentDao investmentDao;
    private final IncomeDao incomeDao;
    private final BalanceDao balanceDao;
    private final ListsDao listsDao;

    public WorkbookExportService(ExpenseDao expenseDao, InvestmentDao investmentDao,
                                 IncomeDao incomeDao, BalanceDao balanceDao, ListsDao listsDao) {
        this.expenseDao = expenseDao;
        this.investmentDao = investmentDao;
        this.incomeDao = incomeDao;
        this.balanceDao = balanceDao;
        this.listsDao = listsDao;
    }

    /** Builds the workbook from the current data and returns its {@code .xlsx} bytes. */
    public byte[] export() {
        try (XSSFWorkbook wb = openTemplate()) {
            writeExpenses(wb, expenseDao.findAll());
            writeInvestments(wb, investmentDao.findAll());
            writeIncome(wb, incomeDao.findAll());
            writeBalances(wb, balanceDao.findAll());
            writeLists(wb, listsDao.readAll());
            return serialize(wb);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private XSSFWorkbook openTemplate() {
        try (InputStream in = new ClassPathResource(TEMPLATE).getInputStream()) {
            return new XSSFWorkbook(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to open export template " + TEMPLATE, e);
        }
    }

    private void writeExpenses(Workbook wb, List<ExpenseRow> rows) {
        Sheet sheet = requireSheet(wb, SHEET_EXPENSES);
        int r = FIRST_DATA_ROW;
        for (ExpenseRow in : rows) {
            Row row = rowAt(sheet, r++);
            setDate(wb, row, E_DATE, in.date());
            setText(row, E_PAID_BY, in.paidBy());
            setText(row, E_CATEGORY, in.category());
            setText(row, E_DESC, in.description());
            getOrCreateCell(row, E_AMOUNT).setCellValue(in.amount());
            setText(row, E_METHOD, in.paymentMethod());
            setText(row, E_SHARED, in.shared() ? "Yes" : "No");
            setText(row, E_NOTES, in.notes());
            setText(row, E_OCCASION, in.occasion());
        }
    }

    private void writeInvestments(Workbook wb, List<InvestmentRow> rows) {
        Sheet sheet = requireSheet(wb, SHEET_INVESTMENTS);
        int r = FIRST_DATA_ROW;
        for (InvestmentRow in : rows) {
            Row row = rowAt(sheet, r++);
            setDate(wb, row, I_DATE, in.date());
            setText(row, I_OWNER, in.owner());
            setText(row, I_TYPE, in.type());
            setText(row, I_NAME, in.name());
            setText(row, I_ACCOUNT, in.account());
            getOrCreateCell(row, I_AMOUNT).setCellValue(in.amountInvested());
            if (in.currentValue() != null) {
                getOrCreateCell(row, I_CURRENT).setCellValue(in.currentValue());
            } else {
                getOrCreateCell(row, I_CURRENT).setBlank();
            }
            setText(row, I_NOTES, in.notes());
        }
    }

    private void writeIncome(Workbook wb, List<IncomeRow> rows) {
        Sheet sheet = requireSheet(wb, SHEET_INCOME);
        int r = FIRST_DATA_ROW;
        for (IncomeRow in : rows) {
            Row row = rowAt(sheet, r++);
            setDate(wb, row, IN_DATE, in.date());
            setText(row, IN_EARNER, in.earner());
            setText(row, IN_TYPE, in.incomeType());
            setText(row, IN_SOURCE, in.source());
            getOrCreateCell(row, IN_AMOUNT).setCellValue(in.amount());
            setText(row, IN_RECEIVED, in.receivedIn());
            setText(row, IN_NOTES, in.notes());
        }
    }

    private void writeBalances(Workbook wb, List<BalanceRow> rows) {
        Sheet sheet = requireSheet(wb, SHEET_BALANCES);
        int r = FIRST_DATA_ROW;
        for (BalanceRow in : rows) {
            Row row = rowAt(sheet, r++);
            setDate(wb, row, B_DATE, in.asOnDate());
            setText(row, B_OWNER, in.owner());
            setText(row, B_ACCT_TYPE, in.accountType());
            setText(row, B_INSTITUTION, in.institution());
            getOrCreateCell(row, B_BALANCE).setCellValue(in.balance());
            setText(row, B_NOTES, in.notes());
        }
    }

    private void writeLists(Workbook wb, Lists lists) {
        Sheet sheet = requireSheet(wb, SHEET_LISTS);
        writeColumn(sheet, L_CATEGORIES, lists.categories());
        writeColumn(sheet, L_METHODS, lists.paymentMethods());
        writeColumn(sheet, L_PEOPLE, lists.people());
        writeColumn(sheet, L_TYPES, lists.investmentTypes());
        writeColumn(sheet, L_ACCOUNTS, lists.accounts());
        writeColumn(sheet, L_INCOME_TYPES, lists.incomeTypes());
        writeColumn(sheet, L_ACCOUNT_TYPES, lists.accountTypes());
        writeColumn(sheet, L_OCCASIONS, lists.occasions());
    }

    private void writeColumn(Sheet sheet, int col, List<String> values) {
        int r = FIRST_DATA_ROW;
        for (String value : values) {
            getOrCreateCell(rowAt(sheet, r++), col).setCellValue(value);
        }
    }

    /**
     * Serializes a workbook to {@code .xlsx} bytes, flagging formulas to
     * recalculate when Excel next opens it (so the Summary tabs refresh).
     */
    byte[] serialize(XSSFWorkbook wb) {
        wb.setForceFormulaRecalculation(true);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Row rowAt(Sheet sheet, int r) {
        Row row = sheet.getRow(r);
        return row != null ? row : sheet.createRow(r);
    }

    private Sheet requireSheet(Workbook wb, String name) {
        Sheet sheet = wb.getSheet(name);
        if (sheet == null) {
            throw new IllegalStateException("Template is missing required sheet: " + name);
        }
        return sheet;
    }

    private void setText(Row row, int col, String value) {
        Cell cell = getOrCreateCell(row, col);
        if (value == null || value.isEmpty()) {
            cell.setBlank();
        } else {
            cell.setCellValue(value);
        }
    }

    private void setDate(Workbook wb, Row row, int col, LocalDate date) {
        Cell cell = getOrCreateCell(row, col);
        cell.setCellValue(date);
        CellStyle existing = cell.getCellStyle();
        // Ensure a date number format so Excel renders it as a date, not a serial.
        if (existing == null || existing.getDataFormat() == 0) {
            CellStyle style = wb.createCellStyle();
            style.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));
            cell.setCellStyle(style);
        }
    }

    private Cell getOrCreateCell(Row row, int col) {
        Cell cell = row.getCell(col);
        return cell != null ? cell : row.createCell(col);
    }
}
