package com.atlas.gic.shared.tenancy.adapter.web;

import com.atlas.gic.shared.tenancy.application.TenantContextHolder;
import com.atlas.gic.shared.tenancy.application.TenantContextSnapshot;
import com.atlas.gic.shared.tenancy.domain.TenantId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.atlas.gic.shared.security.application.PlatformAccessAuthorization;
import com.atlas.gic.shared.security.application.TenantAccessAuthorization;

import java.io.IOException;

@Component
@EnableConfigurationProperties(TenantContextFilter.TenancyProperties.class)
public class TenantContextFilter extends OncePerRequestFilter {

    private final TenancyProperties properties;
    private final PlatformAccessAuthorization platformAccessAuthorization;
    private final TenantAccessAuthorization tenantAccessAuthorization;

    public TenantContextFilter(
            TenancyProperties properties,
            PlatformAccessAuthorization platformAccessAuthorization,
            TenantAccessAuthorization tenantAccessAuthorization) {
        this.properties = properties;
        this.platformAccessAuthorization = platformAccessAuthorization;
        this.tenantAccessAuthorization = tenantAccessAuthorization;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        var tenantHeader = request.getHeader(properties.tenantHeader());
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        var platformAccess = platformAccessAuthorization.hasPlatformAccess(authentication);

        try (var ignored = TenantContextHolder.open(snapshot(tenantHeader, platformAccess, authentication))) {
            filterChain.doFilter(request, response);
        }
    }

    private TenantContextSnapshot snapshot(String tenantHeader, boolean platformAccess, Authentication authentication) {
        if (platformAccess) {
            return TenantContextSnapshot.platform();
        }
        if (tenantHeader == null || tenantHeader.isBlank()) {
            return new TenantContextSnapshot(null, false);
        }
        TenantId tenantId;
        try {
            tenantId = TenantId.parse(tenantHeader);
        } catch (IllegalArgumentException ignored) {
            return new TenantContextSnapshot(null, false);
        }
        if (!tenantAccessAuthorization.hasTenantAccess(authentication, tenantId)) {
            return new TenantContextSnapshot(null, false);
        }
        return TenantContextSnapshot.tenant(tenantId);
    }

    @ConfigurationProperties("atlas.gic.tenancy")
    public record TenancyProperties(String tenantHeader) {
    }
}
