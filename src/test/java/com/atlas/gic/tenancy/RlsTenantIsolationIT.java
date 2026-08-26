package com.atlas.gic.tenancy;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class RlsTenantIsolationIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    private static UUID tenantA;
    private static UUID tenantB;
    private static UUID personB;
    private static final String APP_USER = "atlas_gic_app";
    private static final String APP_PASSWORD = "atlas_gic_app_password";

    @BeforeAll
    static void migrateAndSeed() throws Exception {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        personB = UUID.randomUUID();

        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE ROLE %s LOGIN PASSWORD '%s'".formatted(APP_USER, APP_PASSWORD));
            statement.execute("GRANT USAGE ON SCHEMA gic TO %s".formatted(APP_USER));
            statement.execute("GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA gic TO %s".formatted(APP_USER));
            statement.executeUpdate("""
                    INSERT INTO gic.tenants (tenant_id, code, display_name)
                    VALUES ('%s', 'tenant-a', 'Tenant A'), ('%s', 'tenant-b', 'Tenant B')
                    """.formatted(tenantA, tenantB));
            statement.execute("SET atlas.platform_access = 'true'");
            statement.executeUpdate("""
                    INSERT INTO gic.tenant_isolation_probe (tenant_id, label)
                    VALUES ('%s', 'A only'), ('%s', 'B only')
                    """.formatted(tenantA, tenantB));
            statement.executeUpdate("""
                    INSERT INTO gic.person (
                        person_id, tenant_id, given_name, family_name, display_name, status
                    )
                    VALUES ('%s', '%s', 'Tenant', 'B', 'Tenant B', 'ACTIVE')
                    """.formatted(personB, tenantB));
            statement.executeUpdate("""
                    INSERT INTO gic.person_identifier (
                        person_id, tenant_id, identifier_type, identifier_value, normalized_identifier_value, issuer
                    )
                    VALUES ('%s', '%s', 'CI', 'B-123', 'B123', 'PY')
                    """.formatted(personB, tenantB));
            statement.execute("RESET atlas.platform_access");
        }
    }

    @Test
    void tenantCannotReadAnotherTenantRows() throws Exception {
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), APP_USER, APP_PASSWORD);
             var statement = connection.createStatement()) {
            statement.execute("SET atlas.current_tenant = '%s'".formatted(tenantA));

            try (var resultSet = statement.executeQuery("SELECT label FROM gic.tenant_isolation_probe ORDER BY label")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("label")).isEqualTo("A only");
                assertThat(resultSet.next()).isFalse();
            }
        }
    }

    @Test
    void missingTenantContextDeniesTenantScopedWrites() throws Exception {
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), APP_USER, APP_PASSWORD);
             var statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO gic.tenant_isolation_probe (tenant_id, label)
                    VALUES ('%s', 'missing context')
                    """.formatted(tenantA)))
                    .hasMessageContaining("violates row-level security policy");
        }
    }

    @Test
    void tenantCannotWriteAnotherTenantRows() throws Exception {
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), APP_USER, APP_PASSWORD);
             var statement = connection.createStatement()) {
            statement.execute("SET atlas.current_tenant = '%s'".formatted(tenantA));

            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO gic.tenant_isolation_probe (tenant_id, label)
                    VALUES ('%s', 'cross tenant write')
                    """.formatted(tenantB)))
                    .hasMessageContaining("violates row-level security policy");
        }
    }

    @Test
    void platformAccessIsExplicitAndAuditable() throws Exception {
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), APP_USER, APP_PASSWORD);
             var statement = connection.createStatement()) {
            statement.execute("SET atlas.platform_access = 'true'");
            statement.executeUpdate("""
                    INSERT INTO gic.platform_access_audit (actor, reason)
                    VALUES ('foundation-test', 'validate explicit platform access path')
                    """);

            try (var resultSet = statement.executeQuery("SELECT count(*) FROM gic.tenant_isolation_probe")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isEqualTo(2);
            }
        }
    }

    @Test
    void tenantCanInsertAndReadOwnPerson() throws Exception {
        var personA = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), APP_USER, APP_PASSWORD);
             var statement = connection.createStatement()) {
            statement.execute("SET atlas.current_tenant = '%s'".formatted(tenantA));
            statement.executeUpdate("""
                    INSERT INTO gic.person (
                        person_id, tenant_id, given_name, family_name, display_name, status
                    )
                    VALUES ('%s', '%s', 'Tenant', 'A', 'Tenant A', 'ACTIVE')
                    """.formatted(personA, tenantA));
            statement.executeUpdate("""
                    INSERT INTO gic.person_identifier (
                        person_id, tenant_id, identifier_type, identifier_value, normalized_identifier_value, issuer
                    )
                    VALUES ('%s', '%s', 'CI', 'A-123', 'A123', 'PY')
                    """.formatted(personA, tenantA));

            try (var resultSet = statement.executeQuery("SELECT display_name FROM gic.person WHERE person_id = '%s'".formatted(personA))) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("display_name")).isEqualTo("Tenant A");
            }
        }
    }

    @Test
    void tenantCannotReadAnotherTenantPerson() throws Exception {
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), APP_USER, APP_PASSWORD);
             var statement = connection.createStatement()) {
            statement.execute("SET atlas.current_tenant = '%s'".formatted(tenantA));

            try (var resultSet = statement.executeQuery("SELECT count(*) FROM gic.person WHERE person_id = '%s'".formatted(personB))) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isZero();
            }
        }
    }

    @Test
    void tenantCannotUpdateAnotherTenantPerson() throws Exception {
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), APP_USER, APP_PASSWORD);
             var statement = connection.createStatement()) {
            statement.execute("SET atlas.current_tenant = '%s'".formatted(tenantA));

            var updated = statement.executeUpdate("""
                    UPDATE gic.person
                    SET family_name = 'Changed'
                    WHERE person_id = '%s'
                    """.formatted(personB));

            assertThat(updated).isZero();
        }
    }

    @Test
    void missingTenantContextDeniesPersonWrites() throws Exception {
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), APP_USER, APP_PASSWORD);
             var statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO gic.person (
                        person_id, tenant_id, given_name, family_name, display_name, status
                    )
                    VALUES ('%s', '%s', 'Missing', 'Tenant', 'Missing Tenant', 'ACTIVE')
                    """.formatted(UUID.randomUUID(), tenantA)))
                    .hasMessageContaining("violates row-level security policy");
        }
    }
}
