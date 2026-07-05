# Verify Report: Liquibase Seed Security

## Status
PASS

## Evidence
- `./gradlew :server:smp:test --tests "com.profiletailors.smp.infrastructure.db.LiquibaseBaselineChangelogTest" --no-daemon -PexcludeTags=modularity,postgres` — PASS
- `./gradlew :server:smp:test --tests "com.profiletailors.smp.identity.infrastructure.LocalFixturesRunnerTest" --no-daemon -PexcludeTags=modularity,postgres` — PASS
- `scripts/check-liquibase-seed-secrets.sh` — PASS
- Negative scan using temporary CSV containing `password_hash,$2b$12$forbidden` — script failed as expected
- `just backend-test-fast` — PASS
- `git diff --check` — PASS

## Notes
- Full `just ci` was not run because the scoped backend/security change is covered by focused backend tests plus the backend-fast suite.
- `LocalFixturesRunner` restores local dev login via runtime-generated credential under `@Profile("local-fixtures")`.
- No hash is stored in version control — the password `S3cr3tP@ssw0rd*123` is hardcoded in the runner and hashed at startup using the active `PasswordHasher`.
