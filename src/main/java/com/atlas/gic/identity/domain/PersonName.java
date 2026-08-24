package com.atlas.gic.identity.domain;

public record PersonName(String givenName, String middleName, String familyName) {

    public PersonName {
        givenName = required(givenName, "givenName");
        middleName = normalizeOptional(middleName);
        familyName = required(familyName, "familyName");
    }

    public String displayName() {
        if (middleName == null) {
            return "%s %s".formatted(givenName, familyName);
        }
        return "%s %s %s".formatted(givenName, middleName, familyName);
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
        return value.trim();
    }
}
