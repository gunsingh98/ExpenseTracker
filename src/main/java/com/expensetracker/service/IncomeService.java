package com.expensetracker.service;

import com.expensetracker.audit.AuditAction;
import com.expensetracker.audit.AuditService;
import com.expensetracker.audit.EntityType;
import com.expensetracker.data.IncomeDao;
import com.expensetracker.model.IncomeInput;
import com.expensetracker.model.IncomeRow;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class IncomeService {

    private final IncomeDao dao;
    private final AuditService audit;

    public IncomeService(IncomeDao dao, AuditService audit) {
        this.dao = dao;
        this.audit = audit;
    }

    /** All income entries, most recent first. */
    public List<IncomeRow> list() {
        return dao.findAll();
    }

    public void add(IncomeInput input) {
        dao.insert(input);
        audit.record(AuditAction.CREATE, EntityType.INCOME, null, null, input);
    }

    public void update(int id, IncomeInput input) {
        IncomeRow old = find(id);
        dao.update(id, input);
        audit.record(AuditAction.UPDATE, EntityType.INCOME, String.valueOf(id), old, input);
    }

    public void delete(int id) {
        IncomeRow old = find(id);
        dao.delete(id);
        audit.record(AuditAction.DELETE, EntityType.INCOME, String.valueOf(id), old, null);
    }

    /** The current row for {@code id}, captured before a mutation for the audit trail. */
    private IncomeRow find(int id) {
        IncomeRow row = dao.findById(id);
        if (row == null) {
            throw new IllegalArgumentException("No income with id " + id);
        }
        return row;
    }
}
