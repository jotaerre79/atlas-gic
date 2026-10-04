package com.atlas.gic.relationships.application;

import com.atlas.gic.identity.application.OrganizationNotFoundException;
import com.atlas.gic.identity.application.PersonNotFoundException;
import com.atlas.gic.identity.application.TenantContextRequiredException;
import com.atlas.gic.identity.domain.OrganizationId;
import com.atlas.gic.identity.domain.PersonId;
import com.atlas.gic.relationships.domain.PersonOrganizationRelationship;
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

class AssignPersonOrganizationRelationshipUseCaseTest {

    private final TenantId tenantId = TenantId.of(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private final PersonId personId = PersonId.of(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
    private final OrganizationId organizationId = OrganizationId.of(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
    private RecordingRepository repository;
    private RecordingAudit audit;
    private AssignPersonOrganizationRelationshipUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = new RecordingRepository();
        audit = new RecordingAudit();
        useCase = new AssignPersonOrganizationRelationshipUseCase(
                new FixedTenantContext(Optional.of(tenantId)),
                repository,
                audit,
                () -> "relationship-test");
    }

    @Test
    void assignsRelationshipAndWritesAudit() {
        repository.personExists = true;
        repository.organizationExists = true;

        var result = useCase.assign(validCommand());

        assertThat(result.status()).isEqualTo(PersonOrganizationRelationshipStatus.ACTIVE);
        assertThat(result.relationshipType()).isEqualTo(PersonOrganizationRelationshipType.REPRESENTATIVE_OF);
        assertThat(repository.saved).singleElement()
                .satisfies(relationship -> {
                    assertThat(relationship.tenantId()).isEqualTo(tenantId);
                    assertThat(relationship.personId()).isEqualTo(personId);
                    assertThat(relationship.organizationId()).isEqualTo(organizationId);
                });
        assertThat(repository.lastActor).isEqualTo("relationship-test");
        assertThat(audit.entries).singleElement()
                .satisfies(entry -> {
                    assertThat(entry.actor()).isEqualTo("relationship-test");
                    assertThat(entry.tenantId()).isEqualTo(tenantId);
                    assertThat(entry.relationshipType()).isEqualTo(PersonOrganizationRelationshipType.REPRESENTATIVE_OF);
                    assertThat(entry.correlationId()).isEqualTo("corr-relationship");
                });
    }

    @Test
    void requiresTenantContext() {
        useCase = new AssignPersonOrganizationRelationshipUseCase(
                new FixedTenantContext(Optional.empty()),
                repository,
                audit,
                () -> "relationship-test");

        assertThatThrownBy(() -> useCase.assign(validCommand()))
                .isInstanceOf(TenantContextRequiredException.class);
    }

    @Test
    void returnsNotFoundWhenPersonOrOrganizationDoesNotExistInTenant() {
        repository.personExists = false;

        assertThatThrownBy(() -> useCase.assign(validCommand()))
                .isInstanceOf(PersonNotFoundException.class);

        repository.personExists = true;
        repository.organizationExists = false;
        assertThatThrownBy(() -> useCase.assign(validCommand()))
                .isInstanceOf(OrganizationNotFoundException.class);
    }

    @Test
    void propagatesDuplicateActiveRelationshipConflict() {
        repository.personExists = true;
        repository.organizationExists = true;
        repository.duplicate = true;

        assertThatThrownBy(() -> useCase.assign(validCommand()))
                .isInstanceOf(DuplicateActivePersonOrganizationRelationshipException.class);
        assertThat(audit.entries).isEmpty();
    }

    @Test
    void rejectsMissingCorrelationIdAndValidFrom() {
        assertThatThrownBy(() -> new AssignPersonOrganizationRelationshipCommand(
                personId,
                organizationId,
                PersonOrganizationRelationshipType.REPRESENTATIVE_OF,
                LocalDate.parse("2026-09-07"),
                " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("correlationId");

        assertThatThrownBy(() -> new AssignPersonOrganizationRelationshipCommand(
                personId,
                organizationId,
                PersonOrganizationRelationshipType.REPRESENTATIVE_OF,
                null,
                "corr"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("validFrom");
    }

    private AssignPersonOrganizationRelationshipCommand validCommand() {
        return new AssignPersonOrganizationRelationshipCommand(
                personId,
                organizationId,
                PersonOrganizationRelationshipType.REPRESENTATIVE_OF,
                LocalDate.parse("2026-09-07"),
                "corr-relationship");
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
        private boolean organizationExists;
        private boolean duplicate;
        private String lastActor;
        private final List<PersonOrganizationRelationship> saved = new ArrayList<>();

        @Override
        public boolean personExists(TenantId tenantId, PersonId personId) {
            return personExists;
        }

        @Override
        public boolean organizationExists(TenantId tenantId, OrganizationId organizationId) {
            return organizationExists;
        }

        @Override
        public void save(PersonOrganizationRelationship relationship, String actor) {
            if (duplicate) {
                throw new DuplicateActivePersonOrganizationRelationshipException();
            }
            lastActor = actor;
            saved.add(relationship);
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

    private static class RecordingAudit implements PersonOrganizationRelationshipCreatedAudit {

        private final List<PersonOrganizationRelationshipCreatedAuditEntry> entries = new ArrayList<>();

        @Override
        public void record(PersonOrganizationRelationshipCreatedAuditEntry entry) {
            entries.add(entry);
        }
    }
}
