package com.atlas.gic.identity.application;

import com.atlas.gic.identity.domain.PersonId;
import com.atlas.gic.shared.tenancy.domain.TenantId;

import java.time.Instant;
import java.util.Objects;

public record PersonRegisteredAuditEntry(
        String actor,
        TenantId tenantId,
        PersonId personId,
        String correlationId,
        Instant timestamp) {

    public static final String ACTION = "PERSON_REGISTERED";

    public PersonRegisteredAuditEntry {
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("actor is required");
        }
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(personId, "personId must not be null");
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}
