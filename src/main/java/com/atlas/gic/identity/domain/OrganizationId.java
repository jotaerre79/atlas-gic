package com.atlas.gic.identity.domain;

import java.util.Objects;
import java.util.UUID;

public record OrganizationId(UUID value) {

    public OrganizationId {
        Objects.requireNonNull(value, "organizationId must not be null");
    }

    public static OrganizationId newId() {
        return new OrganizationId(UUID.randomUUID());
    }

    public static OrganizationId of(UUID value) {
        return new OrganizationId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
