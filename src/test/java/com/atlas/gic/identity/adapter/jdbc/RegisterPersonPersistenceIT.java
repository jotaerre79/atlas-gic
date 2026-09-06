package com.atlas.gic.identity.adapter.jdbc;

import com.atlas.gic.identity.application.DuplicatePersonIdentifierException;
import com.atlas.gic.identity.application.RegisterPersonCommand;
import com.atlas.gic.identity.application.RegisterPersonUseCase;
import com.atlas.gic.shared.tenancy.application.TenantContext;
import com.atlas.gic.shared.tenancy.domain.TenantId;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class RegisterPersonPersistenceIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final String APP_USER = "atlas_gic_person_app";
    private static final String APP_PASSWORD = "atlas_gic_person_app_password";
    private static TenantId tenantId;
    private static TenantId otherTenantId;

    @BeforeAll
    static void migrateAndSeed() throws Exception {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        tenantId = TenantId.of(UUID.randomUUID());
        otherTenantId = TenantId.of(UUID.randomUUID());

        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE ROLE %s LOGIN PASSWORD '%s'".formatted(APP_USER, APP_PASSWORD));
            statement.execute("GRANT USAGE ON SCHEMA gic TO %s".formatted(APP_USER));
            statement.execute("GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA gic TO %s".formatted(APP_USER));
            statement.executeUpdate("""
                    INSERT INTO gic.tenants (tenant_id, code, display_name)
                    VALUES ('%s', 'person-it', 'Person IT')
                    """.formatted(tenantId));
            statement.executeUpdate("""
                    INSERT INTO gic.tenants (tenant_id, code, display_name)
                    VALUES ('%s', 'person-other-it', 'Person Other IT')
                    """.formatted(otherTenantId));
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
    void registerPersonPersistsPersonIdentifierAndAudit() {
        var dataSource = appDataSource();
        var jdbcTemplate = new JdbcTemplate(dataSource);
        var transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        var identifier = uniqueIdentifier();
        var correlationId = "corr-" + identifier;
        var useCase = new RegisterPersonUseCase(
                new FixedTenantContext(tenantId),
                new JdbcPersonRepository(jdbcTemplate),
                new JdbcPersonRegistrationAudit(jdbcTemplate),
                () -> "persistence-it");

        var result = transactionTemplate.execute(status -> useCase.register(validCommand(identifier, correlationId)));

        assertThat(result).isNotNull();
        assertThat(result.displayName()).isEqualTo("Juan Perez");

        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.queryForObject(
                    "SELECT set_config('atlas.current_tenant', ?, true)",
                    String.class,
                    tenantId.toString());
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM gic.person WHERE person_id = ? AND tenant_id = ?",
                    Integer.class,
                    result.personId().value(),
                    tenantId.value())).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT count(*) FROM gic.person_identifier
                    WHERE person_id = ? AND tenant_id = ? AND normalized_identifier_value = ?
                    """,
                    Integer.class,
                    result.personId().value(),
                    tenantId.value(),
                    normalizedIdentifier(identifier))).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT count(*) FROM gic.person_audit
                    WHERE action = 'PERSON_REGISTERED'
                      AND actor = 'persistence-it'
                      AND tenant_id = ?
                      AND person_id = ?
                      AND correlation_id = ?
                    """,
                    Integer.class,
                    tenantId.value(),
                    result.personId().value(),
                    correlationId)).isEqualTo(1);
        });
    }

    @Test
    void personAuditIsVisibleOnlyForOwningTenantContext() {
        var dataSource = appDataSource();
        var jdbcTemplate = new JdbcTemplate(dataSource);
        var transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        var identifier = uniqueIdentifier();
        var correlationId = "corr-" + identifier;
        var useCase = new RegisterPersonUseCase(
                new FixedTenantContext(tenantId),
                new JdbcPersonRepository(jdbcTemplate),
                new JdbcPersonRegistrationAudit(jdbcTemplate),
                () -> "persistence-it");

        var result = transactionTemplate.execute(status -> useCase.register(validCommand(identifier, correlationId)));

        assertThat(result).isNotNull();
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.queryForObject(
                    "SELECT set_config('atlas.current_tenant', ?, true)",
                    String.class,
                    tenantId.toString());
            assertThat(auditRowsFor(jdbcTemplate, result.personId().value(), correlationId)).isEqualTo(1);
        });
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.queryForObject(
                    "SELECT set_config('atlas.current_tenant', ?, true)",
                    String.class,
                    otherTenantId.toString());
            assertThat(auditRowsFor(jdbcTemplate, result.personId().value(), correlationId)).isZero();
        });
        transactionTemplate.executeWithoutResult(status ->
                assertThat(auditRowsFor(jdbcTemplate, result.personId().value(), correlationId)).isZero());
    }

    @Test
    void duplicateIdentifierMapsToConflictException() {
        var dataSource = appDataSource();
        var jdbcTemplate = new JdbcTemplate(dataSource);
        var transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        var identifier = uniqueIdentifier();
        var useCase = new RegisterPersonUseCase(
                new FixedTenantContext(tenantId),
                new JdbcPersonRepository(jdbcTemplate),
                new JdbcPersonRegistrationAudit(jdbcTemplate),
                () -> "persistence-it");

        transactionTemplate.executeWithoutResult(status -> useCase.register(validCommand(identifier, "corr-" + identifier)));

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(
                status -> useCase.register(validCommand(identifier, "corr-duplicate-" + identifier))))
                .isInstanceOf(DuplicatePersonIdentifierException.class);
    }

    private static DriverManagerDataSource appDataSource() {
        var dataSource = new DriverManagerDataSource();
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUsername(APP_USER);
        dataSource.setPassword(APP_PASSWORD);
        return dataSource;
    }

    private int auditRowsFor(JdbcTemplate jdbcTemplate, UUID personId, String correlationId) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*) FROM gic.person_audit
                WHERE action = 'PERSON_REGISTERED'
                  AND actor = 'persistence-it'
                  AND person_id = ?
                  AND correlation_id = ?
                """,
                Integer.class,
                personId,
                correlationId);
    }

    private String uniqueIdentifier() {
        return UUID.randomUUID().toString();
    }

    private String normalizedIdentifier(String identifier) {
        return identifier.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }

    private RegisterPersonCommand validCommand(String identifierValue, String correlationId) {
        return new RegisterPersonCommand(
                "Juan",
                null,
                "Perez",
                new RegisterPersonCommand.IdentifierCommand("CI", identifierValue, "PY"),
                correlationId);
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
