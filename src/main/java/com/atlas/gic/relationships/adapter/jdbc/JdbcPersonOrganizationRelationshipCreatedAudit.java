package com.atlas.gic.relationships.adapter.jdbc;

import com.atlas.gic.relationships.application.PersonOrganizationRelationshipCreatedAudit;
import com.atlas.gic.relationships.application.PersonOrganizationRelationshipCreatedAuditEntry;
import com.atlas.gic.shared.tenancy.domain.TenantId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.Types;

@Repository
@ConditionalOnBean(JdbcTemplate.class)
public class JdbcPersonOrganizationRelationshipCreatedAudit implements PersonOrganizationRelationshipCreatedAudit {

    private final JdbcTemplate jdbcTemplate;

    public JdbcPersonOrganizationRelationshipCreatedAudit(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void record(PersonOrganizationRelationshipCreatedAuditEntry entry) {
        setCurrentTenant(entry.tenantId());
        jdbcTemplate.update("""
                INSERT INTO gic.person_organization_relationship_audit (
                    tenant_id, relationship_id, person_id, organization_id,
                    relationship_type, action, actor, correlation_id, created_at
                )
                VALUES (?, ?, ?, ?, ?, 'PERSON_ORGANIZATION_RELATIONSHIP_CREATED', ?, ?, ?)
                """,
                ps -> {
                    ps.setObject(1, entry.tenantId().value(), Types.OTHER);
                    ps.setObject(2, entry.relationshipId().value(), Types.OTHER);
                    ps.setObject(3, entry.personId().value(), Types.OTHER);
                    ps.setObject(4, entry.organizationId().value(), Types.OTHER);
                    ps.setString(5, entry.relationshipType().name());
                    ps.setString(6, entry.actor());
                    ps.setString(7, entry.correlationId());
                    ps.setTimestamp(8, Timestamp.from(entry.createdAt()));
                });
    }

    private void setCurrentTenant(TenantId tenantId) {
        jdbcTemplate.queryForObject(
                "SELECT set_config('atlas.current_tenant', ?, true)",
                String.class,
                tenantId.toString());
    }
}
