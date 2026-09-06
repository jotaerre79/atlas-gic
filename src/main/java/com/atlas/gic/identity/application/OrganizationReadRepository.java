package com.atlas.gic.identity.application;

import com.atlas.gic.identity.domain.OrganizationId;
import com.atlas.gic.shared.tenancy.domain.TenantId;

import java.util.Optional;

public interface OrganizationReadRepository {

    Optional<OrganizationView> findById(TenantId tenantId, OrganizationId organizationId);

    OrganizationSearchPage search(TenantId tenantId, String query, int page, int size);
}
