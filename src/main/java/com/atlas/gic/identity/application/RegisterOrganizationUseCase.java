package com.atlas.gic.identity.application;

import com.atlas.gic.identity.domain.Organization;
import com.atlas.gic.identity.domain.OrganizationIdentifier;
import com.atlas.gic.identity.domain.OrganizationName;
import com.atlas.gic.shared.security.application.CurrentActor;
import com.atlas.gic.shared.tenancy.application.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterOrganizationUseCase {

    private final TenantContext tenantContext;
    private final OrganizationRepository organizationRepository;
    private final OrganizationRegistrationAudit audit;
    private final CurrentActor currentActor;

    public RegisterOrganizationUseCase(
            TenantContext tenantContext,
            OrganizationRepository organizationRepository,
            OrganizationRegistrationAudit audit,
            CurrentActor currentActor) {
        this.tenantContext = tenantContext;
        this.organizationRepository = organizationRepository;
        this.audit = audit;
        this.currentActor = currentActor;
    }

    @Transactional
    public RegisterOrganizationResult register(RegisterOrganizationCommand command) {
        var tenantId = tenantContext.currentTenant().orElseThrow(TenantContextRequiredException::new);
        if (command.identifier() == null) {
            throw new IllegalArgumentException("identifier is required");
        }

        var organization = Organization.register(
                tenantId,
                new OrganizationName(command.legalName(), command.tradeName()),
                OrganizationIdentifier.of(
                        command.identifier().type(),
                        command.identifier().value(),
                        command.identifier().countryCode()));

        organizationRepository.save(organization);
        audit.record(new OrganizationRegisteredAuditEntry(
                currentActor.actor(),
                tenantId,
                organization.organizationId(),
                command.correlationId(),
                organization.createdAt()));

        return new RegisterOrganizationResult(
                organization.organizationId(),
                organization.name().legalName(),
                organization.name().tradeName(),
                organization.status(),
                new RegisterOrganizationResult.IdentifierResult(
                        organization.identifier().type(),
                        organization.identifier().countryCode(),
                        organization.identifier().maskedValue()),
                organization.createdAt());
    }
}
