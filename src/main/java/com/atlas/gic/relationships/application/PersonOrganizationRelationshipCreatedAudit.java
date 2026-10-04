package com.atlas.gic.relationships.application;

public interface PersonOrganizationRelationshipCreatedAudit {

    void record(PersonOrganizationRelationshipCreatedAuditEntry entry);
}
