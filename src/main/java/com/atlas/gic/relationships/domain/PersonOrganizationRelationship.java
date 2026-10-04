package com.atlas.gic.relationships.domain;

import com.atlas.gic.identity.domain.OrganizationId;
import com.atlas.gic.identity.domain.PersonId;
import com.atlas.gic.shared.tenancy.domain.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public record PersonOrganizationRelationship(
        PersonOrganizationRelationshipId relationshipId,
        TenantId tenantId,
        PersonId personId,
        OrganizationId organizationId,
        PersonOrganizationRelationshipType relationshipType,
        LocalDate validFrom,
        LocalDate validTo,
        PersonOrganizationRelationshipStatus status,
        Instant createdAt) {

    public PersonOrganizationRelationship {
        Objects.requireNonNull(relationshipId, "relationshipId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(personId, "personId must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(relationshipType, "relationshipType must not be null");
        Objects.requireNonNull(validFrom, "validFrom must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (validTo != null && validTo.isBefore(validFrom)) {
            throw new IllegalArgumentException("validTo must not be before validFrom");
        }
    }

    public static PersonOrganizationRelationship active(
            TenantId tenantId,
            PersonId personId,
            OrganizationId organizationId,
            PersonOrganizationRelationshipType relationshipType,
            LocalDate validFrom) {
        return new PersonOrganizationRelationship(
                PersonOrganizationRelationshipId.newId(),
                tenantId,
                personId,
                organizationId,
                relationshipType,
                validFrom,
                null,
                PersonOrganizationRelationshipStatus.ACTIVE,
                Instant.now());
    }
}
