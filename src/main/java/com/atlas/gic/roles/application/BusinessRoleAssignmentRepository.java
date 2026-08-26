package com.atlas.gic.roles.application;

import com.atlas.gic.identity.domain.PersonId;
import com.atlas.gic.roles.domain.BusinessRoleAssignment;
import com.atlas.gic.roles.domain.BusinessRoleAssignmentId;
import com.atlas.gic.shared.tenancy.domain.TenantId;

import java.util.List;
import java.util.Optional;

public interface BusinessRoleAssignmentRepository {

    boolean personExists(TenantId tenantId, PersonId personId);

    void save(BusinessRoleAssignment assignment, String actor);

    List<BusinessRoleAssignmentView> findByPerson(TenantId tenantId, PersonId personId);

    Optional<BusinessRoleAssignment> findById(TenantId tenantId, PersonId personId, BusinessRoleAssignmentId assignmentId);

    boolean endActive(TenantId tenantId, PersonId personId, BusinessRoleAssignment endedAssignment, String actor, String reason);
}
