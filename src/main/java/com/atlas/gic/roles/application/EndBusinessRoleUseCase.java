package com.atlas.gic.roles.application;

import com.atlas.gic.identity.application.PersonNotFoundException;
import com.atlas.gic.identity.application.TenantContextRequiredException;
import com.atlas.gic.roles.domain.BusinessRoleAssignmentStatus;
import com.atlas.gic.shared.security.application.CurrentActor;
import com.atlas.gic.shared.tenancy.application.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EndBusinessRoleUseCase {

    private final TenantContext tenantContext;
    private final BusinessRoleAssignmentRepository repository;
    private final BusinessRoleEndedAudit audit;
    private final CurrentActor currentActor;

    public EndBusinessRoleUseCase(
            TenantContext tenantContext,
            BusinessRoleAssignmentRepository repository,
            BusinessRoleEndedAudit audit,
            CurrentActor currentActor) {
        this.tenantContext = tenantContext;
        this.repository = repository;
        this.audit = audit;
        this.currentActor = currentActor;
    }

    @Transactional
    public EndBusinessRoleResult end(EndBusinessRoleCommand command) {
        var tenantId = tenantContext.currentTenant().orElseThrow(TenantContextRequiredException::new);
        if (!repository.personExists(tenantId, command.personId())) {
            throw new PersonNotFoundException();
        }

        var assignment = repository.findById(tenantId, command.personId(), command.assignmentId())
                .orElseThrow(PersonNotFoundException::new);
        if (assignment.status() != BusinessRoleAssignmentStatus.ACTIVE) {
            throw new BusinessRoleAlreadyEndedException();
        }

        var ended = assignment.end(command.validTo());
        var actor = currentActor.actor();
        if (!repository.endActive(tenantId, command.personId(), ended, actor, command.reason())) {
            throw new BusinessRoleAlreadyEndedException();
        }

        audit.record(new BusinessRoleEndedAuditEntry(
                actor,
                tenantId,
                ended.personId(),
                ended.assignmentId(),
                ended.role(),
                ended.validTo(),
                command.reason(),
                command.correlationId(),
                null));

        return new EndBusinessRoleResult(
                ended.assignmentId(),
                ended.personId(),
                ended.role(),
                ended.status(),
                ended.validFrom(),
                ended.validTo());
    }
}
