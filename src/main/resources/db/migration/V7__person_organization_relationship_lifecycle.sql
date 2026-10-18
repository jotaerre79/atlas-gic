ALTER TABLE gic.person_organization_relationship
    DROP CONSTRAINT person_organization_relationship_status_check;

ALTER TABLE gic.person_organization_relationship
    ADD CONSTRAINT person_organization_relationship_status_check
        CHECK (status IN ('ACTIVE', 'ENDED'));

ALTER TABLE gic.person_organization_relationship
    ADD CONSTRAINT person_organization_relationship_lifecycle_check
        CHECK ((status = 'ACTIVE' AND valid_to IS NULL)
            OR (status = 'ENDED' AND valid_to IS NOT NULL));

ALTER TABLE gic.person_organization_relationship_audit
    DROP CONSTRAINT person_organization_relationship_audit_action_check;

ALTER TABLE gic.person_organization_relationship_audit
    ADD CONSTRAINT person_organization_relationship_audit_action_check
        CHECK (action IN (
            'PERSON_ORGANIZATION_RELATIONSHIP_CREATED',
            'PERSON_ORGANIZATION_RELATIONSHIP_ENDED'
        ));
