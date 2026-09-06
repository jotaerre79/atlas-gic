package com.atlas.gic.identity.domain;

import com.atlas.gic.shared.tenancy.domain.TenantId;

import java.time.Instant;
import java.util.Objects;

public record Organization(
        OrganizationId organizationId,
        TenantId tenantId,
        OrganizationName name,
        IdentityStatus status,
        OrganizationIdentifier identifier,
        Instant createdAt) {

    public Organization {
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(identifier, "identifier must not be null");
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public static Organization register(
            TenantId tenantId,
            OrganizationName name,
            OrganizationIdentifier identifier) {
        return new Organization(OrganizationId.newId(), tenantId, name, IdentityStatus.ACTIVE, identifier, null);
    }
}
