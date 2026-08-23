CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE SCHEMA IF NOT EXISTS gic;

CREATE TABLE IF NOT EXISTS gic.tenants (
    tenant_id uuid PRIMARY KEY,
    code varchar(80) NOT NULL UNIQUE,
    display_name varchar(160) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS gic.tenant_isolation_probe (
    probe_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES gic.tenants (tenant_id),
    label varchar(160) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS gic.platform_access_audit (
    audit_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    actor varchar(160) NOT NULL,
    reason varchar(400) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE gic.tenant_isolation_probe ENABLE ROW LEVEL SECURITY;
ALTER TABLE gic.tenant_isolation_probe FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation_probe_tenant_policy ON gic.tenant_isolation_probe;

CREATE POLICY tenant_isolation_probe_tenant_policy
    ON gic.tenant_isolation_probe
    USING (
        tenant_id = nullif(current_setting('atlas.current_tenant', true), '')::uuid
        OR current_setting('atlas.platform_access', true) = 'true'
    )
    WITH CHECK (
        tenant_id = nullif(current_setting('atlas.current_tenant', true), '')::uuid
        OR current_setting('atlas.platform_access', true) = 'true'
    );
