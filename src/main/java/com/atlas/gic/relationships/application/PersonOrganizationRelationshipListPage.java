package com.atlas.gic.relationships.application;

import java.util.List;

public record PersonOrganizationRelationshipListPage(
        List<PersonOrganizationRelationshipView> items,
        int page,
        int size,
        long total) {
}
