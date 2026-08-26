package com.atlas.gic.roles.domain;

import com.atlas.gic.identity.domain.PersonId;
import com.atlas.gic.shared.tenancy.domain.TenantId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BusinessRoleAssignmentTest {

    private final TenantId tenantId = TenantId.of(UUID.randomUUID());
    private final PersonId personId = PersonId.of(UUID.randomUUID());

    @Test
    void allowsDifferentBusinessRolesForSamePerson() {
        var socio = BusinessRoleAssignment.active(
                tenantId,
                personId,
                BusinessRoleType.SOCIO,
                LocalDate.parse("2026-08-26"),
                null);
        var cliente = BusinessRoleAssignment.active(
                tenantId,
                personId,
                BusinessRoleType.CLIENTE,
                LocalDate.parse("2026-08-26"),
                null);

        assertThat(socio.role()).isEqualTo(BusinessRoleType.SOCIO);
        assertThat(cliente.role()).isEqualTo(BusinessRoleType.CLIENTE);
        assertThat(socio.personId()).isEqualTo(cliente.personId());
    }

    @Test
    void rejectsInvalidValidityPeriod() {
        assertThatThrownBy(() -> BusinessRoleAssignment.active(
                tenantId,
                personId,
                BusinessRoleType.PROVEEDOR,
                LocalDate.parse("2026-08-26"),
                LocalDate.parse("2026-08-25")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("validTo");
    }

    @Test
    void endsActiveAssignment() {
        var assignment = BusinessRoleAssignment.active(
                tenantId,
                personId,
                BusinessRoleType.SOCIO,
                LocalDate.parse("2026-01-10"),
                null);

        var ended = assignment.end(LocalDate.parse("2026-08-26"));

        assertThat(ended.status()).isEqualTo(BusinessRoleAssignmentStatus.ENDED);
        assertThat(ended.validTo()).isEqualTo(LocalDate.parse("2026-08-26"));
        assertThat(ended.assignmentId()).isEqualTo(assignment.assignmentId());
    }

    @Test
    void rejectsEndingWithoutValidTo() {
        var assignment = BusinessRoleAssignment.active(
                tenantId,
                personId,
                BusinessRoleType.SOCIO,
                LocalDate.parse("2026-01-10"),
                null);

        assertThatThrownBy(() -> assignment.end(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("validTo");
    }

    @Test
    void rejectsEndingEndedAssignmentAgain() {
        var ended = BusinessRoleAssignment.active(
                tenantId,
                personId,
                BusinessRoleType.CLIENTE,
                LocalDate.parse("2026-01-10"),
                null).end(LocalDate.parse("2026-08-26"));

        assertThatThrownBy(() -> ended.end(LocalDate.parse("2026-08-27")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active");
    }
}
