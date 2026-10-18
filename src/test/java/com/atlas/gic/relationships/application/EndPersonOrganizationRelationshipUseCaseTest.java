package com.atlas.gic.relationships.application;

import com.atlas.gic.identity.application.PersonNotFoundException;
import com.atlas.gic.identity.application.TenantContextRequiredException;
import com.atlas.gic.identity.domain.OrganizationId;
import com.atlas.gic.identity.domain.PersonId;
import com.atlas.gic.relationships.domain.PersonOrganizationRelationship;
import com.atlas.gic.relationships.domain.PersonOrganizationRelationshipId;
import com.atlas.gic.relationships.domain.PersonOrganizationRelationshipStatus;
import com.atlas.gic.relationships.domain.PersonOrganizationRelationshipType;
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

class EndPersonOrganizationRelationshipUseCaseTest {

    private final TenantId tenantId = TenantId.of(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private final PersonId personId = PersonId.of(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
    private final OrganizationId organizationId = OrganizationId.of(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
    private RecordingRepository repository;
    private RecordingEndedAudit audit;
    private PersonOrganizationRelationship relationship;
    private EndPersonOrganizationRelationshipUseCase useCase;

    @BeforeEach
    void setUp() {
        relationship = PersonOrganizationRelationship.active(
                tenantId,
                personId,
                organizationId,
                PersonOrganizationRelationshipType.REPRESENTATIVE_OF,
                LocalDate.parse("2026-09-07"));
        repository = new RecordingRepository();
        repository.personExists = true;
        repository.relationship = Optional.of(relationship);
        audit = new RecordingEndedAudit();
        useCase = new EndPersonOrganizationRelationshipUseCase(
                new FixedTenantContext(Optional.of(tenantId)),
                repository,
                audit,
                () -> "relationship-test");
    }

    @Test
    void endsActiveRelationshipAndWritesAudit() {
        var result = useCase.end(validCommand());

        assertThat(result.status()).isEqualTo(PersonOrganizationRelationshipStatus.ENDED);
        assertThat(result.validTo()).isEqualTo(LocalDate.parse("2026-10-04"));
        assertThat(repository.ended).isNotNull();
        assertThat(repository.ended.status()).isEqualTo(PersonOrganizationRelationshipStatus.ENDED);
        assertThat(audit.entries).singleElement()
                .satisfies(entry -> {
                    assertThat(entry.actor()).isEqualTo("relationship-test");
                    assertThat(entry.tenantId()).isEqualTo(tenantId);
                    assertThat(entry.relationshipId()).isEqualTo(relationship.relationshipId());
                    assertThat(entry.relationshipType()).isEqualTo(PersonOrganizationRelationshipType.REPRESENTATIVE_OF);
                    assertThat(entry.correlationId()).isEqualTo("corr-end");
                });
    }

    @Test
    void requiresTenantContext() {
        useCase = new EndPersonOrganizationRelationshipUseCase(
                new FixedTenantContext(Optional.empty()),
                repository,
                audit,
                () -> "relationship-test");

        assertThatThrownBy(() -> useCase.end(validCommand()))
                .isInstanceOf(TenantContextRequiredException.class);
    }

    @Test
    void returnsNotFoundWhenPersonOrRelationshipDoesNotExist() {
        repository.personExists = false;
        assertThatThrownBy(() -> useCase.end(validCommand()))
                .isInstanceOf(PersonNotFoundException.class);

        repository.personExists = true;
        repository.relationship = Optional.empty();
        assertThatThrownBy(() -> useCase.end(validCommand()))
                .isInstanceOf(PersonNotFoundException.class);
    }

    @Test
    void rejectsAlreadyEndedRelationship() {
        repository.relationship = Optional.of(relationship.end(LocalDate.parse("2026-10-04")));

        assertThatThrownBy(() -> useCase.end(validCommand()))
                .isInstanceOf(PersonOrganizationRelationshipAlreadyEndedException.class);
        assertThat(audit.entries).isEmpty();
    }

    @Test
    void mapsConcurrentLostUpdateToConflict() {
        repository.endActiveResult = false;

        assertThatThrownBy(() -> useCase.end(validCommand()))
                .isInstanceOf(PersonOrganizationRelationshipAlreadyEndedException.class);
        assertThat(audit.entries).isEmpty();
    }

    @Test
    void rejectsInvalidCommandValues() {
        assertThatThrownBy(() -> useCase.end(new EndPersonOrganizationRelationshipCommand(
                personId,
                relationship.relationshipId(),
                null,
                "corr-end")))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("validTo");

        assertThatThrownBy(() -> useCase.end(new EndPersonOrganizationRelationshipCommand(
                personId,
                relationship.relationshipId(),
                LocalDate.parse("2026-10-04"),
                " ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("correlationId");

        assertThatThrownBy(() -> useCase.end(new EndPersonOrganizationRelationshipCommand(
                personId,
                relationship.relationshipId(),
                LocalDate.parse("2026-09-06"),
                "corr-end")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("validTo");
    }

    @Test
    void propagatesRepositoryAndAuditFailures() {
        repository.failOnEnd = true;
        assertThatThrownBy(() -> useCase.end(validCommand()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("repository failure");

        repository.failOnEnd = false;
        audit.fail = true;
        assertThatThrownBy(() -> useCase.end(validCommand()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("audit failure");
    }

    private EndPersonOrganizationRelationshipCommand validCommand() {
        return new EndPersonOrganizationRelationshipCommand(
                personId,
                relationship.relationshipId(),
                LocalDate.parse("2026-10-04"),
                "corr-end");
    }

    @Test
    void rejectsFutureEndDateUsingFixedUtcClockBeforeWriting() {
        useCase = new EndPersonOrganizationRelationshipUseCase(
                new FixedTenantContext(Optional.of(tenantId)), repository, audit, () -> "relationship-test",
                java.time.Clock.fixed(java.time.Instant.parse("2026-10-04T00:00:00Z"), java.time.ZoneOffset.UTC));
        assertThatThrownBy(() -> useCase.end(new EndPersonOrganizationRelationshipCommand(
                personId, relationship.relationshipId(), LocalDate.parse("2026-10-05"), "future-end")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("future");
        assertThat(repository.ended).isNull();
        assertThat(audit.entries).isEmpty();
        assertThat(useCase.end(validCommand()).status()).isEqualTo(PersonOrganizationRelationshipStatus.ENDED);
    }

    private record FixedTenantContext(Optional<TenantId> tenantId) implements TenantContext {

        @Override
        public Optional<TenantId> currentTenant() {
            return tenantId;
        }

        @Override
        public boolean platformAccess() {
            return false;
        }
    }

    private static class RecordingRepository implements PersonOrganizationRelationshipRepository {

        private boolean personExists;
        private boolean endActiveResult = true;
        private boolean failOnEnd;
        private Optional<PersonOrganizationRelationship> relationship = Optional.empty();
        private PersonOrganizationRelationship ended;

        @Override
        public boolean personExists(TenantId tenantId, PersonId personId) {
            return personExists;
        }

        @Override
        public boolean organizationExists(TenantId tenantId, OrganizationId organizationId) {
            return false;
        }

        @Override
        public void save(PersonOrganizationRelationship relationship, String actor) {
        }

        @Override
        public Optional<PersonOrganizationRelationship> findById(
                TenantId tenantId,
                PersonId personId,
                PersonOrganizationRelationshipId relationshipId) {
            return relationship.filter(value -> value.relationshipId().equals(relationshipId));
        }

        @Override
        public boolean endActive(TenantId tenantId, PersonId personId, PersonOrganizationRelationship endedRelationship) {
            if (failOnEnd) {
                throw new IllegalStateException("repository failure");
            }
            ended = endedRelationship;
            return endActiveResult;
        }

        @Override
        public PersonOrganizationRelationshipListPage findByPerson(
                TenantId tenantId,
                PersonId personId,
                PersonOrganizationRelationshipStatus status,
                int page,
                int size) {
            return new PersonOrganizationRelationshipListPage(List.of(), page, size, 0);
        }
    }

    private static class RecordingEndedAudit implements PersonOrganizationRelationshipEndedAudit {

        private boolean fail;
        private final List<PersonOrganizationRelationshipEndedAuditEntry> entries = new ArrayList<>();

        @Override
        public void record(PersonOrganizationRelationshipEndedAuditEntry entry) {
            if (fail) {
                throw new IllegalStateException("audit failure");
            }
            entries.add(entry);
        }
    }
}
