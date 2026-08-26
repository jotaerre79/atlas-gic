package com.atlas.gic.roles.application;

import com.atlas.gic.identity.application.PersonNotFoundException;
import com.atlas.gic.roles.domain.BusinessRoleAssignment;
import com.atlas.gic.shared.security.application.CurrentActor;
import com.atlas.gic.shared.tenancy.application.TenantContext;
import com.atlas.gic.identity.application.TenantContextRequiredException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssignBusinessRoleUseCase {

    private final TenantContext tenantContext;
    private final BusinessRoleAssignmentRepository repository;
    private final BusinessRoleAssignedAudit audit;
    private final CurrentActor currentActor;

    public AssignBusinessRoleUseCase(
            TenantContext tenantContext,
            BusinessRoleAssignmentRepository repository,
            BusinessRoleAssignedAudit audit,
            CurrentActor currentActor) {
        this.tenantContext = tenantContext;
        this.repository = repository;
        this.audit = audit;
        this.currentActor = currentActor;
    }

    @Transactional
    public AssignBusinessRoleResult assign(AssignBusinessRoleCommand command) {
        var tenantId = tenantContext.currentTenant().orElseThrow(TenantContextRequiredException::new);
        if (!repository.personExists(tenantId, command.personId())) {
            throw new PersonNotFoundException();
        }

        var assignment = BusinessRoleAssignment.active(
                tenantId,
                command.personId(),
                command.role(),
                command.validFrom(),
                command.validTo());

        var actor = currentActor.actor();
        repository.save(assignment, actor);
        audit.record(new BusinessRoleAssignedAuditEntry(
                actor,
                tenantId,
                assignment.personId(),
                assignment.assignmentId(),
                assignment.role(),
                command.correlationId(),
                null));

        return new AssignBusinessRoleResult(
                assignment.assignmentId(),
                assignment.personId(),
                assignment.role(),
                assignment.status(),
                assignment.validFrom(),
                assignment.validTo());
    }
}
