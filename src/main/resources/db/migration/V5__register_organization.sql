CREATE TABLE IF NOT EXISTS gic.organization (
    organization_id uuid NOT NULL,
    tenant_id uuid NOT NULL REFERENCES gic.tenants (tenant_id),
    legal_name varchar(240) NOT NULL,
    trade_name varchar(240),
    status varchar(40) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id),
    UNIQUE (tenant_id, organization_id),
    CONSTRAINT organization_status_check CHECK (status IN ('ACTIVE')),
    CONSTRAINT organization_legal_name_check CHECK (btrim(legal_name) <> '')
);

CREATE TABLE IF NOT EXISTS gic.organization_identifier (
    organization_identifier_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    identifier_type varchar(40) NOT NULL,
    identifier_value varchar(160) NOT NULL,
    normalized_identifier_value varchar(160) NOT NULL,
    country_code varchar(2) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT organization_identifier_org_fk
        FOREIGN KEY (tenant_id, organization_id)
        REFERENCES gic.organization (tenant_id, organization_id)
        ON DELETE RESTRICT,
    CONSTRAINT organization_identifier_supported_type_check CHECK (identifier_type IN ('RUC')),
    CONSTRAINT organization_identifier_supported_country_check CHECK (country_code IN ('PY')),
    CONSTRAINT organization_identifier_normalized_check CHECK (btrim(normalized_identifier_value) <> ''),
    CONSTRAINT organization_identifier_normalized_format_check
        CHECK (normalized_identifier_value ~ '^[0-9]{2,20}$'),
    CONSTRAINT organization_identifier_unique_per_tenant
        UNIQUE (tenant_id, identifier_type, country_code, normalized_identifier_value)
);

CREATE TABLE IF NOT EXISTS gic.organization_audit (
    audit_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    actor varchar(160) NOT NULL,
    tenant_id uuid NOT NULL REFERENCES gic.tenants (tenant_id),
    action varchar(80) NOT NULL,
    organization_id uuid NOT NULL,
    correlation_id varchar(160) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT organization_audit_action_check CHECK (action IN ('ORGANIZATION_REGISTERED')),
    CONSTRAINT organization_audit_org_fk
        FOREIGN KEY (tenant_id, organization_id)
        REFERENCES gic.organization (tenant_id, organization_id)
        ON DELETE RESTRICT
);

ALTER TABLE gic.organization ENABLE ROW LEVEL SECURITY;
ALTER TABLE gic.organization FORCE ROW LEVEL SECURITY;
ALTER TABLE gic.organization_identifier ENABLE ROW LEVEL SECURITY;
ALTER TABLE gic.organization_identifier FORCE ROW LEVEL SECURITY;
ALTER TABLE gic.organization_audit ENABLE ROW LEVEL SECURITY;
ALTER TABLE gic.organization_audit FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS organization_tenant_policy ON gic.organization;
DROP POLICY IF EXISTS organization_identifier_tenant_policy ON gic.organization_identifier;
DROP POLICY IF EXISTS organization_audit_tenant_policy ON gic.organization_audit;

CREATE POLICY organization_tenant_policy
    ON gic.organization
    USING (
        tenant_id = nullif(current_setting('atlas.current_tenant', true), '')::uuid
        OR current_setting('atlas.platform_access', true) = 'true'
    )
    WITH CHECK (
        tenant_id = nullif(current_setting('atlas.current_tenant', true), '')::uuid
        OR current_setting('atlas.platform_access', true) = 'true'
    );

CREATE POLICY organization_identifier_tenant_policy
    ON gic.organization_identifier
    USING (
        tenant_id = nullif(current_setting('atlas.current_tenant', true), '')::uuid
        OR current_setting('atlas.platform_access', true) = 'true'
    )
    WITH CHECK (
        tenant_id = nullif(current_setting('atlas.current_tenant', true), '')::uuid
        OR current_setting('atlas.platform_access', true) = 'true'
    );

CREATE POLICY organization_audit_tenant_policy
    ON gic.organization_audit
    USING (
        tenant_id = nullif(current_setting('atlas.current_tenant', true), '')::uuid
        OR current_setting('atlas.platform_access', true) = 'true'
    )
    WITH CHECK (
        tenant_id = nullif(current_setting('atlas.current_tenant', true), '')::uuid
        OR current_setting('atlas.platform_access', true) = 'true'
    );
