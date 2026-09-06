package com.atlas.gic.identity.application;

import com.atlas.gic.identity.domain.OrganizationId;
import com.atlas.gic.shared.tenancy.application.TenantContext;
import com.atlas.gic.shared.tenancy.domain.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrganizationRetrievalUseCaseTest {

    private static final TenantId TENANT_ID = TenantId.of(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private static final OrganizationId ORGANIZATION_ID = OrganizationId.of(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));

    @Test
    void getsOrganizationFromCurrentTenant() {
        var repository = new RecordingOrganizationReadRepository();
        repository.organization = Optional.of(organizationView(ORGANIZATION_ID));
        var useCase = new OrganizationRetrievalUseCase(new FixedTenantContext(Optional.of(TENANT_ID)), repository);

        var result = useCase.get(ORGANIZATION_ID);

        assertThat(result.organizationId()).isEqualTo(ORGANIZATION_ID.value());
        assertThat(repository.lastTenant).isEqualTo(TENANT_ID);
    }

    @Test
    void returnsNotFoundWhenRepositoryDoesNotExposeOrganization() {
        var useCase = new OrganizationRetrievalUseCase(
                new FixedTenantContext(Optional.of(TENANT_ID)),
                new RecordingOrganizationReadRepository());

        assertThatThrownBy(() -> useCase.get(ORGANIZATION_ID))
                .isInstanceOf(OrganizationNotFoundException.class);
    }

    @Test
    void searchRequiresAuthorizedTenantContext() {
        var useCase = new OrganizationRetrievalUseCase(
                new FixedTenantContext(Optional.empty()),
                new RecordingOrganizationReadRepository());

        assertThatThrownBy(() -> useCase.search(null, 0, 20))
                .isInstanceOf(TenantContextRequiredException.class);
    }

    @Test
    void rejectsInvalidPagination() {
        var useCase = new OrganizationRetrievalUseCase(
                new FixedTenantContext(Optional.of(TENANT_ID)),
                new RecordingOrganizationReadRepository());

        assertThatThrownBy(() -> useCase.search(null, -1, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.search(null, 0, 101))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.search(null, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.search(null, Integer.MAX_VALUE, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void searchNormalizesBlankQueryToNullAndKeepsOutOfRangePageEmpty() {
        var repository = new RecordingOrganizationReadRepository();
        var useCase = new OrganizationRetrievalUseCase(new FixedTenantContext(Optional.of(TENANT_ID)), repository);

        var result = useCase.search("   ", 3, 20);

        assertThat(repository.lastQuery).isNull();
        assertThat(repository.lastPage).isEqualTo(3);
        assertThat(repository.lastSize).isEqualTo(20);
        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isZero();
    }

    private static OrganizationView organizationView(OrganizationId organizationId) {
        return new OrganizationView(
                organizationId.value(),
                "Atlas Cooperativa",
                "Atlas",
                "ACTIVE",
                List.of(new OrganizationView.IdentifierView("RUC", "PY", "****3456")),
                Instant.parse("2026-09-06T00:00:00Z"));
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

    private static class RecordingOrganizationReadRepository implements OrganizationReadRepository {

        private Optional<OrganizationView> organization = Optional.empty();
        private TenantId lastTenant;
        private String lastQuery = "unset";
        private int lastPage = -1;
        private int lastSize = -1;

        @Override
        public Optional<OrganizationView> findById(TenantId tenantId, OrganizationId organizationId) {
            lastTenant = tenantId;
            return organization;
        }

        @Override
        public OrganizationSearchPage search(TenantId tenantId, String query, int page, int size) {
            lastTenant = tenantId;
            lastQuery = query;
            lastPage = page;
            lastSize = size;
            return new OrganizationSearchPage(List.of(), page, size, 0);
        }
    }
}
