package com.atlas.gic.shared.audit.application;

import java.time.Instant;

public record PlatformAccessAuditEntry(
        String actor,
        String reason,
        String operation,
        String correlationId,
        Instant timestamp) {

    public PlatformAccessAuditEntry {
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("actor is required");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is required");
        }
        if (operation == null || operation.isBlank()) {
            throw new IllegalArgumentException("operation is required");
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}
