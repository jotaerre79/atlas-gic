package com.atlas.gic.identity.adapter.jdbc;

import com.atlas.gic.identity.application.PersonReadRepository;
import com.atlas.gic.identity.application.PersonSearchItem;
import com.atlas.gic.identity.application.PersonSearchPage;
import com.atlas.gic.identity.application.PersonView;
import com.atlas.gic.identity.domain.PersonId;
import com.atlas.gic.shared.tenancy.domain.TenantId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

@Repository
@ConditionalOnBean(JdbcTemplate.class)
public class JdbcPersonReadRepository implements PersonReadRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcPersonReadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<PersonView> findById(TenantId tenantId, PersonId personId) {
        setCurrentTenant(tenantId);
        return jdbcTemplate.query("""
                SELECT
                    p.person_id,
                    p.status,
                    p.given_name,
                    p.middle_name,
                    p.family_name,
                    p.display_name,
                    pi.identifier_type,
                    pi.identifier_value,
                    pi.issuer
                FROM gic.person p
                LEFT JOIN gic.person_identifier pi
                    ON pi.tenant_id = p.tenant_id AND pi.person_id = p.person_id
                WHERE p.tenant_id = ? AND p.person_id = ?
                ORDER BY pi.created_at, pi.person_identifier_id
                """,
                ps -> {
                    ps.setObject(1, tenantId.value(), Types.OTHER);
                    ps.setObject(2, personId.value(), Types.OTHER);
                },
                rs -> {
                    PersonViewBuilder builder = null;
                    while (rs.next()) {
                        if (builder == null) {
                            builder = new PersonViewBuilder(
                                    rs.getObject("person_id", UUID.class),
                                    rs.getString("status"),
                                    rs.getString("given_name"),
                                    rs.getString("middle_name"),
                                    rs.getString("family_name"),
                                    rs.getString("display_name"));
                        }
                        var identifierType = rs.getString("identifier_type");
                        if (identifierType != null) {
                            builder.identifiers.add(new PersonView.IdentifierView(
                                    identifierType,
                                    rs.getString("issuer"),
                                    mask(rs.getString("identifier_value"))));
                        }
                    }
                    return builder == null ? Optional.empty() : Optional.of(builder.build());
                });
    }

    @Override
    public PersonSearchPage search(TenantId tenantId, String query, int page, int size) {
        setCurrentTenant(tenantId);
        var likeQuery = query == null ? null : "%" + query.toLowerCase() + "%";
        var total = jdbcTemplate.query("""
                SELECT count(*)
                FROM gic.person p
                WHERE p.tenant_id = ?
                  AND (
                    ? IS NULL
                    OR lower(p.display_name) LIKE ?
                    OR lower(p.given_name) LIKE ?
                    OR lower(p.family_name) LIKE ?
                    OR EXISTS (
                        SELECT 1
                        FROM gic.person_identifier pi
                        WHERE pi.tenant_id = p.tenant_id
                          AND pi.person_id = p.person_id
                          AND lower(pi.normalized_identifier_value) LIKE ?
                    )
                  )
                """,
                ps -> {
                    ps.setObject(1, tenantId.value(), Types.OTHER);
                    ps.setString(2, likeQuery);
                    ps.setString(3, likeQuery);
                    ps.setString(4, likeQuery);
                    ps.setString(5, likeQuery);
                    ps.setString(6, likeQuery);
                },
                rs -> rs.next() ? rs.getLong(1) : 0L);

        var items = jdbcTemplate.query("""
                SELECT
                    p.person_id,
                    p.display_name,
                    p.status,
                    pi.identifier_type,
                    pi.issuer
                FROM gic.person p
                LEFT JOIN LATERAL (
                    SELECT identifier_type, issuer
                    FROM gic.person_identifier
                    WHERE tenant_id = p.tenant_id AND person_id = p.person_id
                    ORDER BY created_at, person_identifier_id
                    LIMIT 1
                ) pi ON true
                WHERE p.tenant_id = ?
                  AND (
                    ? IS NULL
                    OR lower(p.display_name) LIKE ?
                    OR lower(p.given_name) LIKE ?
                    OR lower(p.family_name) LIKE ?
                    OR EXISTS (
                        SELECT 1
                        FROM gic.person_identifier pii
                        WHERE pii.tenant_id = p.tenant_id
                          AND pii.person_id = p.person_id
                          AND lower(pii.normalized_identifier_value) LIKE ?
                    )
                  )
                ORDER BY lower(p.display_name), p.person_id
                LIMIT ? OFFSET ?
                """,
                ps -> {
                    ps.setObject(1, tenantId.value(), Types.OTHER);
                    ps.setString(2, likeQuery);
                    ps.setString(3, likeQuery);
                    ps.setString(4, likeQuery);
                    ps.setString(5, likeQuery);
                    ps.setString(6, likeQuery);
                    ps.setInt(7, size);
                    ps.setInt(8, page * size);
                },
                (rs, rowNum) -> mapSearchItem(rs));

        return new PersonSearchPage(items, page, size, total);
    }

    private void setCurrentTenant(TenantId tenantId) {
        jdbcTemplate.queryForObject(
                "SELECT set_config('atlas.current_tenant', ?, true)",
                String.class,
                tenantId.toString());
    }

    private PersonSearchItem mapSearchItem(ResultSet rs) throws SQLException {
        return new PersonSearchItem(
                rs.getObject("person_id", UUID.class),
                rs.getString("display_name"),
                rs.getString("status"),
                rs.getString("identifier_type"),
                rs.getString("issuer"));
    }

    private static String mask(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        var visible = Math.min(4, value.length());
        return "*".repeat(Math.max(0, value.length() - visible)) + value.substring(value.length() - visible);
    }

    private record PersonViewBuilder(
            UUID personId,
            String status,
            String givenName,
            String middleName,
            String familyName,
            String displayName,
            ArrayList<PersonView.IdentifierView> identifiers) {

        PersonViewBuilder(
                UUID personId,
                String status,
                String givenName,
                String middleName,
                String familyName,
                String displayName) {
            this(personId, status, givenName, middleName, familyName, displayName, new ArrayList<>());
        }

        PersonView build() {
            return new PersonView(personId, status, givenName, middleName, familyName, displayName, identifiers);
        }
    }
}
