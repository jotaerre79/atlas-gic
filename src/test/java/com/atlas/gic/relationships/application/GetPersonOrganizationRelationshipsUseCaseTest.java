package com.atlas.gic.relationships.application;

import com.atlas.gic.identity.application.PersonNotFoundException;
import com.atlas.gic.identity.application.TenantContextRequiredException;
import com.atlas.gic.identity.domain.OrganizationId;
import com.atlas.gic.identity.domain.PersonId;
import com.atlas.gic.relationships.domain.PersonOrganizationRelationship;
import com.atlas.gic.relationships.domain.PersonOrganizationRelationshipStatus;
import com.atlas.gic.relationships.domain.PersonOrganizationRelationshipType;
import com.atlas.gic.shared.tenancy.application.TenantContext;
import com.atlas.gic.shared.tenancy.domain.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GetPersonOrganizationRelationshipsUseCaseTest {

    private static final TenantId TENANT_ID = TenantId.of(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private static final PersonId PERSON_ID = PersonId.of(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));

    @Test
    void listsRelationshipsForExistingPerson() {
        var repository = new RecordingRepository();
        repository.personExists = true;
        repository.page = new PersonOrganizationRelationshipListPage(List.of(new PersonOrganizationRelationshipView(
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                PERSON_ID.value(),
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                "Atlas Cooperativa",
                "Atlas",
                PersonOrganizationRelationshipType.REPRESENTATIVE_OF,
                LocalDate.parse("2026-09-07"),
                null,
                PersonOrganizationRelationshipStatus.ACTIVE,
                Instant.parse("2026-09-07T00:00:00Z"))), 0, 20, 1);
        var useCase = new GetPersonOrganizationRelationshipsUseCase(new FixedTenantContext(Optional.of(TENANT_ID)), repository);

        var result = useCase.get(PERSON_ID, 0, 20, PersonOrganizationRelationshipStatus.ACTIVE);

        assertThat(result.total()).isEqualTo(1);
        assertThat(repository.lastTenant).isEqualTo(TENANT_ID);
        assertThat(repository.lastStatus).isEqualTo(PersonOrganizationRelationshipStatus.ACTIVE);
    }

    @Test
    void rejectsInvalidPagination() {
        var useCase = new GetPersonOrganizationRelationshipsUseCase(
                new FixedTenantContext(Optional.of(TENANT_ID)),
                new RecordingRepository());

        assertThatThrownBy(() -> useCase.get(PERSON_ID, -1, 20, PersonOrganizationRelationshipStatus.ACTIVE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.get(PERSON_ID, 0, 0, PersonOrganizationRelationshipStatus.ACTIVE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.get(PERSON_ID, 0, 101, PersonOrganizationRelationshipStatus.ACTIVE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresTenantAndExistingPerson() {
        var repository = new RecordingRepository();
        var emptyTenantUseCase = new GetPersonOrganizationRelationshipsUseCase(
                new FixedTenantContext(Optional.empty()),
                repository);

        assertThatThrownBy(() -> emptyTenantUseCase.get(PERSON_ID, 0, 20, PersonOrganizationRelationshipStatus.ACTIVE))
                .isInstanceOf(TenantContextRequiredException.class);

        var useCase = new GetPersonOrganizationRelationshipsUseCase(new FixedTenantContext(Optional.of(TENANT_ID)), repository);
        assertThatThrownBy(() -> useCase.get(PERSON_ID, 0, 20, PersonOrganizationRelationshipStatus.ACTIVE))
                .isInstanceOf(PersonNotFoundException.class);
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
        private TenantId lastTenant;
        private PersonOrganizationRelationshipStatus lastStatus;
        private PersonOrganizationRelationshipListPage page =
                new PersonOrganizationRelationshipListPage(List.of(), 0, 20, 0);

        @Override
        public boolean personExists(TenantId tenantId, PersonId personId) {
            lastTenant = tenantId;
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
        public PersonOrganizationRelationshipListPage findByPerson(
                TenantId tenantId,
                PersonId personId,
                PersonOrganizationRelationshipStatus status,
                int page,
                int size) {
            lastTenant = tenantId;
            lastStatus = status;
            return this.page;
        }
    }
}
