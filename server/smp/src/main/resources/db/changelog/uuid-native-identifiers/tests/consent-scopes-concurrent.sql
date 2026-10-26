BEGIN;
WITH granted AS (
    INSERT INTO consent_records (id, :"scope_column", subject_kind, subject_value, consent_type, purpose, policy_version, source, locale, status, given_at)
    VALUES (gen_random_uuid(), :'scope_id'::uuid, 'ANONYMOUS', 'concurrent-subject', 'EXPLICIT', 'scope-concurrent', 'v1', 'test', 'en', 'ACTIVE', now())
    ON CONFLICT (:"scope_column", subject_kind, subject_value, purpose, policy_version) WHERE status = 'ACTIVE' DO NOTHING RETURNING *
) INSERT INTO consent_record_events (id, consent_id, workspace_id, waitlist_id, subject_kind, subject_value, consent_type, purpose, policy_version, source, locale, status, given_at, event_at)
    SELECT gen_random_uuid(), id, workspace_id, waitlist_id, subject_kind, subject_value, consent_type, purpose, policy_version, source, locale, status, given_at, now() FROM granted;
SELECT pg_sleep(2);
COMMIT;
