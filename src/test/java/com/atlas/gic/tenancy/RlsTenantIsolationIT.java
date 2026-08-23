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

    @BeforeAll
    static void migrateAndSeed() throws Exception {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();

        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO gic.tenants (tenant_id, code, display_name)
                    VALUES ('%s', 'tenant-a', 'Tenant A'), ('%s', 'tenant-b', 'Tenant B')
                    """.formatted(tenantA, tenantB));
            statement.execute("SET atlas.platform_access = 'true'");
            statement.executeUpdate("""
                    INSERT INTO gic.tenant_isolation_probe (tenant_id, label)
                    VALUES ('%s', 'A only'), ('%s', 'B only')
                    """.formatted(tenantA, tenantB));
            statement.execute("RESET atlas.platform_access");
        }
    }

    @Test
    void tenantCannotReadAnotherTenantRows() throws Exception {
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
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
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO gic.tenant_isolation_probe (tenant_id, label)
                    VALUES ('%s', 'missing context')
                    """.formatted(tenantA)))
                    .hasMessageContaining("violates row-level security policy");
        }
    }

    @Test
    void platformAccessIsExplicitAndAuditable() throws Exception {
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
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
}
