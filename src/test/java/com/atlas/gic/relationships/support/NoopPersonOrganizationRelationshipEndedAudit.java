package com.atlas.gic.relationships.support;

import com.atlas.gic.relationships.application.PersonOrganizationRelationshipEndedAudit;
import com.atlas.gic.relationships.application.PersonOrganizationRelationshipEndedAuditEntry;

public class NoopPersonOrganizationRelationshipEndedAudit implements PersonOrganizationRelationshipEndedAudit {

    @Override
    public void record(PersonOrganizationRelationshipEndedAuditEntry entry) {
    }
}
