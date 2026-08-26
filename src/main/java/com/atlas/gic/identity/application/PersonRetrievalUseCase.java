package com.atlas.gic.identity.application;

import com.atlas.gic.identity.domain.PersonId;
import com.atlas.gic.shared.tenancy.application.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonRetrievalUseCase {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private final TenantContext tenantContext;
    private final PersonReadRepository personReadRepository;

    public PersonRetrievalUseCase(TenantContext tenantContext, PersonReadRepository personReadRepository) {
        this.tenantContext = tenantContext;
        this.personReadRepository = personReadRepository;
    }

    @Transactional(readOnly = true)
    public PersonView get(PersonId personId) {
        var tenantId = tenantContext.currentTenant().orElseThrow(TenantContextRequiredException::new);
        return personReadRepository.findById(tenantId, personId).orElseThrow(PersonNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public PersonSearchPage search(String query, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be greater than or equal to zero");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
        }

        var tenantId = tenantContext.currentTenant().orElseThrow(TenantContextRequiredException::new);
        return personReadRepository.search(tenantId, normalize(query), page, size);
    }

    private String normalize(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return query.trim();
    }
}
