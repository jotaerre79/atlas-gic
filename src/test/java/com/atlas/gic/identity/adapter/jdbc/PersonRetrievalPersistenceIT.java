package com.atlas.gic.identity.adapter.jdbc;

import com.atlas.gic.identity.domain.PersonId;
import com.atlas.gic.shared.tenancy.domain.TenantId;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class PersonRetrievalPersistenceIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final String APP_USER = "atlas_gic_person_read_app";
    private static final String APP_PASSWORD = "atlas_gic_person_read_app_password";
    private static TenantId tenantA;
    private static TenantId tenantB;
    private static PersonId personA1;
    private static PersonId personA2;
    private static PersonId personB1;

    @BeforeAll
    static void migrateAndSeed() throws Exception {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        tenantA = TenantId.of(UUID.randomUUID());
        tenantB = TenantId.of(UUID.randomUUID());
        personA1 = PersonId.of(UUID.randomUUID());
        personA2 = PersonId.of(UUID.randomUUID());
        personB1 = PersonId.of(UUID.randomUUID());

        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE ROLE %s LOGIN PASSWORD '%s'".formatted(APP_USER, APP_PASSWORD));
            statement.execute("GRANT USAGE ON SCHEMA gic TO %s".formatted(APP_USER));
            statement.execute("GRANT SELECT ON ALL TABLES IN SCHEMA gic TO %s".formatted(APP_USER));
            statement.executeUpdate("""
                    INSERT INTO gic.tenants (tenant_id, code, display_name)
                    VALUES ('%s', 'read-a', 'Read A'), ('%s', 'read-b', 'Read B')
                    """.formatted(tenantA, tenantB));
            statement.execute("SET atlas.platform_access = 'true'");
            insertPerson(statement, tenantA, personA1, "Ana", "Alvarez", "CI", "A-123", "A123");
            insertPerson(statement, tenantA, personA2, "Bruno", "Benitez", "CI", "A-456", "A456");
            insertPerson(statement, tenantB, personB1, "Carlos", "Cross", "CI", "B-999", "B999");
            statement.execute("RESET atlas.platform_access");
        }
    }

    @Test
    void getPersonFindsCurrentTenantPerson() {
        var repository = repository();
        var result = transactionTemplate().execute(status -> repository.findById(tenantA, personA1));

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().displayName()).isEqualTo("Ana Alvarez");
        assertThat(result.orElseThrow().identifiers()).singleElement()
                .satisfies(identifier -> assertThat(identifier.maskedValue()).endsWith("123"));
    }

    @Test
    void getPersonDoesNotExposeAnotherTenantPerson() {
        var repository = repository();
        var result = transactionTemplate().execute(status -> repository.findById(tenantA, personB1));

        assertThat(result).isEmpty();
    }

    @Test
    void searchReturnsOnlyCurrentTenantPeopleWithStablePagination() {
        var repository = repository();

        var firstPage = transactionTemplate().execute(status -> repository.search(tenantA, null, 0, 1));
        var secondPage = transactionTemplate().execute(status -> repository.search(tenantA, null, 1, 1));
        var queryPage = transactionTemplate().execute(status -> repository.search(tenantA, "bruno", 0, 20));
        var crossTenantQuery = transactionTemplate().execute(status -> repository.search(tenantA, "carlos", 0, 20));

        assertThat(firstPage).isNotNull();
        assertThat(firstPage.total()).isEqualTo(2);
        assertThat(firstPage.items()).singleElement()
                .satisfies(item -> assertThat(item.displayName()).isEqualTo("Ana Alvarez"));
        assertThat(secondPage).isNotNull();
        assertThat(secondPage.items()).singleElement()
                .satisfies(item -> assertThat(item.displayName()).isEqualTo("Bruno Benitez"));
        assertThat(queryPage).isNotNull();
        assertThat(queryPage.items()).singleElement()
                .satisfies(item -> assertThat(item.personId()).isEqualTo(personA2.value()));
        assertThat(crossTenantQuery).isNotNull();
        assertThat(crossTenantQuery.items()).isEmpty();
    }

    @Test
    void rlsDeniesReadsWithoutTenantContext() {
        var jdbcTemplate = new JdbcTemplate(appDataSource());

        var count = jdbcTemplate.queryForObject("SELECT count(*) FROM gic.person", Integer.class);

        assertThat(count).isZero();
    }

    private static void insertPerson(
            java.sql.Statement statement,
            TenantId tenantId,
            PersonId personId,
            String givenName,
            String familyName,
            String identifierType,
            String identifierValue,
            String normalizedIdentifierValue) throws java.sql.SQLException {
        statement.executeUpdate("""
                INSERT INTO gic.person (
                    person_id, tenant_id, given_name, family_name, display_name, status
                )
                VALUES ('%s', '%s', '%s', '%s', '%s %s', 'ACTIVE')
                """.formatted(personId, tenantId, givenName, familyName, givenName, familyName));
        statement.executeUpdate("""
                INSERT INTO gic.person_identifier (
                    person_id, tenant_id, identifier_type, identifier_value, normalized_identifier_value, issuer
                )
                VALUES ('%s', '%s', '%s', '%s', '%s', 'PY')
                """.formatted(personId, tenantId, identifierType, identifierValue, normalizedIdentifierValue));
    }

    private static JdbcPersonReadRepository repository() {
        return new JdbcPersonReadRepository(new JdbcTemplate(appDataSource()));
    }

    private static TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(new DataSourceTransactionManager(appDataSource()));
    }

    private static DriverManagerDataSource appDataSource() {
        var dataSource = new DriverManagerDataSource();
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUsername(APP_USER);
        dataSource.setPassword(APP_PASSWORD);
        return dataSource;
    }
}
