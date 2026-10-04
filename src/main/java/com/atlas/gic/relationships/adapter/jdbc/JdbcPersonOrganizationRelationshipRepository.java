package com.atlas.gic.relationships.adapter.jdbc;

import com.atlas.gic.identity.domain.OrganizationId;
import com.atlas.gic.identity.domain.PersonId;
import com.atlas.gic.relationships.application.DuplicateActivePersonOrganizationRelationshipException;
import com.atlas.gic.relationships.application.PersonOrganizationRelationshipListPage;
import com.atlas.gic.relationships.application.PersonOrganizationRelationshipRepository;
import com.atlas.gic.relationships.application.PersonOrganizationRelationshipView;
import com.atlas.gic.relationships.domain.PersonOrganizationRelationship;
import com.atlas.gic.relationships.domain.PersonOrganizationRelationshipStatus;
import com.atlas.gic.relationships.domain.PersonOrganizationRelationshipType;
import com.atlas.gic.shared.tenancy.domain.TenantId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.UUID;

@Repository
@ConditionalOnBean(JdbcTemplate.class)
public class JdbcPersonOrganizationRelationshipRepository implements PersonOrganizationRelationshipRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcPersonOrganizationRelationshipRepository(JdbcTemplate jdbcTemplate) {
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
                ps -> {
                    ps.setObject(1, tenantId.value(), Types.OTHER);
                    ps.setObject(2, personId.value(), Types.OTHER);
                },
                rs -> rs.next() ? rs.getInt(1) : 0);
        return count != null && count > 0;
    }

    @Override
    public boolean organizationExists(TenantId tenantId, OrganizationId organizationId) {
        setCurrentTenant(tenantId);
        var count = jdbcTemplate.query("""
                SELECT count(*)
                FROM gic.organization
                WHERE tenant_id = ? AND organization_id = ?
                """,
                ps -> {
                    ps.setObject(1, tenantId.value(), Types.OTHER);
                    ps.setObject(2, organizationId.value(), Types.OTHER);
                },
                rs -> rs.next() ? rs.getInt(1) : 0);
        return count != null && count > 0;
    }

    @Override
    public void save(PersonOrganizationRelationship relationship, String actor) {
        setCurrentTenant(relationship.tenantId());
        try {
            jdbcTemplate.update("""
                    INSERT INTO gic.person_organization_relationship (
                        relationship_id, tenant_id, person_id, organization_id,
                        relationship_type, status, valid_from, valid_to, created_at, created_by
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    ps -> {
                        ps.setObject(1, relationship.relationshipId().value(), Types.OTHER);
                        ps.setObject(2, relationship.tenantId().value(), Types.OTHER);
                        ps.setObject(3, relationship.personId().value(), Types.OTHER);
                        ps.setObject(4, relationship.organizationId().value(), Types.OTHER);
                        ps.setString(5, relationship.relationshipType().name());
                        ps.setString(6, relationship.status().name());
                        ps.setObject(7, relationship.validFrom());
                        ps.setObject(8, relationship.validTo());
                        ps.setTimestamp(9, Timestamp.from(relationship.createdAt()));
                        ps.setString(10, actor);
                    });
        } catch (DuplicateKeyException exception) {
            throw new DuplicateActivePersonOrganizationRelationshipException();
        }
    }

    @Override
    public PersonOrganizationRelationshipListPage findByPerson(
            TenantId tenantId,
            PersonId personId,
            PersonOrganizationRelationshipStatus status,
            int page,
            int size) {
        setCurrentTenant(tenantId);
        var offset = Math.multiplyExact(page, size);
        var total = jdbcTemplate.query("""
                SELECT count(*)
                FROM gic.person_organization_relationship r
                WHERE r.tenant_id = ?
                  AND r.person_id = ?
                  AND r.status = ?
                """,
                ps -> {
                    ps.setObject(1, tenantId.value(), Types.OTHER);
                    ps.setObject(2, personId.value(), Types.OTHER);
                    ps.setString(3, status.name());
                },
                rs -> rs.next() ? rs.getLong(1) : 0L);

        var items = jdbcTemplate.query("""
                SELECT
                    r.relationship_id,
                    r.person_id,
                    r.organization_id,
                    o.legal_name,
                    o.trade_name,
                    r.relationship_type,
                    r.valid_from,
                    r.valid_to,
                    r.status,
                    r.created_at
                FROM gic.person_organization_relationship r
                JOIN gic.organization o
                  ON o.tenant_id = r.tenant_id AND o.organization_id = r.organization_id
                WHERE r.tenant_id = ?
                  AND r.person_id = ?
                  AND r.status = ?
                ORDER BY r.valid_from DESC, r.relationship_id
                LIMIT ? OFFSET ?
                """,
                ps -> {
                    ps.setObject(1, tenantId.value(), Types.OTHER);
                    ps.setObject(2, personId.value(), Types.OTHER);
                    ps.setString(3, status.name());
                    ps.setInt(4, size);
                    ps.setInt(5, offset);
                },
                (rs, rowNum) -> mapView(rs));

        return new PersonOrganizationRelationshipListPage(items, page, size, total == null ? 0 : total);
    }

    private void setCurrentTenant(TenantId tenantId) {
        jdbcTemplate.queryForObject(
                "SELECT set_config('atlas.current_tenant', ?, true)",
                String.class,
                tenantId.toString());
    }

    private PersonOrganizationRelationshipView mapView(ResultSet rs) throws SQLException {
        return new PersonOrganizationRelationshipView(
                rs.getObject("relationship_id", UUID.class),
                rs.getObject("person_id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getString("legal_name"),
                rs.getString("trade_name"),
                PersonOrganizationRelationshipType.valueOf(rs.getString("relationship_type")),
                rs.getObject("valid_from", java.time.LocalDate.class),
                rs.getObject("valid_to", java.time.LocalDate.class),
                PersonOrganizationRelationshipStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant());
    }
}
