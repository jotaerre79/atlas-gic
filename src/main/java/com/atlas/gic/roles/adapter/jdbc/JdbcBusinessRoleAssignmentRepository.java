package com.atlas.gic.roles.adapter.jdbc;

import com.atlas.gic.identity.domain.PersonId;
import com.atlas.gic.roles.application.BusinessRoleAssignmentRepository;
import com.atlas.gic.roles.application.BusinessRoleAssignmentView;
import com.atlas.gic.roles.application.DuplicateActiveBusinessRoleException;
import com.atlas.gic.roles.domain.BusinessRoleAssignment;
import com.atlas.gic.roles.domain.BusinessRoleAssignmentId;
import com.atlas.gic.roles.domain.BusinessRoleAssignmentStatus;
import com.atlas.gic.roles.domain.BusinessRoleType;
import com.atlas.gic.shared.tenancy.domain.TenantId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
@ConditionalOnBean(JdbcTemplate.class)
public class JdbcBusinessRoleAssignmentRepository implements BusinessRoleAssignmentRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcBusinessRoleAssignmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean personExists(TenantId tenantId, PersonId personId) {
        setCurrentTenant(tenantId);
        var count = jdbcTemplate.query("""
                SELECT count(*)
                FROM gic.person
                WHERE tenant_id = ? AND person_id = ?
                """,
                (ps) -> {
                    ps.setObject(1, tenantId.value(), Types.OTHER);
                    ps.setObject(2, personId.value(), Types.OTHER);
                },
                (rs) -> rs.next() ? rs.getInt(1) : 0);
        return count != null && count > 0;
    }

    @Override
    public void save(BusinessRoleAssignment assignment, String actor) {
        setCurrentTenant(assignment.tenantId());
        try {
            jdbcTemplate.update("""
                    INSERT INTO gic.business_role_assignment (
                        assignment_id, tenant_id, person_id, role_type, status, valid_from, valid_to, created_by
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    (ps) -> {
                        ps.setObject(1, assignment.assignmentId().value(), Types.OTHER);
                        ps.setObject(2, assignment.tenantId().value(), Types.OTHER);
                        ps.setObject(3, assignment.personId().value(), Types.OTHER);
                        ps.setString(4, assignment.role().name());
                        ps.setString(5, assignment.status().name());
                        ps.setObject(6, assignment.validFrom());
                        ps.setObject(7, assignment.validTo());
                        ps.setString(8, actor);
                    });
        } catch (DuplicateKeyException exception) {
            throw new DuplicateActiveBusinessRoleException();
        }
    }

    @Override
    public List<BusinessRoleAssignmentView> findByPerson(TenantId tenantId, PersonId personId) {
        setCurrentTenant(tenantId);
        return jdbcTemplate.query("""
                SELECT assignment_id, person_id, role_type, status, valid_from, valid_to
                FROM gic.business_role_assignment
                WHERE tenant_id = ? AND person_id = ?
                ORDER BY valid_from, role_type, assignment_id
                """,
                (ps) -> {
                    ps.setObject(1, tenantId.value(), Types.OTHER);
                    ps.setObject(2, personId.value(), Types.OTHER);
                },
                (rs, rowNum) -> mapView(rs));
    }

    @Override
    public Optional<BusinessRoleAssignment> findById(
            TenantId tenantId,
            PersonId personId,
            BusinessRoleAssignmentId assignmentId) {
        setCurrentTenant(tenantId);
        return jdbcTemplate.query("""
                SELECT assignment_id, tenant_id, person_id, role_type, status, valid_from, valid_to
                FROM gic.business_role_assignment
                WHERE tenant_id = ? AND person_id = ? AND assignment_id = ?
                """,
                (ps) -> {
                    ps.setObject(1, tenantId.value(), Types.OTHER);
                    ps.setObject(2, personId.value(), Types.OTHER);
                    ps.setObject(3, assignmentId.value(), Types.OTHER);
                },
                (rs) -> rs.next() ? Optional.of(mapAssignment(rs)) : Optional.empty());
    }

    @Override
    public boolean endActive(
            TenantId tenantId,
            PersonId personId,
            BusinessRoleAssignment endedAssignment,
            String actor,
            String reason) {
        setCurrentTenant(tenantId);
        var updated = jdbcTemplate.update("""
                UPDATE gic.business_role_assignment
                SET status = 'ENDED',
                    valid_to = ?,
                    ended_at = now(),
                    ended_by = ?,
                    end_reason = ?
                WHERE tenant_id = ?
                  AND person_id = ?
                  AND assignment_id = ?
                  AND status = 'ACTIVE'
                """,
                (ps) -> {
                    ps.setObject(1, endedAssignment.validTo());
                    ps.setString(2, actor);
                    ps.setString(3, reason);
                    ps.setObject(4, tenantId.value(), Types.OTHER);
                    ps.setObject(5, personId.value(), Types.OTHER);
                    ps.setObject(6, endedAssignment.assignmentId().value(), Types.OTHER);
                });
        return updated == 1;
    }

    private void setCurrentTenant(TenantId tenantId) {
        jdbcTemplate.queryForObject(
                "SELECT set_config('atlas.current_tenant', ?, true)",
                String.class,
                tenantId.toString());
    }

    private BusinessRoleAssignmentView mapView(ResultSet rs) throws SQLException {
        return new BusinessRoleAssignmentView(
                rs.getObject("assignment_id", UUID.class),
                rs.getObject("person_id", UUID.class),
                BusinessRoleType.valueOf(rs.getString("role_type")),
                BusinessRoleAssignmentStatus.valueOf(rs.getString("status")),
                rs.getObject("valid_from", java.time.LocalDate.class),
                rs.getObject("valid_to", java.time.LocalDate.class));
    }

    private BusinessRoleAssignment mapAssignment(ResultSet rs) throws SQLException {
        return new BusinessRoleAssignment(
                BusinessRoleAssignmentId.of(rs.getObject("assignment_id", UUID.class)),
                TenantId.of(rs.getObject("tenant_id", UUID.class)),
                PersonId.of(rs.getObject("person_id", UUID.class)),
                BusinessRoleType.valueOf(rs.getString("role_type")),
                rs.getObject("valid_from", java.time.LocalDate.class),
                rs.getObject("valid_to", java.time.LocalDate.class),
                BusinessRoleAssignmentStatus.valueOf(rs.getString("status")));
    }
}
