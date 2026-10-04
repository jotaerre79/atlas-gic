package com.atlas.gic.relationships.support;

import com.atlas.gic.relationships.application.PersonOrganizationRelationshipCreatedAudit;
import com.atlas.gic.relationships.application.PersonOrganizationRelationshipCreatedAuditEntry;

public class NoopPersonOrganizationRelationshipCreatedAudit implements PersonOrganizationRelationshipCreatedAudit {

    @Override
    public void record(PersonOrganizationRelationshipCreatedAuditEntry entry) {
    }
}
