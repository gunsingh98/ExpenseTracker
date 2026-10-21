package com.expensetracker.service;

import com.expensetracker.audit.AuditAction;
import com.expensetracker.audit.AuditService;
import com.expensetracker.audit.EntityType;
import com.expensetracker.data.InvestmentDao;
import com.expensetracker.model.FundValueInput;
import com.expensetracker.model.InvestmentRow;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifies InvestmentService.setFundCurrentValue groups a fund onto a single row. */
class InvestmentServiceFundTest {

    private final InvestmentDao dao = mock(InvestmentDao.class);
    private final AuditService audit = mock(AuditService.class);
    private final InvestmentService service = new InvestmentService(dao, audit);

    private InvestmentRow inv(int id, LocalDate date, String name, Double currentValue) {
        return new InvestmentRow(id, date, "Me", "Mutual Funds", name, "Brokerage", 5000, currentValue, "");
    }

    @Test
    void setsValueOnMostRecentContributionAndClearsOtherRowsOfSameFund() {
        InvestmentRow jan = inv(5, LocalDate.of(2026, 1, 10), "PPFAS", 8000.0);
        InvestmentRow feb = inv(6, LocalDate.of(2026, 2, 10), "PPFAS", null);
        InvestmentRow gold = inv(7, LocalDate.of(2026, 2, 10), "Gold", 3000.0); // different fund, untouched
        when(dao.findAll()).thenReturn(List.of(jan, feb, gold));

        service.setFundCurrentValue("PPFAS", 20000.0);

        // Value lands on the latest PPFAS row (Feb, id 6); the earlier PPFAS row (id 5)
        // is cleared; the Gold row is not referenced at all.
        verify(dao).setFundCurrentValue(eq(6), eq(20000.0), eq(List.of(5)));
        verify(audit).record(AuditAction.UPDATE, EntityType.INVESTMENT, "fund:PPFAS",
                null, new FundValueInput("PPFAS", 20000.0));
    }

    @Test
    void trimsFundNameWhenMatching() {
        InvestmentRow row = inv(5, LocalDate.of(2026, 1, 10), "PPFAS", null);
        when(dao.findAll()).thenReturn(List.of(row));

        service.setFundCurrentValue("  PPFAS  ", 12000.0);

        verify(dao).setFundCurrentValue(eq(5), eq(12000.0), eq(List.of()));
    }

    @Test
    void unknownFundThrows() {
        when(dao.findAll()).thenReturn(List.of());

        assertThatThrownBy(() -> service.setFundCurrentValue("Nope", 100.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteFundRemovesEveryContributionOfThatFund() {
        InvestmentRow jan = inv(5, LocalDate.of(2026, 1, 10), "PPFAS", 8000.0);
        InvestmentRow feb = inv(6, LocalDate.of(2026, 2, 10), "PPFAS", null);
        InvestmentRow gold = inv(7, LocalDate.of(2026, 2, 10), "Gold", 3000.0); // different fund, untouched
        when(dao.findAll()).thenReturn(List.of(jan, feb, gold));

        service.deleteFund("PPFAS");

        // Both PPFAS rows (ids 5 and 6) are deleted together; Gold (id 7) is left alone.
        verify(dao).deleteFund(eq(List.of(5, 6)));
        verify(audit).record(AuditAction.DELETE, EntityType.INVESTMENT, "fund:PPFAS",
                List.of(jan, feb), null);
    }

    @Test
    void deleteFundTrimsName() {
        InvestmentRow row = inv(5, LocalDate.of(2026, 1, 10), "PPFAS", null);
        when(dao.findAll()).thenReturn(List.of(row));

        service.deleteFund("  PPFAS  ");

        verify(dao).deleteFund(eq(List.of(5)));
    }

    @Test
    void deleteUnknownFundThrows() {
        when(dao.findAll()).thenReturn(List.of());

        assertThatThrownBy(() -> service.deleteFund("Nope"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
