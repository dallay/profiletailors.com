INSERT INTO waitlists (id, key, name, context, status) VALUES
('historical-list-a', 'public-launch', 'Public launch', 'test', 'ACTIVE'),
('historical-list-b', 'public:beta', 'Public beta', 'test', 'ACTIVE');
INSERT INTO consent_records (id, workspace_id, subject_kind, subject_value, consent_type, purpose, policy_version, source, locale, status, given_at, withdrawn_at, withdrawal_reason, version) VALUES
('cs-d0000000-0000-4000-8000-000000000001', 'waitlist:public-launch', 'ANONYMOUS', 'shared-consent-subject', 'EXPLICIT', 'scope-regression', 'v1', 'public-form', 'es', 'ACTIVE', now(), NULL, NULL, 3),
('cs-d0000000-0000-4000-8000-000000000002', 'waitlist:public:beta', 'ANONYMOUS', 'shared-consent-subject', 'EXPLICIT', 'scope-regression', 'v1', 'public-form', 'en', 'ACTIVE', now(), NULL, NULL, 5),
('cs-d0000000-0000-4000-8000-000000000003', 'ws-20000000-0000-4000-8000-000000000001', 'ANONYMOUS', 'shared-consent-subject', 'EXPLICIT', 'scope-regression', 'v1', 'tenant-settings', 'en', 'ACTIVE', now(), NULL, NULL, 2),
('cs-d0000000-0000-4000-8000-000000000004', 'waitlist:public-launch', 'ANONYMOUS', 'shared-consent-subject', 'EXPLICIT', 'scope-regression', 'v0', 'public-form', 'es', 'WITHDRAWN', now() - interval '2 days', now() - interval '1 day', 'USER_REQUEST', 7);
INSERT INTO consent_record_events (id, consent_id, workspace_id, subject_kind, subject_value, consent_type, purpose, policy_version, source, locale, status, given_at, withdrawn_at, withdrawal_reason, event_at)
SELECT replace(id, 'cs-', 'ce-'), id, workspace_id, subject_kind, subject_value, consent_type, purpose, policy_version, source, locale, status, given_at, withdrawn_at, withdrawal_reason, now()
FROM consent_records WHERE purpose = 'scope-regression';
CREATE TABLE uuid_test_consent_snapshot AS
SELECT id AS legacy_id, workspace_id AS legacy_scope, to_jsonb(c) - 'id' - 'workspace_id' AS evidence
FROM consent_records c WHERE purpose = 'scope-regression';
CREATE TABLE uuid_test_consent_event_snapshot AS
SELECT id AS legacy_id, consent_id AS legacy_consent_id, workspace_id AS legacy_scope,
    to_jsonb(e) - 'id' - 'consent_id' - 'workspace_id' AS evidence
FROM consent_record_events e WHERE purpose = 'scope-regression';
CREATE TABLE uuid_test_consent_scope_counts AS SELECT
    (SELECT count(*) FROM workspaces) AS workspace_count,
    (SELECT count(*) FROM waitlists) AS waitlist_count,
    (SELECT count(*) FROM consent_records) AS record_count,
    (SELECT count(*) FROM consent_record_events) AS event_count;
