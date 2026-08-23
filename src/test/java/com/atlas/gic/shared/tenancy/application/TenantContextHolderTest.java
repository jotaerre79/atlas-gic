package com.atlas.gic.shared.tenancy.application;

import com.atlas.gic.shared.tenancy.domain.TenantId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TenantContextHolderTest {

    private final TenantContextHolder contextHolder = new TenantContextHolder();

    @Test
    void exposesTenantInsideScopeAndClearsItAfterwards() {
        var tenantId = TenantId.of(UUID.randomUUID());

        try (var ignored = TenantContextHolder.open(TenantContextSnapshot.tenant(tenantId))) {
            assertThat(contextHolder.currentTenant()).contains(tenantId);
            assertThat(contextHolder.platformAccess()).isFalse();
        }

        assertThat(contextHolder.currentTenant()).isEmpty();
        assertThat(contextHolder.platformAccess()).isFalse();
    }

    @Test
    void platformAccessIsExplicit() {
        try (var ignored = TenantContextHolder.open(TenantContextSnapshot.platform())) {
            assertThat(contextHolder.currentTenant()).isEmpty();
            assertThat(contextHolder.platformAccess()).isTrue();
        }
    }
}
