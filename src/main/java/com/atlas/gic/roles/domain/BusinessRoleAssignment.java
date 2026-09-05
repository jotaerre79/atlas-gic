package com.atlas.gic.roles.domain;

import com.atlas.gic.identity.domain.PersonId;
import com.atlas.gic.shared.tenancy.domain.TenantId;

import java.time.LocalDate;
import java.util.Objects;

public record BusinessRoleAssignment(
        BusinessRoleAssignmentId assignmentId,
        TenantId tenantId,
        PersonId personId,
        BusinessRoleType role,
        LocalDate validFrom,
        LocalDate validTo,
        BusinessRoleAssignmentStatus status) {

    public BusinessRoleAssignment {
        Objects.requireNonNull(assignmentId, "assignmentId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(personId, "personId must not be null");
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(validFrom, "validFrom must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (validTo != null && validTo.isBefore(validFrom)) {
            throw new IllegalArgumentException("validTo must not be before validFrom");
        }
    }

    public static BusinessRoleAssignment active(
            TenantId tenantId,
            PersonId personId,
            BusinessRoleType role,
            LocalDate validFrom,
            LocalDate validTo) {
        return new BusinessRoleAssignment(
                BusinessRoleAssignmentId.newId(),
                tenantId,
                personId,
                role,
                validFrom,
                validTo,
                BusinessRoleAssignmentStatus.ACTIVE);
    }

    public BusinessRoleAssignment end(LocalDate endDate) {
        if (status != BusinessRoleAssignmentStatus.ACTIVE) {
            throw new IllegalStateException("only active assignments can be ended");
        }
        Objects.requireNonNull(endDate, "validTo must not be null");
        return new BusinessRoleAssignment(
                assignmentId,
                tenantId,
                personId,
                role,
                validFrom,
                endDate,
                BusinessRoleAssignmentStatus.ENDED);
    }
}
