package com.atlas.gic.identity.adapter.jdbc;

import com.atlas.gic.identity.application.DuplicatePersonIdentifierException;
import com.atlas.gic.identity.application.PersonRepository;
import com.atlas.gic.identity.domain.Person;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;

@Repository
@ConditionalOnBean(JdbcTemplate.class)
public class JdbcPersonRepository implements PersonRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcPersonRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(Person person) {
        try {
            jdbcTemplate.queryForObject(
                    "SELECT set_config('atlas.current_tenant', ?, true)",
                    String.class,
                    person.tenantId().toString());
            jdbcTemplate.update("""
                    INSERT INTO gic.person (
                        person_id, tenant_id, given_name, middle_name, family_name, display_name, status
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    person.personId().value(),
                    person.tenantId().value(),
                    person.name().givenName(),
                    person.name().middleName(),
                    person.name().familyName(),
                    person.displayName(),
                    person.status().name());

            jdbcTemplate.update("""
                    INSERT INTO gic.person_identifier (
                        person_id, tenant_id, identifier_type, identifier_value, normalized_identifier_value, issuer
                    )
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    ps -> {
                        ps.setObject(1, person.personId().value(), Types.OTHER);
                        ps.setObject(2, person.tenantId().value(), Types.OTHER);
                        ps.setString(3, person.identifier().type());
                        ps.setString(4, person.identifier().value());
                        ps.setString(5, person.identifier().normalizedValue());
                        ps.setString(6, person.identifier().issuer());
                    });
        } catch (DuplicateKeyException exception) {
            throw new DuplicatePersonIdentifierException();
        }
    }
}
