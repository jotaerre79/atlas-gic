package com.atlas.gic.relationships.application;

import com.atlas.gic.identity.application.OrganizationNotFoundException;
import com.atlas.gic.identity.application.PersonNotFoundException;
import com.atlas.gic.identity.application.TenantContextRequiredException;
import com.atlas.gic.relationships.domain.PersonOrganizationRelationship;
import com.atlas.gic.shared.security.application.CurrentActor;
import com.atlas.gic.shared.tenancy.application.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssignPersonOrganizationRelationshipUseCase {

    private final TenantContext tenantContext;
    private final PersonOrganizationRelationshipRepository repository;
    private final PersonOrganizationRelationshipCreatedAudit audit;
    private final CurrentActor currentActor;

    public AssignPersonOrganizationRelationshipUseCase(
            TenantContext tenantContext,
            PersonOrganizationRelationshipRepository repository,
            PersonOrganizationRelationshipCreatedAudit audit,
            CurrentActor currentActor) {
        this.tenantContext = tenantContext;
        this.repository = repository;
        this.audit = audit;
        this.currentActor = currentActor;
    }

    @Transactional
    public AssignPersonOrganizationRelationshipResult assign(AssignPersonOrganizationRelationshipCommand command) {
        var tenantId = tenantContext.currentTenant().orElseThrow(TenantContextRequiredException::new);
        if (!repository.personExists(tenantId, command.personId())) {
            throw new PersonNotFoundException();
        }
        if (!repository.organizationExists(tenantId, command.organizationId())) {
            throw new OrganizationNotFoundException();
        }

        var relationship = PersonOrganizationRelationship.active(
                tenantId,
                command.personId(),
                command.organizationId(),
                command.relationshipType(),
                command.validFrom());

        var actor = currentActor.actor();
        repository.save(relationship, actor);
        audit.record(new PersonOrganizationRelationshipCreatedAuditEntry(
                actor,
                tenantId,
                relationship.relationshipId(),
                relationship.personId(),
                relationship.organizationId(),
                relationship.relationshipType(),
                command.correlationId(),
                relationship.createdAt()));

        return new AssignPersonOrganizationRelationshipResult(
                relationship.relationshipId(),
                relationship.personId(),
                relationship.organizationId(),
                relationship.relationshipType(),
                relationship.validFrom(),
                relationship.validTo(),
                relationship.status(),
                relationship.createdAt());
    }
}
