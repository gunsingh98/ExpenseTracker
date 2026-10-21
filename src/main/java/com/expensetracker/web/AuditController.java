package com.expensetracker.web;

import com.expensetracker.audit.AuditEntry;
import com.expensetracker.audit.AuditService;
import com.expensetracker.audit.EntityType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

/**
 * Read-only audit trail. Protected by the existing {@code /api/**} security
 * rule, so any authenticated household user may view it.
 */
@RestController
@RequestMapping("/api/audit")
@Validated
public class AuditController {

    private final AuditService audit;

    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping
    public List<AuditEntry> list(
            @RequestParam(required = false) String entityType,
            @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return audit.query(normalize(entityType), limit, offset);
    }

    /** Validates the optional entityType filter against the enum (400 if unknown). */
    private static String normalize(String entityType) {
        if (entityType == null || entityType.isBlank()) {
            return null;
        }
        try {
            return EntityType.valueOf(entityType.toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown entity type: " + entityType);
        }
    }
}
