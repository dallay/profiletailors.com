#!/usr/bin/env bash
set -euo pipefail
root=$(git rev-parse --show-toplevel)
sql="$root/server/smp/src/main/resources/db/changelog/uuid-native-identifiers/tests"
resources="$root/server/smp/src/main/resources"
classpath=${LIQUIBASE_TEST_CLASSPATH:?Set LIQUIBASE_TEST_CLASSPATH to the existing Liquibase CLI and PostgreSQL JDBC dependency classpath}
container="pt-uuid-sql-test-$$"
logs=${UUID_SQL_TEST_LOGS:?Set UUID_SQL_TEST_LOGS to an external evidence directory}
mkdir -p "$logs"
docker run -d --name "$container" -e POSTGRES_PASSWORD=disposable -e POSTGRES_DB=uuid_sql -p 127.0.0.1::5432 postgres:18 > "$logs/container-id"
trap 'docker rm -f "$container" > /dev/null' EXIT
until docker exec "$container" pg_isready -h 127.0.0.1 -U postgres > /dev/null 2>&1; do sleep 1; done
port=$(docker port "$container" 5432/tcp | cut -d: -f2)
psql_test() {
    docker exec -i "$container" psql -v ON_ERROR_STOP=1 -U postgres -d "$database" "$@"
}
consent_schema_signature() {
    psql_test -At <<'SQL'
SELECT jsonb_build_object(
    'columns', (SELECT jsonb_agg(jsonb_build_array(table_name, column_name, data_type, is_nullable) ORDER BY table_name, ordinal_position)
        FROM information_schema.columns WHERE table_schema = current_schema() AND table_name IN ('consent_records', 'consent_record_events')),
    'constraints', (SELECT jsonb_agg(jsonb_build_array(conrelid::regclass::text, conname, contype, pg_get_constraintdef(oid)) ORDER BY conname)
        FROM pg_constraint WHERE conrelid IN ('consent_records'::regclass, 'consent_record_events'::regclass)),
    'indexes', (SELECT jsonb_agg(indexdef ORDER BY indexname) FROM pg_indexes WHERE schemaname = current_schema() AND tablename IN ('consent_records', 'consent_record_events'))
)::text;
SQL
}
liquibase_test() {
    local context=$1
    local approval=$2
    (cd "$resources" && java -cp "$classpath" liquibase.integration.commandline.Main "--url=jdbc:postgresql://localhost:$port/$database$approval" --username=postgres --password=disposable --changeLogFile=db/changelog/db.changelog-master.yaml "--contexts=$context" update)
}
approved='?options=-c%20profiletailors.uuid_cutover_approved=on'
database=uuid_sql
liquibase_test baseline '' > "$logs/baseline.log" 2>&1
psql_test < "$sql/fixture.sql" > "$logs/fixture.log"
for case_name in missing_approval orphan collision unknown_subject invitation_orphan unknown_board replay audit_target_orphan audit_unknown_target audit_unknown_metadata audit_metadata_orphan audit_metadata_shape audit_metadata_value audit_semantic_target tombstone_workspace_orphan absent_notifications_audit absent_notifications_metadata; do
    docker exec "$container" createdb -U postgres -T uuid_sql "uuid_$case_name"
    database="uuid_$case_name"
    case "$case_name" in
        missing_approval) approval=''; expected='UUID cutover requires explicit operator approval and stopped writers' ;;
        orphan) approval=$approved; expected='Unknown or unmapped consent subject'; psql_test -c "UPDATE consent_records SET subject_value = 'missing-user' WHERE subject_kind = 'USER'" ;;
        collision) approval=$approved; expected='duplicate key value violates unique constraint'; psql_test -c "INSERT INTO principals (id, principal_type, subject) VALUES ('user-10000000-0000-4000-8000-000000000001', 'USER', 'collision')" ;;
        unknown_subject) approval=$approved; expected='Unknown or unmapped consent subject'; psql_test -c "UPDATE consent_record_events SET subject_kind = 'UNKNOWN'" ;;
        invitation_orphan) approval=$approved; expected='Unknown invitation source'; psql_test -c "UPDATE invitations SET source_reference_id = 'missing-waitlist-entry'" ;;
        unknown_board) approval=$approved; expected='Invalid semantic idea board columns shape'; psql_test -c "UPDATE idea_board_configs SET columns_json = '{}'" ;;
        replay) approval=$approved; expected='Embedded or polymorphic references require reviewed mapping: idempotency_records'; psql_test -c "INSERT INTO idempotency_records (workspace_id, principal_id, tool_name, key_hash, response_json, created_at) VALUES ('ws-20000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', 'publication_create', 'opaque-hash', '{}', now())" ;;
        audit_target_orphan) approval=$approved; expected='Unmapped platform admin audit target'; psql_test -c "UPDATE platform_admin_audit_events SET target_id = 'missing-entry' WHERE target_type = 'WaitlistEntry'" ;;
        audit_unknown_target) approval=$approved; expected='Unknown platform admin audit target'; psql_test -c "UPDATE platform_admin_audit_events SET target_type = 'UNKNOWN' WHERE target_type = 'WaitlistEntry'" ;;
        audit_unknown_metadata) approval=$approved; expected='Unknown platform admin audit metadata field'; psql_test -c "UPDATE platform_admin_audit_events SET metadata = '{\"unknownReference\":\"wl-50000000-0000-4000-8000-000000000001\"}' WHERE target_type = 'WaitlistEntry'" ;;
        audit_metadata_orphan) approval=$approved; expected='Unmapped platform admin audit metadata reference'; psql_test -c "UPDATE platform_admin_audit_events SET metadata = '{\"workspaceId\":\"missing-workspace\"}' WHERE target_type = 'WaitlistEntry'" ;;
        audit_metadata_shape) approval=$approved; expected='Invalid platform admin audit metadata shape'; psql_test -c "UPDATE platform_admin_audit_events SET metadata = '[]' WHERE target_type = 'WaitlistEntry'" ;;
        audit_metadata_value) approval=$approved; expected='Unknown platform admin audit metadata field or value'; psql_test -c "UPDATE platform_admin_audit_events SET metadata = '{\"role\":{\"nestedReference\":\"wl-50000000-0000-4000-8000-000000000001\"}}' WHERE target_type = 'WaitlistEntry'" ;;
        audit_semantic_target) approval=$approved; expected='Unknown platform admin audit target'; psql_test -c "UPDATE platform_admin_audit_events SET target_id = 'wl-50000000-0000-4000-8000-000000000001' WHERE target_type = 'CONFIGURATION'" ;;
        tombstone_workspace_orphan) approval=$approved; expected='Unmapped IDs or orphans in credential_deletion_tombstones.workspace_id'; psql_test -c "UPDATE credential_deletion_tombstones SET workspace_id = 'missing-workspace'" ;;
        absent_notifications_audit) approval=$approved; expected='Unmapped platform admin audit target'; psql_test -c "UPDATE platform_admin_audit_events SET target_type = 'NOTIFICATION', target_id = 'ntf-c0000000-0000-4000-8000-000000000001' WHERE target_type = 'WaitlistEntry'" ;;
        absent_notifications_metadata) approval=$approved; expected='Unmapped platform admin audit metadata reference'; psql_test -c "UPDATE platform_admin_audit_events SET metadata = '{\"notificationId\":\"ntf-c0000000-0000-4000-8000-000000000001\"}' WHERE target_type = 'WaitlistEntry'" ;;
    esac
    if liquibase_test uuid-native-identifiers "$approval" > "$logs/$case_name.log" 2>&1; then
        printf 'Unexpected success: %s\n' "$case_name"
        exit 1
    fi
    grep -F "$expected" "$logs/$case_name.log" > /dev/null
    psql_test -c "DO \$\$ BEGIN IF (SELECT data_type FROM information_schema.columns WHERE table_name = 'ideas' AND column_name = 'id') <> 'character varying' OR (SELECT count(*) FROM ideas WHERE id = 'idea-30000000-0000-4000-8000-000000000001') <> 1 OR EXISTS (SELECT 1 FROM consent_records WHERE subject_kind = 'WORKSPACE' AND subject_value <> 'ws-20000000-0000-4000-8000-000000000001') THEN RAISE EXCEPTION 'Failed conversion did not preserve legacy state'; END IF; END \$\$;"
    printf 'PASS rejection and atomicity: %s\n' "$case_name"
done
database=uuid_notifications
docker exec "$container" createdb -U postgres -T uuid_sql "$database"
psql_test < "$sql/notifications-fixture.sql" > "$logs/notifications-fixture.log"
for case_name in collision target_orphan successor_orphan payload_orphan invitation_orphan report_orphan asset_orphan unknown_template unknown_field payload_shape payload_value missing_reference; do
    docker exec "$container" createdb -U postgres -T uuid_notifications "notification_$case_name"
    database="notification_$case_name"
    case "$case_name" in
        collision) expected='duplicate key value violates unique constraint'; psql_test -c "INSERT INTO notifications SELECT 'c0000000-0000-4000-8000-000000000001', idempotency_key || ':collision', channel, recipient, template_id, payload, status, sent_at, failed_at, error_message, created_at, updated_at FROM notifications WHERE id = 'ntf-c0000000-0000-4000-8000-000000000001'" ;;
        target_orphan) expected='Unmapped platform admin audit target'; psql_test -c "UPDATE platform_admin_audit_events SET target_id = 'ntf-c0000000-0000-4000-8000-000000000009' WHERE event_id = 'b0000000-0000-4000-8000-000000000001'" ;;
        successor_orphan) expected='Unmapped platform admin audit metadata reference'; psql_test -c "UPDATE platform_admin_audit_events SET metadata = jsonb_set(metadata::jsonb, '{retryNotificationId}', '\"c0000000-0000-4000-8000-000000000009\"')::text WHERE event_id = 'b0000000-0000-4000-8000-000000000001'" ;;
        payload_orphan) expected='Unmapped notification payload reference'; psql_test -c "UPDATE notifications SET payload = jsonb_set(payload, '{waitlistEntryId}', '\"missing-entry\"') WHERE template_id = 'waitlist.welcome'" ;;
        invitation_orphan) expected='Unmapped notification payload reference'; psql_test -c "UPDATE notifications SET payload = jsonb_set(payload, '{invitationId}', '\"missing-invitation\"') WHERE template_id = 'platform.invitation'" ;;
        report_orphan) expected='Unmapped notification payload reference'; psql_test -c "UPDATE notifications SET payload = jsonb_set(payload, '{reportId}', '\"missing-report\"') WHERE template_id = 'governance.takedown.reported'" ;;
        asset_orphan) expected='Unmapped notification payload reference'; psql_test -c "UPDATE notifications SET payload = jsonb_set(payload, '{assetId}', '\"missing-asset\"') WHERE template_id = 'governance.takedown.reported'" ;;
        unknown_template) expected='Unknown notification payload template'; psql_test -c "UPDATE notifications SET template_id = 'UNKNOWN' WHERE template_id = 'waitlist.welcome'" ;;
        unknown_field) expected='Unknown notification payload field or value'; psql_test -c "UPDATE notifications SET payload = payload || '{\"unknownReference\":\"wl-50000000-0000-4000-8000-000000000001\"}' WHERE template_id = 'waitlist.welcome'" ;;
        payload_shape) expected='Invalid notification payload shape'; psql_test -c "UPDATE notifications SET payload = '[]' WHERE template_id = 'waitlist.welcome'" ;;
        payload_value) expected='Unknown notification payload field or value'; psql_test -c "UPDATE notifications SET payload = jsonb_set(payload, '{waitlistName}', '{\"nestedReference\":\"wl-50000000-0000-4000-8000-000000000001\"}') WHERE template_id = 'waitlist.welcome'" ;;
        missing_reference) expected='Missing notification payload reference'; psql_test -c "UPDATE notifications SET payload = payload - 'waitlistEntryId' WHERE template_id = 'waitlist.welcome'" ;;
    esac
    before_notifications=$(psql_test -Atc 'SELECT md5(jsonb_agg(to_jsonb(n) ORDER BY id)::text) FROM notifications n')
    before_audit=$(psql_test -Atc 'SELECT md5(jsonb_agg(to_jsonb(a) ORDER BY event_id)::text) FROM platform_admin_audit_events a')
    if liquibase_test uuid-native-identifiers "$approved" > "$logs/notification-$case_name.log" 2>&1; then
        printf 'Unexpected notification success: %s\n' "$case_name"
        exit 1
    fi
    grep -F "$expected" "$logs/notification-$case_name.log" > /dev/null
    test "$before_notifications" = "$(psql_test -Atc 'SELECT md5(jsonb_agg(to_jsonb(n) ORDER BY id)::text) FROM notifications n')"
    test "$before_audit" = "$(psql_test -Atc 'SELECT md5(jsonb_agg(to_jsonb(a) ORDER BY event_id)::text) FROM platform_admin_audit_events a')"
    psql_test -c "DO \$\$ BEGIN IF (SELECT data_type FROM information_schema.columns WHERE table_name = 'notifications' AND column_name = 'id') <> 'character varying' THEN RAISE EXCEPTION 'Notification UUID conversion did not roll back'; END IF; END \$\$;"
    printf 'PASS notification rejection and atomicity: %s\n' "$case_name"
done
database=uuid_notifications
liquibase_test uuid-native-identifiers "$approved" > "$logs/notifications-positive.log" 2>&1
psql_test < "$sql/assertions.sql" | tee "$logs/notifications-base-assertions.log"
psql_test < "$sql/notifications-assertions.sql" | tee "$logs/notifications-assertions.log"
printf 'PASS populated notifications, payloads, retry audit and opaque fields\n'
database=uuid_consent_scopes
docker exec "$container" createdb -U postgres -T uuid_sql "$database"
psql_test < "$sql/consent-scopes-fixture.sql" > "$logs/consent-scopes-fixture.log"
for case_name in missing_projection_key missing_event_key ambiguous_key event_scope_mismatch event_orphan; do
    docker exec "$container" createdb -U postgres -T uuid_consent_scopes "consent_$case_name"
    database="consent_$case_name"
    case "$case_name" in
        missing_projection_key) expected='Missing or ambiguous historical waitlist consent key in consent_records'; psql_test -c "UPDATE consent_records SET workspace_id = 'waitlist:missing-key' WHERE id = 'cs-d0000000-0000-4000-8000-000000000001'" ;;
        missing_event_key) expected='Missing or ambiguous historical waitlist consent key in consent_record_events'; psql_test -c "UPDATE consent_record_events SET workspace_id = 'waitlist:missing-key' WHERE consent_id = 'cs-d0000000-0000-4000-8000-000000000001'" ;;
        ambiguous_key) expected='Missing or ambiguous historical waitlist consent key'; psql_test -c "ALTER TABLE waitlists DROP CONSTRAINT uq_waitlists_key; INSERT INTO waitlists (id, key, name, context, status) VALUES ('historical-list-duplicate', 'public-launch', 'Ambiguous historical import', 'test', 'ACTIVE')" ;;
        event_scope_mismatch) expected='Historical consent event/projection scope mismatch'; psql_test -c "UPDATE consent_record_events SET workspace_id = 'waitlist:public:beta' WHERE consent_id = 'cs-d0000000-0000-4000-8000-000000000001'" ;;
        event_orphan) expected='Historical consent event/projection scope mismatch'; psql_test -c "UPDATE consent_record_events SET consent_id = 'missing-consent' WHERE consent_id = 'cs-d0000000-0000-4000-8000-000000000001'" ;;
    esac
    before_records=$(psql_test -Atc 'SELECT md5(jsonb_agg(to_jsonb(c) ORDER BY id)::text) FROM consent_records c')
    before_events=$(psql_test -Atc 'SELECT md5(jsonb_agg(to_jsonb(e) ORDER BY id)::text) FROM consent_record_events e')
    before_waitlists=$(psql_test -Atc 'SELECT md5(jsonb_agg(to_jsonb(w) ORDER BY id)::text) FROM waitlists w')
    if liquibase_test uuid-native-identifiers "$approved" > "$logs/consent-$case_name.log" 2>&1; then
        printf 'Unexpected consent scope success: %s\n' "$case_name"
        exit 1
    fi
    grep -F "$expected" "$logs/consent-$case_name.log" > /dev/null
    test "$before_records" = "$(psql_test -Atc 'SELECT md5(jsonb_agg(to_jsonb(c) ORDER BY id)::text) FROM consent_records c')"
    test "$before_events" = "$(psql_test -Atc 'SELECT md5(jsonb_agg(to_jsonb(e) ORDER BY id)::text) FROM consent_record_events e')"
    test "$before_waitlists" = "$(psql_test -Atc 'SELECT md5(jsonb_agg(to_jsonb(w) ORDER BY id)::text) FROM waitlists w')"
    psql_test -c "DO \$\$ BEGIN IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name IN ('consent_records', 'consent_record_events') AND column_name = 'waitlist_id') OR EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name IN ('consent_records', 'consent_record_events') AND column_name = 'workspace_id' AND is_nullable <> 'NO') OR EXISTS (SELECT 1 FROM uuid_identifier_migration_map WHERE entity_table = 'waitlists' AND legacy_id = 'historical-list-a') THEN RAISE EXCEPTION 'Failed consent migration left schema or mappings changed'; END IF; END \$\$;"
    printf 'PASS consent scope rejection and atomicity: %s\n' "$case_name"
done
database=uuid_consent_scopes
liquibase_test uuid-native-identifiers "$approved" > "$logs/consent-scopes-positive.log" 2>&1
psql_test < "$sql/consent-scopes-assertions.sql" | tee "$logs/consent-scopes-assertions.log"
psql_test < "$sql/consent-scopes-runtime.sql" | tee "$logs/consent-scopes-runtime.log"
for scope_column in workspace_id waitlist_id; do
    if [[ "$scope_column" == workspace_id ]]; then
        scope_id=20000000-0000-4000-8000-000000000001
    else
        scope_id=$(psql_test -Atc "SELECT id FROM waitlists WHERE key = 'public-launch'")
    fi
    psql_test -v "scope_column=$scope_column" -v "scope_id=$scope_id" < "$sql/consent-scopes-concurrent.sql" > "$logs/consent-concurrent-$scope_column-1.log" &
    first_request=$!
    psql_test -v "scope_column=$scope_column" -v "scope_id=$scope_id" < "$sql/consent-scopes-concurrent.sql" > "$logs/consent-concurrent-$scope_column-2.log" &
    second_request=$!
    wait "$first_request"
    wait "$second_request"
    psql_test -c "DO \$\$ BEGIN IF (SELECT count(*) FROM consent_records WHERE $scope_column = '$scope_id'::uuid AND subject_value = 'concurrent-subject' AND purpose = 'scope-concurrent' AND status = 'ACTIVE') <> 1 OR (SELECT count(*) FROM consent_record_events WHERE $scope_column = '$scope_id'::uuid AND subject_value = 'concurrent-subject' AND purpose = 'scope-concurrent') <> 1 THEN RAISE EXCEPTION 'Concurrent scoped consent duplicated projection or event'; END IF; END \$\$;"
    printf 'PASS concurrent consent idempotency and history: %s\n' "$scope_column"
done
psql_test -c "SELECT count(*) AS validated_foreign_keys FROM pg_constraint WHERE contype = 'f' AND connamespace = 'public'::regnamespace AND convalidated; SELECT count(*) AS valid_indexes FROM pg_index WHERE indisvalid" | tee "$logs/consent-scopes-constraints.log"
printf 'PASS WAITLIST/WORKSPACE migration, legal evidence, isolation, withdrawal and history\n'
database=uuid_sql
liquibase_test uuid-native-identifiers "$approved" > "$logs/positive.log" 2>&1
psql_test -c "DO \$\$ BEGIN IF to_regclass('notifications') IS NOT NULL OR EXISTS (SELECT 1 FROM uuid_identifier_migration_map WHERE entity_table = 'notifications') THEN RAISE EXCEPTION 'Absent notifications table was created or mapped'; END IF; END \$\$;" | tee "$logs/notifications-absent.log"
psql_test < "$sql/assertions.sql" | tee "$logs/assertions.log"
psql_test < "$sql/audit-assertions.sql" | tee "$logs/audit-assertions.log"
psql_test < "$sql/defaults.sql" | tee "$logs/defaults-identity.log"
database=uuid_serial
docker exec "$container" createdb -U postgres "$database"
liquibase_test baseline '' > "$logs/serial-baseline.log" 2>&1
psql_test < "$sql/fixture.sql" > "$logs/serial-fixture.log"
psql_test < "$sql/serial-defaults.sql" > "$logs/serial-defaults.log"
liquibase_test uuid-native-identifiers "$approved" > "$logs/serial-positive.log" 2>&1
psql_test < "$sql/assertions.sql" | tee "$logs/serial-assertions.log"
psql_test < "$sql/audit-assertions.sql" | tee "$logs/serial-audit-assertions.log"
psql_test < "$sql/defaults.sql" | tee "$logs/defaults-serial.log"
expected_scope_schema=$(consent_schema_signature)
database=uuid_fresh
docker exec "$container" createdb -U postgres "$database"
liquibase_test baseline '' > "$logs/fresh-baseline.log" 2>&1
liquibase_test uuid-native-identifiers "$approved" > "$logs/fresh-positive.log" 2>&1
consent_schema_signature > "$logs/fresh-consent-schema.json"
test "$expected_scope_schema" = "$(cat "$logs/fresh-consent-schema.json")"
printf '%s\n' "$expected_scope_schema" > "$logs/populated-consent-schema.json"
printf 'PASS fresh baseline/cutover consent schema equals populated conversion\n'
printf 'PASS populated conversion, row counts, semantic preservation, foreign keys and indexes\n'
