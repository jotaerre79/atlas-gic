package com.atlas.gic.identity.application;

public interface PersonRegistrationAudit {

    void record(PersonRegisteredAuditEntry entry);
}
