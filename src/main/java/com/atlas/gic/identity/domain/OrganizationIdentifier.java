package com.atlas.gic.identity.domain;

import java.util.Locale;

public record OrganizationIdentifier(String type, String value, String countryCode, String normalizedValue) {

    private static final int MIN_NORMALIZED_LENGTH = 2;
    private static final int MAX_NORMALIZED_LENGTH = 20;
    private static final int MAX_VALUE_LENGTH = 160;

    public OrganizationIdentifier {
        type = required(type, "identifier.type").toUpperCase(Locale.ROOT);
        value = required(value, "identifier.value");
        countryCode = required(countryCode, "identifier.countryCode").toUpperCase(Locale.ROOT);
        if (!"RUC".equals(type)) {
            throw new IllegalArgumentException("identifier.type is not supported");
        }
        if (!"PY".equals(countryCode)) {
            throw new IllegalArgumentException("identifier.countryCode is not supported");
        }
        normalizedValue = normalizeValue(value);
        if (normalizedValue.isBlank()
                || normalizedValue.length() < MIN_NORMALIZED_LENGTH
                || normalizedValue.length() > MAX_NORMALIZED_LENGTH) {
            throw new IllegalArgumentException("identifier.value is invalid");
        }
        if (!normalizedValue.matches("[0-9]+")) {
            throw new IllegalArgumentException("identifier.value is invalid");
        }
    }

    public static OrganizationIdentifier of(String type, String value, String countryCode) {
        return new OrganizationIdentifier(type, value, countryCode, null);
    }

    public String maskedValue() {
        if (normalizedValue.length() <= 4) {
            return "****";
        }
        return "****" + normalizedValue.substring(normalizedValue.length() - 4);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("%s is required".formatted(field));
        }
        var normalized = value.trim();
        if (normalized.length() > MAX_VALUE_LENGTH) {
            throw new IllegalArgumentException("%s is too long".formatted(field));
        }
        return normalized;
    }

    private static String normalizeValue(String value) {
        return value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }
}
