package com.atlas.gic.identity.application;

public record RegisterOrganizationCommand(
        String legalName,
        String tradeName,
        IdentifierCommand identifier,
        String correlationId) {

    public record IdentifierCommand(String type, String value, String countryCode) {
    }
}
