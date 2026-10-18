package com.atlas.gic.relationships.adapter.jdbc;

import com.atlas.gic.relationships.application.PersonOrganizationRelationshipEndedAudit;
import com.atlas.gic.relationships.application.PersonOrganizationRelationshipEndedAuditEntry;
import com.atlas.gic.shared.tenancy.domain.TenantId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.Types;

@Repository
@ConditionalOnBean(JdbcTemplate.class)
public class JdbcPersonOrganizationRelationshipEndedAudit implements PersonOrganizationRelationshipEndedAudit {

    private final JdbcTemplate jdbcTemplate;

    public JdbcPersonOrganizationRelationshipEndedAudit(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void record(PersonOrganizationRelationshipEndedAuditEntry entry) {
        setCurrentTenant(entry.tenantId());
        jdbcTemplate.update("""
                INSERT INTO gic.person_organization_relationship_audit (
                    tenant_id, relationship_id, person_id, organization_id,
                    relationship_type, action, actor, correlation_id, created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                ps -> {
                    ps.setObject(1, entry.tenantId().value(), Types.OTHER);
                    ps.setObject(2, entry.relationshipId().value(), Types.OTHER);
                    ps.setObject(3, entry.personId().value(), Types.OTHER);
                    ps.setObject(4, entry.organizationId().value(), Types.OTHER);
                    ps.setString(5, entry.relationshipType().name());
                    ps.setString(6, entry.action());
                    ps.setString(7, entry.actor());
                    ps.setString(8, entry.correlationId());
                    ps.setTimestamp(9, Timestamp.from(entry.timestamp()));
                });
    }

    private void setCurrentTenant(TenantId tenantId) {
        jdbcTemplate.queryForObject(
                "SELECT set_config('atlas.current_tenant', ?, true)",
                String.class,
                tenantId.toString());
    }
}
