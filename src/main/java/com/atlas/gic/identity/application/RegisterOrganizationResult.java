package com.atlas.gic.identity.application;

import com.atlas.gic.identity.domain.IdentityStatus;
import com.atlas.gic.identity.domain.OrganizationId;

import java.time.Instant;

public record RegisterOrganizationResult(
        OrganizationId organizationId,
        String legalName,
        String tradeName,
        IdentityStatus status,
        IdentifierResult identifier,
        Instant createdAt) {

    public record IdentifierResult(String type, String countryCode, String maskedValue) {
    }
}
