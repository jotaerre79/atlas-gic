package com.atlas.gic.identity.domain;

public record OrganizationName(String legalName, String tradeName) {

    private static final int MAX_NAME_LENGTH = 240;

    public OrganizationName {
        legalName = required(legalName, "legalName");
        tradeName = normalizeOptional(tradeName);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("%s is required".formatted(field));
        }
        var normalized = value.trim();
        if (normalized.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("%s is too long".formatted(field));
        }
        return normalized;
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        var normalized = value.trim();
        if (normalized.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("tradeName is too long");
        }
        return normalized;
    }
}
