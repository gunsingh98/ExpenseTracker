package com.expensetracker.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Records and queries audit entries. Writes are <strong>best-effort</strong>:
 * {@link #record} never throws, so a failure to persist an audit entry can
 * never break the user's actual mutation.
 *
 * <p>{@link #record} must be called on the request thread, where the Spring
 * Security context carries the authenticated user.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditDao dao;
    private final ObjectMapper mapper;

    public AuditService(AuditDao dao, ObjectMapper mapper) {
        this.dao = dao;
        this.mapper = mapper;
    }

    /**
     * Records a change by the current user. Old/new values are serialized to
     * JSON; pass {@code null} for the side that does not apply (old for CREATE,
     * new for DELETE).
     */
    public void record(AuditAction action, EntityType type, String entityId,
                       Object oldValue, Object newValue) {
        try {
            dao.insert(new AuditEntry(
                    0,
                    Instant.now(),
                    currentUsername(),
                    action.name(),
                    type.name(),
                    entityId,
                    toJson(oldValue),
                    toJson(newValue)));
        } catch (RuntimeException e) {
            log.warn("Audit write failed (action={}, type={}, id={}) — mutation unaffected",
                    action, type, entityId, e);
        }
    }

    /** Audit entries newest-first, optionally filtered by entity type. */
    public List<AuditEntry> query(String entityType, int limit, int offset) {
        return dao.query(entityType, limit, offset);
    }

    private static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getName() != null) ? auth.getName() : "unknown";
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            // Surfaced to record()'s catch; logged there, mutation unaffected.
            throw new IllegalStateException("Could not serialize audit value", e);
        }
    }
}
