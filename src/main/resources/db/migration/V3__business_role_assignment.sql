CREATE TABLE IF NOT EXISTS gic.business_role_assignment (
    assignment_id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES gic.tenants (tenant_id),
    person_id uuid NOT NULL,
    role_type varchar(40) NOT NULL,
    status varchar(40) NOT NULL,
    valid_from date NOT NULL,
    valid_to date,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(160) NOT NULL,
    CONSTRAINT business_role_assignment_person_fk
        FOREIGN KEY (tenant_id, person_id)
        REFERENCES gic.person (tenant_id, person_id)
        ON DELETE RESTRICT,
    CONSTRAINT business_role_assignment_role_check
        CHECK (role_type IN ('SOCIO', 'CLIENTE', 'PROVEEDOR')),
    CONSTRAINT business_role_assignment_status_check
        CHECK (status IN ('ACTIVE', 'ENDED')),
    CONSTRAINT business_role_assignment_valid_period_check
        CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

CREATE UNIQUE INDEX IF NOT EXISTS business_role_assignment_active_unique
    ON gic.business_role_assignment (tenant_id, person_id, role_type)
    WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS business_role_assignment_person_order_idx
    ON gic.business_role_assignment (tenant_id, person_id, valid_from, role_type, assignment_id);

CREATE TABLE IF NOT EXISTS gic.business_role_assignment_audit (
    audit_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    actor varchar(160) NOT NULL,
    tenant_id uuid NOT NULL REFERENCES gic.tenants (tenant_id),
    action varchar(80) NOT NULL,
    person_id uuid NOT NULL,
    assignment_id uuid NOT NULL,
    role_type varchar(40) NOT NULL,
    correlation_id varchar(160),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT business_role_assignment_audit_action_check
        CHECK (action IN ('BUSINESS_ROLE_ASSIGNED')),
    CONSTRAINT business_role_assignment_audit_person_fk
        FOREIGN KEY (tenant_id, person_id)
        REFERENCES gic.person (tenant_id, person_id)
        ON DELETE RESTRICT
);

ALTER TABLE gic.business_role_assignment ENABLE ROW LEVEL SECURITY;
ALTER TABLE gic.business_role_assignment FORCE ROW LEVEL SECURITY;
ALTER TABLE gic.business_role_assignment_audit ENABLE ROW LEVEL SECURITY;
ALTER TABLE gic.business_role_assignment_audit FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS business_role_assignment_tenant_policy ON gic.business_role_assignment;
DROP POLICY IF EXISTS business_role_assignment_audit_tenant_policy ON gic.business_role_assignment_audit;

CREATE POLICY business_role_assignment_tenant_policy
    ON gic.business_role_assignment
    USING (
        tenant_id = nullif(current_setting('atlas.current_tenant', true), '')::uuid
        OR current_setting('atlas.platform_access', true) = 'true'
    )
    WITH CHECK (
        tenant_id = nullif(current_setting('atlas.current_tenant', true), '')::uuid
        OR current_setting('atlas.platform_access', true) = 'true'
    );

CREATE POLICY business_role_assignment_audit_tenant_policy
    ON gic.business_role_assignment_audit
    USING (
        tenant_id = nullif(current_setting('atlas.current_tenant', true), '')::uuid
        OR current_setting('atlas.platform_access', true) = 'true'
    )
    WITH CHECK (
        tenant_id = nullif(current_setting('atlas.current_tenant', true), '')::uuid
        OR current_setting('atlas.platform_access', true) = 'true'
    );
