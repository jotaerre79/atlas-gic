package com.atlas.gic.roles.application;

import com.atlas.gic.identity.domain.PersonId;
import com.atlas.gic.roles.domain.BusinessRoleAssignmentId;
import com.atlas.gic.roles.domain.BusinessRoleType;
import com.atlas.gic.shared.tenancy.domain.TenantId;

import java.time.Instant;
import java.util.Objects;

public record BusinessRoleAssignedAuditEntry(
        String actor,
        TenantId tenantId,
        PersonId personId,
        BusinessRoleAssignmentId assignmentId,
        BusinessRoleType role,
        String correlationId,
        Instant timestamp) {

    public static final String ACTION = "BUSINESS_ROLE_ASSIGNED";

    public BusinessRoleAssignedAuditEntry {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(personId, "personId must not be null");
        Objects.requireNonNull(assignmentId, "assignmentId must not be null");
        Objects.requireNonNull(role, "role must not be null");
        timestamp = timestamp == null ? Instant.now() : timestamp;
    }

    public String action() {
        return ACTION;
    }
}
