package com.atlas.gic.identity.domain;

import com.atlas.gic.shared.tenancy.domain.TenantId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrganizationTest {

    @Test
    void createsValidOrganization() {
        var tenantId = TenantId.of(UUID.randomUUID());
        var organization = Organization.register(
                tenantId,
                new OrganizationName("  Atlas Cooperativa  ", "  Atlas  "),
                OrganizationIdentifier.of("ruc", "80012345-6", "py"));

        assertThat(organization.organizationId()).isNotNull();
        assertThat(organization.tenantId()).isEqualTo(tenantId);
        assertThat(organization.status()).isEqualTo(IdentityStatus.ACTIVE);
        assertThat(organization.name().legalName()).isEqualTo("Atlas Cooperativa");
        assertThat(organization.name().tradeName()).isEqualTo("Atlas");
        assertThat(organization.identifier().type()).isEqualTo("RUC");
        assertThat(organization.identifier().countryCode()).isEqualTo("PY");
        assertThat(organization.identifier().normalizedValue()).isEqualTo("800123456");
        assertThat(organization.identifier().maskedValue()).isEqualTo("****3456");
        assertThat(organization.createdAt()).isNotNull();
    }

    @Test
    void rejectsMissingLegalName() {
        assertThatThrownBy(() -> new OrganizationName(" ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("legalName");

        assertThatThrownBy(() -> new OrganizationName("A".repeat(241), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("legalName");

        assertThatThrownBy(() -> new OrganizationName("Atlas Cooperativa", "A".repeat(241)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tradeName");
    }

    @Test
    void rejectsInvalidIdentifier() {
        assertThatThrownBy(() -> OrganizationIdentifier.of("RUC", " - ", "PY"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("identifier.value");

        assertThatThrownBy(() -> OrganizationIdentifier.of("RUC", "ABC-123", "PY"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("identifier.value");

        assertThatThrownBy(() -> OrganizationIdentifier.of("RUC", "1", "PY"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("identifier.value");

        assertThatThrownBy(() -> OrganizationIdentifier.of("RUC", "123456789012345678901", "PY"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("identifier.value");

        assertThatThrownBy(() -> OrganizationIdentifier.of("RUC", "1".repeat(161), "PY"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("identifier.value");
    }

    @Test
    void supportsOnlyInitialRucPyIdentifier() {
        assertThatThrownBy(() -> OrganizationIdentifier.of("TIN", "80012345-6", "PY"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("identifier.type");

        assertThatThrownBy(() -> OrganizationIdentifier.of("RUC", "80012345-6", "BR"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("identifier.countryCode");
    }
}
