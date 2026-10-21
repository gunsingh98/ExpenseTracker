package com.expensetracker.service;

import com.expensetracker.audit.AuditAction;
import com.expensetracker.audit.AuditService;
import com.expensetracker.audit.EntityType;
import com.expensetracker.data.InvestmentDao;
import com.expensetracker.model.FundValueInput;
import com.expensetracker.model.InvestmentInput;
import com.expensetracker.model.InvestmentRow;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class InvestmentService {

    private final InvestmentDao dao;
    private final AuditService audit;

    public InvestmentService(InvestmentDao dao, AuditService audit) {
        this.dao = dao;
        this.audit = audit;
    }

    /** All investment entries, most recent first. */
    public List<InvestmentRow> list() {
        return dao.findAll();
    }

    public void add(InvestmentInput input) {
        dao.insert(input);
        audit.record(AuditAction.CREATE, EntityType.INVESTMENT, null, null, input);
    }

    public void update(int id, InvestmentInput input) {
        InvestmentRow old = find(id);
        dao.update(id, input);
        audit.record(AuditAction.UPDATE, EntityType.INVESTMENT, String.valueOf(id), old, input);
    }

    public void delete(int id) {
        InvestmentRow old = find(id);
        dao.delete(id);
        audit.record(AuditAction.DELETE, EntityType.INVESTMENT, String.valueOf(id), old, null);
    }

    /**
     * Sets the market value of an entire fund — every investment row sharing
     * {@code fundName} (trimmed, case-sensitive, matching how the UI groups funds)
     * — to {@code currentValue}. The value is kept on the fund's most recent
     * contribution and the current-value cell on its other rows is cleared, so a
     * fund that was contributed to monthly but valued once does not inflate the
     * dashboard's net-worth total (which sums current values across rows).
     *
     * @param currentValue the fund's total market value, or {@code null} to clear it
     * @throws IllegalArgumentException if no investment rows match {@code fundName}
     */
    public void setFundCurrentValue(String fundName, Double currentValue) {
        String key = normalize(fundName);
        List<InvestmentRow> fund = dao.findAll().stream()
                .filter(r -> normalize(r.name()).equals(key))
                .toList();
        if (fund.isEmpty()) {
            throw new IllegalArgumentException("No investments found for fund: " + fundName);
        }
        InvestmentRow target = fund.stream()
                .max(Comparator.comparing(InvestmentRow::date).thenComparingInt(InvestmentRow::id))
                .orElseThrow();
        List<Integer> clearIds = fund.stream()
                .map(InvestmentRow::id)
                .filter(id -> id != target.id())
                .toList();
        dao.setFundCurrentValue(target.id(), currentValue, clearIds);
        audit.record(AuditAction.UPDATE, EntityType.INVESTMENT, "fund:" + key, null,
                new FundValueInput(fundName, currentValue));
    }

    /**
     * Deletes an entire fund — every investment row sharing {@code fundName}
     * (trimmed, matching how the UI groups funds) — in one mutation, so all of a
     * fund's contributions are removed together rather than one at a time.
     *
     * @throws IllegalArgumentException if no investment rows match {@code fundName}
     */
    public void deleteFund(String fundName) {
        String key = normalize(fundName);
        List<InvestmentRow> fund = dao.findAll().stream()
                .filter(r -> normalize(r.name()).equals(key))
                .toList();
        if (fund.isEmpty()) {
            throw new IllegalArgumentException("No investments found for fund: " + fundName);
        }
        List<Integer> ids = fund.stream().map(InvestmentRow::id).toList();
        dao.deleteFund(ids);
        audit.record(AuditAction.DELETE, EntityType.INVESTMENT, "fund:" + key, fund, null);
    }

    /** The current row for {@code id}, captured before a mutation for the audit trail. */
    private InvestmentRow find(int id) {
        InvestmentRow row = dao.findById(id);
        if (row == null) {
            throw new IllegalArgumentException("No investment with id " + id);
        }
        return row;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim();
    }
}
