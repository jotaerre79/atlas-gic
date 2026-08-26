package com.atlas.gic.identity.application;

import com.atlas.gic.identity.domain.PersonId;
import com.atlas.gic.shared.tenancy.domain.TenantId;

import java.util.Optional;

public interface PersonReadRepository {

    Optional<PersonView> findById(TenantId tenantId, PersonId personId);

    PersonSearchPage search(TenantId tenantId, String query, int page, int size);
}
