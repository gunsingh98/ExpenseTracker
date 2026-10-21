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
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * One-time importer: loads a household workbook (the same {@code .xlsx} shape the
 * app exports) into the SQLite data store. It is the read-side mirror of
 * {@link WorkbookExportService} — same sheet names, same column layout, data from
 * Excel row 5 (index {@value #FIRST_DATA_ROW}) — reading each row with Apache POI
 * (which decodes date serials and shared strings for us) and inserting it through
 * the existing DAOs.
 *
 * <p>Rows are read until the first one with a blank date column, matching how the
 * exporter writes contiguous rows followed by pre-formatted empty formula rows.
 * The auto-formula columns (Month / Year / Gain-Loss / Net-Worth) are ignored —
 * the app recomputes those.
 */
@Service
public class WorkbookImportService {

    private static final Logger log = LoggerFactory.getLogger(WorkbookImportService.class);

    /** Data rows begin at Excel row 5 (0-based index 4); headers are on row 4. */
    static final int FIRST_DATA_ROW = 4;

    // Column indices mirror WorkbookExportService (auto-formula columns omitted).
    private static final int E_DATE = 0, E_PAID_BY = 2, E_CATEGORY = 3, E_DESC = 4,
            E_AMOUNT = 5, E_METHOD = 6, E_SHARED = 7, E_NOTES = 8, E_OCCASION = 10;
    private static final int I_DATE = 0, I_OWNER = 2, I_TYPE = 3, I_NAME = 4, I_ACCOUNT = 5,
            I_AMOUNT = 6, I_CURRENT = 7, I_NOTES = 9;
    private static final int IN_DATE = 0, IN_EARNER = 2, IN_TYPE = 3, IN_SOURCE = 4,
            IN_AMOUNT = 5, IN_RECEIVED = 6, IN_NOTES = 7;
    private static final int B_DATE = 0, B_OWNER = 1, B_ACCT_TYPE = 2, B_INSTITUTION = 3,
            B_BALANCE = 4, B_NOTES = 5;
    private static final int L_CATEGORIES = 0, L_METHODS = 1, L_PEOPLE = 2, L_TYPES = 3,
            L_ACCOUNTS = 4, L_INCOME_TYPES = 5, L_ACCOUNT_TYPES = 6, L_OCCASIONS = 7;

    private final ExpenseDao expenseDao;
    private final InvestmentDao investmentDao;
    private final IncomeDao incomeDao;
    private final BalanceDao balanceDao;
    private final ListsDao listsDao;

    public WorkbookImportService(ExpenseDao expenseDao, InvestmentDao investmentDao,
                                 IncomeDao incomeDao, BalanceDao balanceDao, ListsDao listsDao) {
        this.expenseDao = expenseDao;
        this.investmentDao = investmentDao;
        this.incomeDao = incomeDao;
        this.balanceDao = balanceDao;
        this.listsDao = listsDao;
    }

    /** Counts of rows inserted per sheet. */
    public record Result(int expenses, int investments, int income, int balances, int lists) {}

    /** Reads the workbook at {@code path} and inserts every data row. */
    public Result importFrom(Path path) {
        try (InputStream in = Files.newInputStream(path);
             XSSFWorkbook wb = new XSSFWorkbook(in)) {
            Result result = new Result(
                    importExpenses(wb),
                    importInvestments(wb),
                    importIncome(wb),
                    importBalances(wb),
                    importLists(wb));
            log.info("Workbook import complete from {}: {} expenses, {} investments, "
                            + "{} income, {} balances, {} list values",
                    path, result.expenses(), result.investments(), result.income(),
                    result.balances(), result.lists());
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read workbook " + path, e);
        }
    }

    private int importExpenses(XSSFWorkbook wb) {
        Sheet sheet = requireSheet(wb, WorkbookExportService.SHEET_EXPENSES);
        int count = 0;
        for (int r = FIRST_DATA_ROW; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            LocalDate date = date(row, E_DATE);
            if (date == null) {
                break; // first blank-date row marks the end of the data block
            }
            expenseDao.insert(new ExpenseInput(
                    date,
                    str(row, E_PAID_BY),
                    str(row, E_CATEGORY),
                    str(row, E_DESC),
                    nonNull(num(row, E_AMOUNT)),
                    str(row, E_METHOD),
                    isYes(str(row, E_SHARED)),
                    str(row, E_NOTES),
                    str(row, E_OCCASION)));
            count++;
        }
        return count;
    }

    private int importInvestments(XSSFWorkbook wb) {
        Sheet sheet = requireSheet(wb, WorkbookExportService.SHEET_INVESTMENTS);
        int count = 0;
        for (int r = FIRST_DATA_ROW; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            LocalDate date = date(row, I_DATE);
            if (date == null) {
                break;
            }
            investmentDao.insert(new InvestmentInput(
                    date,
                    str(row, I_OWNER),
                    str(row, I_TYPE),
                    str(row, I_NAME),
                    str(row, I_ACCOUNT),
                    nonNull(num(row, I_AMOUNT)),
                    num(row, I_CURRENT),
                    str(row, I_NOTES)));
            count++;
        }
        return count;
    }

    private int importIncome(XSSFWorkbook wb) {
        Sheet sheet = requireSheet(wb, WorkbookExportService.SHEET_INCOME);
        int count = 0;
        for (int r = FIRST_DATA_ROW; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            LocalDate date = date(row, IN_DATE);
            if (date == null) {
                break;
            }
            incomeDao.insert(new IncomeInput(
                    date,
                    str(row, IN_EARNER),
                    str(row, IN_TYPE),
                    str(row, IN_SOURCE),
                    nonNull(num(row, IN_AMOUNT)),
                    str(row, IN_RECEIVED),
                    str(row, IN_NOTES)));
            count++;
        }
        return count;
    }

    private int importBalances(XSSFWorkbook wb) {
        Sheet sheet = requireSheet(wb, WorkbookExportService.SHEET_BALANCES);
        int count = 0;
        for (int r = FIRST_DATA_ROW; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            LocalDate date = date(row, B_DATE);
            if (date == null) {
                break;
            }
            balanceDao.insert(new BalanceInput(
                    date,
                    str(row, B_OWNER),
                    str(row, B_ACCT_TYPE),
                    str(row, B_INSTITUTION),
                    nonNull(num(row, B_BALANCE)),
                    str(row, B_NOTES)));
            count++;
        }
        return count;
    }

    private int importLists(XSSFWorkbook wb) {
        Sheet sheet = requireSheet(wb, WorkbookExportService.SHEET_LISTS);
        int total = 0;
        total += replaceList(sheet, ListColumn.CATEGORIES, L_CATEGORIES);
        total += replaceList(sheet, ListColumn.PAYMENT_METHODS, L_METHODS);
        total += replaceList(sheet, ListColumn.PEOPLE, L_PEOPLE);
        total += replaceList(sheet, ListColumn.INVESTMENT_TYPES, L_TYPES);
        total += replaceList(sheet, ListColumn.ACCOUNTS, L_ACCOUNTS);
        total += replaceList(sheet, ListColumn.INCOME_TYPES, L_INCOME_TYPES);
        total += replaceList(sheet, ListColumn.ACCOUNT_TYPES, L_ACCOUNT_TYPES);
        total += replaceList(sheet, ListColumn.OCCASIONS, L_OCCASIONS);
        return total;
    }

    /** Reads one Lists column top-down until the first blank, then replaces the stored list. */
    private int replaceList(Sheet sheet, ListColumn column, int col) {
        List<String> values = new ArrayList<>();
        for (int r = FIRST_DATA_ROW; r <= sheet.getLastRowNum(); r++) {
            String value = str(sheet.getRow(r), col);
            if (value == null) {
                break;
            }
            values.add(value);
        }
        if (!values.isEmpty()) {
            listsDao.replace(column, values);
        }
        return values.size();
    }

    // --- cell readers --------------------------------------------------------

    private static Cell cell(Row row, int col) {
        return row == null ? null : row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
    }

    private static CellType effectiveType(Cell cell) {
        CellType type = cell.getCellType();
        return type == CellType.FORMULA ? cell.getCachedFormulaResultType() : type;
    }

    /** Trimmed text of a cell, or {@code null} when blank. Numerics render without a trailing {@code .0}. */
    private static String str(Row row, int col) {
        Cell cell = cell(row, col);
        if (cell == null) {
            return null;
        }
        String value = switch (effectiveType(cell)) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                double d = cell.getNumericCellValue();
                yield d == Math.rint(d) ? Long.toString((long) d) : Double.toString(d);
            }
            case BOOLEAN -> cell.getBooleanCellValue() ? "Yes" : "No";
            default -> null;
        };
        if (value == null) {
            return null;
        }
        value = value.trim();
        return value.isEmpty() ? null : value;
    }

    /** Date value of a cell (serial or ISO text), or {@code null} when blank. */
    private static LocalDate date(Row row, int col) {
        Cell cell = cell(row, col);
        if (cell == null) {
            return null;
        }
        CellType type = effectiveType(cell);
        if (type == CellType.NUMERIC) {
            return DateUtil.getLocalDateTime(cell.getNumericCellValue()).toLocalDate();
        }
        if (type == CellType.STRING) {
            String text = cell.getStringCellValue().trim();
            if (!text.isEmpty()) {
                return LocalDate.parse(text);
            }
        }
        return null;
    }

    /** Numeric value of a cell, or {@code null} when blank. */
    private static Double num(Row row, int col) {
        Cell cell = cell(row, col);
        if (cell == null) {
            return null;
        }
        CellType type = effectiveType(cell);
        if (type == CellType.NUMERIC) {
            return cell.getNumericCellValue();
        }
        if (type == CellType.STRING) {
            String text = cell.getStringCellValue().trim().replace(",", "");
            if (!text.isEmpty()) {
                return Double.parseDouble(text);
            }
        }
        return null;
    }

    private static double nonNull(Double value) {
        return value == null ? 0.0 : value;
    }

    private static boolean isYes(String value) {
        return value != null && value.equalsIgnoreCase("Yes");
    }

    private static Sheet requireSheet(XSSFWorkbook wb, String name) {
        Sheet sheet = wb.getSheet(name);
        if (sheet == null) {
            throw new IllegalStateException("Workbook is missing required sheet: " + name);
        }
        return sheet;
    }
}
