package com.atlas.gic.relationships.application;

import com.atlas.gic.identity.domain.OrganizationId;
import com.atlas.gic.identity.domain.PersonId;
import com.atlas.gic.relationships.domain.PersonOrganizationRelationship;
import com.atlas.gic.relationships.domain.PersonOrganizationRelationshipStatus;
import com.atlas.gic.shared.tenancy.domain.TenantId;

public interface PersonOrganizationRelationshipRepository {

    boolean personExists(TenantId tenantId, PersonId personId);

    boolean organizationExists(TenantId tenantId, OrganizationId organizationId);

    void save(PersonOrganizationRelationship relationship, String actor);

    PersonOrganizationRelationshipListPage findByPerson(
            TenantId tenantId,
            PersonId personId,
            PersonOrganizationRelationshipStatus status,
            int page,
            int size);
}
