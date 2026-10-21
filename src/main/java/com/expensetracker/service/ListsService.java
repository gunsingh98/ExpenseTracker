package com.expensetracker.service;

import com.expensetracker.audit.AuditAction;
import com.expensetracker.audit.AuditService;
import com.expensetracker.audit.EntityType;
import com.expensetracker.data.ListColumn;
import com.expensetracker.data.ListsDao;
import com.expensetracker.model.Lists;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ListsService {

    private final ListsDao dao;
    private final AuditService audit;

    public ListsService(ListsDao dao, AuditService audit) {
        this.dao = dao;
        this.audit = audit;
    }

    public Lists get() {
        return dao.readAll();
    }

    public void replace(ListColumn column, List<String> values) {
        List<String> cleaned = values.stream()
                .map(v -> v == null ? "" : v.trim())
                .filter(v -> !v.isEmpty())
                .distinct()
                .toList();
        List<String> old = dao.values(column);
        dao.replace(column, cleaned);
        audit.record(AuditAction.REPLACE, EntityType.LIST, column.name(), old, cleaned);
    }
}
