ALTER TABLE email_verification_tokens ALTER COLUMN id DROP IDENTITY IF EXISTS;
ALTER TABLE idempotency_records ALTER COLUMN id DROP IDENTITY IF EXISTS;
CREATE SEQUENCE uuid_test_email_tokens_id_seq OWNED BY email_verification_tokens.id;
CREATE SEQUENCE uuid_test_idempotency_id_seq OWNED BY idempotency_records.id;
ALTER TABLE email_verification_tokens ALTER COLUMN id SET DEFAULT nextval('uuid_test_email_tokens_id_seq');
ALTER TABLE idempotency_records ALTER COLUMN id SET DEFAULT nextval('uuid_test_idempotency_id_seq');
