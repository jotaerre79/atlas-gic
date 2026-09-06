package com.atlas.gic.identity.adapter.jdbc;

import com.atlas.gic.identity.application.OrganizationReadRepository;
import com.atlas.gic.identity.application.OrganizationSearchItem;
import com.atlas.gic.identity.application.OrganizationSearchPage;
import com.atlas.gic.identity.application.OrganizationView;
import com.atlas.gic.identity.domain.OrganizationId;
import com.atlas.gic.identity.domain.OrganizationIdentifier;
import com.atlas.gic.shared.tenancy.domain.TenantId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

@Repository
@ConditionalOnBean(JdbcTemplate.class)
public class JdbcOrganizationReadRepository implements OrganizationReadRepository {

    private static final char LIKE_ESCAPE = '\\';

    private final JdbcTemplate jdbcTemplate;

    public JdbcOrganizationReadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<OrganizationView> findById(TenantId tenantId, OrganizationId organizationId) {
        setCurrentTenant(tenantId);
        return jdbcTemplate.query("""
                SELECT
                    o.organization_id,
                    o.legal_name,
                    o.trade_name,
                    o.status,
                    o.created_at,
                    oi.identifier_type,
                    oi.country_code,
                    oi.normalized_identifier_value
                FROM gic.organization o
                LEFT JOIN gic.organization_identifier oi
                    ON oi.tenant_id = o.tenant_id AND oi.organization_id = o.organization_id
                WHERE o.tenant_id = ? AND o.organization_id = ?
                ORDER BY oi.created_at, oi.organization_identifier_id
                """,
                ps -> {
                    ps.setObject(1, tenantId.value(), Types.OTHER);
                    ps.setObject(2, organizationId.value(), Types.OTHER);
                },
                rs -> {
                    OrganizationViewBuilder builder = null;
                    while (rs.next()) {
                        if (builder == null) {
                            builder = new OrganizationViewBuilder(
                                    rs.getObject("organization_id", UUID.class),
                                    rs.getString("legal_name"),
                                    rs.getString("trade_name"),
                                    rs.getString("status"),
                                    rs.getTimestamp("created_at"));
                        }
                        var identifierType = rs.getString("identifier_type");
                        if (identifierType != null) {
                            builder.identifiers.add(new OrganizationView.IdentifierView(
                                    identifierType,
                                    rs.getString("country_code"),
                                    mask(rs.getString("normalized_identifier_value"))));
                        }
                    }
                    return builder == null ? Optional.empty() : Optional.of(builder.build());
                });
    }

    @Override
    public OrganizationSearchPage search(TenantId tenantId, String query, int page, int size) {
        setCurrentTenant(tenantId);
        var likeQuery = query == null ? null : "%" + escapeLike(query.toLowerCase()) + "%";
        var normalizedIdentifierQuery = normalizedIdentifierQuery(query);
        var offset = Math.multiplyExact(page, size);

        var total = jdbcTemplate.query("""
                SELECT count(*)
                FROM gic.organization o
                WHERE o.tenant_id = ?
                  AND (
                    ? IS NULL
                    OR lower(o.legal_name) LIKE ? ESCAPE '\\'
                    OR lower(o.trade_name) LIKE ? ESCAPE '\\'
                    OR (
                        ? IS NOT NULL
                        AND EXISTS (
                            SELECT 1
                            FROM gic.organization_identifier oi
                            WHERE oi.tenant_id = o.tenant_id
                              AND oi.organization_id = o.organization_id
                              AND oi.normalized_identifier_value LIKE ? ESCAPE '\\'
                        )
                    )
                  )
                """,
                ps -> {
                    ps.setObject(1, tenantId.value(), Types.OTHER);
                    ps.setString(2, likeQuery);
                    ps.setString(3, likeQuery);
                    ps.setString(4, likeQuery);
                    ps.setString(5, normalizedIdentifierQuery);
                    ps.setString(6, normalizedIdentifierQuery);
                },
                rs -> rs.next() ? rs.getLong(1) : 0L);

        var items = jdbcTemplate.query("""
                SELECT
                    o.organization_id,
                    o.legal_name,
                    o.trade_name,
                    o.status,
                    oi.identifier_type,
                    oi.country_code
                FROM gic.organization o
                LEFT JOIN LATERAL (
                    SELECT identifier_type, country_code
                    FROM gic.organization_identifier
                    WHERE tenant_id = o.tenant_id AND organization_id = o.organization_id
                    ORDER BY created_at, organization_identifier_id
                    LIMIT 1
                ) oi ON true
                WHERE o.tenant_id = ?
                  AND (
                    ? IS NULL
                    OR lower(o.legal_name) LIKE ? ESCAPE '\\'
                    OR lower(o.trade_name) LIKE ? ESCAPE '\\'
                    OR (
                        ? IS NOT NULL
                        AND EXISTS (
                            SELECT 1
                            FROM gic.organization_identifier oii
                            WHERE oii.tenant_id = o.tenant_id
                              AND oii.organization_id = o.organization_id
                              AND oii.normalized_identifier_value LIKE ? ESCAPE '\\'
                        )
                    )
                  )
                ORDER BY lower(o.legal_name), o.organization_id
                LIMIT ? OFFSET ?
                """,
                ps -> {
                    ps.setObject(1, tenantId.value(), Types.OTHER);
                    ps.setString(2, likeQuery);
                    ps.setString(3, likeQuery);
                    ps.setString(4, likeQuery);
                    ps.setString(5, normalizedIdentifierQuery);
                    ps.setString(6, normalizedIdentifierQuery);
                    ps.setInt(7, size);
                    ps.setInt(8, offset);
                },
                (rs, rowNum) -> mapSearchItem(rs));

        return new OrganizationSearchPage(items, page, size, total);
    }

    private void setCurrentTenant(TenantId tenantId) {
        jdbcTemplate.queryForObject(
                "SELECT set_config('atlas.current_tenant', ?, true)",
                String.class,
                tenantId.toString());
    }

    private OrganizationSearchItem mapSearchItem(ResultSet rs) throws SQLException {
        return new OrganizationSearchItem(
                rs.getObject("organization_id", UUID.class),
                rs.getString("legal_name"),
                rs.getString("trade_name"),
                rs.getString("status"),
                rs.getString("identifier_type"),
                rs.getString("country_code"));
    }

    private static String normalizedIdentifierQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        try {
            return "%" + OrganizationIdentifier.of("RUC", query, "PY").normalizedValue() + "%";
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String escapeLike(String value) {
        return value
                .replace(String.valueOf(LIKE_ESCAPE), "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private static String mask(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        var visible = Math.min(4, value.length());
        return "****" + value.substring(value.length() - visible);
    }

    private record OrganizationViewBuilder(
            UUID organizationId,
            String legalName,
            String tradeName,
            String status,
            Timestamp createdAt,
            ArrayList<OrganizationView.IdentifierView> identifiers) {

        OrganizationViewBuilder(
                UUID organizationId,
                String legalName,
                String tradeName,
                String status,
                Timestamp createdAt) {
            this(organizationId, legalName, tradeName, status, createdAt, new ArrayList<>());
        }

        OrganizationView build() {
            return new OrganizationView(
                    organizationId,
                    legalName,
                    tradeName,
                    status,
                    identifiers,
                    createdAt.toInstant());
        }
    }
}
