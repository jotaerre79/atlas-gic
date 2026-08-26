package com.atlas.gic.identity.domain;

import com.atlas.gic.shared.tenancy.domain.TenantId;

import java.util.Objects;

public record Person(
        PersonId personId,
        TenantId tenantId,
        PersonName name,
        IdentityStatus status,
        PersonIdentifier identifier) {

    public Person {
        Objects.requireNonNull(personId, "personId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(identifier, "identifier must not be null");
    }

    public static Person register(TenantId tenantId, PersonName name, PersonIdentifier identifier) {
        return new Person(PersonId.newId(), tenantId, name, IdentityStatus.ACTIVE, identifier);
    }

    public String displayName() {
        return name.displayName();
    }
}
