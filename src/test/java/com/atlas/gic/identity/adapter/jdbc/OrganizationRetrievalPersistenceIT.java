package com.atlas.gic.identity.adapter.jdbc;

import com.atlas.gic.identity.domain.OrganizationId;
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
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class OrganizationRetrievalPersistenceIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final String APP_USER = "atlas_gic_organization_read_app";
    private static final String APP_PASSWORD = "atlas_gic_organization_read_app_password";
    private static TenantId tenantA;
    private static TenantId tenantB;
    private static OrganizationId atlasA;
    private static OrganizationId betaA;
    private static OrganizationId crossTenant;
    private static OrganizationId duplicateRucA;
    private static OrganizationId duplicateRucB;
    private static OrganizationId wildcardA;

    @BeforeAll
    static void migrateAndSeed() throws Exception {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        tenantA = TenantId.of(UUID.randomUUID());
        tenantB = TenantId.of(UUID.randomUUID());
        atlasA = OrganizationId.of(UUID.randomUUID());
        betaA = OrganizationId.of(UUID.randomUUID());
        crossTenant = OrganizationId.of(UUID.randomUUID());
        duplicateRucA = OrganizationId.of(UUID.randomUUID());
        duplicateRucB = OrganizationId.of(UUID.randomUUID());
        wildcardA = OrganizationId.of(UUID.randomUUID());

        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE ROLE %s LOGIN PASSWORD '%s'".formatted(APP_USER, APP_PASSWORD));
            statement.execute("GRANT USAGE ON SCHEMA gic TO %s".formatted(APP_USER));
            statement.execute("GRANT SELECT ON ALL TABLES IN SCHEMA gic TO %s".formatted(APP_USER));
            statement.executeUpdate("""
                    INSERT INTO gic.tenants (tenant_id, code, display_name)
                    VALUES ('%s', 'org-read-a', 'Organization Read A'), ('%s', 'org-read-b', 'Organization Read B')
                    """.formatted(tenantA, tenantB));
        }

        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            insertOrganization(connection, tenantA, atlasA, "Atlas Cooperativa", "Atlas", "80012345-6", "800123456");
            insertOrganization(connection, tenantA, betaA, "Beta Servicios", "BETA Trade", "80098765-4", "800987654");
            insertOrganization(connection, tenantB, crossTenant, "Cross Tenant SA", "Cross", "90011122-3", "900111223");
            insertOrganization(connection, tenantA, duplicateRucA, "Duplicada A", "Dup A", "70000000-1", "700000001");
            insertOrganization(connection, tenantB, duplicateRucB, "Duplicada B", "Dup B", "70000000-1", "700000001");
            insertOrganization(connection, tenantA, wildcardA, "100%_Literal\\Company", null, "81000000-2", "810000002");
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
    void getOrganizationFindsCurrentTenantOrganizationWithMaskedIdentifier() {
        var dataSource = appDataSource();
        var repository = repository(dataSource);

        var result = transactionTemplate(dataSource).execute(status -> repository.findById(tenantA, atlasA));

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().legalName()).isEqualTo("Atlas Cooperativa");
        assertThat(result.orElseThrow().tradeName()).isEqualTo("Atlas");
        assertThat(result.orElseThrow().identifiers()).singleElement()
                .satisfies(identifier -> {
                    assertThat(identifier.type()).isEqualTo("RUC");
                    assertThat(identifier.countryCode()).isEqualTo("PY");
                    assertThat(identifier.maskedValue()).isEqualTo("****3456");
                    assertThat(identifier.maskedValue()).doesNotContain("800123456");
                });
    }

    @Test
    void getOrganizationDoesNotExposeAnotherTenantOrganization() {
        var dataSource = appDataSource();
        var repository = repository(dataSource);

        var result = transactionTemplate(dataSource).execute(status -> repository.findById(tenantA, crossTenant));

        assertThat(result).isEmpty();
    }

    @Test
    void rlsDeniesReadsWithoutTenantContext() {
        var jdbcTemplate = new JdbcTemplate(appDataSource());

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM gic.organization", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM gic.organization_identifier", Integer.class)).isZero();
    }

    @Test
    void searchFindsByLegalNameAndTradeNameInsideCurrentTenantOnly() {
        var dataSource = appDataSource();
        var repository = repository(dataSource);
        var transactionTemplate = transactionTemplate(dataSource);

        var byLegalName = transactionTemplate.execute(status -> repository.search(tenantA, "atlas", 0, 20));
        var byTradeName = transactionTemplate.execute(status -> repository.search(tenantA, "beta trade", 0, 20));
        var crossTenantQuery = transactionTemplate.execute(status -> repository.search(tenantA, "cross tenant", 0, 20));

        assertThat(byLegalName).isNotNull();
        assertThat(byLegalName.items()).singleElement()
                .satisfies(item -> assertThat(item.organizationId()).isEqualTo(atlasA.value()));
        assertThat(byTradeName).isNotNull();
        assertThat(byTradeName.items()).singleElement()
                .satisfies(item -> assertThat(item.organizationId()).isEqualTo(betaA.value()));
        assertThat(crossTenantQuery).isNotNull();
        assertThat(crossTenantQuery.items()).isEmpty();
    }

    @Test
    void searchFindsByRucWithAndWithoutSeparatorsAndDoesNotExposeIdentifierValueInList() {
        var dataSource = appDataSource();
        var repository = repository(dataSource);
        var transactionTemplate = transactionTemplate(dataSource);

        var withSeparators = transactionTemplate.execute(status -> repository.search(tenantA, "80012345-6", 0, 20));
        var withoutSeparators = transactionTemplate.execute(status -> repository.search(tenantA, "800123456", 0, 20));

        assertThat(withSeparators).isNotNull();
        assertThat(withSeparators.items()).singleElement().satisfies(item -> {
            assertThat(item.organizationId()).isEqualTo(atlasA.value());
            assertThat(item.identifierType()).isEqualTo("RUC");
            assertThat(item.identifierCountryCode()).isEqualTo("PY");
        });
        assertThat(withoutSeparators).isNotNull();
        assertThat(withoutSeparators.items()).singleElement()
                .satisfies(item -> assertThat(item.organizationId()).isEqualTo(atlasA.value()));
    }

    @Test
    void sameRucInTwoTenantsReturnsOnlyCurrentTenantOrganization() {
        var dataSource = appDataSource();
        var repository = repository(dataSource);

        var tenantAResult = transactionTemplate(dataSource).execute(status -> repository.search(tenantA, "70000000-1", 0, 20));
        var tenantBResult = transactionTemplate(dataSource).execute(status -> repository.search(tenantB, "70000000-1", 0, 20));

        assertThat(tenantAResult).isNotNull();
        assertThat(tenantAResult.items()).singleElement()
                .satisfies(item -> assertThat(item.organizationId()).isEqualTo(duplicateRucA.value()));
        assertThat(tenantBResult).isNotNull();
        assertThat(tenantBResult.items()).singleElement()
                .satisfies(item -> assertThat(item.organizationId()).isEqualTo(duplicateRucB.value()));
    }

    @Test
    void searchUsesStableOrderPaginationAndKeepsTotalForOutOfRangePage() {
        var dataSource = appDataSource();
        var repository = repository(dataSource);
        var transactionTemplate = transactionTemplate(dataSource);

        var firstPage = transactionTemplate.execute(status -> repository.search(tenantA, null, 0, 1));
        var secondPage = transactionTemplate.execute(status -> repository.search(tenantA, null, 1, 1));
        var outOfRange = transactionTemplate.execute(status -> repository.search(tenantA, null, 20, 10));

        assertThat(firstPage).isNotNull();
        assertThat(firstPage.total()).isEqualTo(4);
        assertThat(firstPage.items()).singleElement()
                .satisfies(item -> assertThat(item.legalName()).isEqualTo("100%_Literal\\Company"));
        assertThat(secondPage).isNotNull();
        assertThat(secondPage.items()).singleElement()
                .satisfies(item -> assertThat(item.legalName()).isEqualTo("Atlas Cooperativa"));
        assertThat(outOfRange).isNotNull();
        assertThat(outOfRange.total()).isEqualTo(4);
        assertThat(outOfRange.items()).isEmpty();
    }

    @Test
    void searchTreatsPercentUnderscoreAndEscapeAsLiteralText() {
        var dataSource = appDataSource();
        var repository = repository(dataSource);
        var transactionTemplate = transactionTemplate(dataSource);

        var byPercentUnderscore = transactionTemplate.execute(status -> repository.search(tenantA, "100%_", 0, 20));
        var byEscape = transactionTemplate.execute(status -> repository.search(tenantA, "Literal\\Company", 0, 20));

        assertThat(byPercentUnderscore).isNotNull();
        assertThat(byPercentUnderscore.items()).singleElement()
                .satisfies(item -> assertThat(item.organizationId()).isEqualTo(wildcardA.value()));
        assertThat(byEscape).isNotNull();
        assertThat(byEscape.items()).singleElement()
                .satisfies(item -> assertThat(item.organizationId()).isEqualTo(wildcardA.value()));
    }

    private static void insertOrganization(
            java.sql.Connection connection,
            TenantId tenantId,
            OrganizationId organizationId,
            String legalName,
            String tradeName,
            String identifierValue,
            String normalizedIdentifierValue) throws SQLException {
        try (PreparedStatement organization = connection.prepareStatement("""
                INSERT INTO gic.organization (
                    organization_id, tenant_id, legal_name, trade_name, status, created_at
                )
                VALUES (?, ?, ?, ?, 'ACTIVE', ?)
                """)) {
            organization.setObject(1, organizationId.value(), Types.OTHER);
            organization.setObject(2, tenantId.value(), Types.OTHER);
            organization.setString(3, legalName);
            organization.setString(4, tradeName);
            organization.setTimestamp(5, Timestamp.from(Instant.parse("2026-09-06T00:00:00Z")));
            organization.executeUpdate();
        }
        try (PreparedStatement identifier = connection.prepareStatement("""
                INSERT INTO gic.organization_identifier (
                    organization_id, tenant_id, identifier_type, identifier_value, normalized_identifier_value, country_code
                )
                VALUES (?, ?, 'RUC', ?, ?, 'PY')
                """)) {
            identifier.setObject(1, organizationId.value(), Types.OTHER);
            identifier.setObject(2, tenantId.value(), Types.OTHER);
            identifier.setString(3, identifierValue);
            identifier.setString(4, normalizedIdentifierValue);
            identifier.executeUpdate();
        }
    }

    private static JdbcOrganizationReadRepository repository(DriverManagerDataSource dataSource) {
        return new JdbcOrganizationReadRepository(new JdbcTemplate(dataSource));
    }

    private static TransactionTemplate transactionTemplate(DriverManagerDataSource dataSource) {
        return new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    private static DriverManagerDataSource appDataSource() {
        var dataSource = new DriverManagerDataSource();
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUsername(APP_USER);
        dataSource.setPassword(APP_PASSWORD);
        return dataSource;
    }
}
