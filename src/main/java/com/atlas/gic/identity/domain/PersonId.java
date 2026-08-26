package com.atlas.gic.identity.domain;

import java.util.Objects;
import java.util.UUID;

public record PersonId(UUID value) {

    public PersonId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static PersonId newId() {
        return new PersonId(UUID.randomUUID());
    }

    public static PersonId of(UUID value) {
        return new PersonId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
