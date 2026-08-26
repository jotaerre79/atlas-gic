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

    @BeforeAll
    static void migrateAndSeed() throws Exception {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        tenantId = TenantId.of(UUID.randomUUID());

        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE ROLE %s LOGIN PASSWORD '%s'".formatted(APP_USER, APP_PASSWORD));
            statement.execute("GRANT USAGE ON SCHEMA gic TO %s".formatted(APP_USER));
            statement.execute("GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA gic TO %s".formatted(APP_USER));
            statement.executeUpdate("""
                    INSERT INTO gic.tenants (tenant_id, code, display_name)
                    VALUES ('%s', 'person-it', 'Person IT')
                    """.formatted(tenantId));
        }
    }

    @Test
    void registerPersonPersistsPersonIdentifierAndAudit() {
        var dataSource = appDataSource();
        var jdbcTemplate = new JdbcTemplate(dataSource);
        var transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        var useCase = new RegisterPersonUseCase(
                new FixedTenantContext(tenantId),
                new JdbcPersonRepository(jdbcTemplate),
                new JdbcPersonRegistrationAudit(jdbcTemplate),
                () -> "persistence-it");

        var result = transactionTemplate.execute(status -> useCase.register(validCommand("1234567")));

        assertThat(result).isNotNull();
        assertThat(result.displayName()).isEqualTo("Juan Perez");

        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.queryForObject(
                    "SELECT set_config('atlas.current_tenant', ?, true)",
                    String.class,
                    tenantId.toString());
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM gic.person", Integer.class)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM gic.person_identifier", Integer.class)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT count(*) FROM gic.person_audit
                    WHERE action = 'PERSON_REGISTERED' AND actor = 'persistence-it'
                    """, Integer.class)).isEqualTo(1);
        });
    }

    @Test
    void duplicateIdentifierMapsToConflictException() {
        var dataSource = appDataSource();
        var jdbcTemplate = new JdbcTemplate(dataSource);
        var transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        var useCase = new RegisterPersonUseCase(
                new FixedTenantContext(tenantId),
                new JdbcPersonRepository(jdbcTemplate),
                new JdbcPersonRegistrationAudit(jdbcTemplate),
                () -> "persistence-it");

        transactionTemplate.executeWithoutResult(status -> useCase.register(validCommand("9999999")));

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> useCase.register(validCommand("9999999"))))
                .isInstanceOf(DuplicatePersonIdentifierException.class);
    }

    private static DriverManagerDataSource appDataSource() {
        var dataSource = new DriverManagerDataSource();
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUsername(APP_USER);
        dataSource.setPassword(APP_PASSWORD);
        return dataSource;
    }

    private RegisterPersonCommand validCommand(String identifierValue) {
        return new RegisterPersonCommand(
                "Juan",
                null,
                "Perez",
                new RegisterPersonCommand.IdentifierCommand("CI", identifierValue, "PY"),
                "corr-persistence");
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
