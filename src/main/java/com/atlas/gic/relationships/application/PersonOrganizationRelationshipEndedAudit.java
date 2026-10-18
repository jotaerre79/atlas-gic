package com.atlas.gic.relationships.application;

public interface PersonOrganizationRelationshipEndedAudit {

    void record(PersonOrganizationRelationshipEndedAuditEntry entry);
}
