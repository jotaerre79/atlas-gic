package com.atlas.gic.relationships.application;

import com.atlas.gic.identity.domain.OrganizationId;
import com.atlas.gic.identity.domain.PersonId;
import com.atlas.gic.relationships.domain.PersonOrganizationRelationshipId;
import com.atlas.gic.relationships.domain.PersonOrganizationRelationshipStatus;
import com.atlas.gic.relationships.domain.PersonOrganizationRelationshipType;

import java.time.Instant;
import java.time.LocalDate;

public record AssignPersonOrganizationRelationshipResult(
        PersonOrganizationRelationshipId relationshipId,
        PersonId personId,
        OrganizationId organizationId,
        PersonOrganizationRelationshipType relationshipType,
        LocalDate validFrom,
        LocalDate validTo,
        PersonOrganizationRelationshipStatus status,
        Instant createdAt) {
}
