package com.atlas.gic.roles.application;

import com.atlas.gic.identity.domain.PersonId;
import com.atlas.gic.roles.domain.BusinessRoleAssignmentId;

import java.time.LocalDate;

public record EndBusinessRoleCommand(
        PersonId personId,
        BusinessRoleAssignmentId assignmentId,
        LocalDate validTo,
        String reason,
        String correlationId) {
}
