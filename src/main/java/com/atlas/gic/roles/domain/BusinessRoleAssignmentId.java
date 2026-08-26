package com.atlas.gic.roles.domain;

import java.util.Objects;
import java.util.UUID;

public record BusinessRoleAssignmentId(UUID value) {

    public BusinessRoleAssignmentId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static BusinessRoleAssignmentId newId() {
        return new BusinessRoleAssignmentId(UUID.randomUUID());
    }

    public static BusinessRoleAssignmentId of(UUID value) {
        return new BusinessRoleAssignmentId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
