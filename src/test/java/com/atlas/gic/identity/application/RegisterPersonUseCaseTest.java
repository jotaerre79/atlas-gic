package com.atlas.gic.identity.application;

import com.atlas.gic.identity.domain.Person;
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

class RegisterPersonUseCaseTest {

    private static final TenantId TENANT_A = TenantId.of(UUID.fromString("11111111-1111-1111-1111-111111111111"));

    private final RecordingPersonRepository repository = new RecordingPersonRepository();
    private final RecordingPersonRegistrationAudit audit = new RecordingPersonRegistrationAudit();
    private final CurrentActor actor = () -> "application-test";

    @Test
    void registersPersonWithAuthorizedTenant() {
        var useCase = new RegisterPersonUseCase(new FixedTenantContext(TENANT_A), repository, audit, actor);

        var result = useCase.register(validCommand());

        assertThat(result.status().name()).isEqualTo("ACTIVE");
        assertThat(result.displayName()).isEqualTo("Juan Perez");
        assertThat(repository.saved()).singleElement().satisfies(person -> {
            assertThat(person.tenantId()).isEqualTo(TENANT_A);
            assertThat(person.identifier().normalizedValue()).isEqualTo("1234567");
        });
        assertThat(audit.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.actor()).isEqualTo("application-test");
            assertThat(entry.tenantId()).isEqualTo(TENANT_A);
            assertThat(entry.correlationId()).isEqualTo("corr-app");
        });
    }

    @Test
    void tenantMustComeFromAuthorizedContext() {
        var useCase = new RegisterPersonUseCase(new FixedTenantContext(null), repository, audit, actor);

        assertThatThrownBy(() -> useCase.register(validCommand()))
                .isInstanceOf(TenantContextRequiredException.class);
        assertThat(repository.saved()).isEmpty();
        assertThat(audit.entries()).isEmpty();
    }

    @Test
    void duplicateIdentifierConflictIsControlled() {
        repository.failWithDuplicate = true;
        var useCase = new RegisterPersonUseCase(new FixedTenantContext(TENANT_A), repository, audit, actor);

        assertThatThrownBy(() -> useCase.register(validCommand()))
                .isInstanceOf(DuplicatePersonIdentifierException.class);
        assertThat(audit.entries()).isEmpty();
    }

    private RegisterPersonCommand validCommand() {
        return new RegisterPersonCommand(
                "Juan",
                null,
                "Perez",
                new RegisterPersonCommand.IdentifierCommand("CI", "1234567", "PY"),
                "corr-app");
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

    private static class RecordingPersonRepository implements PersonRepository {

        private final List<Person> saved = new ArrayList<>();
        private boolean failWithDuplicate;

        @Override
        public void save(Person person) {
            if (failWithDuplicate) {
                throw new DuplicatePersonIdentifierException();
            }
            saved.add(person);
        }

        List<Person> saved() {
            return List.copyOf(saved);
        }
    }

    private static class RecordingPersonRegistrationAudit implements PersonRegistrationAudit {

        private final List<PersonRegisteredAuditEntry> entries = new ArrayList<>();

        @Override
        public void record(PersonRegisteredAuditEntry entry) {
            entries.add(entry);
        }

        List<PersonRegisteredAuditEntry> entries() {
            return List.copyOf(entries);
        }
    }
}
