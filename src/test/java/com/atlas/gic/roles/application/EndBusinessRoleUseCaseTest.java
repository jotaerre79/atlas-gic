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

class EndBusinessRoleUseCaseTest {

    private final TenantId tenantId = TenantId.of(UUID.randomUUID());
    private final PersonId personId = PersonId.of(UUID.randomUUID());
    private final BusinessRoleAssignment assignment = BusinessRoleAssignment.active(
            tenantId,
            personId,
            BusinessRoleType.SOCIO,
            LocalDate.parse("2026-01-10"),
            null);
    private RecordingRepository repository;
    private RecordingAudit audit;
    private EndBusinessRoleUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = new RecordingRepository();
        repository.personExists = true;
        repository.assignment = Optional.of(assignment);
        audit = new RecordingAudit();
        useCase = new EndBusinessRoleUseCase(
                new FixedTenantContext(tenantId),
                repository,
                audit,
                () -> "lifecycle-test");
    }

    @Test
    void endsActiveAssignmentAndWritesAudit() {
        var result = useCase.end(command(assignment.assignmentId(), LocalDate.parse("2026-08-26")));

        assertThat(result.status().name()).isEqualTo("ENDED");
        assertThat(repository.endedAssignment.validTo()).isEqualTo(LocalDate.parse("2026-08-26"));
        assertThat(repository.lastActor).isEqualTo("lifecycle-test");
        assertThat(repository.lastReason).isEqualTo("voluntary end");
        assertThat(audit.entries).singleElement()
                .satisfies(entry -> {
                    assertThat(entry.action()).isEqualTo(BusinessRoleEndedAuditEntry.ACTION);
                    assertThat(entry.actor()).isEqualTo("lifecycle-test");
                    assertThat(entry.assignmentId()).isEqualTo(assignment.assignmentId());
                    assertThat(entry.validTo()).isEqualTo(LocalDate.parse("2026-08-26"));
                });
    }

    @Test
    void returnsNotFoundWhenPersonDoesNotExistInTenant() {
        repository.personExists = false;

        assertThatThrownBy(() -> useCase.end(command(assignment.assignmentId(), LocalDate.parse("2026-08-26"))))
                .isInstanceOf(PersonNotFoundException.class);
    }

    @Test
    void returnsNotFoundWhenAssignmentDoesNotBelongToPersonAndTenant() {
        repository.assignment = Optional.empty();

        assertThatThrownBy(() -> useCase.end(command(assignment.assignmentId(), LocalDate.parse("2026-08-26"))))
                .isInstanceOf(PersonNotFoundException.class);
    }

    @Test
    void rejectsAlreadyEndedAssignment() {
        repository.assignment = Optional.of(assignment.end(LocalDate.parse("2026-08-26")));

        assertThatThrownBy(() -> useCase.end(command(assignment.assignmentId(), LocalDate.parse("2026-08-27"))))
                .isInstanceOf(BusinessRoleAlreadyEndedException.class);
    }

    @Test
    void mapsConcurrentUpdateLossToConflict() {
        repository.concurrentUpdateLost = true;

        assertThatThrownBy(() -> useCase.end(command(assignment.assignmentId(), LocalDate.parse("2026-08-26"))))
                .isInstanceOf(BusinessRoleAlreadyEndedException.class);
    }

    @Test
    void requiresTenantContext() {
        useCase = new EndBusinessRoleUseCase(new EmptyTenantContext(), repository, audit, () -> "lifecycle-test");

        assertThatThrownBy(() -> useCase.end(command(assignment.assignmentId(), LocalDate.parse("2026-08-26"))))
                .isInstanceOf(TenantContextRequiredException.class);
    }

    private EndBusinessRoleCommand command(BusinessRoleAssignmentId assignmentId, LocalDate validTo) {
        return new EndBusinessRoleCommand(
                personId,
                assignmentId,
                validTo,
                "voluntary end",
                "corr-end");
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
        private Optional<BusinessRoleAssignment> assignment = Optional.empty();
        private boolean concurrentUpdateLost;
        private BusinessRoleAssignment endedAssignment;
        private String lastActor;
        private String lastReason;

        @Override
        public boolean personExists(TenantId tenantId, PersonId personId) {
            return personExists;
        }

        @Override
        public void save(BusinessRoleAssignment assignment, String actor) {
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
            return assignment;
        }

        @Override
        public boolean endActive(
                TenantId tenantId,
                PersonId personId,
                BusinessRoleAssignment endedAssignment,
                String actor,
                String reason) {
            if (concurrentUpdateLost) {
                return false;
            }
            this.endedAssignment = endedAssignment;
            this.lastActor = actor;
            this.lastReason = reason;
            return true;
        }
    }

    private static class RecordingAudit implements BusinessRoleEndedAudit {

        private final List<BusinessRoleEndedAuditEntry> entries = new ArrayList<>();

        @Override
        public void record(BusinessRoleEndedAuditEntry entry) {
            entries.add(entry);
        }
    }
}
