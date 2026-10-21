package com.expensetracker.workbook;

import com.expensetracker.data.BalanceDao;
import com.expensetracker.data.ExpenseDao;
import com.expensetracker.data.IncomeDao;
import com.expensetracker.data.InvestmentDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * One-time workbook migration, triggered from the command line:
 *
 * <pre>{@code
 * java -jar expense-tracker.jar --import=./Household_Expenses_and_Investments_Tracker-3.xlsx
 * }</pre>
 *
 * <p>Runs after the schema is created but is a no-op unless {@code --import=<path>}
 * is supplied. To avoid duplicating rows it refuses to import into a database that
 * already holds expense/investment/income/balance data; pass {@code --import-force}
 * to override that guard. The import is additive (it does not clear existing rows),
 * so the guard is the only protection against a double run — intended for a fresh,
 * empty database.
 */
@Component
public class WorkbookImportRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(WorkbookImportRunner.class);

    private final WorkbookImportService importService;
    private final ExpenseDao expenseDao;
    private final InvestmentDao investmentDao;
    private final IncomeDao incomeDao;
    private final BalanceDao balanceDao;

    public WorkbookImportRunner(WorkbookImportService importService, ExpenseDao expenseDao,
                                InvestmentDao investmentDao, IncomeDao incomeDao,
                                BalanceDao balanceDao) {
        this.importService = importService;
        this.expenseDao = expenseDao;
        this.investmentDao = investmentDao;
        this.incomeDao = incomeDao;
        this.balanceDao = balanceDao;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> paths = args.getOptionValues("import");
        if (paths == null || paths.isEmpty()) {
            return; // normal startup — no migration requested
        }
        Path path = Path.of(paths.get(paths.size() - 1));
        if (!Files.isReadable(path)) {
            throw new IllegalArgumentException("--import file not found or unreadable: " + path);
        }

        boolean force = args.containsOption("import-force");
        if (!force && hasExistingData()) {
            log.warn("--import ignored: database already contains data. "
                    + "Pass --import-force to import anyway (rows are added, not replaced).");
            return;
        }

        log.info("Importing workbook {} ...", path);
        importService.importFrom(path);
    }

    private boolean hasExistingData() {
        return !expenseDao.findAll().isEmpty()
                || !investmentDao.findAll().isEmpty()
                || !incomeDao.findAll().isEmpty()
                || !balanceDao.findAll().isEmpty();
    }
}
