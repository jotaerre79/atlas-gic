package com.atlas.gic.roles.application;

public interface BusinessRoleAssignedAudit {

    void record(BusinessRoleAssignedAuditEntry entry);
}
