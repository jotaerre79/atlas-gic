package com.atlas.gic.identity.adapter.jdbc;

import com.atlas.gic.identity.application.DuplicateOrganizationIdentifierException;
import com.atlas.gic.identity.application.OrganizationRepository;
import com.atlas.gic.identity.domain.Organization;
import com.atlas.gic.shared.tenancy.domain.TenantId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.Types;

@Repository
@ConditionalOnBean(JdbcTemplate.class)
public class JdbcOrganizationRepository implements OrganizationRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcOrganizationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(Organization organization) {
        setCurrentTenant(organization.tenantId());
        try {
            jdbcTemplate.update("""
                    INSERT INTO gic.organization (
                        organization_id, tenant_id, legal_name, trade_name, status, created_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    (ps) -> {
                        ps.setObject(1, organization.organizationId().value(), Types.OTHER);
                        ps.setObject(2, organization.tenantId().value(), Types.OTHER);
                        ps.setString(3, organization.name().legalName());
                        ps.setString(4, organization.name().tradeName());
                        ps.setString(5, organization.status().name());
                        ps.setTimestamp(6, Timestamp.from(organization.createdAt()));
                    });

            jdbcTemplate.update("""
                    INSERT INTO gic.organization_identifier (
                        organization_id, tenant_id, identifier_type, identifier_value,
                        normalized_identifier_value, country_code
                    )
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    (ps) -> {
                        ps.setObject(1, organization.organizationId().value(), Types.OTHER);
                        ps.setObject(2, organization.tenantId().value(), Types.OTHER);
                        ps.setString(3, organization.identifier().type());
                        ps.setString(4, organization.identifier().value());
                        ps.setString(5, organization.identifier().normalizedValue());
                        ps.setString(6, organization.identifier().countryCode());
                    });
        } catch (DuplicateKeyException exception) {
            throw new DuplicateOrganizationIdentifierException();
        }
    }

    private void setCurrentTenant(TenantId tenantId) {
        jdbcTemplate.queryForObject(
                "SELECT set_config('atlas.current_tenant', ?, true)",
                String.class,
                tenantId.toString());
    }
}
