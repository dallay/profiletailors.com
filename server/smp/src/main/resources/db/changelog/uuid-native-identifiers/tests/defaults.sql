BEGIN;
INSERT INTO email_verification_tokens (email, token_hash, expires_at)
VALUES ('post-cutover@example.invalid', 'post-cutover-token', now() + interval '1 day');
INSERT INTO idempotency_records (workspace_id, principal_id, tool_name, key_hash, response_json)
VALUES ('20000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', 'post-cutover', 'opaque-post-cutover-hash', '{}');
DO $$
BEGIN
    IF (SELECT id FROM email_verification_tokens WHERE token_hash = 'post-cutover-token') IS NULL
        OR (SELECT id FROM idempotency_records WHERE tool_name = 'post-cutover') IS NULL THEN
        RAISE EXCEPTION 'Generated UUID missing';
    END IF;
END $$;
ROLLBACK;
