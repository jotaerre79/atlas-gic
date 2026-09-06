package com.atlas.gic.identity.adapter.jdbc;

import com.atlas.gic.identity.application.DuplicateOrganizationIdentifierException;
import com.atlas.gic.identity.application.OrganizationRegisteredAuditEntry;
import com.atlas.gic.identity.application.OrganizationRegistrationAudit;
import com.atlas.gic.identity.application.RegisterOrganizationCommand;
import com.atlas.gic.identity.application.RegisterOrganizationUseCase;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class RegisterOrganizationPersistenceIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final String APP_USER = "atlas_gic_organization_app";
    private static final String APP_PASSWORD = "atlas_gic_organization_app_password";
    private static TenantId tenantA;
    private static TenantId tenantB;

    @BeforeAll
    static void migrateAndSeed() throws Exception {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        tenantA = TenantId.of(UUID.randomUUID());
        tenantB = TenantId.of(UUID.randomUUID());

        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE ROLE %s LOGIN PASSWORD '%s'".formatted(APP_USER, APP_PASSWORD));
            statement.execute("GRANT USAGE ON SCHEMA gic TO %s".formatted(APP_USER));
            statement.execute("GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA gic TO %s".formatted(APP_USER));
            statement.executeUpdate("""
                    INSERT INTO gic.tenants (tenant_id, code, display_name)
                    VALUES ('%s', 'organization-a', 'Organization A'), ('%s', 'organization-b', 'Organization B')
                    """.formatted(tenantA, tenantB));
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
    void registerOrganizationPersistsOrganizationIdentifierAndAudit() {
        var dataSource = appDataSource();
        var jdbcTemplate = new JdbcTemplate(dataSource);
        var transactionTemplate = transactionTemplate(dataSource);
        var identifier = uniqueRuc();
        var correlationId = "corr-org-" + identifier;
        var useCase = useCase(tenantA, jdbcTemplate);

        var result = transactionTemplate.execute(status -> useCase.register(validCommand(identifier, correlationId)));

        assertThat(result).isNotNull();
        assertThat(result.legalName()).isEqualTo("Atlas Cooperativa");
        assertThat(result.identifier().maskedValue()).endsWith(identifier.substring(identifier.length() - 4));

        transactionTemplate.executeWithoutResult(status -> {
            setCurrentTenant(jdbcTemplate, tenantA);
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT count(*)
                    FROM gic.organization
                    WHERE organization_id = ? AND tenant_id = ? AND status = 'ACTIVE'
                    """,
                    Integer.class,
                    result.organizationId().value(),
                    tenantA.value())).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT count(*)
                    FROM gic.organization_identifier
                    WHERE organization_id = ?
                      AND tenant_id = ?
                      AND identifier_type = 'RUC'
                      AND country_code = 'PY'
                      AND normalized_identifier_value = ?
                    """,
                    Integer.class,
                    result.organizationId().value(),
                    tenantA.value(),
                    normalizedIdentifier(identifier))).isEqualTo(1);
            assertThat(auditRowsFor(jdbcTemplate, result.organizationId().value(), correlationId)).isEqualTo(1);
        });
    }

    @Test
    void organizationAuditIsVisibleOnlyForOwningTenantContext() {
        var dataSource = appDataSource();
        var jdbcTemplate = new JdbcTemplate(dataSource);
        var transactionTemplate = transactionTemplate(dataSource);
        var identifier = uniqueRuc();
        var correlationId = "corr-org-" + identifier;
        var useCase = useCase(tenantA, jdbcTemplate);

        var result = transactionTemplate.execute(status -> useCase.register(validCommand(identifier, correlationId)));

        assertThat(result).isNotNull();
        transactionTemplate.executeWithoutResult(status -> {
            setCurrentTenant(jdbcTemplate, tenantA);
            assertThat(auditRowsFor(jdbcTemplate, result.organizationId().value(), correlationId)).isEqualTo(1);
        });
        transactionTemplate.executeWithoutResult(status -> {
            setCurrentTenant(jdbcTemplate, tenantB);
            assertThat(auditRowsFor(jdbcTemplate, result.organizationId().value(), correlationId)).isZero();
        });
        transactionTemplate.executeWithoutResult(status ->
                assertThat(auditRowsFor(jdbcTemplate, result.organizationId().value(), correlationId)).isZero());
    }

    @Test
    void duplicateIdentifierInSameTenantMapsToConflictException() {
        var dataSource = appDataSource();
        var jdbcTemplate = new JdbcTemplate(dataSource);
        var transactionTemplate = transactionTemplate(dataSource);
        var identifier = uniqueRuc();
        var useCase = useCase(tenantA, jdbcTemplate);

        transactionTemplate.executeWithoutResult(
                status -> useCase.register(validCommand(identifier, "corr-org-" + identifier)));

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(
                status -> useCase.register(validCommand(identifier, "corr-org-duplicate-" + identifier))))
                .isInstanceOf(DuplicateOrganizationIdentifierException.class);

        transactionTemplate.executeWithoutResult(status -> {
            setCurrentTenant(jdbcTemplate, tenantA);
            assertThat(organizationRowsForIdentifier(jdbcTemplate, identifier)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT count(*)
                    FROM gic.organization_audit
                    WHERE correlation_id = ?
                    """,
                    Integer.class,
                    "corr-org-duplicate-" + identifier)).isZero();
        });
    }

    @Test
    void auditFailureRollsBackOrganizationAndIdentifier() {
        var dataSource = appDataSource();
        var jdbcTemplate = new JdbcTemplate(dataSource);
        var transactionTemplate = transactionTemplate(dataSource);
        var identifier = uniqueRuc();
        var useCase = new RegisterOrganizationUseCase(
                new FixedTenantContext(tenantA),
                new JdbcOrganizationRepository(jdbcTemplate),
                new FailingOrganizationRegistrationAudit(),
                () -> "organization-it");

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(
                status -> useCase.register(validCommand(identifier, "corr-org-failing-audit-" + identifier))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("audit unavailable");

        transactionTemplate.executeWithoutResult(status -> {
            setCurrentTenant(jdbcTemplate, tenantA);
            assertThat(organizationRowsForIdentifier(jdbcTemplate, identifier)).isZero();
        });
    }

    @Test
    void sameIdentifierCanExistInDifferentTenants() {
        var dataSource = appDataSource();
        var jdbcTemplate = new JdbcTemplate(dataSource);
        var transactionTemplate = transactionTemplate(dataSource);
        var identifier = uniqueRuc();
        var tenantAUseCase = useCase(tenantA, jdbcTemplate);
        var tenantBUseCase = useCase(tenantB, jdbcTemplate);

        var orgA = transactionTemplate.execute(
                status -> tenantAUseCase.register(validCommand(identifier, "corr-org-a-" + identifier)));
        var orgB = transactionTemplate.execute(
                status -> tenantBUseCase.register(validCommand(identifier, "corr-org-b-" + identifier)));

        assertThat(orgA).isNotNull();
        assertThat(orgB).isNotNull();
        assertThat(orgA.organizationId()).isNotEqualTo(orgB.organizationId());
        transactionTemplate.executeWithoutResult(status -> {
            setCurrentTenant(jdbcTemplate, tenantA);
            assertThat(organizationRowsForIdentifier(jdbcTemplate, identifier)).isEqualTo(1);
        });
        transactionTemplate.executeWithoutResult(status -> {
            setCurrentTenant(jdbcTemplate, tenantB);
            assertThat(organizationRowsForIdentifier(jdbcTemplate, identifier)).isEqualTo(1);
        });
    }

    @Test
    void rlsDeniesOrganizationRowsWithoutTenantContextAndCrossTenantWrites() throws Exception {
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), APP_USER, APP_PASSWORD);
             var statement = connection.createStatement()) {
            try (var resultSet = statement.executeQuery("SELECT count(*) FROM gic.organization")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isZero();
            }

            statement.execute("SET atlas.current_tenant = '%s'".formatted(tenantA));
            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO gic.organization (
                        organization_id, tenant_id, legal_name, status
                    )
                    VALUES ('%s', '%s', 'Cross Tenant SA', 'ACTIVE')
                    """.formatted(UUID.randomUUID(), tenantB)))
                    .hasMessageContaining("violates row-level security policy");
        }
    }

    private static RegisterOrganizationUseCase useCase(TenantId tenantId, JdbcTemplate jdbcTemplate) {
        return new RegisterOrganizationUseCase(
                new FixedTenantContext(tenantId),
                new JdbcOrganizationRepository(jdbcTemplate),
                new JdbcOrganizationRegistrationAudit(jdbcTemplate),
                () -> "organization-it");
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

    private int auditRowsFor(JdbcTemplate jdbcTemplate, UUID organizationId, String correlationId) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM gic.organization_audit
                WHERE action = 'ORGANIZATION_REGISTERED'
                  AND actor = 'organization-it'
                  AND organization_id = ?
                  AND correlation_id = ?
                """,
                Integer.class,
                organizationId,
                correlationId);
    }

    private int organizationRowsForIdentifier(JdbcTemplate jdbcTemplate, String identifier) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM gic.organization_identifier
                WHERE identifier_type = 'RUC'
                  AND country_code = 'PY'
                  AND normalized_identifier_value = ?
                """,
                Integer.class,
                normalizedIdentifier(identifier));
    }

    private String uniqueRuc() {
        var digits = (UUID.randomUUID().toString() + UUID.randomUUID()).replaceAll("[^0-9]", "");
        return "80" + digits.substring(0, 12);
    }

    private String normalizedIdentifier(String identifier) {
        return identifier.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }

    private RegisterOrganizationCommand validCommand(String identifierValue, String correlationId) {
        return new RegisterOrganizationCommand(
                "Atlas Cooperativa",
                "Atlas",
                new RegisterOrganizationCommand.IdentifierCommand("RUC", identifierValue, "PY"),
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

    private static class FailingOrganizationRegistrationAudit implements OrganizationRegistrationAudit {

        @Override
        public void record(OrganizationRegisteredAuditEntry entry) {
            throw new IllegalStateException("audit unavailable");
        }
    }
}
