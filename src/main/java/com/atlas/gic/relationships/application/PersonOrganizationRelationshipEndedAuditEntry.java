package com.atlas.gic.relationships.application;

import com.atlas.gic.identity.domain.OrganizationId;
import com.atlas.gic.identity.domain.PersonId;
import com.atlas.gic.relationships.domain.PersonOrganizationRelationshipId;
import com.atlas.gic.relationships.domain.PersonOrganizationRelationshipType;
import com.atlas.gic.shared.tenancy.domain.TenantId;

import java.time.Instant;
import java.util.Objects;

public record PersonOrganizationRelationshipEndedAuditEntry(
        String actor,
        TenantId tenantId,
        PersonOrganizationRelationshipId relationshipId,
        PersonId personId,
        OrganizationId organizationId,
        PersonOrganizationRelationshipType relationshipType,
        String correlationId,
        Instant timestamp) {

    public static final String ACTION = "PERSON_ORGANIZATION_RELATIONSHIP_ENDED";

    public PersonOrganizationRelationshipEndedAuditEntry {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(relationshipId, "relationshipId must not be null");
        Objects.requireNonNull(personId, "personId must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(relationshipType, "relationshipType must not be null");
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId must not be blank");
        }
        if (correlationId.length() > 160) {
            throw new IllegalArgumentException("correlationId must not exceed 160 characters");
        }
        timestamp = timestamp == null ? Instant.now() : timestamp;
    }

    public String action() {
        return ACTION;
    }
}
