package com.atlas.gic.shared.tenancy.application;

import com.atlas.gic.shared.tenancy.domain.TenantId;

import java.util.Optional;

public record TenantContextSnapshot(TenantId tenantId, boolean platformAccess) {

    public Optional<TenantId> optionalTenantId() {
        return Optional.ofNullable(tenantId);
    }

    public static TenantContextSnapshot tenant(TenantId tenantId) {
        return new TenantContextSnapshot(tenantId, false);
    }

    public static TenantContextSnapshot platform() {
        return new TenantContextSnapshot(null, true);
    }
}
