package com.expensetracker.service;

import com.expensetracker.audit.AuditAction;
import com.expensetracker.audit.AuditService;
import com.expensetracker.audit.EntityType;
import com.expensetracker.data.BalanceDao;
import com.expensetracker.model.BalanceInput;
import com.expensetracker.model.BalanceRow;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BalanceService {

    private final BalanceDao dao;
    private final AuditService audit;

    public BalanceService(BalanceDao dao, AuditService audit) {
        this.dao = dao;
        this.audit = audit;
    }

    /** All balance snapshots, most recent first. */
    public List<BalanceRow> list() {
        return dao.findAll();
    }

    public void add(BalanceInput input) {
        dao.insert(input);
        audit.record(AuditAction.CREATE, EntityType.BALANCE, null, null, input);
    }

    public void update(int id, BalanceInput input) {
        BalanceRow old = find(id);
        dao.update(id, input);
        audit.record(AuditAction.UPDATE, EntityType.BALANCE, String.valueOf(id), old, input);
    }

    public void delete(int id) {
        BalanceRow old = find(id);
        dao.delete(id);
        audit.record(AuditAction.DELETE, EntityType.BALANCE, String.valueOf(id), old, null);
    }

    /** The current row for {@code id}, captured before a mutation for the audit trail. */
    private BalanceRow find(int id) {
        BalanceRow row = dao.findById(id);
        if (row == null) {
            throw new IllegalArgumentException("No balance with id " + id);
        }
        return row;
    }
}
