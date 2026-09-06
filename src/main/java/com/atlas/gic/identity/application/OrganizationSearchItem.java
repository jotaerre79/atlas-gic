package com.atlas.gic.identity.application;

import java.util.Objects;
import java.util.UUID;

public record OrganizationSearchItem(
        UUID organizationId,
        String legalName,
        String tradeName,
        String status,
        String identifierType,
        String identifierCountryCode) {

    public OrganizationSearchItem {
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(legalName, "legalName must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }
}
