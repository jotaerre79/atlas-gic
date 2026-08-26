package com.atlas.gic.roles.application;

import com.atlas.gic.identity.application.PersonNotFoundException;
import com.atlas.gic.identity.application.TenantContextRequiredException;
import com.atlas.gic.identity.domain.PersonId;
import com.atlas.gic.shared.tenancy.application.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetBusinessRolesUseCase {

    private final TenantContext tenantContext;
    private final BusinessRoleAssignmentRepository repository;

    public GetBusinessRolesUseCase(TenantContext tenantContext, BusinessRoleAssignmentRepository repository) {
        this.tenantContext = tenantContext;
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public BusinessRoleAssignmentList get(PersonId personId) {
        var tenantId = tenantContext.currentTenant().orElseThrow(TenantContextRequiredException::new);
        if (!repository.personExists(tenantId, personId)) {
            throw new PersonNotFoundException();
        }
        return new BusinessRoleAssignmentList(repository.findByPerson(tenantId, personId));
    }
}
