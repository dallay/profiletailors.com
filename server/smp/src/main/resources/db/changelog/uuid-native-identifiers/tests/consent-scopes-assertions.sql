DO $$
DECLARE item record; expected_waitlist uuid; expected_workspace uuid;
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema()
        AND table_name IN ('consent_records', 'consent_record_events')
        AND column_name IN ('workspace_id', 'waitlist_id') AND (data_type <> 'uuid' OR is_nullable <> 'YES'))
        OR (SELECT count(*) FROM information_schema.columns WHERE table_schema = current_schema()
            AND table_name IN ('consent_records', 'consent_record_events') AND column_name IN ('workspace_id', 'waitlist_id')) <> 4 THEN
        RAISE EXCEPTION 'Consent scope UUID/nullability shape mismatch';
    END IF;
    IF (SELECT count(*) FROM workspaces) <> (SELECT workspace_count FROM uuid_test_consent_scope_counts)
        OR (SELECT count(*) FROM waitlists) <> (SELECT waitlist_count FROM uuid_test_consent_scope_counts)
        OR (SELECT count(*) FROM consent_records) <> (SELECT record_count FROM uuid_test_consent_scope_counts)
        OR (SELECT count(*) FROM consent_record_events) <> (SELECT event_count FROM uuid_test_consent_scope_counts) THEN
        RAISE EXCEPTION 'Consent migration changed counts or fabricated a scope';
    END IF;
    IF EXISTS (SELECT 1 FROM consent_records WHERE (workspace_id IS NULL) = (waitlist_id IS NULL))
        OR EXISTS (SELECT 1 FROM consent_record_events WHERE (workspace_id IS NULL) = (waitlist_id IS NULL)) THEN
        RAISE EXCEPTION 'Consent scope XOR failed';
    END IF;
    FOR item IN SELECT s.*, c.workspace_id, c.waitlist_id, to_jsonb(c) - 'id' - 'workspace_id' - 'waitlist_id' AS actual
        FROM uuid_test_consent_snapshot s JOIN uuid_identifier_migration_map m ON m.entity_table = 'consent_records' AND m.legacy_id = s.legacy_id
        JOIN consent_records c ON c.id = m.native_id LOOP
        IF item.actual IS DISTINCT FROM item.evidence THEN RAISE EXCEPTION 'Consent legal evidence changed: %', item.legacy_id; END IF;
        IF item.legacy_scope LIKE 'waitlist:%' THEN
            SELECT id INTO STRICT expected_waitlist FROM waitlists WHERE key = substring(item.legacy_scope FROM length('waitlist:') + 1);
            IF item.workspace_id IS NOT NULL OR item.waitlist_id IS DISTINCT FROM expected_waitlist THEN
                RAISE EXCEPTION 'Historical WAITLIST did not resolve to the real list';
            END IF;
        ELSE
            SELECT native_id INTO STRICT expected_workspace FROM uuid_identifier_migration_map WHERE entity_table = 'workspaces' AND legacy_id = item.legacy_scope;
            IF item.waitlist_id IS NOT NULL OR item.workspace_id IS DISTINCT FROM expected_workspace THEN RAISE EXCEPTION 'WORKSPACE consent scope changed'; END IF;
        END IF;
    END LOOP;
    IF (SELECT count(*) FROM consent_records c JOIN uuid_identifier_migration_map m ON m.entity_table = 'consent_records' AND m.native_id = c.id JOIN uuid_test_consent_snapshot s ON s.legacy_id = m.legacy_id) <> 4 THEN
        RAISE EXCEPTION 'Historical consent mapping relationship lost';
    END IF;
    IF EXISTS (
        SELECT 1 FROM consent_record_events e LEFT JOIN consent_records c ON c.id = e.consent_id
        WHERE c.id IS NULL OR (e.workspace_id, e.waitlist_id) IS DISTINCT FROM (c.workspace_id, c.waitlist_id)
    ) THEN RAISE EXCEPTION 'Consent event/projection scope mismatch'; END IF;
    IF (SELECT count(*) FROM consent_record_events e JOIN uuid_identifier_migration_map m ON m.entity_table = 'consent_record_events' AND m.native_id = e.id JOIN uuid_test_consent_event_snapshot s ON s.legacy_id = m.legacy_id) <> 4
        OR EXISTS (
            SELECT 1 FROM uuid_test_consent_event_snapshot s
            JOIN uuid_identifier_migration_map m ON m.entity_table = 'consent_record_events' AND m.legacy_id = s.legacy_id
            JOIN consent_record_events e ON e.id = m.native_id
            JOIN uuid_identifier_migration_map parent ON parent.entity_table = 'consent_records' AND parent.legacy_id = s.legacy_consent_id
            WHERE e.consent_id <> parent.native_id OR to_jsonb(e) - 'id' - 'consent_id' - 'workspace_id' - 'waitlist_id' IS DISTINCT FROM s.evidence
        ) THEN RAISE EXCEPTION 'Consent event evidence or identity changed'; END IF;
    IF (SELECT key FROM waitlists WHERE id = (SELECT native_id FROM uuid_identifier_migration_map WHERE entity_table = 'waitlists' AND legacy_id = 'historical-list-a')) IS DISTINCT FROM 'public-launch'
        OR (SELECT key FROM waitlists WHERE id = (SELECT native_id FROM uuid_identifier_migration_map WHERE entity_table = 'waitlists' AND legacy_id = 'historical-list-b')) IS DISTINCT FROM 'public:beta' THEN
        RAISE EXCEPTION 'Semantic waitlist keys changed';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'f' AND connamespace = 'public'::regnamespace AND NOT convalidated)
        OR EXISTS (SELECT 1 FROM pg_index WHERE NOT indisvalid) THEN RAISE EXCEPTION 'Invalid consent constraint or index'; END IF;
END $$;
