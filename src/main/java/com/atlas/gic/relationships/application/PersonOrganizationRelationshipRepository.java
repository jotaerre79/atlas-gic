package com.atlas.gic.relationships.application;

import com.atlas.gic.identity.domain.OrganizationId;
import com.atlas.gic.identity.domain.PersonId;
import com.atlas.gic.relationships.domain.PersonOrganizationRelationship;
import com.atlas.gic.relationships.domain.PersonOrganizationRelationshipId;
import com.atlas.gic.relationships.domain.PersonOrganizationRelationshipStatus;
import com.atlas.gic.shared.tenancy.domain.TenantId;

import java.util.Optional;

public interface PersonOrganizationRelationshipRepository {

    boolean personExists(TenantId tenantId, PersonId personId);

    boolean organizationExists(TenantId tenantId, OrganizationId organizationId);

    void save(PersonOrganizationRelationship relationship, String actor);

    default Optional<PersonOrganizationRelationship> findById(
            TenantId tenantId,
            PersonId personId,
            PersonOrganizationRelationshipId relationshipId) {
        throw new UnsupportedOperationException("findById is not implemented");
    }

    default boolean endActive(TenantId tenantId, PersonId personId, PersonOrganizationRelationship endedRelationship) {
        throw new UnsupportedOperationException("endActive is not implemented");
    }

    PersonOrganizationRelationshipListPage findByPerson(
            TenantId tenantId,
            PersonId personId,
            PersonOrganizationRelationshipStatus status,
            int page,
            int size);
}
