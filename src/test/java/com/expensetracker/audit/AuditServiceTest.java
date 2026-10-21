package com.expensetracker.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuditServiceTest {

    private final AuditDao dao = mock(AuditDao.class);
    private final AuditService service = new AuditService(dao, new ObjectMapper());

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "n/a", List.of()));
    }

    @Test
    void recordsCurrentUserActionAndSerializedValues() {
        authenticateAs("alice");

        service.record(AuditAction.UPDATE, EntityType.EXPENSE, "7",
                new Snapshot("Groceries", 100), new Snapshot("Groceries", 150));

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(dao).insert(captor.capture());
        AuditEntry entry = captor.getValue();

        assertThat(entry.username()).isEqualTo("alice");
        assertThat(entry.action()).isEqualTo("UPDATE");
        assertThat(entry.entityType()).isEqualTo("EXPENSE");
        assertThat(entry.entityId()).isEqualTo("7");
        assertThat(entry.oldValue()).contains("\"amount\":100");
        assertThat(entry.newValue()).contains("\"amount\":150");
        assertThat(entry.timestamp()).isNotNull();
    }

    @Test
    void nullSideSerializesToNull() {
        authenticateAs("bob");
        service.record(AuditAction.CREATE, EntityType.EXPENSE, null, null, new Snapshot("Rent", 2000));

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(dao).insert(captor.capture());
        assertThat(captor.getValue().oldValue()).isNull();
        assertThat(captor.getValue().newValue()).isNotNull();
    }

    @Test
    void fallsBackToUnknownWhenNoAuthentication() {
        // No authentication set in the context.
        service.record(AuditAction.DELETE, EntityType.LIST, "CATEGORIES", List.of("a"), null);

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(dao).insert(captor.capture());
        assertThat(captor.getValue().username()).isEqualTo("unknown");
    }

    @Test
    void bestEffortSwallowsPersistenceFailure() {
        authenticateAs("alice");
        doThrow(new RuntimeException("db down")).when(dao).insert(org.mockito.ArgumentMatchers.any());

        assertThatCode(() ->
                service.record(AuditAction.CREATE, EntityType.EXPENSE, null, null, new Snapshot("x", 1)))
                .doesNotThrowAnyException();
    }

    private record Snapshot(String category, int amount) {
    }
}
