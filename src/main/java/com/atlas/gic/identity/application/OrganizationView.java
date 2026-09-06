package com.atlas.gic.identity.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record OrganizationView(
        UUID organizationId,
        String legalName,
        String tradeName,
        String status,
        List<IdentifierView> identifiers,
        Instant createdAt) {

    public OrganizationView {
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(legalName, "legalName must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        identifiers = List.copyOf(identifiers == null ? List.of() : identifiers);
    }

    public record IdentifierView(String type, String countryCode, String maskedValue) {
    }
}
