package com.atlas.gic.relationships.application;

import com.atlas.gic.identity.domain.OrganizationId;
import com.atlas.gic.identity.domain.PersonId;
import com.atlas.gic.relationships.domain.PersonOrganizationRelationshipType;

import java.time.LocalDate;

public record AssignPersonOrganizationRelationshipCommand(
        PersonId personId,
        OrganizationId organizationId,
        PersonOrganizationRelationshipType relationshipType,
        LocalDate validFrom,
        String correlationId) {

    public AssignPersonOrganizationRelationshipCommand {
        if (personId == null) {
            throw new IllegalArgumentException("personId is required");
        }
        if (organizationId == null) {
            throw new IllegalArgumentException("organizationId is required");
        }
        if (relationshipType == null) {
            throw new IllegalArgumentException("relationshipType is required");
        }
        if (validFrom == null) {
            throw new IllegalArgumentException("validFrom is required");
        }
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId is required");
        }
        correlationId = correlationId.trim();
        if (correlationId.length() > 160) {
            throw new IllegalArgumentException("correlationId is too long");
        }
    }
}
