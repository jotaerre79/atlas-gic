package com.atlas.gic.roles.application;

import com.atlas.gic.identity.domain.PersonId;
import com.atlas.gic.roles.domain.BusinessRoleAssignment;
import com.atlas.gic.shared.tenancy.domain.TenantId;

import java.util.List;

public interface BusinessRoleAssignmentRepository {

    boolean personExists(TenantId tenantId, PersonId personId);

    void save(BusinessRoleAssignment assignment, String actor);

    List<BusinessRoleAssignmentView> findByPerson(TenantId tenantId, PersonId personId);
}
