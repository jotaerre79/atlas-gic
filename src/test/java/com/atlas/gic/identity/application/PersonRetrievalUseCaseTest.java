package com.atlas.gic.identity.application;

import com.atlas.gic.identity.domain.PersonId;
import com.atlas.gic.shared.tenancy.application.TenantContext;
import com.atlas.gic.shared.tenancy.domain.TenantId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersonRetrievalUseCaseTest {

    private static final TenantId TENANT_ID = TenantId.of(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private static final PersonId PERSON_ID = PersonId.of(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));

    @Test
    void getsPersonFromCurrentTenant() {
        var repository = new RecordingPersonReadRepository();
        repository.person = Optional.of(personView(PERSON_ID));
        var useCase = new PersonRetrievalUseCase(new FixedTenantContext(Optional.of(TENANT_ID)), repository);

        var result = useCase.get(PERSON_ID);

        assertThat(result.personId()).isEqualTo(PERSON_ID.value());
        assertThat(repository.lastTenant).isEqualTo(TENANT_ID);
    }

    @Test
    void returnsNotFoundWhenRepositoryDoesNotExposePerson() {
        var repository = new RecordingPersonReadRepository();
        var useCase = new PersonRetrievalUseCase(new FixedTenantContext(Optional.of(TENANT_ID)), repository);

        assertThatThrownBy(() -> useCase.get(PERSON_ID))
                .isInstanceOf(PersonNotFoundException.class);
    }

    @Test
    void searchRequiresAuthorizedTenantContext() {
        var useCase = new PersonRetrievalUseCase(
                new FixedTenantContext(Optional.empty()),
                new RecordingPersonReadRepository());

        assertThatThrownBy(() -> useCase.search(null, 0, 20))
                .isInstanceOf(TenantContextRequiredException.class);
    }

    @Test
    void rejectsInvalidPagination() {
        var useCase = new PersonRetrievalUseCase(
                new FixedTenantContext(Optional.of(TENANT_ID)),
                new RecordingPersonReadRepository());

        assertThatThrownBy(() -> useCase.search(null, -1, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.search(null, 0, 101))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.search(null, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void searchNormalizesBlankQueryToNull() {
        var repository = new RecordingPersonReadRepository();
        var useCase = new PersonRetrievalUseCase(new FixedTenantContext(Optional.of(TENANT_ID)), repository);

        useCase.search("   ", 0, 20);

        assertThat(repository.lastQuery).isNull();
        assertThat(repository.lastPage).isZero();
        assertThat(repository.lastSize).isEqualTo(20);
    }

    private static PersonView personView(PersonId personId) {
        return new PersonView(
                personId.value(),
                "ACTIVE",
                "Juan",
                null,
                "Perez",
                "Juan Perez",
                List.of(new PersonView.IdentifierView("CI", "PY", "***4567")));
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

    private static class RecordingPersonReadRepository implements PersonReadRepository {

        private Optional<PersonView> person = Optional.empty();
        private TenantId lastTenant;
        private String lastQuery = "unset";
        private int lastPage = -1;
        private int lastSize = -1;

        @Override
        public Optional<PersonView> findById(TenantId tenantId, PersonId personId) {
            lastTenant = tenantId;
            return person;
        }

        @Override
        public PersonSearchPage search(TenantId tenantId, String query, int page, int size) {
            lastTenant = tenantId;
            lastQuery = query;
            lastPage = page;
            lastSize = size;
            return new PersonSearchPage(List.of(), page, size, 0);
        }
    }
}
