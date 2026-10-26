SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30min';
CREATE TEMP TABLE uuid_columns (table_name text, column_name text, entity_table text, entity_column text) ON COMMIT DROP;
INSERT INTO uuid_columns VALUES
    ('principals', 'id', 'principals', 'id'),
    ('user_identities', 'principal_id', 'principals', 'id'),
    ('local_password_credentials', 'principal_id', 'principals', 'id'),
    ('email_verification_tokens', 'id', 'email_verification_tokens', 'id'),
    ('password_reset_tokens', 'id', 'password_reset_tokens', 'id'),
    ('password_reset_tokens', 'principal_id', 'principals', 'id'),
    ('password_reset_notification_failures', 'id', 'password_reset_notification_failures', 'id'),
    ('password_reset_notification_failures', 'principal_id', 'principals', 'id'),
    ('user_preferences', 'principal_id', 'principals', 'id'),
    ('service_account_credentials', 'id', 'service_account_credentials', 'id'),
    ('service_account_credentials', 'principal_id', 'principals', 'id'),
    ('api_key_credentials', 'id', 'api_key_credentials', 'id'),
    ('api_key_credentials', 'principal_id', 'principals', 'id'),
    ('api_key_credentials', 'replaced_by_credential_id', 'api_key_credentials', 'id'),
    ('api_key_credentials', 'replaced_credential_id', 'api_key_credentials', 'id'),
    ('refresh_sessions', 'id', 'refresh_sessions', 'id'),
    ('refresh_sessions', 'principal_id', 'principals', 'id'),
    ('refresh_sessions', 'replaced_by_session_id', 'refresh_sessions', 'id'),
    ('refresh_sessions', 'replaced_session_id', 'refresh_sessions', 'id'),
    ('workspaces', 'id', 'workspaces', 'id'),
    ('workspace_ownerships', 'workspace_id', 'workspaces', 'id'),
    ('workspace_ownerships', 'owner_principal_id', 'principals', 'id'),
    ('workspace_ownerships', 'created_by', 'principals', 'id'),
    ('workspace_memberships', 'id', 'workspace_memberships', 'id'),
    ('workspace_memberships', 'workspace_id', 'workspaces', 'id'),
    ('workspace_memberships', 'principal_id', 'principals', 'id'),
    ('permissions', 'id', 'permissions', 'id'),
    ('roles', 'id', 'roles', 'id'),
    ('role_permissions', 'role_id', 'roles', 'id'),
    ('role_permissions', 'permission_id', 'permissions', 'id'),
    ('membership_roles', 'membership_id', 'workspace_memberships', 'id'),
    ('membership_roles', 'role_id', 'roles', 'id'),
    ('workspace_direct_grants', 'id', 'workspace_direct_grants', 'id'),
    ('workspace_direct_grants', 'workspace_id', 'workspaces', 'id'),
    ('workspace_direct_grants', 'principal_id', 'principals', 'id'),
    ('workspace_direct_grants', 'permission_id', 'permissions', 'id'),
    ('workspace_entitlements', 'id', 'workspace_entitlements', 'id'),
    ('workspace_entitlements', 'workspace_id', 'workspaces', 'id'),
    ('workspace_target_scopes', 'id', 'workspace_target_scopes', 'id'),
    ('workspace_target_scopes', 'workspace_id', 'workspaces', 'id'),
    ('workspace_target_scopes', 'principal_id', 'principals', 'id'),
    ('workspace_target_scopes', 'permission_id', 'permissions', 'id'),
    ('audit_events', 'id', 'audit_events', 'id'),
    ('audit_events', 'actor_principal_id', 'principals', 'id'),
    ('audit_events', 'workspace_id', 'workspaces', 'id'),
    ('compliance_controls', 'id', 'compliance_controls', 'id'),
    ('compliance_control_applicability_rules', 'id', 'compliance_control_applicability_rules', 'id'),
    ('compliance_control_applicability_rules', 'control_id', 'compliance_controls', 'id'),
    ('compliance_control_applicability_dimensions', 'id', 'compliance_control_applicability_dimensions', 'id'),
    ('compliance_control_applicability_dimensions', 'rule_id', 'compliance_control_applicability_rules', 'id'),
    ('compliance_control_evidence_requirements', 'id', 'compliance_control_evidence_requirements', 'id'),
    ('compliance_control_evidence_requirements', 'control_id', 'compliance_controls', 'id'),
    ('compliance_evidences', 'id', 'compliance_evidences', 'id'),
    ('compliance_control_evidences', 'id', 'compliance_control_evidences', 'id'),
    ('compliance_control_evidences', 'control_id', 'compliance_controls', 'id'),
    ('compliance_control_evidences', 'evidence_id', 'compliance_evidences', 'id'),
    ('compliance_risk_acceptances', 'id', 'compliance_risk_acceptances', 'id'),
    ('compliance_risk_acceptances', 'control_id', 'compliance_controls', 'id'),
    ('compliance_risk_acceptances', 'requested_by', 'principals', 'id'),
    ('consent_records', 'id', 'consent_records', 'id'),
    ('consent_records', 'workspace_id', 'workspaces', 'id'),
    ('consent_records', 'waitlist_id', 'waitlists', 'id'),
    ('consent_record_events', 'id', 'consent_record_events', 'id'),
    ('consent_record_events', 'consent_id', 'consent_records', 'id'),
    ('consent_record_events', 'workspace_id', 'workspaces', 'id'),
    ('consent_record_events', 'waitlist_id', 'waitlists', 'id'),
    ('takedown_reports', 'report_id', 'takedown_reports', 'report_id'),
    ('takedown_reports', 'workspace_id', 'workspaces', 'id'),
    ('takedown_reports', 'asset_id', 'media_assets', 'asset_id'),
    ('takedown_reports', 'reported_by_id', 'principals', 'id'),
    ('takedown_reports', 'reviewed_by_id', 'principals', 'id'),
    ('evidence_links', 'id', 'evidence_links', 'id'),
    ('evidence_links', 'evidence_id', 'compliance_evidences', 'id'),
    ('social_connections', 'id', 'social_connections', 'id'),
    ('social_connections', 'workspace_id', 'workspaces', 'id'),
    ('social_accounts', 'id', 'social_accounts', 'id'),
    ('social_accounts', 'social_connection_id', 'social_connections', 'id'),
    ('social_accounts', 'workspace_id', 'workspaces', 'id'),
    ('publication_assets', 'id', 'publication_assets', 'id'),
    ('publication_assets', 'workspace_id', 'workspaces', 'id'),
    ('publication_assets', 'created_by_principal_id', 'principals', 'id'),
    ('publications', 'id', 'publications', 'id'),
    ('publications', 'workspace_id', 'workspaces', 'id'),
    ('publications', 'author_principal_id', 'principals', 'id'),
    ('publications', 'social_account_id', 'social_accounts', 'id'),
    ('publication_asset_links', 'publication_id', 'publications', 'id'),
    ('publication_asset_links', 'asset_id', 'publication_assets', 'id'),
    ('publication_jobs', 'id', 'publication_jobs', 'id'),
    ('publication_jobs', 'publication_id', 'publications', 'id'),
    ('publication_jobs', 'workspace_id', 'workspaces', 'id'),
    ('delivery_attempts', 'id', 'delivery_attempts', 'id'),
    ('delivery_attempts', 'publication_id', 'publications', 'id'),
    ('delivery_attempts', 'publication_job_id', 'publication_jobs', 'id'),
    ('secure_credentials', 'id', 'secure_credentials', 'id'),
    ('notification_events', 'id', 'notification_events', 'id'),
    ('notification_events', 'workspace_id', 'workspaces', 'id'),
    ('notification_events', 'social_account_id', 'social_accounts', 'id'),
    ('notification_events', 'publication_id', 'publications', 'id'),
    ('recurring_schedules', 'id', 'recurring_schedules', 'id'),
    ('recurring_schedules', 'workspace_id', 'workspaces', 'id'),
    ('recurring_schedules', 'created_by', 'principals', 'id'),
    ('recurring_schedules', 'template_post_id', 'publications', 'id'),
    ('social_content_actor_capabilities', 'id', 'social_content_actor_capabilities', 'id'),
    ('social_content_actor_capabilities', 'workspace_id', 'workspaces', 'id'),
    ('social_content_actor_capabilities', 'social_account_id', 'social_accounts', 'id'),
    ('social_content_posts', 'id', 'social_content_posts', 'id'),
    ('social_content_posts', 'workspace_id', 'workspaces', 'id'),
    ('social_content_posts', 'social_account_id', 'social_accounts', 'id'),
    ('social_content_posts', 'local_publication_id', 'publications', 'id'),
    ('social_content_comments', 'id', 'social_content_comments', 'id'),
    ('social_content_comments', 'workspace_id', 'workspaces', 'id'),
    ('social_content_comments', 'post_id', 'social_content_posts', 'id'),
    ('social_content_payload_cache', 'id', 'social_content_payload_cache', 'id'),
    ('social_content_payload_cache', 'workspace_id', 'workspaces', 'id'),
    ('social_content_sync_checkpoints', 'id', 'social_content_sync_checkpoints', 'id'),
    ('social_content_sync_checkpoints', 'workspace_id', 'workspaces', 'id'),
    ('social_content_sync_checkpoints', 'social_account_id', 'social_accounts', 'id'),
    ('social_content_webhook_events', 'id', 'social_content_webhook_events', 'id'),
    ('social_content_webhook_events', 'workspace_id', 'workspaces', 'id'),
    ('social_content_webhook_events', 'social_account_id', 'social_accounts', 'id'),
    ('social_content_reply_commands', 'id', 'social_content_reply_commands', 'id'),
    ('social_content_reply_commands', 'workspace_id', 'workspaces', 'id'),
    ('social_content_reply_commands', 'social_account_id', 'social_accounts', 'id'),
    ('bulk_import_jobs', 'id', 'bulk_import_jobs', 'id'),
    ('bulk_import_jobs', 'workspace_id', 'workspaces', 'id'),
    ('bulk_import_jobs', 'principal_id', 'principals', 'id'),
    ('bulk_import_rows', 'id', 'bulk_import_rows', 'id'),
    ('bulk_import_rows', 'job_id', 'bulk_import_jobs', 'id'),
    ('bulk_import_rows', 'publication_id', 'publications', 'id'),
    ('credential_deletion_tombstones', 'workspace_id', 'workspaces', 'id'),
    ('credential_deletion_tombstones', 'connection_id', 'social_connections', 'id'),
    ('idea_board_configs', 'workspace_id', 'workspaces', 'id'),
    ('ideas', 'id', 'ideas', 'id'),
    ('ideas', 'workspace_id', 'workspaces', 'id'),
    ('ideas', 'converted_to_publication_id', 'publications', 'id'),
    ('idempotency_records', 'id', 'idempotency_records', 'id'),
    ('idempotency_records', 'workspace_id', 'workspaces', 'id'),
    ('idempotency_records', 'principal_id', 'principals', 'id'),
    ('links', 'id', 'links', 'id'),
    ('links', 'owner_id', 'workspaces', 'id'),
    ('shortlink_idempotency', 'owner_id', 'workspaces', 'id'),
    ('link_clicks', 'id', 'link_clicks', 'id'),
    ('link_clicks', 'link_id', 'links', 'id'),
    ('platform_admin_user_control_idempotency', 'operator_principal_id', 'principals', 'id'),
    ('platform_admin_user_control_idempotency', 'target_principal_id', 'principals', 'id'),
    ('platform_admin_configuration_idempotency', 'operator_principal_id', 'principals', 'id'),
    ('hashtag_saved_sets', 'id', 'hashtag_saved_sets', 'id'),
    ('hashtag_saved_sets', 'workspace_id', 'workspaces', 'id'),
    ('media_assets', 'asset_id', 'media_assets', 'asset_id'),
    ('media_assets', 'workspace_id', 'workspaces', 'id'),
    ('workspace_upload_slots', 'workspace_id', 'workspaces', 'id'),
    ('media_rate_limits', 'workspace_id', 'workspaces', 'id'),
    ('workspace_file_blobs', 'workspace_id', 'workspaces', 'id'),
    ('waitlists', 'id', 'waitlists', 'id'),
    ('waitlist_entries', 'id', 'waitlist_entries', 'id'),
    ('waitlist_entries', 'waitlist_id', 'waitlists', 'id'),
    ('waitlist_withdrawal_urls', 'entry_id', 'waitlist_entries', 'id'),
    ('data_subject_requests', 'id', 'data_subject_requests', 'id'),
    ('data_subject_requests', 'requested_by', 'principals', 'id'),
    ('data_subject_requests', 'workspace_id', 'workspaces', 'id'),
    ('platform_role_assignments', 'id', 'platform_role_assignments', 'id'),
    ('platform_role_assignments', 'principal_id', 'principals', 'id'),
    ('platform_role_assignments', 'assigned_by', 'principals', 'id'),
    ('platform_role_assignments', 'revoked_by', 'principals', 'id'),
    ('waitlist_invitations', 'id', 'waitlist_invitations', 'id'),
    ('waitlist_invitations', 'waitlist_entry_id', 'waitlist_entries', 'id'),
    ('waitlist_invitations', 'revoked_by', 'principals', 'id'),
    ('waitlist_invitations', 'created_by', 'principals', 'id'),
    ('platform_admin_audit_events', 'operator_principal_id', 'principals', 'id'),
    ('invitations', 'id', 'invitations', 'id'),
    ('invitations', 'workspace_id', 'workspaces', 'id'),
    ('invitations', 'issued_by', 'principals', 'id'),
    ('invitations', 'accepted_principal_id', 'principals', 'id');
CREATE TEMP TABLE uuid_roots (table_name text, column_name text, legacy_prefix text) ON COMMIT DROP;
INSERT INTO uuid_roots VALUES
    ('principals', 'id', 'user|pa'),
    ('email_verification_tokens', 'id', ''),
    ('password_reset_tokens', 'id', ''),
    ('password_reset_notification_failures', 'id', ''),
    ('service_account_credentials', 'id', 'sac'),
    ('api_key_credentials', 'id', 'akc'),
    ('refresh_sessions', 'id', 'refresh-session'),
    ('workspaces', 'id', 'ws'),
    ('workspace_memberships', 'id', 'wm'),
    ('permissions', 'id', ''),
    ('roles', 'id', ''),
    ('workspace_direct_grants', 'id', 'wdg'),
    ('workspace_entitlements', 'id', 'went'),
    ('workspace_target_scopes', 'id', 'wts'),
    ('audit_events', 'id', 'audit'),
    ('compliance_controls', 'id', 'ctrl'),
    ('compliance_control_applicability_rules', 'id', 'ctrlrule'),
    ('compliance_control_applicability_dimensions', 'id', 'ctrldim'),
    ('compliance_control_evidence_requirements', 'id', ''),
    ('compliance_evidences', 'id', ''),
    ('compliance_control_evidences', 'id', ''),
    ('compliance_risk_acceptances', 'id', 'ra'),
    ('consent_records', 'id', 'cs'),
    ('consent_record_events', 'id', 'ce'),
    ('takedown_reports', 'report_id', ''),
    ('evidence_links', 'id', ''),
    ('social_connections', 'id', 'soconn'),
    ('social_accounts', 'id', 'soacc'),
    ('publication_assets', 'id', 'pa|asset'),
    ('publications', 'id', 'pub'),
    ('publication_jobs', 'id', 'pjob'),
    ('delivery_attempts', 'id', ''),
    ('secure_credentials', 'id', ''),
    ('notification_events', 'id', 'nevt'),
    ('recurring_schedules', 'id', 'recur'),
    ('social_content_actor_capabilities', 'id', ''),
    ('social_content_posts', 'id', ''),
    ('social_content_comments', 'id', ''),
    ('social_content_payload_cache', 'id', ''),
    ('social_content_sync_checkpoints', 'id', ''),
    ('social_content_webhook_events', 'id', ''),
    ('social_content_reply_commands', 'id', ''),
    ('bulk_import_jobs', 'id', 'bulk'),
    ('bulk_import_rows', 'id', 'brow'),
    ('ideas', 'id', 'idea'),
    ('idempotency_records', 'id', ''),
    ('links', 'id', ''),
    ('link_clicks', 'id', ''),
    ('hashtag_saved_sets', 'id', ''),
    ('media_assets', 'asset_id', ''),
    ('waitlists', 'id', ''),
    ('waitlist_entries', 'id', ''),
    ('data_subject_requests', 'id', 'dsr'),
    ('platform_role_assignments', 'id', ''),
    ('waitlist_invitations', 'id', ''),
    ('invitations', 'id', '');
DO $migration$
DECLARE
    item record;
    bad bigint;
    consent_table text;
    uuid_pattern text := '[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}';
BEGIN
    IF current_setting('profiletailors.uuid_cutover_approved', true) IS DISTINCT FROM 'on' THEN
        RAISE EXCEPTION 'UUID cutover requires explicit operator approval and stopped writers';
    END IF;
    IF to_regclass('notifications') IS NOT NULL THEN
        INSERT INTO uuid_columns VALUES ('notifications', 'id', 'notifications', 'id');
        INSERT INTO uuid_roots VALUES ('notifications', 'id', 'ntf');
    END IF;
    FOR item IN SELECT DISTINCT table_name FROM uuid_columns ORDER BY table_name LOOP
        EXECUTE format('LOCK TABLE %I IN ACCESS EXCLUSIVE MODE', item.table_name);
    END LOOP;
    FOREACH consent_table IN ARRAY ARRAY['consent_records', 'consent_record_events'] LOOP
        EXECUTE format('ALTER TABLE %I ADD COLUMN waitlist_id varchar(64)', consent_table);
        EXECUTE format('ALTER TABLE %I ALTER COLUMN workspace_id DROP NOT NULL', consent_table);
        EXECUTE format(
            'SELECT count(*) FROM %I c WHERE c.workspace_id LIKE ''waitlist:%%'' AND (SELECT count(*) FROM waitlists w WHERE w.key = substring(c.workspace_id FROM length(''waitlist:'') + 1)) <> 1',
            consent_table
        ) INTO bad;
        IF bad <> 0 THEN RAISE EXCEPTION 'Missing or ambiguous historical waitlist consent key in %: %', consent_table, bad; END IF;
        EXECUTE format(
            'UPDATE %I c SET waitlist_id = w.id::text, workspace_id = NULL FROM waitlists w WHERE c.workspace_id LIKE ''waitlist:%%'' AND w.key = substring(c.workspace_id FROM length(''waitlist:'') + 1)',
            consent_table
        );
        EXECUTE format('SELECT count(*) FROM %I WHERE (workspace_id IS NULL) = (waitlist_id IS NULL)', consent_table) INTO bad;
        IF bad <> 0 THEN RAISE EXCEPTION 'Invalid consent scope shape in %: %', consent_table, bad; END IF;
    END LOOP;
    IF EXISTS (
        SELECT 1 FROM consent_record_events e LEFT JOIN consent_records c ON c.id = e.consent_id
        WHERE c.id IS NULL OR (e.workspace_id, e.waitlist_id) IS DISTINCT FROM (c.workspace_id, c.waitlist_id)
    ) THEN RAISE EXCEPTION 'Historical consent event/projection scope mismatch'; END IF;
    FOR item IN SELECT * FROM uuid_roots WHERE table_name IN ('email_verification_tokens', 'idempotency_records') LOOP
        EXECUTE format('INSERT INTO uuid_identifier_migration_map SELECT %L, %I::text, md5(%L || %I::text)::uuid FROM %I ON CONFLICT (entity_table, legacy_id) DO NOTHING', item.table_name, item.column_name, 'profiletailors:uuid-native-identifiers:' || item.table_name || ':', item.column_name, item.table_name);
    END LOOP;
    FOR item IN SELECT * FROM uuid_roots WHERE table_name NOT IN ('email_verification_tokens', 'idempotency_records') LOOP
        EXECUTE format(
            'INSERT INTO uuid_identifier_migration_map SELECT %L, %I::text, CASE WHEN %I::text ~ %L THEN %I::text::uuid WHEN %I::text ~ %L THEN substring(%I::text from %L)::uuid ELSE md5(%L || %I::text)::uuid END FROM %I ON CONFLICT (entity_table, legacy_id) DO NOTHING',
            item.table_name, item.column_name, item.column_name, '^' || uuid_pattern || '$', item.column_name,
            item.column_name, '^' || CASE WHEN item.legacy_prefix = '' THEN 'a^' ELSE '((' || item.legacy_prefix || ')-)?' END || uuid_pattern || '$',
            item.column_name, '(' || uuid_pattern || ')$',
            'profiletailors:uuid-native-identifiers:' || item.table_name || ':', item.column_name, item.table_name
        );
    END LOOP;
    INSERT INTO uuid_identifier_migration_map (entity_table, legacy_id, native_id)
    SELECT DISTINCT 'social_connections', t.connection_id::text,
        CASE WHEN t.connection_id::text ~ ('^' || uuid_pattern || '$') THEN t.connection_id::text::uuid
             ELSE md5('profiletailors:uuid-native-identifiers:social_connections:' || t.connection_id::text)::uuid END
    FROM credential_deletion_tombstones t
    WHERE NOT EXISTS (SELECT 1 FROM uuid_identifier_migration_map m WHERE m.entity_table = 'social_connections' AND m.legacy_id = t.connection_id::text);
    FOR item IN SELECT * FROM uuid_columns LOOP
        EXECUTE format('SELECT count(*) FROM %I x WHERE x.%I IS NOT NULL AND NOT EXISTS (SELECT 1 FROM uuid_identifier_migration_map m WHERE m.entity_table = %L AND m.legacy_id = x.%I::text)', item.table_name, item.column_name, item.entity_table, item.column_name) INTO bad;
        IF bad <> 0 THEN RAISE EXCEPTION 'Unmapped IDs or orphans in %.%: %', item.table_name, item.column_name, bad; END IF;
        IF item.entity_table <> 'publication_groups'
            AND (item.table_name, item.column_name) <> ('credential_deletion_tombstones', 'connection_id') THEN
            EXECUTE format('SELECT count(*) FROM %I x WHERE x.%I IS NOT NULL AND NOT EXISTS (SELECT 1 FROM %I p WHERE p.%I::text = x.%I::text)', item.table_name, item.column_name, item.entity_table, item.entity_column, item.column_name) INTO bad;
            IF bad <> 0 THEN RAISE EXCEPTION 'Orphans in %.%: %', item.table_name, item.column_name, bad; END IF;
        END IF;
    END LOOP;
END
$migration$;
CREATE TEMP TABLE uuid_counts ON COMMIT DROP AS SELECT table_name, 0::bigint AS row_count FROM uuid_columns GROUP BY table_name;
DO $counts$
DECLARE item record;
BEGIN
    FOR item IN SELECT table_name FROM uuid_counts LOOP
        EXECUTE format('UPDATE uuid_counts SET row_count = (SELECT count(*) FROM %I) WHERE table_name = %L', item.table_name, item.table_name);
    END LOOP;
END
$counts$;
DO $embedded$
BEGIN
    IF EXISTS (SELECT 1 FROM idea_board_configs WHERE jsonb_typeof(columns_json::jsonb) IS DISTINCT FROM 'array') THEN RAISE EXCEPTION 'Invalid semantic idea board columns shape'; END IF;

    IF EXISTS (SELECT 1 FROM workspace_target_scopes WHERE allowed_target_ids_json::jsonb <> '[]'::jsonb) THEN RAISE EXCEPTION 'Embedded or polymorphic references require reviewed mapping: workspace_target_scopes'; END IF;
    IF EXISTS (SELECT 1 FROM workspace_direct_grants WHERE conditions_json IS NOT NULL AND conditions_json::jsonb NOT IN ('{}'::jsonb, 'null'::jsonb)) THEN RAISE EXCEPTION 'Embedded or polymorphic references require reviewed mapping: workspace_direct_grants'; END IF;
    IF EXISTS (SELECT 1 FROM audit_events WHERE target_id IS NOT NULL OR details_json::jsonb <> '{}'::jsonb) THEN RAISE EXCEPTION 'Embedded or polymorphic references require reviewed mapping: audit_events'; END IF;


    IF EXISTS (SELECT 1 FROM bulk_import_rows WHERE errors IS NOT NULL AND errors::jsonb NOT IN ('[]'::jsonb, '{}'::jsonb, 'null'::jsonb)) THEN RAISE EXCEPTION 'Embedded or polymorphic references require reviewed mapping: bulk_import_rows'; END IF;
    IF EXISTS (SELECT 1 FROM social_content_payload_cache WHERE encrypted_payload IS NOT NULL) THEN RAISE EXCEPTION 'Embedded or polymorphic references require reviewed mapping: social_content_payload_cache'; END IF;
    IF EXISTS (SELECT 1 FROM idempotency_records WHERE response_json IS NOT NULL) THEN RAISE EXCEPTION 'Embedded or polymorphic references require reviewed mapping: idempotency_records'; END IF;
    IF EXISTS (SELECT 1 FROM shortlink_idempotency WHERE result_json IS NOT NULL) THEN RAISE EXCEPTION 'Stored shortlink responses require reviewed mapping'; END IF;

    IF EXISTS (SELECT 1 FROM compliance_risk_acceptances WHERE workspace_scope IS NOT NULL OR accepted_by IS NOT NULL) THEN RAISE EXCEPTION 'Polymorphic compliance scopes require review'; END IF;
    IF EXISTS (SELECT 1 FROM compliance_control_applicability_dimensions WHERE scope_type = 'WORKSPACE') THEN RAISE EXCEPTION 'Workspace applicability dimension requires reviewed mapping'; END IF;

END
$embedded$;
CREATE FUNCTION uuid_identifier_mapped(entity text, legacy text) RETURNS uuid
LANGUAGE plpgsql STABLE STRICT AS $mapping$
DECLARE mapped uuid;
BEGIN
    SELECT native_id INTO STRICT mapped FROM uuid_identifier_migration_map WHERE entity_table = entity AND legacy_id = legacy;
    RETURN mapped;
END
$mapping$;
DO $$
DECLARE
    consent_table text;
    unmapped bigint;
BEGIN
    FOREACH consent_table IN ARRAY ARRAY['consent_records', 'consent_record_events'] LOOP
        EXECUTE format('SELECT count(*) FROM %I c LEFT JOIN uuid_identifier_migration_map m ON m.entity_table = CASE c.subject_kind WHEN ''USER'' THEN ''principals'' WHEN ''WORKSPACE'' THEN ''workspaces'' END AND m.legacy_id = c.subject_value WHERE c.subject_kind NOT IN (''USER'', ''WORKSPACE'', ''ANONYMOUS'') OR (c.subject_kind IN (''USER'', ''WORKSPACE'') AND m.native_id IS NULL)', consent_table) INTO unmapped;
        IF unmapped > 0 THEN RAISE EXCEPTION 'Unknown or unmapped consent subject in %: %', consent_table, unmapped; END IF;
        EXECUTE format('UPDATE %I c SET subject_value = m.native_id::text FROM uuid_identifier_migration_map m WHERE m.entity_table = CASE c.subject_kind WHEN ''USER'' THEN ''principals'' WHEN ''WORKSPACE'' THEN ''workspaces'' END AND m.legacy_id = c.subject_value', consent_table);
    END LOOP;
    IF EXISTS (
        SELECT 1 FROM invitations i LEFT JOIN uuid_identifier_migration_map m
        ON m.entity_table = 'waitlist_entries' AND m.legacy_id = i.source_reference_id
        WHERE i.source NOT IN ('DIRECT', 'WAITLIST')
           OR (i.source = 'DIRECT' AND i.source_reference_id IS NOT NULL)
           OR (i.source = 'WAITLIST' AND m.native_id IS NULL)
    ) THEN RAISE EXCEPTION 'Unknown invitation source or unmapped WAITLIST reference'; END IF;
    UPDATE invitations i SET source_reference_id = m.native_id::text
    FROM uuid_identifier_migration_map m
    WHERE i.source = 'WAITLIST' AND m.entity_table = 'waitlist_entries' AND m.legacy_id = i.source_reference_id;
END $$;
CREATE TEMP TABLE uuid_admin_audit_targets (target_type text PRIMARY KEY, entity_table text NOT NULL) ON COMMIT DROP;
INSERT INTO uuid_admin_audit_targets VALUES
    ('Principal', 'principals'),
    ('USER', 'principals'),
    ('WaitlistEntry', 'waitlist_entries'),
    ('WaitlistInvitation', 'waitlist_invitations'),
    ('Invitation', 'invitations'),
    ('TAKEDOWN_REPORT', 'takedown_reports'),
    ('NOTIFICATION', 'notifications');
CREATE TEMP TABLE uuid_admin_audit_fields (field_name text PRIMARY KEY, entity_table text NOT NULL) ON COMMIT DROP;
INSERT INTO uuid_admin_audit_fields VALUES
    ('reportId', 'takedown_reports'),
    ('workspaceId', 'workspaces'),
    ('assetId', 'media_assets'),
    ('notificationId', 'notifications'),
    ('retryNotificationId', 'notifications');
DO $admin_audit$
DECLARE
    event record;
    field record;
    target_entity text;
    field_entity text;
    mapped uuid;
    converted_metadata jsonb;
BEGIN
    FOR event IN SELECT event_id, target_type, target_id, metadata FROM platform_admin_audit_events LOOP
        SELECT entity_table INTO target_entity FROM uuid_admin_audit_targets WHERE target_type = event.target_type;
        IF target_entity IS NOT NULL THEN
            SELECT native_id INTO mapped FROM uuid_identifier_migration_map WHERE entity_table = target_entity AND legacy_id = event.target_id;
            IF mapped IS NULL THEN RAISE EXCEPTION 'Unmapped platform admin audit target: %', event.event_id; END IF;
            UPDATE platform_admin_audit_events SET target_id = mapped::text WHERE event_id = event.event_id;
        ELSIF event.target_type <> 'CONFIGURATION' OR event.target_id <> 'registration.mode' THEN
            RAISE EXCEPTION 'Unknown platform admin audit target type or semantic key: %', event.event_id;
        END IF;
        IF event.metadata IS NULL THEN CONTINUE; END IF;
        converted_metadata := event.metadata::jsonb;
        IF jsonb_typeof(converted_metadata) IS DISTINCT FROM 'object' THEN
            RAISE EXCEPTION 'Invalid platform admin audit metadata shape: %', event.event_id;
        END IF;
        FOR field IN SELECT key, value FROM jsonb_each(converted_metadata) LOOP
            SELECT entity_table INTO field_entity FROM uuid_admin_audit_fields WHERE field_name = field.key;
            IF field_entity IS NOT NULL THEN
                SELECT native_id INTO mapped FROM uuid_identifier_migration_map
                WHERE entity_table = field_entity AND legacy_id = (field.value #>> '{}');
                IF jsonb_typeof(field.value) <> 'string' OR mapped IS NULL THEN
                    RAISE EXCEPTION 'Unmapped platform admin audit metadata reference: %.%', event.event_id, field.key;
                END IF;
                converted_metadata := jsonb_set(converted_metadata, ARRAY[field.key], to_jsonb(mapped::text));
            ELSIF field.key NOT IN ('role', 'revokedSessionCount', 'previousMode', 'newMode', 'channel', 'templateId', 'retryOutcome', 'priorStatus', 'priorError')
                OR jsonb_typeof(field.value) <> 'string' THEN
                RAISE EXCEPTION 'Unknown platform admin audit metadata field or value: %.%', event.event_id, field.key;
            END IF;
        END LOOP;
        UPDATE platform_admin_audit_events SET metadata = converted_metadata::text WHERE event_id = event.event_id;
    END LOOP;
END
$admin_audit$;
CREATE TEMP TABLE uuid_notification_payload_fields (
    template_id text,
    field_name text,
    entity_table text,
    PRIMARY KEY (template_id, field_name)
) ON COMMIT DROP;
INSERT INTO uuid_notification_payload_fields VALUES
    ('waitlist.welcome', 'waitlistEntryId', 'waitlist_entries'),
    ('waitlist.welcome', 'waitlistName', NULL),
    ('waitlist.welcome', 'locale', NULL),
    ('platform.invitation', 'invitationId', 'invitations'),
    ('platform.invitation', 'email', NULL),
    ('platform.invitation', 'workspaceName', NULL),
    ('platform.invitation', 'target', NULL),
    ('platform.invitation', 'locale', NULL),
    ('governance.takedown.reported', 'reportId', 'takedown_reports'),
    ('governance.takedown.reported', 'assetId', 'media_assets'),
    ('governance.takedown.reported', 'reason', NULL),
    ('governance.takedown.reported', 'reporterEmail', NULL),
    ('governance.takedown.reported', 'mediaReferenceUrl', NULL),
    ('governance.takedown.approved', 'reportId', 'takedown_reports'),
    ('governance.takedown.approved', 'assetId', 'media_assets'),
    ('governance.takedown.rejected', 'reportId', 'takedown_reports'),
    ('governance.takedown.rejected', 'assetId', 'media_assets'),
    ('governance.takedown.rejected', 'rejectionReason', NULL);
DO $notification_payloads$
DECLARE
    notification record;
    field record;
    field_entity text;
    mapped uuid;
    converted_payload jsonb;
BEGIN
    IF to_regclass('notifications') IS NULL THEN RETURN; END IF;
    FOR notification IN SELECT id, template_id, payload FROM notifications LOOP
        IF NOT EXISTS (SELECT 1 FROM uuid_notification_payload_fields WHERE template_id = notification.template_id) THEN
            RAISE EXCEPTION 'Unknown notification payload template: %', notification.template_id;
        END IF;
        converted_payload := notification.payload::jsonb;
        IF jsonb_typeof(converted_payload) IS DISTINCT FROM 'object' THEN
            RAISE EXCEPTION 'Invalid notification payload shape: %', notification.id;
        END IF;
        IF EXISTS (
            SELECT 1 FROM uuid_notification_payload_fields f
            WHERE f.template_id = notification.template_id AND f.entity_table IS NOT NULL
                AND NOT (converted_payload ? f.field_name)
        ) THEN RAISE EXCEPTION 'Missing notification payload reference: %', notification.id; END IF;
        FOR field IN SELECT key, value FROM jsonb_each(converted_payload) LOOP
            SELECT entity_table INTO field_entity FROM uuid_notification_payload_fields
            WHERE template_id = notification.template_id AND field_name = field.key;
            IF NOT FOUND OR jsonb_typeof(field.value) <> 'string' THEN
                RAISE EXCEPTION 'Unknown notification payload field or value: %.%', notification.id, field.key;
            END IF;
            IF field_entity IS NOT NULL THEN
                SELECT native_id INTO mapped FROM uuid_identifier_migration_map
                WHERE entity_table = field_entity AND legacy_id = (field.value #>> '{}');
                IF mapped IS NULL THEN RAISE EXCEPTION 'Unmapped notification payload reference: %.%', notification.id, field.key; END IF;
                converted_payload := jsonb_set(converted_payload, ARRAY[field.key], to_jsonb(mapped::text));
            END IF;
        END LOOP;
        UPDATE notifications SET payload = converted_payload WHERE id = notification.id;
    END LOOP;
END
$notification_payloads$;
CREATE TEMP TABLE uuid_foreign_keys ON COMMIT DROP AS
SELECT conrelid::regclass AS relation, conname, pg_get_constraintdef(oid) AS definition
FROM pg_constraint
WHERE contype = 'f' AND (conrelid IN (SELECT to_regclass(table_name) FROM uuid_columns)
    OR confrelid IN (SELECT to_regclass(table_name) FROM uuid_columns));
DO $convert$
DECLARE item record;
BEGIN
    FOR item IN SELECT * FROM uuid_foreign_keys LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', item.relation, item.conname);
    END LOOP;
    FOR item IN SELECT c.*, i.is_identity, i.column_default FROM uuid_columns c JOIN information_schema.columns i
        ON i.table_schema = current_schema() AND i.table_name = c.table_name AND i.column_name = c.column_name
        WHERE i.data_type <> 'uuid' LOOP
        IF item.is_identity = 'YES' THEN
            EXECUTE format('ALTER TABLE %I ALTER COLUMN %I DROP IDENTITY', item.table_name, item.column_name);
        END IF;
        EXECUTE format('ALTER TABLE %I ALTER COLUMN %I DROP DEFAULT', item.table_name, item.column_name);
        EXECUTE format('ALTER TABLE %I ALTER COLUMN %I TYPE uuid USING uuid_identifier_mapped(%L, %I::text)', item.table_name, item.column_name, item.entity_table, item.column_name);
        IF item.is_identity = 'YES' OR item.column_default ~ '^nextval\(' THEN
            EXECUTE format('ALTER TABLE %I ALTER COLUMN %I SET DEFAULT gen_random_uuid()', item.table_name, item.column_name);
        END IF;
    END LOOP;
    FOR item IN SELECT * FROM uuid_foreign_keys LOOP
        EXECUTE format('ALTER TABLE %s ADD CONSTRAINT %I %s', item.relation, item.conname, item.definition);
    END LOOP;
END
$convert$;
ALTER TABLE consent_records
    ADD CONSTRAINT ck_consent_records_scope CHECK ((workspace_id IS NULL) <> (waitlist_id IS NULL)),
    ADD CONSTRAINT fk_consent_records_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    ADD CONSTRAINT fk_consent_records_waitlist FOREIGN KEY (waitlist_id) REFERENCES waitlists (id),
    ADD CONSTRAINT uq_consent_records_id_workspace UNIQUE (id, workspace_id),
    ADD CONSTRAINT uq_consent_records_id_waitlist UNIQUE (id, waitlist_id);
ALTER TABLE consent_record_events
    ADD CONSTRAINT ck_consent_record_events_scope CHECK ((workspace_id IS NULL) <> (waitlist_id IS NULL)),
    ADD CONSTRAINT fk_consent_events_workspace_scope FOREIGN KEY (consent_id, workspace_id) REFERENCES consent_records (id, workspace_id),
    ADD CONSTRAINT fk_consent_events_waitlist_scope FOREIGN KEY (consent_id, waitlist_id) REFERENCES consent_records (id, waitlist_id);
ALTER INDEX uq_consent_active RENAME TO uq_consent_active_workspace;
CREATE UNIQUE INDEX uq_consent_active_waitlist ON consent_records
    (waitlist_id, subject_kind, subject_value, purpose, policy_version) WHERE status = 'ACTIVE';
CREATE INDEX idx_consent_records_waitlist_identity ON consent_records (waitlist_id, subject_kind, subject_value, purpose);
CREATE INDEX idx_consent_events_waitlist_identity ON consent_record_events (waitlist_id, subject_kind, subject_value, purpose, event_at);
DO $verify$
DECLARE item record; actual bigint;
BEGIN
    FOR item IN SELECT * FROM uuid_counts LOOP
        EXECUTE format('SELECT count(*) FROM %I', item.table_name) INTO actual;
        IF actual <> item.row_count THEN RAISE EXCEPTION 'Row count changed in %', item.table_name; END IF;
    END LOOP;
    IF EXISTS (SELECT 1 FROM uuid_columns c JOIN information_schema.columns i ON i.table_schema = current_schema() AND i.table_name = c.table_name AND i.column_name = c.column_name WHERE i.data_type <> 'uuid') THEN
        RAISE EXCEPTION 'UUID type verification failed';
    END IF;
END
$verify$;
DROP FUNCTION uuid_identifier_mapped(text, text);
