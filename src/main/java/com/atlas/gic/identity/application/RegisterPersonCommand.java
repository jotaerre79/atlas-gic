package com.atlas.gic.identity.application;

public record RegisterPersonCommand(
        String givenName,
        String middleName,
        String familyName,
        IdentifierCommand identifier,
        String correlationId) {

    public record IdentifierCommand(String type, String value, String issuer) {
    }
}
