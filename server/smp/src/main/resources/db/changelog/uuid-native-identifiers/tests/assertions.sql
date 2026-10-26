DO $$
BEGIN
    IF (SELECT count(*) FROM information_schema.columns WHERE table_schema = current_schema() AND table_name IN ('consent_records', 'consent_record_events')
        AND column_name IN ('workspace_id', 'waitlist_id') AND data_type = 'uuid' AND is_nullable = 'YES') <> 4 THEN
        RAISE EXCEPTION 'Consent scope columns are not nullable native UUIDs';
    END IF;
    IF (SELECT count(*) FROM ideas) <> 1 OR (SELECT column_id FROM ideas) <> 'custom' THEN RAISE EXCEPTION 'Semantic idea column changed'; END IF;
    IF (SELECT columns_json::jsonb FROM idea_board_configs) <> '[{"id":"raw","name":"Raw"},{"id":"custom","name":"Custom"}]'::jsonb THEN RAISE EXCEPTION 'Board configuration changed'; END IF;
    IF EXISTS (SELECT 1 FROM consent_records WHERE subject_kind = 'ANONYMOUS' AND subject_value <> 'opaque-hash-123') THEN RAISE EXCEPTION 'Anonymous subject changed'; END IF;
    IF EXISTS (SELECT 1 FROM consent_record_events WHERE subject_kind = 'ANONYMOUS' AND subject_value <> 'opaque-hash-123') THEN RAISE EXCEPTION 'Anonymous event subject changed'; END IF;
    IF (SELECT count(*) FROM consent_records) <> 3 OR (SELECT count(*) FROM consent_record_events) <> 3 THEN RAISE EXCEPTION 'Consent row count changed'; END IF;
    IF EXISTS (SELECT 1 FROM consent_records WHERE subject_kind = 'USER' AND subject_value <> '10000000-0000-4000-8000-000000000001') THEN RAISE EXCEPTION 'User subject mismatch'; END IF;
    IF EXISTS (SELECT 1 FROM consent_records WHERE subject_kind = 'WORKSPACE' AND subject_value <> '20000000-0000-4000-8000-000000000001') THEN RAISE EXCEPTION 'Workspace subject mismatch'; END IF;
    IF EXISTS (SELECT 1 FROM consent_record_events e JOIN consent_records c ON c.id = e.consent_id WHERE e.subject_value <> c.subject_value) THEN RAISE EXCEPTION 'Consent event subject mismatch'; END IF;
    IF (SELECT source_reference_id FROM invitations) <> md5('profiletailors:uuid-native-identifiers:waitlist_entries:wl-50000000-0000-4000-8000-000000000001')::uuid::text
        OR NOT EXISTS (SELECT 1 FROM invitations i JOIN waitlist_entries e ON e.id::text = i.source_reference_id) THEN RAISE EXCEPTION 'Waitlist source reference mismatch'; END IF;
    IF (SELECT token_hash FROM invitations) <> 'opaque-token' OR (SELECT candidate_key FROM invitations) <> 'opaque-candidate' OR (SELECT token_hash FROM waitlist_invitations) <> 'opaque-waitlist-token' THEN RAISE EXCEPTION 'Opaque invitation fields changed'; END IF;
    IF (SELECT created_by::text FROM waitlist_invitations) <> '10000000-0000-4000-8000-000000000001' THEN RAISE EXCEPTION 'Invitation actor mismatch'; END IF;
    IF (SELECT count(*) FROM credential_deletion_tombstones) <> 1 OR EXISTS (SELECT 1 FROM credential_deletion_tombstones WHERE connection_id <> md5('profiletailors:uuid-native-identifiers:social_connections:deleted-80000000-0000-4000-8000-000000000001')::uuid OR credential_reference <> 'opaque-credential-ref' OR provider_connection_ref <> 'opaque-provider-ref' OR activity_id <> 'pa-006') THEN RAISE EXCEPTION 'Historical tombstone or opaque fields changed'; END IF;
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'f' AND connamespace = 'public'::regnamespace AND NOT convalidated) THEN RAISE EXCEPTION 'Unvalidated foreign key'; END IF;
    IF EXISTS (SELECT 1 FROM pg_index WHERE NOT indisvalid) THEN RAISE EXCEPTION 'Invalid index'; END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND (table_name, column_name) IN (('ideas', 'id'), ('ideas', 'workspace_id'), ('consent_records', 'id'), ('consent_record_events', 'consent_id'), ('waitlist_entries', 'id')) AND data_type <> 'uuid') THEN RAISE EXCEPTION 'Non-native identifier'; END IF;
END $$;
SELECT count(*) AS validated_foreign_keys FROM pg_constraint WHERE contype = 'f' AND connamespace = 'public'::regnamespace AND convalidated;
SELECT count(*) AS valid_indexes FROM pg_index WHERE indisvalid;
