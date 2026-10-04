package com.atlas.gic.relationships.support;

import com.atlas.gic.identity.domain.OrganizationId;
import com.atlas.gic.identity.domain.PersonId;
import com.atlas.gic.relationships.application.PersonOrganizationRelationshipListPage;
import com.atlas.gic.relationships.application.PersonOrganizationRelationshipRepository;
import com.atlas.gic.relationships.domain.PersonOrganizationRelationship;
import com.atlas.gic.relationships.domain.PersonOrganizationRelationshipStatus;
import com.atlas.gic.shared.tenancy.domain.TenantId;

import java.util.List;

public class NoopPersonOrganizationRelationshipRepository implements PersonOrganizationRelationshipRepository {

    @Override
    public boolean personExists(TenantId tenantId, PersonId personId) {
        return false;
    }

    @Override
    public boolean organizationExists(TenantId tenantId, OrganizationId organizationId) {
        return false;
    }

    @Override
    public void save(PersonOrganizationRelationship relationship, String actor) {
    }

    @Override
    public PersonOrganizationRelationshipListPage findByPerson(
            TenantId tenantId,
            PersonId personId,
            PersonOrganizationRelationshipStatus status,
            int page,
            int size) {
        return new PersonOrganizationRelationshipListPage(List.of(), page, size, 0);
    }
}
