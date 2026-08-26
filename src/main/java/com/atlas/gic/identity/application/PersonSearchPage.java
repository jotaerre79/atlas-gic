package com.atlas.gic.identity.application;

import java.util.List;

public record PersonSearchPage(List<PersonSearchItem> items, int page, int size, long total) {

    public PersonSearchPage {
        items = List.copyOf(items == null ? List.of() : items);
    }
}
