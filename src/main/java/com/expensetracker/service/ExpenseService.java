package com.expensetracker.service;

import com.expensetracker.audit.AuditAction;
import com.expensetracker.audit.AuditService;
import com.expensetracker.audit.EntityType;
import com.expensetracker.data.ExpenseDao;
import com.expensetracker.model.ExpenseInput;
import com.expensetracker.model.ExpenseRow;
import org.springframework.stereotype.Service;

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
}
