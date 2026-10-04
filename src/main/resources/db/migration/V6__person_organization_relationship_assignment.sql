CREATE TABLE gic.person_organization_relationship (
    relationship_id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES gic.tenants (tenant_id),
    person_id uuid NOT NULL,
    organization_id uuid NOT NULL,
    relationship_type varchar(60) NOT NULL,
    status varchar(40) NOT NULL,
    valid_from date NOT NULL,
    valid_to date,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(160) NOT NULL,
    CONSTRAINT person_organization_relationship_person_fk
        FOREIGN KEY (tenant_id, person_id)
        REFERENCES gic.person (tenant_id, person_id)
        ON DELETE RESTRICT,
    CONSTRAINT person_organization_relationship_organization_fk
        FOREIGN KEY (tenant_id, organization_id)
        REFERENCES gic.organization (tenant_id, organization_id)
        ON DELETE RESTRICT,
    CONSTRAINT person_organization_relationship_type_check
        CHECK (relationship_type IN ('REPRESENTATIVE_OF')),
    CONSTRAINT person_organization_relationship_status_check
        CHECK (status IN ('ACTIVE')),
    CONSTRAINT person_organization_relationship_valid_period_check
        CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

CREATE UNIQUE INDEX person_organization_relationship_active_unique
    ON gic.person_organization_relationship (tenant_id, person_id, organization_id, relationship_type)
    WHERE status = 'ACTIVE';

CREATE INDEX person_organization_relationship_person_list_idx
    ON gic.person_organization_relationship (tenant_id, person_id, status, valid_from DESC, relationship_id);

CREATE INDEX person_organization_relationship_organization_fk_idx
    ON gic.person_organization_relationship (tenant_id, organization_id);

ALTER TABLE gic.person_organization_relationship
    ADD CONSTRAINT person_organization_relationship_tenant_unique UNIQUE (tenant_id, relationship_id);

CREATE TABLE gic.person_organization_relationship_audit (
    audit_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    actor varchar(160) NOT NULL,
    tenant_id uuid NOT NULL REFERENCES gic.tenants (tenant_id),
    action varchar(100) NOT NULL,
    relationship_id uuid NOT NULL,
    person_id uuid NOT NULL,
    organization_id uuid NOT NULL,
    relationship_type varchar(60) NOT NULL,
    correlation_id varchar(160) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT person_organization_relationship_audit_action_check
        CHECK (action IN ('PERSON_ORGANIZATION_RELATIONSHIP_CREATED')),
    CONSTRAINT person_organization_relationship_audit_type_check
        CHECK (relationship_type IN ('REPRESENTATIVE_OF')),
    CONSTRAINT person_organization_relationship_audit_relationship_fk
        FOREIGN KEY (tenant_id, relationship_id)
        REFERENCES gic.person_organization_relationship (tenant_id, relationship_id)
        ON DELETE RESTRICT,
    CONSTRAINT person_organization_relationship_audit_person_fk
        FOREIGN KEY (tenant_id, person_id)
        REFERENCES gic.person (tenant_id, person_id)
        ON DELETE RESTRICT,
    CONSTRAINT person_organization_relationship_audit_organization_fk
        FOREIGN KEY (tenant_id, organization_id)
        REFERENCES gic.organization (tenant_id, organization_id)
        ON DELETE RESTRICT
);

ALTER TABLE gic.person_organization_relationship ENABLE ROW LEVEL SECURITY;
ALTER TABLE gic.person_organization_relationship FORCE ROW LEVEL SECURITY;
ALTER TABLE gic.person_organization_relationship_audit ENABLE ROW LEVEL SECURITY;
ALTER TABLE gic.person_organization_relationship_audit FORCE ROW LEVEL SECURITY;

CREATE POLICY person_organization_relationship_tenant_policy
    ON gic.person_organization_relationship
    USING (
        tenant_id = nullif(current_setting('atlas.current_tenant', true), '')::uuid
        OR current_setting('atlas.platform_access', true) = 'true'
    )
    WITH CHECK (
        tenant_id = nullif(current_setting('atlas.current_tenant', true), '')::uuid
        OR current_setting('atlas.platform_access', true) = 'true'
    );

CREATE POLICY person_organization_relationship_audit_tenant_policy
    ON gic.person_organization_relationship_audit
    USING (
        tenant_id = nullif(current_setting('atlas.current_tenant', true), '')::uuid
        OR current_setting('atlas.platform_access', true) = 'true'
    )
    WITH CHECK (
        tenant_id = nullif(current_setting('atlas.current_tenant', true), '')::uuid
        OR current_setting('atlas.platform_access', true) = 'true'
    );
