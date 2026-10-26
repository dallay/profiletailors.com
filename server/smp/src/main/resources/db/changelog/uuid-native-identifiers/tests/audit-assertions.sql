DO $$
BEGIN
    IF (SELECT count(*) FROM platform_admin_audit_events) <> 7 THEN RAISE EXCEPTION 'Admin audit row count changed'; END IF;
    IF (SELECT target_id FROM platform_admin_audit_events WHERE target_type = 'WaitlistEntry')
        <> (SELECT native_id::text FROM uuid_identifier_migration_map WHERE entity_table = 'waitlist_entries' AND legacy_id = 'wl-50000000-0000-4000-8000-000000000001') THEN
        RAISE EXCEPTION 'Admin audit waitlist target mismatch';
    END IF;
    IF EXISTS (SELECT 1 FROM platform_admin_audit_events WHERE target_type IN ('Principal', 'USER') AND target_id <> '10000000-0000-4000-8000-000000000002')
        OR (SELECT metadata::jsonb FROM platform_admin_audit_events WHERE target_type = 'Principal') <> '{"revokedSessionCount":"2"}'::jsonb
        OR EXISTS (SELECT 1 FROM platform_admin_audit_events WHERE target_type IN ('USER', 'Invitation') AND metadata IS NOT NULL)
        OR (SELECT metadata::jsonb FROM platform_admin_audit_events WHERE target_type = 'WaitlistInvitation') <> '{}'::jsonb THEN
        RAISE EXCEPTION 'Admin audit principal target or semantic metadata mismatch';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM platform_admin_audit_events a JOIN invitations i ON i.id::text = a.target_id WHERE a.target_type = 'Invitation')
        OR NOT EXISTS (SELECT 1 FROM platform_admin_audit_events a JOIN waitlist_invitations i ON i.id::text = a.target_id WHERE a.target_type = 'WaitlistInvitation')
        OR NOT EXISTS (
            SELECT 1 FROM platform_admin_audit_events a JOIN takedown_reports r ON r.report_id::text = a.target_id
            WHERE a.target_type = 'TAKEDOWN_REPORT' AND a.metadata::jsonb = jsonb_build_object('reportId', r.report_id::text, 'workspaceId', r.workspace_id::text, 'assetId', r.asset_id::text)
        ) THEN RAISE EXCEPTION 'Admin audit related entity mapping mismatch'; END IF;
    IF (SELECT target_id FROM platform_admin_audit_events WHERE target_type = 'CONFIGURATION') <> 'registration.mode'
        OR (SELECT metadata::jsonb FROM platform_admin_audit_events WHERE target_type = 'CONFIGURATION') <> '{"previousMode":"CLOSED","newMode":"INVITE_ONLY"}'::jsonb
        OR (SELECT metadata::jsonb FROM platform_admin_audit_events WHERE target_type = 'WaitlistEntry') <> '{"role":"PLATFORM_ADMIN","workspaceId":"20000000-0000-4000-8000-000000000001"}'::jsonb THEN
        RAISE EXCEPTION 'Semantic admin audit fields changed';
    END IF;
END $$;
