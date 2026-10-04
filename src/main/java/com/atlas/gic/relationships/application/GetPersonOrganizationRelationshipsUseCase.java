package com.atlas.gic.relationships.application;

import com.atlas.gic.identity.application.PersonNotFoundException;
import com.atlas.gic.identity.application.TenantContextRequiredException;
import com.atlas.gic.identity.domain.PersonId;
import com.atlas.gic.relationships.domain.PersonOrganizationRelationshipStatus;
import com.atlas.gic.shared.tenancy.application.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetPersonOrganizationRelationshipsUseCase {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private final TenantContext tenantContext;
    private final PersonOrganizationRelationshipRepository repository;

    public GetPersonOrganizationRelationshipsUseCase(
            TenantContext tenantContext,
            PersonOrganizationRelationshipRepository repository) {
        this.tenantContext = tenantContext;
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PersonOrganizationRelationshipListPage get(
            PersonId personId,
            int page,
            int size,
            PersonOrganizationRelationshipStatus status) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be greater than or equal to zero");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }

        var tenantId = tenantContext.currentTenant().orElseThrow(TenantContextRequiredException::new);
        if (!repository.personExists(tenantId, personId)) {
            throw new PersonNotFoundException();
        }
        return repository.findByPerson(tenantId, personId, status, page, size);
    }
}
