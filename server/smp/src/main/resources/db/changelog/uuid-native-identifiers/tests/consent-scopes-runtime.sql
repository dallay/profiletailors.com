BEGIN;
CREATE FUNCTION pg_temp.expect_consent_constraint(statement text, expected_constraint text) RETURNS void
LANGUAGE plpgsql AS $$
DECLARE actual_constraint text;
BEGIN
    EXECUTE statement;
    RAISE EXCEPTION 'Expected consent constraint was not enforced: %', expected_constraint;
EXCEPTION WHEN check_violation OR foreign_key_violation OR unique_violation THEN
    GET STACKED DIAGNOSTICS actual_constraint = CONSTRAINT_NAME;
    IF actual_constraint IS DISTINCT FROM expected_constraint THEN RAISE; END IF;
END $$;
DO $$
DECLARE
    current_consent record;
    first_list uuid;
    second_list uuid;
    tenant uuid := '20000000-0000-4000-8000-000000000001';
    affected bigint;
    original_events bigint;
    expected_constraint text;
BEGIN
    SELECT id INTO STRICT first_list FROM waitlists WHERE key = 'public-launch';
    SELECT id INTO STRICT second_list FROM waitlists WHERE key = 'public:beta';
    SELECT count(*) INTO original_events FROM consent_record_events;
    IF (SELECT count(*) FROM consent_records WHERE subject_value = 'shared-consent-subject' AND purpose = 'scope-regression' AND policy_version = 'v1' AND status = 'ACTIVE') <> 3
        OR (SELECT count(*) FROM consent_records WHERE workspace_id = tenant AND waitlist_id IS NULL AND purpose = 'scope-regression' AND status = 'ACTIVE') <> 1
        OR (SELECT count(*) FROM consent_records WHERE workspace_id IS NULL AND waitlist_id = first_list AND purpose = 'scope-regression' AND status = 'ACTIVE') <> 1
        OR (SELECT count(*) FROM consent_records WHERE workspace_id IS NULL AND waitlist_id = second_list AND purpose = 'scope-regression' AND status = 'ACTIVE') <> 1 THEN
        RAISE EXCEPTION 'Active consent scopes are not isolated';
    END IF;
    FOR current_consent IN SELECT * FROM consent_records WHERE purpose = 'scope-regression' AND status = 'ACTIVE' LOOP
        expected_constraint := CASE WHEN current_consent.workspace_id IS NULL THEN 'uq_consent_active_waitlist' ELSE 'uq_consent_active_workspace' END;
        PERFORM pg_temp.expect_consent_constraint(format(
            'INSERT INTO consent_records (id, workspace_id, waitlist_id, subject_kind, subject_value, consent_type, purpose, policy_version, source, locale, status, given_at) SELECT gen_random_uuid(), workspace_id, waitlist_id, subject_kind, subject_value, consent_type, purpose, policy_version, source, locale, status, given_at FROM consent_records WHERE id = %L',
            current_consent.id
        ), expected_constraint);
        IF current_consent.workspace_id IS NOT NULL THEN
            WITH attempted AS (
                INSERT INTO consent_records (id, workspace_id, waitlist_id, subject_kind, subject_value, consent_type, purpose, policy_version, source, locale, status, given_at)
                SELECT gen_random_uuid(), workspace_id, waitlist_id, subject_kind, subject_value, consent_type, purpose, policy_version, source, locale, status, given_at
                FROM consent_records WHERE id = current_consent.id
                ON CONFLICT (workspace_id, subject_kind, subject_value, purpose, policy_version) WHERE status = 'ACTIVE' DO NOTHING RETURNING *
            ) INSERT INTO consent_record_events (id, consent_id, workspace_id, waitlist_id, subject_kind, subject_value, consent_type, purpose, policy_version, source, locale, status, given_at, event_at)
                SELECT gen_random_uuid(), id, workspace_id, waitlist_id, subject_kind, subject_value, consent_type, purpose, policy_version, source, locale, status, given_at, now() FROM attempted;
        ELSE
            WITH attempted AS (
                INSERT INTO consent_records (id, workspace_id, waitlist_id, subject_kind, subject_value, consent_type, purpose, policy_version, source, locale, status, given_at)
                SELECT gen_random_uuid(), workspace_id, waitlist_id, subject_kind, subject_value, consent_type, purpose, policy_version, source, locale, status, given_at
                FROM consent_records WHERE id = current_consent.id
                ON CONFLICT (waitlist_id, subject_kind, subject_value, purpose, policy_version) WHERE status = 'ACTIVE' DO NOTHING RETURNING *
            ) INSERT INTO consent_record_events (id, consent_id, workspace_id, waitlist_id, subject_kind, subject_value, consent_type, purpose, policy_version, source, locale, status, given_at, event_at)
                SELECT gen_random_uuid(), id, workspace_id, waitlist_id, subject_kind, subject_value, consent_type, purpose, policy_version, source, locale, status, given_at, now() FROM attempted;
        END IF;
        GET DIAGNOSTICS affected = ROW_COUNT;
        IF affected <> 0 THEN RAISE EXCEPTION 'Idempotent consent created an event'; END IF;
    END LOOP;
    IF (SELECT count(*) FROM consent_record_events) <> original_events THEN RAISE EXCEPTION 'Idempotency changed consent history'; END IF;
    WITH withdrawn AS (
        UPDATE consent_records SET status = 'WITHDRAWN', withdrawn_at = now(), withdrawal_reason = 'USER_REQUEST', version = version + 1
        WHERE workspace_id IS NULL AND waitlist_id = first_list AND subject_kind = 'ANONYMOUS'
            AND subject_value = 'shared-consent-subject' AND purpose = 'scope-regression' AND policy_version = 'v1' AND status = 'ACTIVE'
        RETURNING *
    ) INSERT INTO consent_record_events (id, consent_id, workspace_id, waitlist_id, subject_kind, subject_value, consent_type, purpose, policy_version, source, locale, status, given_at, withdrawn_at, withdrawal_reason, event_at)
        SELECT gen_random_uuid(), id, workspace_id, waitlist_id, subject_kind, subject_value, consent_type, purpose, policy_version, source, locale, status, given_at, withdrawn_at, withdrawal_reason, now() FROM withdrawn;
    GET DIAGNOSTICS affected = ROW_COUNT;
    IF affected <> 1 THEN RAISE EXCEPTION 'Scoped withdrawal did not append exactly one event'; END IF;
    IF (SELECT count(*) FROM consent_records WHERE subject_value = 'shared-consent-subject' AND purpose = 'scope-regression' AND policy_version = 'v1' AND status = 'ACTIVE') <> 2
        OR (SELECT count(*) FROM consent_records WHERE workspace_id = tenant AND waitlist_id IS NULL AND purpose = 'scope-regression' AND status = 'ACTIVE') <> 1
        OR (SELECT count(*) FROM consent_records WHERE workspace_id IS NULL AND waitlist_id = second_list AND purpose = 'scope-regression' AND status = 'ACTIVE') <> 1
        OR EXISTS (SELECT 1 FROM consent_records WHERE workspace_id = first_list AND waitlist_id IS NULL)
        OR (SELECT count(*) FROM consent_record_events WHERE workspace_id IS NULL AND waitlist_id = first_list AND purpose = 'scope-regression') <> 3 THEN
        RAISE EXCEPTION 'Withdrawal leaked across scope or lost history';
    END IF;
    SELECT * INTO STRICT current_consent FROM consent_records WHERE waitlist_id = first_list AND purpose = 'scope-regression' AND policy_version = 'v1';
    IF current_consent.version <> 4 OR current_consent.status <> 'WITHDRAWN' THEN RAISE EXCEPTION 'Withdrawal version/status mismatch'; END IF;
    WITH granted AS (
        INSERT INTO consent_records (id, workspace_id, waitlist_id, subject_kind, subject_value, consent_type, purpose, policy_version, source, locale, status, given_at)
        VALUES (gen_random_uuid(), NULL, first_list, 'ANONYMOUS', 'shared-consent-subject', 'EXPLICIT', 'scope-regression', 'v1', 'public-form', 'es', 'ACTIVE', now())
        ON CONFLICT (waitlist_id, subject_kind, subject_value, purpose, policy_version) WHERE status = 'ACTIVE' DO NOTHING RETURNING *
    ) INSERT INTO consent_record_events (id, consent_id, workspace_id, waitlist_id, subject_kind, subject_value, consent_type, purpose, policy_version, source, locale, status, given_at, event_at)
        SELECT gen_random_uuid(), id, workspace_id, waitlist_id, subject_kind, subject_value, consent_type, purpose, policy_version, source, locale, status, given_at, now() FROM granted;
    GET DIAGNOSTICS affected = ROW_COUNT;
    IF affected <> 1 OR (SELECT count(*) FROM consent_record_events WHERE workspace_id IS NULL AND waitlist_id = first_list AND purpose = 'scope-regression') <> 4
        OR (SELECT count(*) FROM consent_records WHERE workspace_id IS NULL AND waitlist_id = first_list AND purpose = 'scope-regression') <> 3 THEN
        RAISE EXCEPTION 'Regrant did not retain withdrawn history';
    END IF;
    INSERT INTO consent_records (id, waitlist_id, subject_kind, subject_value, consent_type, purpose, policy_version, source, locale, status, given_at)
    VALUES (gen_random_uuid(), first_list, 'ANONYMOUS', 'shared-consent-subject', 'EXPLICIT', 'scope-regression', 'v2', 'public-form', 'es', 'ACTIVE', now());
    IF (SELECT count(*) FROM consent_records WHERE waitlist_id = first_list AND purpose = 'scope-regression' AND status = 'ACTIVE') <> 2 THEN
        RAISE EXCEPTION 'Active identity lost policy version';
    END IF;
    PERFORM pg_temp.expect_consent_constraint(format(
        'INSERT INTO consent_records (id, workspace_id, waitlist_id, subject_kind, subject_value, consent_type, purpose, policy_version, source, locale, status, given_at) VALUES (gen_random_uuid(), %L, %L, ''ANONYMOUS'', ''constraint-subject'', ''EXPLICIT'', ''constraints'', ''v1'', ''test'', ''en'', ''ACTIVE'', now())', tenant, first_list
    ), 'ck_consent_records_scope');
    PERFORM pg_temp.expect_consent_constraint(
        'INSERT INTO consent_records (id, subject_kind, subject_value, consent_type, purpose, policy_version, source, locale, status, given_at) VALUES (gen_random_uuid(), ''ANONYMOUS'', ''constraint-subject'', ''EXPLICIT'', ''constraints'', ''v1'', ''test'', ''en'', ''ACTIVE'', now())',
        'ck_consent_records_scope'
    );
    PERFORM pg_temp.expect_consent_constraint(
        'INSERT INTO consent_records (id, waitlist_id, subject_kind, subject_value, consent_type, purpose, policy_version, source, locale, status, given_at) VALUES (gen_random_uuid(), ''f0000000-0000-4000-8000-000000000001'', ''ANONYMOUS'', ''constraint-subject'', ''EXPLICIT'', ''constraints'', ''v1'', ''test'', ''en'', ''ACTIVE'', now())',
        'fk_consent_records_waitlist'
    );
    PERFORM pg_temp.expect_consent_constraint(
        'INSERT INTO consent_records (id, workspace_id, subject_kind, subject_value, consent_type, purpose, policy_version, source, locale, status, given_at) VALUES (gen_random_uuid(), ''f0000000-0000-4000-8000-000000000001'', ''ANONYMOUS'', ''constraint-subject'', ''EXPLICIT'', ''constraints'', ''v1'', ''test'', ''en'', ''ACTIVE'', now())',
        'fk_consent_records_workspace'
    );
    PERFORM pg_temp.expect_consent_constraint(format(
        'INSERT INTO consent_record_events (id, consent_id, workspace_id, waitlist_id, subject_kind, subject_value, consent_type, purpose, policy_version, source, locale, status, given_at, event_at) SELECT gen_random_uuid(), consent_id, NULL, %L, subject_kind, subject_value, consent_type, purpose, policy_version, source, locale, status, given_at, now() FROM consent_record_events WHERE consent_id = %L LIMIT 1',
        second_list, current_consent.id
    ), 'fk_consent_events_waitlist_scope');
    PERFORM pg_temp.expect_consent_constraint(format(
        'INSERT INTO consent_record_events (id, consent_id, workspace_id, waitlist_id, subject_kind, subject_value, consent_type, purpose, policy_version, source, locale, status, given_at, event_at) SELECT gen_random_uuid(), consent_id, %L, NULL, subject_kind, subject_value, consent_type, purpose, policy_version, source, locale, status, given_at, now() FROM consent_record_events WHERE consent_id = %L LIMIT 1',
        tenant, current_consent.id
    ), 'fk_consent_events_workspace_scope');
    PERFORM pg_temp.expect_consent_constraint(format(
        'INSERT INTO consent_record_events (id, consent_id, workspace_id, waitlist_id, subject_kind, subject_value, consent_type, purpose, policy_version, source, locale, status, given_at, event_at) SELECT gen_random_uuid(), ''f0000000-0000-4000-8000-000000000001'', NULL, waitlist_id, subject_kind, subject_value, consent_type, purpose, policy_version, source, locale, status, given_at, now() FROM consent_record_events WHERE consent_id = %L LIMIT 1',
        current_consent.id
    ), 'fk_consent_events_waitlist_scope');
    PERFORM pg_temp.expect_consent_constraint(format(
        'INSERT INTO consent_record_events (id, consent_id, workspace_id, waitlist_id, subject_kind, subject_value, consent_type, purpose, policy_version, source, locale, status, given_at, event_at) SELECT gen_random_uuid(), consent_id, %L, %L, subject_kind, subject_value, consent_type, purpose, policy_version, source, locale, status, given_at, now() FROM consent_record_events WHERE consent_id = %L LIMIT 1',
        tenant, first_list, current_consent.id
    ), 'ck_consent_record_events_scope');
    PERFORM pg_temp.expect_consent_constraint(format(
        'INSERT INTO consent_record_events (id, consent_id, subject_kind, subject_value, consent_type, purpose, policy_version, source, locale, status, given_at, event_at) SELECT gen_random_uuid(), consent_id, subject_kind, subject_value, consent_type, purpose, policy_version, source, locale, status, given_at, now() FROM consent_record_events WHERE consent_id = %L LIMIT 1',
        current_consent.id
    ), 'ck_consent_record_events_scope');
    INSERT INTO waitlists (id, key, name, context, status) VALUES (tenant, 'same-uuid-list', 'Same UUID, different scope', 'test', 'ACTIVE');
    INSERT INTO consent_records (id, waitlist_id, subject_kind, subject_value, consent_type, purpose, policy_version, source, locale, status, given_at)
    VALUES (gen_random_uuid(), tenant, 'ANONYMOUS', 'shared-consent-subject', 'EXPLICIT', 'scope-regression', 'v1', 'public-form', 'en', 'ACTIVE', now());
    IF (SELECT count(*) FROM consent_records WHERE workspace_id = tenant AND waitlist_id IS NULL AND purpose = 'scope-regression' AND policy_version = 'v1' AND status = 'ACTIVE') <> 1
        OR (SELECT count(*) FROM consent_records WHERE workspace_id IS NULL AND waitlist_id = tenant AND purpose = 'scope-regression' AND policy_version = 'v1' AND status = 'ACTIVE') <> 1 THEN
        RAISE EXCEPTION 'Equal UUID values leaked across WORKSPACE/WAITLIST identity';
    END IF;
END $$;
ROLLBACK;
