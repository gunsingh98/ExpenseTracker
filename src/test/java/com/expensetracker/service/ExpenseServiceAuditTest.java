package com.expensetracker.service;

import com.expensetracker.audit.AuditAction;
import com.expensetracker.audit.AuditService;
import com.expensetracker.audit.EntityType;
import com.expensetracker.data.ExpenseDao;
import com.expensetracker.model.ExpenseInput;
import com.expensetracker.model.ExpenseRow;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifies ExpenseService records the right audit entries around each mutation. */
class ExpenseServiceAuditTest {

    private final ExpenseDao dao = mock(ExpenseDao.class);
    private final AuditService audit = mock(AuditService.class);
    private final ExpenseService service = new ExpenseService(dao, audit);

    private final ExpenseInput input = new ExpenseInput(
            LocalDate.of(2026, 10, 15), "Me", "Groceries", "Milk", 150.0, "UPI", false, "", null);

    @Test
    void addRecordsCreateWithNewValue() {
        service.add(input);
        verify(dao).insert(input);
        verify(audit).record(eq(AuditAction.CREATE), eq(EntityType.EXPENSE), isNull(), isNull(), eq(input));
    }

    @Test
    void updateCapturesOldRowBeforeMutating() {
        ExpenseRow old = row(7);
        when(dao.findById(7)).thenReturn(old);

        service.update(7, input);

        // Old snapshot must be read BEFORE the update is applied.
        InOrder order = inOrder(dao);
        order.verify(dao).findById(7);
        order.verify(dao).update(7, input);
        verify(audit).record(AuditAction.UPDATE, EntityType.EXPENSE, "7", old, input);
    }

    @Test
    void deleteRecordsOldRow() {
        ExpenseRow old = row(3);
        when(dao.findById(3)).thenReturn(old);

        service.delete(3);

        InOrder order = inOrder(dao);
        order.verify(dao).findById(3);
        order.verify(dao).delete(3);
        verify(audit).record(AuditAction.DELETE, EntityType.EXPENSE, "3", old, null);
    }

    private static ExpenseRow row(int id) {
        return new ExpenseRow(id, LocalDate.of(2026, 1, 1), "Me", "Groceries", "old", 100.0, "Cash", false, "", null);
    }
}
