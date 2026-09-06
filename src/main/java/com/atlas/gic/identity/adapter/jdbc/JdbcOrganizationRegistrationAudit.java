package com.atlas.gic.identity.adapter.jdbc;

import com.atlas.gic.identity.application.OrganizationRegisteredAuditEntry;
import com.atlas.gic.identity.application.OrganizationRegistrationAudit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.sql.Types;

@Component
@ConditionalOnBean(JdbcTemplate.class)
public class JdbcOrganizationRegistrationAudit implements OrganizationRegistrationAudit {

    private final JdbcTemplate jdbcTemplate;

    public JdbcOrganizationRegistrationAudit(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void record(OrganizationRegisteredAuditEntry entry) {
        jdbcTemplate.update("""
                INSERT INTO gic.organization_audit (
                    actor, tenant_id, action, organization_id, correlation_id, created_at
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                (ps) -> {
                    ps.setString(1, entry.actor());
                    ps.setObject(2, entry.tenantId().value(), Types.OTHER);
                    ps.setString(3, OrganizationRegisteredAuditEntry.ACTION);
                    ps.setObject(4, entry.organizationId().value(), Types.OTHER);
                    ps.setString(5, entry.correlationId());
                    ps.setTimestamp(6, Timestamp.from(entry.timestamp()));
                });
    }
}
