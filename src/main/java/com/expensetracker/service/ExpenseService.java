package com.expensetracker.service;

import com.expensetracker.audit.AuditAction;
import com.expensetracker.audit.AuditService;
import com.expensetracker.audit.EntityType;
import com.expensetracker.data.ExpenseDao;
import com.expensetracker.data.PageCursor;
import com.expensetracker.model.CursorPage;
import com.expensetracker.model.ExpenseInput;
import com.expensetracker.model.ExpenseRow;
import com.expensetracker.model.ExpenseTotals;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.List;

@Service
public class ExpenseService {

    private final ExpenseDao dao;
    private final AuditService audit;

    public ExpenseService(ExpenseDao dao, AuditService audit) {
        this.dao = dao;
        this.audit = audit;
    }

    /** All expenses, most recent first. */
    public List<ExpenseRow> list() {
        return dao.findAll();
    }

    /**
     * One page of expenses, most recent first, after the opaque {@code cursor} (null
     * for the first page). Fetches one extra row to learn whether another page exists
     * without a COUNT. A malformed cursor throws {@link IllegalArgumentException}.
     */
    public CursorPage<ExpenseRow> page(String cursor, int limit, YearMonth month, String category) {
        PageCursor after = cursor == null || cursor.isBlank() ? null : PageCursor.decode(cursor);
        List<ExpenseRow> rows = dao.findPage(after, limit + 1, month, blankToNull(category));
        if (rows.size() <= limit) {
            return new CursorPage<>(rows, null);
        }
        List<ExpenseRow> items = rows.subList(0, limit);
        ExpenseRow last = items.getLast();
        return new CursorPage<>(List.copyOf(items), new PageCursor(last.date(), last.id()).encode());
    }

    /** Count and sum across all pages for the same filters as {@link #page}. */
    public ExpenseTotals totals(YearMonth month, String category) {
        return dao.totals(month, blankToNull(category));
    }

    /** Months that have expenses, newest first, the page's month-filter options. */
    public List<YearMonth> months() {
        return dao.findMonths();
    }

    public void add(ExpenseInput input) {
        dao.insert(input);
        audit.record(AuditAction.CREATE, EntityType.EXPENSE, null, null, input);
    }

    public void update(int id, ExpenseInput input) {
        ExpenseRow old = find(id);
        dao.update(id, input);
        audit.record(AuditAction.UPDATE, EntityType.EXPENSE, String.valueOf(id), old, input);
    }

    public void delete(int id) {
        ExpenseRow old = find(id);
        dao.delete(id);
        audit.record(AuditAction.DELETE, EntityType.EXPENSE, String.valueOf(id), old, null);
    }

    /** The current row for {@code id}, captured before a mutation for the audit trail. */
    private ExpenseRow find(int id) {
        ExpenseRow row = dao.findById(id);
        if (row == null) {
            throw new IllegalArgumentException("No expense with id " + id);
        }
        return row;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
