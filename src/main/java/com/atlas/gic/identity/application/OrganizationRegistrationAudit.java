package com.atlas.gic.identity.application;

public interface OrganizationRegistrationAudit {

    void record(OrganizationRegisteredAuditEntry entry);
}
