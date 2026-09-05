ALTER TABLE gic.business_role_assignment
    ADD COLUMN IF NOT EXISTS ended_at timestamptz,
    ADD COLUMN IF NOT EXISTS ended_by varchar(160),
    ADD COLUMN IF NOT EXISTS end_reason varchar(500);

ALTER TABLE gic.business_role_assignment
    DROP CONSTRAINT IF EXISTS business_role_assignment_end_metadata_check;

ALTER TABLE gic.business_role_assignment
    ADD CONSTRAINT business_role_assignment_end_metadata_check
        CHECK (
            (status = 'ACTIVE' AND ended_at IS NULL AND ended_by IS NULL)
            OR
            (status = 'ENDED' AND valid_to IS NOT NULL AND ended_at IS NOT NULL AND ended_by IS NOT NULL)
        );

ALTER TABLE gic.business_role_assignment_audit
    ADD COLUMN IF NOT EXISTS valid_to date,
    ADD COLUMN IF NOT EXISTS end_reason varchar(500);

ALTER TABLE gic.business_role_assignment_audit
    DROP CONSTRAINT IF EXISTS business_role_assignment_audit_action_check;

ALTER TABLE gic.business_role_assignment_audit
    ADD CONSTRAINT business_role_assignment_audit_action_check
        CHECK (action IN ('BUSINESS_ROLE_ASSIGNED', 'BUSINESS_ROLE_ENDED'));
