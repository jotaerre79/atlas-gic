package com.atlas.gic.identity.application;

import java.util.Objects;
import java.util.UUID;

public record PersonSearchItem(
        UUID personId,
        String displayName,
        String status,
        String identifierType,
        String identifierIssuer) {

    public PersonSearchItem {
        Objects.requireNonNull(personId, "personId must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }
}
