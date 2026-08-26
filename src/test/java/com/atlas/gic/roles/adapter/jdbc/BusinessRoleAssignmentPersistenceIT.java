package com.atlas.gic.roles.adapter.jdbc;

import com.atlas.gic.identity.application.PersonNotFoundException;
import com.atlas.gic.identity.domain.PersonId;
import com.atlas.gic.roles.application.AssignBusinessRoleCommand;
import com.atlas.gic.roles.application.AssignBusinessRoleUseCase;
import com.atlas.gic.roles.application.BusinessRoleAlreadyEndedException;
import com.atlas.gic.roles.application.DuplicateActiveBusinessRoleException;
import com.atlas.gic.roles.application.EndBusinessRoleCommand;
import com.atlas.gic.roles.application.EndBusinessRoleUseCase;
import com.atlas.gic.roles.application.GetBusinessRolesUseCase;
import com.atlas.gic.roles.domain.BusinessRoleAssignmentId;
import com.atlas.gic.roles.domain.BusinessRoleType;
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
class BusinessRoleAssignmentPersistenceIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final String APP_USER = "atlas_gic_roles_app";
    private static final String APP_PASSWORD = "atlas_gic_roles_app_password";
    private static TenantId tenantA;
    private static TenantId tenantB;
    private static PersonId personA;
    private static PersonId personB;

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

        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE ROLE %s LOGIN PASSWORD '%s'".formatted(APP_USER, APP_PASSWORD));
            statement.execute("GRANT USAGE ON SCHEMA gic TO %s".formatted(APP_USER));
            statement.execute("GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA gic TO %s".formatted(APP_USER));
            statement.executeUpdate("""
                    INSERT INTO gic.tenants (tenant_id, code, display_name)
                    VALUES ('%s', 'roles-a', 'Roles A'), ('%s', 'roles-b', 'Roles B')
                    """.formatted(tenantA, tenantB));
            statement.execute("SET atlas.platform_access = 'true'");
            insertPerson(statement, tenantA, personA, "Persona", "A");
            insertPerson(statement, tenantB, personB, "Persona", "B");
            statement.execute("RESET atlas.platform_access");
        }
    }

    @Test
    void assignPersistsMultipleDifferentRolesAndAuditTrail() {
        var dataSource = appDataSource();
        var jdbcTemplate = new JdbcTemplate(dataSource);
        var transactionTemplate = transactionTemplate(dataSource);
        var repository = new JdbcBusinessRoleAssignmentRepository(jdbcTemplate);
        var useCase = new AssignBusinessRoleUseCase(
                new FixedTenantContext(tenantA),
                repository,
                new JdbcBusinessRoleAssignedAudit(jdbcTemplate),
                () -> "roles-it");
        var uniquePerson = seedPerson(UUID.randomUUID(), tenantA, "Multiple", "Roles");

        var socio = transactionTemplate.execute(status -> useCase.assign(command(uniquePerson, BusinessRoleType.SOCIO)));
        var cliente = transactionTemplate.execute(status -> useCase.assign(command(uniquePerson, BusinessRoleType.CLIENTE)));

        assertThat(socio).isNotNull();
        assertThat(cliente).isNotNull();
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.queryForObject(
                    "SELECT set_config('atlas.current_tenant', ?, true)",
                    String.class,
                    tenantA.toString());
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT count(*)
                    FROM gic.business_role_assignment
                    WHERE person_id = ?
                    """,
                    Integer.class,
                    uniquePerson.value())).isEqualTo(2);
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT count(*)
                    FROM gic.business_role_assignment_audit
                    WHERE action = 'BUSINESS_ROLE_ASSIGNED' AND actor = 'roles-it'
                    """,
                    Integer.class)).isEqualTo(2);
        });
    }

    @Test
    void duplicateActiveRoleMapsToConflictException() {
        var dataSource = appDataSource();
        var jdbcTemplate = new JdbcTemplate(dataSource);
        var transactionTemplate = transactionTemplate(dataSource);
        var useCase = new AssignBusinessRoleUseCase(
                new FixedTenantContext(tenantA),
                new JdbcBusinessRoleAssignmentRepository(jdbcTemplate),
                new JdbcBusinessRoleAssignedAudit(jdbcTemplate),
                () -> "roles-it");
        var uniquePerson = seedPerson(UUID.randomUUID(), tenantA, "Duplicate", "Role");

        transactionTemplate.executeWithoutResult(status -> useCase.assign(command(uniquePerson, BusinessRoleType.PROVEEDOR)));

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(
                status -> useCase.assign(command(uniquePerson, BusinessRoleType.PROVEEDOR))))
                .isInstanceOf(DuplicateActiveBusinessRoleException.class);
    }

    @Test
    void crossTenantPersonIsNotFoundForAssignmentAndRead() {
        var dataSource = appDataSource();
        var jdbcTemplate = new JdbcTemplate(dataSource);
        var transactionTemplate = transactionTemplate(dataSource);
        var repository = new JdbcBusinessRoleAssignmentRepository(jdbcTemplate);
        var assignUseCase = new AssignBusinessRoleUseCase(
                new FixedTenantContext(tenantA),
                repository,
                new JdbcBusinessRoleAssignedAudit(jdbcTemplate),
                () -> "roles-it");
        var getUseCase = new GetBusinessRolesUseCase(new FixedTenantContext(tenantA), repository);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(
                status -> assignUseCase.assign(command(personB, BusinessRoleType.SOCIO))))
                .isInstanceOf(PersonNotFoundException.class);

        assertThatThrownBy(() -> transactionTemplate.execute(status -> getUseCase.get(personB)))
                .isInstanceOf(PersonNotFoundException.class);
    }

    @Test
    void getRolesReturnsOnlyCurrentTenantRolesInStableOrder() {
        var dataSource = appDataSource();
        var jdbcTemplate = new JdbcTemplate(dataSource);
        var transactionTemplate = transactionTemplate(dataSource);
        var repository = new JdbcBusinessRoleAssignmentRepository(jdbcTemplate);
        var assignUseCase = new AssignBusinessRoleUseCase(
                new FixedTenantContext(tenantA),
                repository,
                new JdbcBusinessRoleAssignedAudit(jdbcTemplate),
                () -> "roles-it");
        var getUseCase = new GetBusinessRolesUseCase(new FixedTenantContext(tenantA), repository);
        var uniquePerson = seedPerson(UUID.randomUUID(), tenantA, "Read", "Roles");

        transactionTemplate.executeWithoutResult(status -> assignUseCase.assign(new AssignBusinessRoleCommand(
                uniquePerson,
                BusinessRoleType.PROVEEDOR,
                LocalDate.parse("2026-08-27"),
                null,
                "corr-late")));
        transactionTemplate.executeWithoutResult(status -> assignUseCase.assign(new AssignBusinessRoleCommand(
                uniquePerson,
                BusinessRoleType.SOCIO,
                LocalDate.parse("2026-08-26"),
                null,
                "corr-early")));

        var roles = transactionTemplate.execute(status -> getUseCase.get(uniquePerson));

        assertThat(roles).isNotNull();
        assertThat(roles.items()).extracting(item -> item.role().name())
                .containsExactly("SOCIO", "PROVEEDOR");
    }

    @Test
    void endRolePersistsLifecycleMetadataAndAuditTrail() {
        var dataSource = appDataSource();
        var jdbcTemplate = new JdbcTemplate(dataSource);
        var transactionTemplate = transactionTemplate(dataSource);
        var repository = new JdbcBusinessRoleAssignmentRepository(jdbcTemplate);
        var assignUseCase = new AssignBusinessRoleUseCase(
                new FixedTenantContext(tenantA),
                repository,
                new JdbcBusinessRoleAssignedAudit(jdbcTemplate),
                () -> "roles-it");
        var endUseCase = new EndBusinessRoleUseCase(
                new FixedTenantContext(tenantA),
                repository,
                new JdbcBusinessRoleEndedAudit(jdbcTemplate),
                () -> "roles-it");
        var uniquePerson = seedPerson(UUID.randomUUID(), tenantA, "End", "Role");

        var assignment = transactionTemplate.execute(status -> assignUseCase.assign(command(uniquePerson, BusinessRoleType.SOCIO)));
        var ended = transactionTemplate.execute(status -> endUseCase.end(endCommand(uniquePerson, assignment.assignmentId())));

        assertThat(ended).isNotNull();
        assertThat(ended.status().name()).isEqualTo("ENDED");
        assertThat(ended.validTo()).isEqualTo(LocalDate.parse("2026-08-26"));

        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.queryForObject(
                    "SELECT set_config('atlas.current_tenant', ?, true)",
                    String.class,
                    tenantA.toString());
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT count(*)
                    FROM gic.business_role_assignment
                    WHERE assignment_id = ? AND status = 'ENDED' AND ended_by = 'roles-it'
                    """,
                    Integer.class,
                    assignment.assignmentId().value())).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT count(*)
                    FROM gic.business_role_assignment_audit
                    WHERE assignment_id = ? AND action = 'BUSINESS_ROLE_ENDED' AND actor = 'roles-it'
                    """,
                    Integer.class,
                    assignment.assignmentId().value())).isEqualTo(1);
        });
    }

    @Test
    void endingSameAssignmentTwiceMapsToConflict() {
        var dataSource = appDataSource();
        var jdbcTemplate = new JdbcTemplate(dataSource);
        var transactionTemplate = transactionTemplate(dataSource);
        var repository = new JdbcBusinessRoleAssignmentRepository(jdbcTemplate);
        var assignUseCase = new AssignBusinessRoleUseCase(
                new FixedTenantContext(tenantA),
                repository,
                new JdbcBusinessRoleAssignedAudit(jdbcTemplate),
                () -> "roles-it");
        var endUseCase = new EndBusinessRoleUseCase(
                new FixedTenantContext(tenantA),
                repository,
                new JdbcBusinessRoleEndedAudit(jdbcTemplate),
                () -> "roles-it");
        var uniquePerson = seedPerson(UUID.randomUUID(), tenantA, "End", "Twice");
        var assignment = transactionTemplate.execute(status -> assignUseCase.assign(command(uniquePerson, BusinessRoleType.CLIENTE)));

        transactionTemplate.executeWithoutResult(status -> endUseCase.end(endCommand(uniquePerson, assignment.assignmentId())));

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(
                status -> endUseCase.end(endCommand(uniquePerson, assignment.assignmentId()))))
                .isInstanceOf(BusinessRoleAlreadyEndedException.class);
    }

    @Test
    void atomicEndUpdateAllowsOnlyOneSuccessfulTransition() {
        var dataSource = appDataSource();
        var jdbcTemplate = new JdbcTemplate(dataSource);
        var transactionTemplate = transactionTemplate(dataSource);
        var repository = new JdbcBusinessRoleAssignmentRepository(jdbcTemplate);
        var assignUseCase = new AssignBusinessRoleUseCase(
                new FixedTenantContext(tenantA),
                repository,
                new JdbcBusinessRoleAssignedAudit(jdbcTemplate),
                () -> "roles-it");
        var uniquePerson = seedPerson(UUID.randomUUID(), tenantA, "Atomic", "End");
        var assignment = transactionTemplate.execute(status -> assignUseCase.assign(command(uniquePerson, BusinessRoleType.PROVEEDOR)));
        var loaded = transactionTemplate.execute(status -> repository
                .findById(tenantA, uniquePerson, assignment.assignmentId())
                .orElseThrow());
        var ended = loaded.end(LocalDate.parse("2026-08-26"));

        var first = transactionTemplate.execute(status ->
                repository.endActive(tenantA, uniquePerson, ended, "roles-it", "first"));
        var second = transactionTemplate.execute(status ->
                repository.endActive(tenantA, uniquePerson, ended, "roles-it", "second"));

        assertThat(first).isTrue();
        assertThat(second).isFalse();
    }

    @Test
    void crossTenantAssignmentIsNotFoundForLifecycle() {
        var dataSource = appDataSource();
        var jdbcTemplate = new JdbcTemplate(dataSource);
        var transactionTemplate = transactionTemplate(dataSource);
        var repository = new JdbcBusinessRoleAssignmentRepository(jdbcTemplate);
        var assignUseCaseB = new AssignBusinessRoleUseCase(
                new FixedTenantContext(tenantB),
                repository,
                new JdbcBusinessRoleAssignedAudit(jdbcTemplate),
                () -> "roles-it");
        var endUseCaseA = new EndBusinessRoleUseCase(
                new FixedTenantContext(tenantA),
                repository,
                new JdbcBusinessRoleEndedAudit(jdbcTemplate),
                () -> "roles-it");
        var assignmentB = transactionTemplate.execute(status -> assignUseCaseB.assign(command(personB, BusinessRoleType.SOCIO)));

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(
                status -> endUseCaseA.end(endCommand(personB, assignmentB.assignmentId()))))
                .isInstanceOf(PersonNotFoundException.class);
    }

    @Test
    void rlsDeniesRoleRowsWithoutTenantContextAndCrossTenantWrites() throws Exception {
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), APP_USER, APP_PASSWORD);
             var statement = connection.createStatement()) {
            try (var resultSet = statement.executeQuery("SELECT count(*) FROM gic.business_role_assignment")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isZero();
            }

            statement.execute("SET atlas.current_tenant = '%s'".formatted(tenantA));
            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO gic.business_role_assignment (
                        assignment_id, tenant_id, person_id, role_type, status, valid_from, created_by
                    )
                    VALUES ('%s', '%s', '%s', 'SOCIO', 'ACTIVE', '2026-08-26', 'rls-test')
                    """.formatted(UUID.randomUUID(), tenantB, personB)))
                    .hasMessageContaining("violates row-level security policy");

            var updated = statement.executeUpdate("""
                    UPDATE gic.business_role_assignment
                    SET status = 'ENDED', valid_to = '2026-08-26', ended_at = now(), ended_by = 'rls-test'
                    WHERE tenant_id = '%s'
                    """.formatted(tenantB));
            assertThat(updated).isZero();
        }
    }

    private static AssignBusinessRoleCommand command(PersonId personId, BusinessRoleType role) {
        return new AssignBusinessRoleCommand(
                personId,
                role,
                LocalDate.parse("2026-08-26"),
                null,
                "corr-role-it");
    }

    private static EndBusinessRoleCommand endCommand(PersonId personId, BusinessRoleAssignmentId assignmentId) {
        return new EndBusinessRoleCommand(
                personId,
                assignmentId,
                LocalDate.parse("2026-08-26"),
                "completed lifecycle",
                "corr-end-it");
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
}
