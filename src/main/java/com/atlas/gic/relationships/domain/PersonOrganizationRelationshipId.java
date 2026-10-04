package com.atlas.gic.relationships.domain;

import java.util.Objects;
import java.util.UUID;

public record PersonOrganizationRelationshipId(UUID value) {

    public PersonOrganizationRelationshipId {
        Objects.requireNonNull(value, "relationshipId must not be null");
    }

    public static PersonOrganizationRelationshipId newId() {
        return new PersonOrganizationRelationshipId(UUID.randomUUID());
    }

    public static PersonOrganizationRelationshipId of(UUID value) {
        return new PersonOrganizationRelationshipId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
