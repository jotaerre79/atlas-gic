package com.atlas.gic.relationships.application;

import com.atlas.gic.relationships.domain.PersonOrganizationRelationshipStatus;
import com.atlas.gic.relationships.domain.PersonOrganizationRelationshipType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PersonOrganizationRelationshipView(
        UUID relationshipId,
        UUID personId,
        UUID organizationId,
        String organizationLegalName,
        String organizationTradeName,
        PersonOrganizationRelationshipType relationshipType,
        LocalDate validFrom,
        LocalDate validTo,
        PersonOrganizationRelationshipStatus status,
        Instant createdAt) {
}
