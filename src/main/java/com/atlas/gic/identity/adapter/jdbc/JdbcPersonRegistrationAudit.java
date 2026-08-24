package com.atlas.gic.identity.adapter.jdbc;

import com.atlas.gic.identity.application.PersonRegisteredAuditEntry;
import com.atlas.gic.identity.application.PersonRegistrationAudit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(JdbcTemplate.class)
public class JdbcPersonRegistrationAudit implements PersonRegistrationAudit {

    private final JdbcTemplate jdbcTemplate;

    public JdbcPersonRegistrationAudit(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void record(PersonRegisteredAuditEntry entry) {
        jdbcTemplate.update("""
                INSERT INTO gic.person_audit (
                    actor, tenant_id, action, person_id, correlation_id, created_at
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                entry.actor(),
                entry.tenantId().value(),
                PersonRegisteredAuditEntry.ACTION,
                entry.personId().value(),
                entry.correlationId(),
                entry.timestamp());
    }
}
