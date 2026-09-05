package com.atlas.gic.roles.adapter.jdbc;

import com.atlas.gic.roles.application.BusinessRoleEndedAudit;
import com.atlas.gic.roles.application.BusinessRoleEndedAuditEntry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.sql.Types;

@Component
@ConditionalOnBean(JdbcTemplate.class)
public class JdbcBusinessRoleEndedAudit implements BusinessRoleEndedAudit {

    private final JdbcTemplate jdbcTemplate;

    public JdbcBusinessRoleEndedAudit(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void record(BusinessRoleEndedAuditEntry entry) {
        jdbcTemplate.update("""
                INSERT INTO gic.business_role_assignment_audit (
                    actor, tenant_id, action, person_id, assignment_id, role_type, valid_to, end_reason,
                    correlation_id, created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (ps) -> {
                    ps.setString(1, entry.actor());
                    ps.setObject(2, entry.tenantId().value(), Types.OTHER);
                    ps.setString(3, BusinessRoleEndedAuditEntry.ACTION);
                    ps.setObject(4, entry.personId().value(), Types.OTHER);
                    ps.setObject(5, entry.assignmentId().value(), Types.OTHER);
                    ps.setString(6, entry.role().name());
                    ps.setObject(7, entry.validTo());
                    ps.setString(8, entry.reason());
                    ps.setString(9, entry.correlationId());
                    ps.setTimestamp(10, Timestamp.from(entry.timestamp()));
                });
    }
}
