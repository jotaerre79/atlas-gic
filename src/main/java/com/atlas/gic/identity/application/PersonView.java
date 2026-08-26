package com.atlas.gic.identity.application;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record PersonView(
        UUID personId,
        String status,
        String givenName,
        String middleName,
        String familyName,
        String displayName,
        List<IdentifierView> identifiers) {

    public PersonView {
        Objects.requireNonNull(personId, "personId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(givenName, "givenName must not be null");
        Objects.requireNonNull(familyName, "familyName must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        identifiers = List.copyOf(identifiers == null ? List.of() : identifiers);
    }

    public record IdentifierView(String type, String issuer, String maskedValue) {
    }
}
