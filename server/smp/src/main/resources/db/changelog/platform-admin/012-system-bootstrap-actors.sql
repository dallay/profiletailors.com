ALTER TABLE platform_role_assignments ADD COLUMN actor_kind varchar(32);
UPDATE platform_role_assignments SET actor_kind = 'HUMAN';
ALTER TABLE platform_role_assignments ALTER COLUMN actor_kind SET NOT NULL;
ALTER TABLE platform_role_assignments ALTER COLUMN assigned_by DROP NOT NULL;
ALTER TABLE platform_role_assignments
    ADD CONSTRAINT ck_platform_role_assignment_actor CHECK (
        (actor_kind = 'HUMAN' AND assigned_by IS NOT NULL)
        OR (actor_kind = 'SYSTEM_BOOTSTRAP' AND assigned_by IS NULL AND role = 'PLATFORM_OWNER')
    );

ALTER TABLE platform_admin_audit_events ADD COLUMN actor_kind varchar(32);
UPDATE platform_admin_audit_events SET actor_kind = 'HUMAN';
ALTER TABLE platform_admin_audit_events ALTER COLUMN actor_kind SET NOT NULL;
ALTER TABLE platform_admin_audit_events ALTER COLUMN operator_principal_id DROP NOT NULL;
ALTER TABLE platform_admin_audit_events
    ADD CONSTRAINT ck_platform_admin_audit_actor CHECK (
        (actor_kind = 'HUMAN' AND operator_principal_id IS NOT NULL)
        OR (
            actor_kind = 'SYSTEM_BOOTSTRAP'
            AND operator_principal_id IS NULL
            AND cardinality(operator_platform_roles) = 0
            AND action IN (
                'BOOTSTRAP_INVITATION_ISSUED',
                'BOOTSTRAP_INVITATION_REPLACED',
                'BOOTSTRAP_OWNER_GRANTED'
            )
        )
    );
