package com.atlas.gic.roles.application;

import com.atlas.gic.identity.domain.PersonId;
import com.atlas.gic.roles.domain.BusinessRoleAssignmentId;
import com.atlas.gic.roles.domain.BusinessRoleAssignmentStatus;
import com.atlas.gic.roles.domain.BusinessRoleType;

import java.time.LocalDate;

public record AssignBusinessRoleResult(
        BusinessRoleAssignmentId assignmentId,
        PersonId personId,
        BusinessRoleType role,
        BusinessRoleAssignmentStatus status,
        LocalDate validFrom,
        LocalDate validTo) {
}
