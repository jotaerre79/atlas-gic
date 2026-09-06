package com.atlas.gic.identity.application;

import java.util.List;

public record OrganizationSearchPage(List<OrganizationSearchItem> items, int page, int size, long total) {

    public OrganizationSearchPage {
        items = List.copyOf(items == null ? List.of() : items);
    }
}
