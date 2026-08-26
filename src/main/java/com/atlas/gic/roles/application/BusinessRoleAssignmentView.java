package com.atlas.gic.roles.application;

import com.atlas.gic.roles.domain.BusinessRoleAssignmentStatus;
import com.atlas.gic.roles.domain.BusinessRoleType;

import java.time.LocalDate;
import java.util.UUID;

public record BusinessRoleAssignmentView(
        UUID assignmentId,
        UUID personId,
        BusinessRoleType role,
        BusinessRoleAssignmentStatus status,
        LocalDate validFrom,
        LocalDate validTo) {
}
