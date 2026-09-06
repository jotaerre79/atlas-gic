package com.atlas.gic.identity.application;

import com.atlas.gic.identity.domain.OrganizationId;
import com.atlas.gic.shared.tenancy.application.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationRetrievalUseCase {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private final TenantContext tenantContext;
    private final OrganizationReadRepository organizationReadRepository;

    public OrganizationRetrievalUseCase(
            TenantContext tenantContext,
            OrganizationReadRepository organizationReadRepository) {
        this.tenantContext = tenantContext;
        this.organizationReadRepository = organizationReadRepository;
    }

    @Transactional(readOnly = true)
    public OrganizationView get(OrganizationId organizationId) {
        var tenantId = tenantContext.currentTenant().orElseThrow(TenantContextRequiredException::new);
        return organizationReadRepository.findById(tenantId, organizationId)
                .orElseThrow(OrganizationNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public OrganizationSearchPage search(String query, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be greater than or equal to zero");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        try {
            Math.multiplyExact(page, size);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("page offset is too large", exception);
        }

        var tenantId = tenantContext.currentTenant().orElseThrow(TenantContextRequiredException::new);
        return organizationReadRepository.search(tenantId, normalize(query), page, size);
    }

    private String normalize(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return query.trim();
    }
}
