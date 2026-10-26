CREATE TABLE notifications (
    id varchar(255) PRIMARY KEY,
    idempotency_key varchar(500) UNIQUE NOT NULL,
    channel varchar(50) NOT NULL,
    recipient varchar(500) NOT NULL,
    template_id varchar(255) NOT NULL,
    payload jsonb NOT NULL,
    status varchar(50) NOT NULL,
    sent_at timestamp,
    failed_at timestamp,
    error_message text,
    created_at timestamp NOT NULL,
    updated_at timestamp NOT NULL
);
CREATE INDEX idx_notifications_status ON notifications (status);
INSERT INTO notifications (id, idempotency_key, channel, recipient, template_id, payload, status, created_at, updated_at) VALUES
('ntf-c0000000-0000-4000-8000-000000000001', 'waitlist.welcome:wl-50000000-0000-4000-8000-000000000001', 'EMAIL', 'recipient@example.invalid', 'waitlist.welcome', '{"waitlistEntryId":"wl-50000000-0000-4000-8000-000000000001","waitlistName":"Keep wl-50000000-0000-4000-8000-000000000001 literal","locale":"en"}', 'FAILED', now(), now()),
('ntf-c0000000-0000-4000-8000-000000000002', 'retry:ntf-c0000000-0000-4000-8000-000000000001:opaque-key', 'EMAIL', 'recipient@example.invalid', 'waitlist.welcome', '{"waitlistEntryId":"wl-50000000-0000-4000-8000-000000000001","waitlistName":"Keep wl-50000000-0000-4000-8000-000000000001 literal","locale":"en"}', 'PENDING', now(), now()),
('ntf-c0000000-0000-4000-8000-000000000003', 'invitation:60000000-0000-4000-8000-000000000001:initial', 'EMAIL', 'recipient@example.invalid', 'platform.invitation', '{"invitationId":"60000000-0000-4000-8000-000000000001","email":"recipient@example.invalid","workspaceName":"Keep legacy-audit-report literal","target":"NEW_WORKSPACE","locale":"es"}', 'SENT', now(), now()),
('ntf-c0000000-0000-4000-8000-000000000004', 'governance.takedown.reported:legacy-audit-report:recipient@example.invalid', 'EMAIL', 'recipient@example.invalid', 'governance.takedown.reported', '{"reportId":"legacy-audit-report","assetId":"legacy-audit-asset","reason":"Keep legacy-audit-report literal","reporterEmail":"reporter@example.invalid","mediaReferenceUrl":"https://example.invalid/legacy-audit-asset?token=opaque-token"}', 'SENT', now(), now()),
('ntf-c0000000-0000-4000-8000-000000000005', 'governance.takedown.approved:legacy-audit-report:recipient@example.invalid', 'EMAIL', 'recipient@example.invalid', 'governance.takedown.approved', '{"reportId":"legacy-audit-report","assetId":"legacy-audit-asset"}', 'SENT', now(), now()),
('ntf-c0000000-0000-4000-8000-000000000006', 'governance.takedown.rejected:legacy-audit-report:recipient@example.invalid', 'EMAIL', 'recipient@example.invalid', 'governance.takedown.rejected', '{"reportId":"legacy-audit-report","assetId":"legacy-audit-asset","rejectionReason":"Keep ntf-c0000000-0000-4000-8000-000000000001 literal"}', 'SENT', now(), now()),
('c0000000-0000-4000-8000-000000000007', 'opaque-already-native-key', 'EMAIL', 'recipient@example.invalid', 'waitlist.welcome', '{"waitlistEntryId":"wl-50000000-0000-4000-8000-000000000001","waitlistName":"Raw UUID","locale":"en"}', 'PENDING', now(), now());
UPDATE notifications SET failed_at = created_at, error_message = 'smtp timeout' WHERE status = 'FAILED';
UPDATE notifications SET sent_at = created_at WHERE status = 'SENT';
CREATE TABLE uuid_test_notifications_snapshot AS
SELECT id AS legacy_id, to_jsonb(n) - 'id' - 'payload' AS preserved, payload FROM notifications n;
INSERT INTO platform_admin_audit_events (event_id, occurred_at, operator_principal_id, operator_platform_roles, action, target_type, target_id, result, metadata) VALUES
('b0000000-0000-4000-8000-000000000001', now(), '10000000-0000-4000-8000-000000000001', '{PLATFORM_ADMIN}', 'NOTIFICATION_RETRIED', 'NOTIFICATION', 'ntf-c0000000-0000-4000-8000-000000000001', 'SUCCEEDED', '{"notificationId":"ntf-c0000000-0000-4000-8000-000000000001","retryNotificationId":"ntf-c0000000-0000-4000-8000-000000000002","channel":"EMAIL","templateId":"waitlist.welcome","retryOutcome":"QUEUED","priorStatus":"FAILED","priorError":"Keep ntf-c0000000-0000-4000-8000-000000000001 literal"}'),
('b0000000-0000-4000-8000-000000000002', now(), '10000000-0000-4000-8000-000000000001', '{PLATFORM_ADMIN}', 'NOTIFICATION_RETRIED', 'NOTIFICATION', 'ntf-c0000000-0000-4000-8000-000000000002', 'REJECTED', '{"notificationId":"ntf-c0000000-0000-4000-8000-000000000002","channel":"EMAIL","templateId":"waitlist.welcome","retryOutcome":"ALREADY_PENDING","priorStatus":"PENDING","priorError":""}');
