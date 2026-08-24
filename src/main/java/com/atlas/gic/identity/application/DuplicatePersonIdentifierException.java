package com.atlas.gic.identity.application;

public class DuplicatePersonIdentifierException extends RuntimeException {

    public DuplicatePersonIdentifierException() {
        super("person identifier already exists for tenant");
    }
}
