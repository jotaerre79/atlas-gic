package com.atlas.gic.shared.security.adapter.spring;

import com.atlas.gic.shared.security.application.TenantAccessAuthorization;
import com.atlas.gic.shared.tenancy.domain.TenantId;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class SecurityContextTenantAccessAuthorization implements TenantAccessAuthorization {

    private static final String TENANT_AUTHORITY_PREFIX = "TENANT_";

    @Override
    public boolean hasTenantAccess(Authentication authentication, TenantId tenantId) {
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(TENANT_AUTHORITY_PREFIX + tenantId));
    }
}
