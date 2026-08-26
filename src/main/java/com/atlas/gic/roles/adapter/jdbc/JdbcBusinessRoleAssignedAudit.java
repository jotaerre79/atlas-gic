package com.atlas.gic.roles.adapter.jdbc;

import com.atlas.gic.roles.application.BusinessRoleAssignedAudit;
import com.atlas.gic.roles.application.BusinessRoleAssignedAuditEntry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.sql.Types;

@Component
@ConditionalOnBean(JdbcTemplate.class)
public class JdbcBusinessRoleAssignedAudit implements BusinessRoleAssignedAudit {

    private final JdbcTemplate jdbcTemplate;

    public JdbcBusinessRoleAssignedAudit(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void record(BusinessRoleAssignedAuditEntry entry) {
        jdbcTemplate.update("""
                INSERT INTO gic.business_role_assignment_audit (
                    actor, tenant_id, action, person_id, assignment_id, role_type, correlation_id, created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (ps) -> {
                    ps.setString(1, entry.actor());
                    ps.setObject(2, entry.tenantId().value(), Types.OTHER);
                    ps.setString(3, BusinessRoleAssignedAuditEntry.ACTION);
                    ps.setObject(4, entry.personId().value(), Types.OTHER);
                    ps.setObject(5, entry.assignmentId().value(), Types.OTHER);
                    ps.setString(6, entry.role().name());
                    ps.setString(7, entry.correlationId());
                    ps.setTimestamp(8, Timestamp.from(entry.timestamp()));
                });
    }
}
