package com.atlas.gic.relationships.application;

import com.atlas.gic.identity.domain.OrganizationId;
import com.atlas.gic.identity.domain.PersonId;
import com.atlas.gic.relationships.domain.PersonOrganizationRelationshipId;
import com.atlas.gic.relationships.domain.PersonOrganizationRelationshipType;
import com.atlas.gic.shared.tenancy.domain.TenantId;

import java.time.Instant;

public record PersonOrganizationRelationshipCreatedAuditEntry(
        String actor,
        TenantId tenantId,
        PersonOrganizationRelationshipId relationshipId,
        PersonId personId,
        OrganizationId organizationId,
        PersonOrganizationRelationshipType relationshipType,
        String correlationId,
        Instant createdAt) {
}
