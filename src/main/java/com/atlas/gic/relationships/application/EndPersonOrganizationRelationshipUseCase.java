package com.atlas.gic.relationships.application;

import com.atlas.gic.identity.application.PersonNotFoundException;
import com.atlas.gic.identity.application.TenantContextRequiredException;
import com.atlas.gic.relationships.domain.PersonOrganizationRelationshipStatus;
import com.atlas.gic.shared.security.application.CurrentActor;
import com.atlas.gic.shared.tenancy.application.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.Clock;
import java.time.LocalDate;

@Service
public class EndPersonOrganizationRelationshipUseCase {

    private final TenantContext tenantContext;
    private final PersonOrganizationRelationshipRepository repository;
    private final PersonOrganizationRelationshipEndedAudit audit;
    private final CurrentActor currentActor;
    private final Clock clock;

    @Autowired
    public EndPersonOrganizationRelationshipUseCase(
            TenantContext tenantContext,
            PersonOrganizationRelationshipRepository repository,
            PersonOrganizationRelationshipEndedAudit audit,
            CurrentActor currentActor) {
        this(tenantContext, repository, audit, currentActor, Clock.systemUTC());
    }

    public EndPersonOrganizationRelationshipUseCase(
            TenantContext tenantContext,
            PersonOrganizationRelationshipRepository repository,
            PersonOrganizationRelationshipEndedAudit audit,
            CurrentActor currentActor,
            Clock clock) {
        this.tenantContext = tenantContext;
        this.repository = repository;
        this.audit = audit;
        this.currentActor = currentActor;
        this.clock = clock;
    }

    @Transactional
    public EndPersonOrganizationRelationshipResult end(EndPersonOrganizationRelationshipCommand command) {
        var tenantId = tenantContext.currentTenant().orElseThrow(TenantContextRequiredException::new);
        if (command.validTo() != null && command.validTo().isAfter(LocalDate.now(clock))) {
            throw new IllegalArgumentException("validTo must not be in the future (UTC)");
        }
        if (!repository.personExists(tenantId, command.personId())) {
            throw new PersonNotFoundException();
        }

        var relationship = repository.findById(tenantId, command.personId(), command.relationshipId())
                .orElseThrow(PersonNotFoundException::new);
        if (relationship.status() != PersonOrganizationRelationshipStatus.ACTIVE) {
            throw new PersonOrganizationRelationshipAlreadyEndedException();
        }

        var ended = relationship.end(command.validTo());
        if (!repository.endActive(tenantId, command.personId(), ended)) {
            throw new PersonOrganizationRelationshipAlreadyEndedException();
        }

        var actor = currentActor.actor();
        audit.record(new PersonOrganizationRelationshipEndedAuditEntry(
                actor,
                tenantId,
                ended.relationshipId(),
                ended.personId(),
                ended.organizationId(),
                ended.relationshipType(),
                command.correlationId(),
                null));

        return new EndPersonOrganizationRelationshipResult(
                ended.relationshipId(),
                ended.personId(),
                ended.organizationId(),
                ended.relationshipType(),
                ended.validFrom(),
                ended.validTo(),
                ended.status(),
                ended.createdAt());
    }
}
