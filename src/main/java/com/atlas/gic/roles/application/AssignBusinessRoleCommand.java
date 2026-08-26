package com.atlas.gic.roles.application;

import com.atlas.gic.identity.domain.PersonId;
import com.atlas.gic.roles.domain.BusinessRoleType;

import java.time.LocalDate;

public record AssignBusinessRoleCommand(
        PersonId personId,
        BusinessRoleType role,
        LocalDate validFrom,
        LocalDate validTo,
        String correlationId) {
}
