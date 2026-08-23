package com.atlas.gic.shared.tenancy.application;

import com.atlas.gic.shared.tenancy.domain.TenantId;

import java.util.Optional;

public interface TenantContext {

    Optional<TenantId> currentTenant();

    boolean platformAccess();
}
