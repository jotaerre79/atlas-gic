package com.atlas.gic.relationships.application;

import com.atlas.gic.identity.domain.PersonId;
import com.atlas.gic.relationships.domain.PersonOrganizationRelationshipId;

import java.time.LocalDate;

public record EndPersonOrganizationRelationshipCommand(
        PersonId personId,
        PersonOrganizationRelationshipId relationshipId,
        LocalDate validTo,
        String correlationId) {
    public EndPersonOrganizationRelationshipCommand {
        java.util.Objects.requireNonNull(personId, "personId must not be null");
        java.util.Objects.requireNonNull(relationshipId, "relationshipId must not be null");
        java.util.Objects.requireNonNull(validTo, "validTo must not be null");
        if (correlationId == null || correlationId.isBlank() || correlationId.trim().length() > 160
                || correlationId.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("correlationId must contain 1..160 printable characters");
        }
        correlationId = correlationId.trim();
    }
}
