package com.atlas.gic.shared.security.application;

import com.atlas.gic.shared.tenancy.domain.TenantId;
import org.springframework.security.core.Authentication;

public interface TenantAccessAuthorization {

    boolean hasTenantAccess(Authentication authentication, TenantId tenantId);
}
