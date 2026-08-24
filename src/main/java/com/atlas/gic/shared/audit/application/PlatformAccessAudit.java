package com.atlas.gic.shared.audit.application;

public interface PlatformAccessAudit {

    void record(PlatformAccessAuditEntry entry);
}
