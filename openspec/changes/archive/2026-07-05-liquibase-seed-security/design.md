# Design: Liquibase Seed Security

## Architecture
Use two explicit Liquibase roots: the production-safe `db.changelog-master.yaml`, and `db.changelog-dev.yaml`, which composes the production root with non-secret dev fixtures. `application-dev.yaml` selects the latter and includes the `local-fixtures` profile.

## Local Fixtures Profile
The `local-fixtures` Spring profile activates `LocalFixturesRunner`, an `ApplicationRunner` that:
- Checks for `dev@profiletailors.com` credential existence.
- If absent, hashes `S3cr3tP@ssw0rd*123` with the active `PasswordHasher` and creates the credential.
- Is idempotent — skips if the credential already exists.
- Logs at startup with the principal ID and algorithm used.

The hardcoded password exists only in runtime code under `@Profile("local-fixtures")`, never in migrations or data files.

## Security Decision
Do not commit a reusable local password hash. Developers create credentials through the normal registration flow or activate `local-fixtures` for immediate login convenience. This removes an offline-cracking artifact and avoids a known default account in production-reachable migrations.

## Guardrail
A repository script scans Liquibase YAML, CSV, and SQL resources for BCrypt markers or `password_hash`, allowing the latter only in the schema migration that creates the column. The quality gate invokes the script before builds.

## Trade-offs
Local dev requires activating `local-fixtures` explicitly or accepting no preseeded login. This is intentional: convenience does not justify a committed shared credential, but runtime-generated credentials restore the experience without the security cost.
