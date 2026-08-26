package com.atlas.gic.roles.application;

public interface BusinessRoleEndedAudit {

    void record(BusinessRoleEndedAuditEntry entry);
}
