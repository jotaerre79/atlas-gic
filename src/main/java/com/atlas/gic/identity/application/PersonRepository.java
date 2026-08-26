package com.atlas.gic.identity.application;

import com.atlas.gic.identity.domain.Person;

public interface PersonRepository {

    void save(Person person);
}
