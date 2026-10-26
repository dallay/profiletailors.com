DO $$
DECLARE row record; expected jsonb; mapped uuid;
BEGIN
    IF (SELECT data_type FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'notifications' AND column_name = 'id') IS DISTINCT FROM 'uuid' THEN
        RAISE EXCEPTION 'Notification ID is not native UUID';
    END IF;
    IF (SELECT count(*) FROM notifications) <> 7 OR (SELECT count(*) FROM uuid_identifier_migration_map WHERE entity_table = 'notifications') <> 7 THEN
        RAISE EXCEPTION 'Notification count or mapping changed';
    END IF;
    IF (SELECT count(*) FROM notifications n JOIN uuid_identifier_migration_map m ON m.entity_table = 'notifications' AND m.native_id = n.id
        JOIN uuid_test_notifications_snapshot s ON s.legacy_id = m.legacy_id) <> 7 THEN
        RAISE EXCEPTION 'Notification mapping relationship lost';
    END IF;
    FOR row IN SELECT n.*, s.legacy_id, s.payload AS original_payload, s.preserved
        FROM notifications n JOIN uuid_identifier_migration_map m ON m.entity_table = 'notifications' AND m.native_id = n.id
        JOIN uuid_test_notifications_snapshot s ON s.legacy_id = m.legacy_id LOOP
        IF row.id <> substring(row.legacy_id from '[0-9a-f-]{36}$')::uuid THEN RAISE EXCEPTION 'Notification UUID suffix mismatch'; END IF;
        IF (SELECT to_jsonb(n) - 'id' - 'payload' FROM notifications n WHERE n.id = row.id) IS DISTINCT FROM row.preserved THEN
            RAISE EXCEPTION 'Opaque notification columns changed';
        END IF;
        expected := row.original_payload;
        IF row.template_id = 'waitlist.welcome' THEN
            expected := jsonb_set(expected, '{waitlistEntryId}', to_jsonb(md5('profiletailors:uuid-native-identifiers:waitlist_entries:wl-50000000-0000-4000-8000-000000000001')::uuid::text));
        ELSIF row.template_id = 'platform.invitation' THEN
            expected := jsonb_set(expected, '{invitationId}', to_jsonb('60000000-0000-4000-8000-000000000001'::text));
        ELSE
            SELECT native_id INTO STRICT mapped FROM uuid_identifier_migration_map WHERE entity_table = 'takedown_reports' AND legacy_id = 'legacy-audit-report';
            expected := jsonb_set(expected, '{reportId}', to_jsonb(mapped::text));
            SELECT native_id INTO STRICT mapped FROM uuid_identifier_migration_map WHERE entity_table = 'media_assets' AND legacy_id = 'legacy-audit-asset';
            expected := jsonb_set(expected, '{assetId}', to_jsonb(mapped::text));
        END IF;
        IF row.payload IS DISTINCT FROM expected THEN RAISE EXCEPTION 'Notification payload mapping or opaque data mismatch: %', row.id; END IF;
    END LOOP;
    IF (SELECT count(*) FROM platform_admin_audit_events WHERE target_type = 'NOTIFICATION') <> 2 OR EXISTS (
        SELECT 1 FROM platform_admin_audit_events a LEFT JOIN notifications n ON n.id::text = a.target_id
        WHERE a.target_type = 'NOTIFICATION' AND (n.id IS NULL OR a.metadata::jsonb ->> 'notificationId' <> a.target_id)
    ) THEN RAISE EXCEPTION 'Notification audit target mismatch'; END IF;
    IF (SELECT metadata::jsonb FROM platform_admin_audit_events WHERE event_id = 'b0000000-0000-4000-8000-000000000001') IS DISTINCT FROM
        '{"notificationId":"c0000000-0000-4000-8000-000000000001","retryNotificationId":"c0000000-0000-4000-8000-000000000002","channel":"EMAIL","templateId":"waitlist.welcome","retryOutcome":"QUEUED","priorStatus":"FAILED","priorError":"Keep ntf-c0000000-0000-4000-8000-000000000001 literal"}'::jsonb THEN
        RAISE EXCEPTION 'Notification retry successor mapping or semantic metadata mismatch';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM platform_admin_audit_events a JOIN notifications n ON n.id::text = a.metadata::jsonb ->> 'retryNotificationId' WHERE a.event_id = 'b0000000-0000-4000-8000-000000000001') THEN
        RAISE EXCEPTION 'Notification retry successor orphan';
    END IF;
END $$;
