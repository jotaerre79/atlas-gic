CREATE TABLE IF NOT EXISTS gic.person (
    person_id uuid NOT NULL,
    tenant_id uuid NOT NULL REFERENCES gic.tenants (tenant_id),
    given_name varchar(120) NOT NULL,
    middle_name varchar(120),
    family_name varchar(120) NOT NULL,
    display_name varchar(380) NOT NULL,
    status varchar(40) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (person_id),
    UNIQUE (tenant_id, person_id),
    CONSTRAINT person_status_check CHECK (status IN ('ACTIVE'))
);

CREATE TABLE IF NOT EXISTS gic.person_identifier (
    person_identifier_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    person_id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    identifier_type varchar(40) NOT NULL,
    identifier_value varchar(160) NOT NULL,
    normalized_identifier_value varchar(160) NOT NULL,
    issuer varchar(80),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT person_identifier_person_fk
        FOREIGN KEY (tenant_id, person_id)
        REFERENCES gic.person (tenant_id, person_id)
        ON DELETE RESTRICT,
    CONSTRAINT person_identifier_unique_per_tenant
        UNIQUE (tenant_id, identifier_type, normalized_identifier_value)
);

CREATE TABLE IF NOT EXISTS gic.person_audit (
    audit_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    actor varchar(160) NOT NULL,
    tenant_id uuid NOT NULL REFERENCES gic.tenants (tenant_id),
    action varchar(80) NOT NULL,
    person_id uuid NOT NULL,
    correlation_id varchar(160),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT person_audit_action_check CHECK (action IN ('PERSON_REGISTERED')),
    CONSTRAINT person_audit_person_fk
        FOREIGN KEY (tenant_id, person_id)
        REFERENCES gic.person (tenant_id, person_id)
        ON DELETE RESTRICT
);

ALTER TABLE gic.person ENABLE ROW LEVEL SECURITY;
ALTER TABLE gic.person FORCE ROW LEVEL SECURITY;
ALTER TABLE gic.person_identifier ENABLE ROW LEVEL SECURITY;
ALTER TABLE gic.person_identifier FORCE ROW LEVEL SECURITY;
ALTER TABLE gic.person_audit ENABLE ROW LEVEL SECURITY;
ALTER TABLE gic.person_audit FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS person_tenant_policy ON gic.person;
DROP POLICY IF EXISTS person_identifier_tenant_policy ON gic.person_identifier;
DROP POLICY IF EXISTS person_audit_tenant_policy ON gic.person_audit;

CREATE POLICY person_tenant_policy
    ON gic.person
    USING (
        tenant_id = nullif(current_setting('atlas.current_tenant', true), '')::uuid
        OR current_setting('atlas.platform_access', true) = 'true'
    )
    WITH CHECK (
        tenant_id = nullif(current_setting('atlas.current_tenant', true), '')::uuid
        OR current_setting('atlas.platform_access', true) = 'true'
    );

CREATE POLICY person_identifier_tenant_policy
    ON gic.person_identifier
    USING (
        tenant_id = nullif(current_setting('atlas.current_tenant', true), '')::uuid
        OR current_setting('atlas.platform_access', true) = 'true'
    )
    WITH CHECK (
        tenant_id = nullif(current_setting('atlas.current_tenant', true), '')::uuid
        OR current_setting('atlas.platform_access', true) = 'true'
    );

CREATE POLICY person_audit_tenant_policy
    ON gic.person_audit
    USING (
        tenant_id = nullif(current_setting('atlas.current_tenant', true), '')::uuid
        OR current_setting('atlas.platform_access', true) = 'true'
    )
    WITH CHECK (
        tenant_id = nullif(current_setting('atlas.current_tenant', true), '')::uuid
        OR current_setting('atlas.platform_access', true) = 'true'
    );
