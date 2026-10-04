package com.atlas.gic.relationships.domain;

import com.atlas.gic.identity.domain.OrganizationId;
import com.atlas.gic.identity.domain.PersonId;
import com.atlas.gic.shared.tenancy.domain.TenantId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersonOrganizationRelationshipTest {

    @Test
    void createsActiveRepresentativeRelationship() {
        var relationship = PersonOrganizationRelationship.active(
                TenantId.of(UUID.randomUUID()),
                PersonId.of(UUID.randomUUID()),
                OrganizationId.of(UUID.randomUUID()),
                PersonOrganizationRelationshipType.REPRESENTATIVE_OF,
                LocalDate.parse("2026-09-07"));

        assertThat(relationship.status()).isEqualTo(PersonOrganizationRelationshipStatus.ACTIVE);
        assertThat(relationship.validTo()).isNull();
        assertThat(relationship.createdAt()).isNotNull();
    }

    @Test
    void rejectsInvalidValidityPeriod() {
        assertThatThrownBy(() -> new PersonOrganizationRelationship(
                PersonOrganizationRelationshipId.newId(),
                TenantId.of(UUID.randomUUID()),
                PersonId.of(UUID.randomUUID()),
                OrganizationId.of(UUID.randomUUID()),
                PersonOrganizationRelationshipType.REPRESENTATIVE_OF,
                LocalDate.parse("2026-09-07"),
                LocalDate.parse("2026-09-06"),
                PersonOrganizationRelationshipStatus.ACTIVE,
                java.time.Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
