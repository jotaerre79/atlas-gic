package com.atlas.gic.identity.application;

import com.atlas.gic.identity.domain.Organization;
import com.atlas.gic.shared.security.application.CurrentActor;
import com.atlas.gic.shared.tenancy.application.TenantContext;
import com.atlas.gic.shared.tenancy.domain.TenantId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegisterOrganizationUseCaseTest {

    private static final TenantId TENANT_A = TenantId.of(UUID.fromString("11111111-1111-1111-1111-111111111111"));

    private final RecordingOrganizationRepository repository = new RecordingOrganizationRepository();
    private final RecordingOrganizationRegistrationAudit audit = new RecordingOrganizationRegistrationAudit();
    private final CurrentActor actor = () -> "organization-test";

    @Test
    void registersOrganizationWithAuthorizedTenant() {
        var useCase = new RegisterOrganizationUseCase(new FixedTenantContext(TENANT_A), repository, audit, actor);

        var result = useCase.register(validCommand());

        assertThat(result.status().name()).isEqualTo("ACTIVE");
        assertThat(result.legalName()).isEqualTo("Atlas Cooperativa");
        assertThat(result.tradeName()).isEqualTo("Atlas");
        assertThat(result.identifier().maskedValue()).isEqualTo("****3456");
        assertThat(repository.saved()).singleElement().satisfies(organization -> {
            assertThat(organization.tenantId()).isEqualTo(TENANT_A);
            assertThat(organization.identifier().normalizedValue()).isEqualTo("800123456");
        });
        assertThat(audit.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.actor()).isEqualTo("organization-test");
            assertThat(entry.tenantId()).isEqualTo(TENANT_A);
            assertThat(entry.correlationId()).isEqualTo("corr-org-app");
        });
    }

    @Test
    void tenantMustComeFromAuthorizedContext() {
        var useCase = new RegisterOrganizationUseCase(new FixedTenantContext(null), repository, audit, actor);

        assertThatThrownBy(() -> useCase.register(validCommand()))
                .isInstanceOf(TenantContextRequiredException.class);
        assertThat(repository.saved()).isEmpty();
        assertThat(audit.entries()).isEmpty();
    }

    @Test
    void duplicateIdentifierConflictIsControlled() {
        repository.failWithDuplicate = true;
        var useCase = new RegisterOrganizationUseCase(new FixedTenantContext(TENANT_A), repository, audit, actor);

        assertThatThrownBy(() -> useCase.register(validCommand()))
                .isInstanceOf(DuplicateOrganizationIdentifierException.class);
        assertThat(audit.entries()).isEmpty();
    }

    @Test
    void rejectsMissingCorrelationId() {
        var useCase = new RegisterOrganizationUseCase(new FixedTenantContext(TENANT_A), repository, audit, actor);

        assertThatThrownBy(() -> useCase.register(new RegisterOrganizationCommand(
                "Atlas Cooperativa",
                null,
                new RegisterOrganizationCommand.IdentifierCommand("RUC", "80012345-6", "PY"),
                " ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("correlationId");
    }

    private RegisterOrganizationCommand validCommand() {
        return new RegisterOrganizationCommand(
                "Atlas Cooperativa",
                "Atlas",
                new RegisterOrganizationCommand.IdentifierCommand("RUC", "80012345-6", "PY"),
                "corr-org-app");
    }

    private record FixedTenantContext(TenantId tenantId) implements TenantContext {

        @Override
        public Optional<TenantId> currentTenant() {
            return Optional.ofNullable(tenantId);
        }

        @Override
        public boolean platformAccess() {
            return false;
        }
    }

    private static class RecordingOrganizationRepository implements OrganizationRepository {

        private final List<Organization> saved = new ArrayList<>();
        private boolean failWithDuplicate;

        @Override
        public void save(Organization organization) {
            if (failWithDuplicate) {
                throw new DuplicateOrganizationIdentifierException();
            }
            saved.add(organization);
        }

        List<Organization> saved() {
            return List.copyOf(saved);
        }
    }

    private static class RecordingOrganizationRegistrationAudit implements OrganizationRegistrationAudit {

        private final List<OrganizationRegisteredAuditEntry> entries = new ArrayList<>();

        @Override
        public void record(OrganizationRegisteredAuditEntry entry) {
            entries.add(entry);
        }

        List<OrganizationRegisteredAuditEntry> entries() {
            return List.copyOf(entries);
        }
    }
}
