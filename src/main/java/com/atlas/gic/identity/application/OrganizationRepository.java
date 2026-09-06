package com.atlas.gic.identity.application;

import com.atlas.gic.identity.domain.Organization;

public interface OrganizationRepository {

    void save(Organization organization);
}
