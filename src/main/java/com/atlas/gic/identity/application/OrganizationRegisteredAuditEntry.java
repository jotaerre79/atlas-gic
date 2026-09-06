package com.atlas.gic.identity.application;

import com.atlas.gic.identity.domain.OrganizationId;
import com.atlas.gic.shared.tenancy.domain.TenantId;

import java.time.Instant;
import java.util.Objects;

public record OrganizationRegisteredAuditEntry(
        String actor,
        TenantId tenantId,
        OrganizationId organizationId,
        String correlationId,
        Instant timestamp) {

    public static final String ACTION = "ORGANIZATION_REGISTERED";
    private static final int MAX_CORRELATION_ID_LENGTH = 160;

    public OrganizationRegisteredAuditEntry {
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("actor is required");
        }
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId is required");
        }
        correlationId = correlationId.trim();
        if (correlationId.length() > MAX_CORRELATION_ID_LENGTH) {
            throw new IllegalArgumentException("correlationId is too long");
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}
