package com.atlas.gic.identity.domain;

import java.util.Locale;

public record PersonIdentifier(String type, String value, String issuer, String normalizedValue) {

    public PersonIdentifier {
        type = required(type, "identifier.type").toUpperCase(Locale.ROOT);
        value = required(value, "identifier.value");
        issuer = normalizeOptional(issuer);
        normalizedValue = normalizeValue(value);
        if (normalizedValue.isBlank()) {
            throw new IllegalArgumentException("identifier.value is invalid");
        }
    }

    public static PersonIdentifier of(String type, String value, String issuer) {
        return new PersonIdentifier(type, value, issuer, null);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("%s is required".formatted(field));
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeValue(String value) {
        return value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }
}
