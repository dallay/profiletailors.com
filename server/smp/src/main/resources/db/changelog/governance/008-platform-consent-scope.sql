ALTER TABLE consent_records ADD COLUMN scope_kind varchar(16);
ALTER TABLE consent_record_events ADD COLUMN scope_kind varchar(16);

UPDATE consent_records
SET scope_kind = CASE
    WHEN workspace_id IS NOT NULL AND waitlist_id IS NULL THEN 'WORKSPACE'
    WHEN workspace_id IS NULL AND waitlist_id IS NOT NULL THEN 'WAITLIST'
END;

UPDATE consent_record_events
SET scope_kind = CASE
    WHEN workspace_id IS NOT NULL AND waitlist_id IS NULL THEN 'WORKSPACE'
    WHEN workspace_id IS NULL AND waitlist_id IS NOT NULL THEN 'WAITLIST'
END;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM consent_records WHERE scope_kind IS NULL) THEN
        RAISE EXCEPTION 'Cannot infer historical consent scope';
    END IF;
    IF EXISTS (SELECT 1 FROM consent_record_events WHERE scope_kind IS NULL) THEN
        RAISE EXCEPTION 'Cannot infer historical consent event scope';
    END IF;
    IF EXISTS (
        SELECT 1
        FROM consent_record_events e
        LEFT JOIN consent_records c ON c.id = e.consent_id
        WHERE c.id IS NULL
           OR (e.scope_kind, e.workspace_id, e.waitlist_id)
              IS DISTINCT FROM (c.scope_kind, c.workspace_id, c.waitlist_id)
    ) THEN
        RAISE EXCEPTION 'Historical consent event/projection scope mismatch';
    END IF;
END
$$;

ALTER TABLE consent_records ALTER COLUMN scope_kind SET NOT NULL;
ALTER TABLE consent_record_events ALTER COLUMN scope_kind SET NOT NULL;
ALTER TABLE consent_records DROP CONSTRAINT ck_consent_records_scope;
ALTER TABLE consent_record_events DROP CONSTRAINT ck_consent_record_events_scope;

ALTER TABLE consent_records
    ADD CONSTRAINT ck_consent_records_scope CHECK (
        (scope_kind = 'WORKSPACE' AND workspace_id IS NOT NULL AND waitlist_id IS NULL)
        OR (scope_kind = 'WAITLIST' AND workspace_id IS NULL AND waitlist_id IS NOT NULL)
        OR (scope_kind = 'PLATFORM' AND workspace_id IS NULL AND waitlist_id IS NULL)
    ),
    ADD CONSTRAINT uq_consent_records_id_scope_kind UNIQUE (id, scope_kind);

ALTER TABLE consent_record_events
    ADD CONSTRAINT ck_consent_record_events_scope CHECK (
        (scope_kind = 'WORKSPACE' AND workspace_id IS NOT NULL AND waitlist_id IS NULL)
        OR (scope_kind = 'WAITLIST' AND workspace_id IS NULL AND waitlist_id IS NOT NULL)
        OR (scope_kind = 'PLATFORM' AND workspace_id IS NULL AND waitlist_id IS NULL)
    ),
    ADD CONSTRAINT fk_consent_events_projection_scope
        FOREIGN KEY (consent_id, scope_kind) REFERENCES consent_records (id, scope_kind);

DROP INDEX uq_consent_active_workspace;
DROP INDEX uq_consent_active_waitlist;

CREATE UNIQUE INDEX uq_consent_active_workspace ON consent_records
    (scope_kind, workspace_id, subject_kind, subject_value, purpose, policy_version)
    WHERE status = 'ACTIVE' AND scope_kind = 'WORKSPACE';
CREATE UNIQUE INDEX uq_consent_active_waitlist ON consent_records
    (scope_kind, waitlist_id, subject_kind, subject_value, purpose, policy_version)
    WHERE status = 'ACTIVE' AND scope_kind = 'WAITLIST';
CREATE UNIQUE INDEX uq_consent_active_platform ON consent_records
    (scope_kind, subject_kind, subject_value, purpose, policy_version)
    WHERE status = 'ACTIVE' AND scope_kind = 'PLATFORM';

CREATE INDEX idx_consent_records_scope_identity ON consent_records
    (scope_kind, workspace_id, waitlist_id, subject_kind, subject_value, purpose);
CREATE INDEX idx_consent_events_scope_identity ON consent_record_events
    (scope_kind, workspace_id, waitlist_id, subject_kind, subject_value, purpose, event_at);
