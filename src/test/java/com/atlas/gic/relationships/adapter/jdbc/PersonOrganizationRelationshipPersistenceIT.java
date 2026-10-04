package com.atlas.gic.relationships.adapter.jdbc;

import com.atlas.gic.identity.application.OrganizationNotFoundException;
import com.atlas.gic.identity.application.PersonNotFoundException;
import com.atlas.gic.identity.domain.OrganizationId;
import com.atlas.gic.identity.domain.PersonId;
import com.atlas.gic.relationships.application.AssignPersonOrganizationRelationshipCommand;
import com.atlas.gic.relationships.application.AssignPersonOrganizationRelationshipUseCase;
import com.atlas.gic.relationships.application.DuplicateActivePersonOrganizationRelationshipException;
import com.atlas.gic.relationships.application.GetPersonOrganizationRelationshipsUseCase;
import com.atlas.gic.relationships.application.PersonOrganizationRelationshipCreatedAudit;
import com.atlas.gic.relationships.application.PersonOrganizationRelationshipCreatedAuditEntry;
import com.atlas.gic.relationships.domain.PersonOrganizationRelationshipStatus;
import com.atlas.gic.relationships.domain.PersonOrganizationRelationshipType;
import com.atlas.gic.shared.tenancy.application.TenantContext;
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
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class PersonOrganizationRelationshipPersistenceIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final String APP_USER = "atlas_gic_relationships_app";
    private static final String APP_PASSWORD = "atlas_gic_relationships_app_password";
    private static TenantId tenantA;
    private static TenantId tenantB;
    private static PersonId personA;
    private static PersonId personB;
    private static OrganizationId organizationA;
    private static OrganizationId organizationB;

    @BeforeAll
    static void migrateAndSeed() throws Exception {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        tenantA = TenantId.of(UUID.randomUUID());
        tenantB = TenantId.of(UUID.randomUUID());
        personA = PersonId.of(UUID.randomUUID());
        personB = PersonId.of(UUID.randomUUID());
        organizationA = OrganizationId.of(UUID.randomUUID());
        organizationB = OrganizationId.of(UUID.randomUUID());

        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE ROLE %s LOGIN PASSWORD '%s'".formatted(APP_USER, APP_PASSWORD));
            statement.execute("GRANT USAGE ON SCHEMA gic TO %s".formatted(APP_USER));
            statement.execute("GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA gic TO %s".formatted(APP_USER));
            statement.executeUpdate("""
                    INSERT INTO gic.tenants (tenant_id, code, display_name)
                    VALUES ('%s', 'relationships-a', 'Relationships A'), ('%s', 'relationships-b', 'Relationships B')
                    """.formatted(tenantA, tenantB));
            statement.execute("SET atlas.platform_access = 'true'");
            insertPerson(statement, tenantA, personA, "Persona", "A");
            insertPerson(statement, tenantB, personB, "Persona", "B");
            insertOrganization(statement, tenantA, organizationA, "Organizacion A", "Org A", "801111111");
            insertOrganization(statement, tenantB, organizationB, "Organizacion B", "Org B", "802222222");
            statement.execute("RESET atlas.platform_access");
        }
    }

    @Test
    void appConnectionUsesOrdinaryRoleSubjectToRls() {
        var jdbcTemplate = new JdbcTemplate(appDataSource());

        assertThat(jdbcTemplate.queryForObject("SELECT current_user", String.class)).isEqualTo(APP_USER);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT rolbypassrls FROM pg_roles WHERE rolname = current_user",
                Boolean.class)).isFalse();
    }

    @Test
    void assignPersistsRelationshipAndAuditTrail() {
        var dataSource = appDataSource();
        var jdbcTemplate = new JdbcTemplate(dataSource);
        var transactionTemplate = transactionTemplate(dataSource);
        var useCase = assignUseCase(tenantA, jdbcTemplate);
        var correlationId = "corr-rel-" + UUID.randomUUID();

        var result = transactionTemplate.execute(status -> useCase.assign(command(personA, organizationA, correlationId)));

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(PersonOrganizationRelationshipStatus.ACTIVE);
        assertThat(result.relationshipType()).isEqualTo(PersonOrganizationRelationshipType.REPRESENTATIVE_OF);
        transactionTemplate.executeWithoutResult(status -> {
            setCurrentTenant(jdbcTemplate, tenantA);
            assertThat(relationshipRows(jdbcTemplate, result.relationshipId().value())).isEqualTo(1);
            assertThat(auditRows(jdbcTemplate, result.relationshipId().value(), correlationId)).isEqualTo(1);
        });
    }

    @Test
    void duplicateActiveRelationshipMapsToConflictAndDoesNotWriteSecondAudit() {
        var dataSource = appDataSource();
        var jdbcTemplate = new JdbcTemplate(dataSource);
        var transactionTemplate = transactionTemplate(dataSource);
        var uniquePerson = seedPerson(UUID.randomUUID(), tenantA, "Duplicate", "Relationship");
        var uniqueOrganization = seedOrganization(UUID.randomUUID(), tenantA, "Duplicate SA", "Dup", uniqueRuc());
        var useCase = assignUseCase(tenantA, jdbcTemplate);

        transactionTemplate.executeWithoutResult(
                status -> useCase.assign(command(uniquePerson, uniqueOrganization, "corr-rel-first")));

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(
                status -> useCase.assign(command(uniquePerson, uniqueOrganization, "corr-rel-duplicate"))))
                .isInstanceOf(DuplicateActivePersonOrganizationRelationshipException.class);

        transactionTemplate.executeWithoutResult(status -> {
            setCurrentTenant(jdbcTemplate, tenantA);
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT count(*)
                    FROM gic.person_organization_relationship
                    WHERE person_id = ? AND organization_id = ?
                    """,
                    Integer.class,
                    uniquePerson.value(),
                    uniqueOrganization.value())).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT count(*)
                    FROM gic.person_organization_relationship_audit
                    WHERE correlation_id = 'corr-rel-duplicate'
                    """,
                    Integer.class)).isZero();
        });
    }

    @Test
    void auditFailureRollsBackRelationship() {
        var dataSource = appDataSource();
        var jdbcTemplate = new JdbcTemplate(dataSource);
        var transactionTemplate = transactionTemplate(dataSource);
        var uniquePerson = seedPerson(UUID.randomUUID(), tenantA, "Audit", "Rollback");
        var uniqueOrganization = seedOrganization(UUID.randomUUID(), tenantA, "Audit Rollback SA", "Audit", uniqueRuc());
        var useCase = new AssignPersonOrganizationRelationshipUseCase(
                new FixedTenantContext(tenantA),
                new JdbcPersonOrganizationRelationshipRepository(jdbcTemplate),
                new FailingRelationshipAudit(),
                () -> "relationships-it");

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(
                status -> useCase.assign(command(uniquePerson, uniqueOrganization, "corr-rel-failing-audit"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("audit unavailable");

        transactionTemplate.executeWithoutResult(status -> {
            setCurrentTenant(jdbcTemplate, tenantA);
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT count(*)
                    FROM gic.person_organization_relationship
                    WHERE person_id = ? AND organization_id = ?
                    """,
                    Integer.class,
                    uniquePerson.value(),
                    uniqueOrganization.value())).isZero();
        });
    }

    @Test
    void personAndOrganizationMustBeVisibleInCurrentTenant() {
        var dataSource = appDataSource();
        var jdbcTemplate = new JdbcTemplate(dataSource);
        var transactionTemplate = transactionTemplate(dataSource);
        var useCase = assignUseCase(tenantA, jdbcTemplate);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(
                status -> useCase.assign(command(personB, organizationA, "corr-rel-person-b"))))
                .isInstanceOf(PersonNotFoundException.class);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(
                status -> useCase.assign(command(personA, organizationB, "corr-rel-org-b"))))
                .isInstanceOf(OrganizationNotFoundException.class);
    }

    @Test
    void sameRelationshipBetweenEquivalentTenantResourcesIsAllowedAcrossTenants() {
        var dataSource = appDataSource();
        var jdbcTemplate = new JdbcTemplate(dataSource);
        var transactionTemplate = transactionTemplate(dataSource);
        var useCaseA = assignUseCase(tenantA, jdbcTemplate);
        var useCaseB = assignUseCase(tenantB, jdbcTemplate);
        var uniquePersonA = seedPerson(UUID.randomUUID(), tenantA, "Same", "TenantA");
        var uniqueOrganizationA = seedOrganization(UUID.randomUUID(), tenantA, "Same Tenant A SA", "Same A", uniqueRuc());
        var uniquePersonB = seedPerson(UUID.randomUUID(), tenantB, "Same", "TenantB");
        var uniqueOrganizationB = seedOrganization(UUID.randomUUID(), tenantB, "Same Tenant B SA", "Same B", uniqueRuc());

        var resultA = transactionTemplate.execute(
                status -> useCaseA.assign(command(uniquePersonA, uniqueOrganizationA, "corr-rel-tenant-a")));
        var resultB = transactionTemplate.execute(
                status -> useCaseB.assign(command(uniquePersonB, uniqueOrganizationB, "corr-rel-tenant-b")));

        assertThat(resultA).isNotNull();
        assertThat(resultB).isNotNull();
        assertThat(resultA.relationshipId()).isNotEqualTo(resultB.relationshipId());
    }

    @Test
    void getRelationshipsReturnsOnlyCurrentTenantRowsWithStableOrderAndPagination() {
        var dataSource = appDataSource();
        var jdbcTemplate = new JdbcTemplate(dataSource);
        var transactionTemplate = transactionTemplate(dataSource);
        var repository = new JdbcPersonOrganizationRelationshipRepository(jdbcTemplate);
        var assignUseCase = new AssignPersonOrganizationRelationshipUseCase(
                new FixedTenantContext(tenantA),
                repository,
                new JdbcPersonOrganizationRelationshipCreatedAudit(jdbcTemplate),
                () -> "relationships-it");
        var getUseCase = new GetPersonOrganizationRelationshipsUseCase(new FixedTenantContext(tenantA), repository);
        var uniquePerson = seedPerson(UUID.randomUUID(), tenantA, "List", "Relationships");
        var firstOrganization = seedOrganization(UUID.randomUUID(), tenantA, "Primera SA", "Primera", uniqueRuc());
        var secondOrganization = seedOrganization(UUID.randomUUID(), tenantA, "Segunda SA", "Segunda", uniqueRuc());

        transactionTemplate.executeWithoutResult(status -> assignUseCase.assign(new AssignPersonOrganizationRelationshipCommand(
                uniquePerson,
                firstOrganization,
                PersonOrganizationRelationshipType.REPRESENTATIVE_OF,
                LocalDate.parse("2026-09-07"),
                "corr-rel-first")));
        transactionTemplate.executeWithoutResult(status -> assignUseCase.assign(new AssignPersonOrganizationRelationshipCommand(
                uniquePerson,
                secondOrganization,
                PersonOrganizationRelationshipType.REPRESENTATIVE_OF,
                LocalDate.parse("2026-09-08"),
                "corr-rel-second")));

        var firstPage = transactionTemplate.execute(
                status -> getUseCase.get(uniquePerson, 0, 1, PersonOrganizationRelationshipStatus.ACTIVE));
        var secondPage = transactionTemplate.execute(
                status -> getUseCase.get(uniquePerson, 1, 1, PersonOrganizationRelationshipStatus.ACTIVE));
        var emptyPage = transactionTemplate.execute(
                status -> getUseCase.get(uniquePerson, 3, 1, PersonOrganizationRelationshipStatus.ACTIVE));

        assertThat(firstPage).isNotNull();
        assertThat(firstPage.total()).isEqualTo(2);
        assertThat(firstPage.items()).singleElement()
                .satisfies(item -> assertThat(item.organizationId()).isEqualTo(secondOrganization.value()));
        assertThat(secondPage.items()).singleElement()
                .satisfies(item -> assertThat(item.organizationId()).isEqualTo(firstOrganization.value()));
        assertThat(emptyPage.items()).isEmpty();
        assertThat(emptyPage.total()).isEqualTo(2);
    }

    @Test
    void crossTenantAndMissingTenantContextDoNotExposeRows() throws Exception {
        var dataSource = appDataSource();
        var jdbcTemplate = new JdbcTemplate(dataSource);
        var transactionTemplate = transactionTemplate(dataSource);
        var uniquePerson = seedPerson(UUID.randomUUID(), tenantA, "Rls", "Read");
        var uniqueOrganization = seedOrganization(UUID.randomUUID(), tenantA, "RLS Read SA", "RLS", uniqueRuc());
        var relationship = transactionTemplate.execute(
                status -> assignUseCase(tenantA, jdbcTemplate).assign(command(uniquePerson, uniqueOrganization, "corr-rel-rls")));

        assertThat(relationship).isNotNull();
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), APP_USER, APP_PASSWORD);
             var statement = connection.createStatement()) {
            try (var resultSet = statement.executeQuery("SELECT count(*) FROM gic.person_organization_relationship")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isZero();
            }

            statement.execute("SET atlas.current_tenant = '%s'".formatted(tenantB));
            try (var resultSet = statement.executeQuery("""
                    SELECT count(*)
                    FROM gic.person_organization_relationship
                    WHERE relationship_id = '%s'
                    """.formatted(relationship.relationshipId().value()))) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isZero();
            }
        }
    }

    @Test
    void rlsRejectsCrossTenantWrites() throws Exception {
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), APP_USER, APP_PASSWORD);
             var statement = connection.createStatement()) {
            statement.execute("SET atlas.current_tenant = '%s'".formatted(tenantA));

            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO gic.person_organization_relationship (
                        relationship_id, tenant_id, person_id, organization_id,
                        relationship_type, status, valid_from, created_by
                    )
                    VALUES ('%s', '%s', '%s', '%s', 'REPRESENTATIVE_OF', 'ACTIVE', '2026-09-07', 'rls-test')
                    """.formatted(UUID.randomUUID(), tenantB, personB, organizationB)))
                    .hasMessageContaining("violates row-level security policy");
        }
    }

    private static AssignPersonOrganizationRelationshipUseCase assignUseCase(TenantId tenantId, JdbcTemplate jdbcTemplate) {
        return new AssignPersonOrganizationRelationshipUseCase(
                new FixedTenantContext(tenantId),
                new JdbcPersonOrganizationRelationshipRepository(jdbcTemplate),
                new JdbcPersonOrganizationRelationshipCreatedAudit(jdbcTemplate),
                () -> "relationships-it");
    }

    private static AssignPersonOrganizationRelationshipCommand command(
            PersonId personId,
            OrganizationId organizationId,
            String correlationId) {
        return new AssignPersonOrganizationRelationshipCommand(
                personId,
                organizationId,
                PersonOrganizationRelationshipType.REPRESENTATIVE_OF,
                LocalDate.parse("2026-09-07"),
                correlationId);
    }

    private static DriverManagerDataSource appDataSource() {
        var dataSource = new DriverManagerDataSource();
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUsername(APP_USER);
        dataSource.setPassword(APP_PASSWORD);
        return dataSource;
    }

    private static TransactionTemplate transactionTemplate(DriverManagerDataSource dataSource) {
        return new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    private static void setCurrentTenant(JdbcTemplate jdbcTemplate, TenantId tenantId) {
        jdbcTemplate.queryForObject(
                "SELECT set_config('atlas.current_tenant', ?, true)",
                String.class,
                tenantId.toString());
    }

    private static int relationshipRows(JdbcTemplate jdbcTemplate, UUID relationshipId) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM gic.person_organization_relationship
                WHERE relationship_id = ?
                """,
                Integer.class,
                relationshipId);
    }

    private static int auditRows(JdbcTemplate jdbcTemplate, UUID relationshipId, String correlationId) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM gic.person_organization_relationship_audit
                WHERE action = 'PERSON_ORGANIZATION_RELATIONSHIP_CREATED'
                  AND actor = 'relationships-it'
                  AND relationship_id = ?
                  AND relationship_type = 'REPRESENTATIVE_OF'
                  AND correlation_id = ?
                """,
                Integer.class,
                relationshipId,
                correlationId);
    }

    private static PersonId seedPerson(UUID personId, TenantId tenantId, String givenName, String familyName) {
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("SET atlas.platform_access = 'true'");
            var id = PersonId.of(personId);
            insertPerson(statement, tenantId, id, givenName, familyName);
            statement.execute("RESET atlas.platform_access");
            return id;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static OrganizationId seedOrganization(
            UUID organizationId,
            TenantId tenantId,
            String legalName,
            String tradeName,
            String identifier) {
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("SET atlas.platform_access = 'true'");
            var id = OrganizationId.of(organizationId);
            insertOrganization(statement, tenantId, id, legalName, tradeName, identifier);
            statement.execute("RESET atlas.platform_access");
            return id;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void insertPerson(
            java.sql.Statement statement,
            TenantId tenantId,
            PersonId personId,
            String givenName,
            String familyName) throws java.sql.SQLException {
        statement.executeUpdate("""
                INSERT INTO gic.person (
                    person_id, tenant_id, given_name, family_name, display_name, status
                )
                VALUES ('%s', '%s', '%s', '%s', '%s %s', 'ACTIVE')
                """.formatted(personId, tenantId, givenName, familyName, givenName, familyName));
    }

    private static void insertOrganization(
            java.sql.Statement statement,
            TenantId tenantId,
            OrganizationId organizationId,
            String legalName,
            String tradeName,
            String identifier) throws java.sql.SQLException {
        statement.executeUpdate("""
                INSERT INTO gic.organization (
                    organization_id, tenant_id, legal_name, trade_name, status
                )
                VALUES ('%s', '%s', '%s', '%s', 'ACTIVE')
                """.formatted(organizationId, tenantId, legalName, tradeName));
        statement.executeUpdate("""
                INSERT INTO gic.organization_identifier (
                    organization_id, tenant_id, identifier_type, identifier_value, normalized_identifier_value, country_code
                )
                VALUES ('%s', '%s', 'RUC', '%s', '%s', 'PY')
                """.formatted(organizationId, tenantId, identifier, identifier));
    }

    private static String uniqueRuc() {
        var digits = (UUID.randomUUID().toString() + UUID.randomUUID()).replaceAll("[^0-9]", "");
        return "80" + digits.substring(0, 12);
    }

    private record FixedTenantContext(TenantId tenantId) implements TenantContext {

        @Override
        public Optional<TenantId> currentTenant() {
            return Optional.of(tenantId);
        }

        @Override
        public boolean platformAccess() {
            return false;
        }
    }

    private static class FailingRelationshipAudit implements PersonOrganizationRelationshipCreatedAudit {

        @Override
        public void record(PersonOrganizationRelationshipCreatedAuditEntry entry) {
            throw new IllegalStateException("audit unavailable");
        }
    }
}
