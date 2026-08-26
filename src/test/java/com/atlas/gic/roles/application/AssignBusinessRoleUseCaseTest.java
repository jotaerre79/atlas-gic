package com.atlas.gic.roles.application;

import com.atlas.gic.identity.application.PersonNotFoundException;
import com.atlas.gic.identity.application.TenantContextRequiredException;
import com.atlas.gic.identity.domain.PersonId;
import com.atlas.gic.roles.domain.BusinessRoleAssignment;
import com.atlas.gic.roles.domain.BusinessRoleAssignmentId;
import com.atlas.gic.roles.domain.BusinessRoleType;
import com.atlas.gic.shared.tenancy.application.TenantContext;
import com.atlas.gic.shared.tenancy.domain.TenantId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssignBusinessRoleUseCaseTest {

    private final TenantId tenantId = TenantId.of(UUID.randomUUID());
    private final PersonId personId = PersonId.of(UUID.randomUUID());
    private RecordingRepository repository;
    private RecordingAudit audit;
    private AssignBusinessRoleUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = new RecordingRepository();
        audit = new RecordingAudit();
        useCase = new AssignBusinessRoleUseCase(
                new FixedTenantContext(tenantId),
                repository,
                audit,
                () -> "role-test");
    }

    @Test
    void assignsBusinessRoleAndWritesAudit() {
        repository.personExists = true;

        var result = useCase.assign(command(BusinessRoleType.SOCIO));

        assertThat(result.role()).isEqualTo(BusinessRoleType.SOCIO);
        assertThat(repository.saved).hasSize(1);
        assertThat(repository.saved.get(0).personId()).isEqualTo(personId);
        assertThat(repository.lastActor).isEqualTo("role-test");
        assertThat(audit.entries).singleElement()
                .satisfies(entry -> {
                    assertThat(entry.actor()).isEqualTo("role-test");
                    assertThat(entry.action()).isEqualTo(BusinessRoleAssignedAuditEntry.ACTION);
                    assertThat(entry.role()).isEqualTo(BusinessRoleType.SOCIO);
                });
    }

    @Test
    void returnsNotFoundWhenPersonDoesNotExistInTenant() {
        repository.personExists = false;

        assertThatThrownBy(() -> useCase.assign(command(BusinessRoleType.CLIENTE)))
                .isInstanceOf(PersonNotFoundException.class);
    }

    @Test
    void propagatesDuplicateActiveRoleConflict() {
        repository.personExists = true;
        repository.duplicate = true;

        assertThatThrownBy(() -> useCase.assign(command(BusinessRoleType.SOCIO)))
                .isInstanceOf(DuplicateActiveBusinessRoleException.class);
    }

    @Test
    void requiresTenantContext() {
        useCase = new AssignBusinessRoleUseCase(
                new EmptyTenantContext(),
                repository,
                audit,
                () -> "role-test");

        assertThatThrownBy(() -> useCase.assign(command(BusinessRoleType.PROVEEDOR)))
                .isInstanceOf(TenantContextRequiredException.class);
    }

    private AssignBusinessRoleCommand command(BusinessRoleType role) {
        return new AssignBusinessRoleCommand(
                personId,
                role,
                LocalDate.parse("2026-08-26"),
                null,
                "corr-role");
    }

    private record FixedTenantContext(TenantId tenantId) implements TenantContext {

        @Override
        public Optional<TenantId> currentTenant() {
            return Optional.of(tenantId);
        }

        @Override
        public boolean platformAccess() {
            return false;
        }
    }

    private static class EmptyTenantContext implements TenantContext {

        @Override
        public Optional<TenantId> currentTenant() {
            return Optional.empty();
        }

        @Override
        public boolean platformAccess() {
            return false;
        }
    }

    private static class RecordingRepository implements BusinessRoleAssignmentRepository {

        private boolean personExists;
        private boolean duplicate;
        private String lastActor;
        private final List<BusinessRoleAssignment> saved = new ArrayList<>();

        @Override
        public boolean personExists(TenantId tenantId, PersonId personId) {
            return personExists;
        }

        @Override
        public void save(BusinessRoleAssignment assignment, String actor) {
            if (duplicate) {
                throw new DuplicateActiveBusinessRoleException();
            }
            lastActor = actor;
            saved.add(assignment);
        }

        @Override
        public List<BusinessRoleAssignmentView> findByPerson(TenantId tenantId, PersonId personId) {
            return List.of();
        }

        @Override
        public Optional<BusinessRoleAssignment> findById(
                TenantId tenantId,
                PersonId personId,
                BusinessRoleAssignmentId assignmentId) {
            return Optional.empty();
        }

        @Override
        public boolean endActive(
                TenantId tenantId,
                PersonId personId,
                BusinessRoleAssignment endedAssignment,
                String actor,
                String reason) {
            return false;
        }
    }

    private static class RecordingAudit implements BusinessRoleAssignedAudit {

        private final List<BusinessRoleAssignedAuditEntry> entries = new ArrayList<>();

        @Override
        public void record(BusinessRoleAssignedAuditEntry entry) {
            entries.add(entry);
        }
    }
}
