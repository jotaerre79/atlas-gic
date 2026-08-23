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
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@EnableConfigurationProperties(TenantContextFilter.TenancyProperties.class)
public class TenantContextFilter extends OncePerRequestFilter {

    private final TenancyProperties properties;

    public TenantContextFilter(TenancyProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        var tenantHeader = request.getHeader(properties.tenantHeader());
        var platformHeader = request.getHeader(properties.platformAccessHeader());
        var platformAccess = "true".equalsIgnoreCase(platformHeader);

        try (var ignored = TenantContextHolder.open(snapshot(tenantHeader, platformAccess))) {
            filterChain.doFilter(request, response);
        }
    }

    private TenantContextSnapshot snapshot(String tenantHeader, boolean platformAccess) {
        if (platformAccess) {
            return TenantContextSnapshot.platform();
        }
        if (tenantHeader == null || tenantHeader.isBlank()) {
            return new TenantContextSnapshot(null, false);
        }
        return TenantContextSnapshot.tenant(TenantId.parse(tenantHeader));
    }

    @ConfigurationProperties("atlas.gic.tenancy")
    public record TenancyProperties(String tenantHeader, String platformAccessHeader) {
    }
}
