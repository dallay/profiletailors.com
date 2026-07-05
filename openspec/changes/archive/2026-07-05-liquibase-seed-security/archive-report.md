# Archive Report — Liquibase Seed Security

> **Archived:** 2026-07-05
> **Phase:** archive
> **Status:** COMPLETE

---

## Summary

Change `liquibase-seed-security` has been fully planned, implemented, verified, and archived. Production and development Liquibase changelogs are now isolated, committed BCrypt password hashes are removed, and a CI guardrail prevents credential seed material from reaching production migrations.

---

## Specs Synced to Main Spec

| Domain | Action | Details |
|--------|--------|---------|
| `liquibase-seed-security` | Created — new spec from delta | 3 requirements: production changelog isolation, explicit dev seeds, credential seed prohibition |

**Delta spec copied to:** `openspec/specs/liquibase-seed-security/spec.md`

This is a new domain spec — no existing main spec was present.

---

## Archive Contents

| Artifact | Location |
|----------|----------|
| Proposal | `openspec/changes/archive/2026-07-05-liquibase-seed-security/proposal.md` |
| Spec | `openspec/changes/archive/2026-07-05-liquibase-seed-security/spec.md` |
| Design | `openspec/changes/archive/2026-07-05-liquibase-seed-security/design.md` |
| Tasks | `openspec/changes/archive/2026-07-05-liquibase-seed-security/tasks.md` |
| Verify Report | `openspec/changes/archive/2026-07-05-liquibase-seed-security/verify-report.md` |
| State | `openspec/changes/archive/2026-07-05-liquibase-seed-security/state.yaml` |
| Archive Report | `openspec/changes/archive/2026-07-05-liquibase-seed-security/archive-report.md` |

---

## Implementation Summary

### Changelog Architecture
- **Before:** Single `db.changelog-master.yaml` included both schema migrations and dev seed data in the same chain.
- **After:** Two explicit roots — `db.changelog-master.yaml` (production-safe, schema + reference data only) and `db.changelog-dev.yaml` (composes production root with non-secret dev fixtures). The `dev` Spring profile selects the dev changelog and includes the `local-fixtures` profile.

### Committed Credential Removal
- Removed committed BCrypt password hash CSV and its Liquibase loader.
- No password hash or seed row for `password_hash` exists in version-controlled Liquibase resources.

### CI Guardrail
- New script `scripts/check-liquibase-seed-secrets.sh` scans YAML, CSV, and SQL resources for BCrypt markers and `password_hash` columns.
- Allows `password_hash` only in the schema migration that creates the column.
- Verified: negative test with a forbidden CSV correctly fails the script.

### Local Development Convenience
- `LocalFixturesRunner` with `@Profile("local-fixtures")` — an `ApplicationRunner` that:
  - Checks for `dev@profiletailors.com` credential existence at startup.
  - If absent, hashes `S3cr3tP@ssw0rd*123` with the active `PasswordHasher` and creates the credential.
  - Idempotent — skips if credential already exists.
  - Logs the principal ID and algorithm used.
- `application-local-fixtures.yaml` created and included from `application-dev.yaml`.

### Key Files Modified/Created
- `server/smp/src/main/resources/db/changelog/db.changelog-master.yaml` — removed dev seed include
- `server/smp/src/main/resources/db/changelog/db.changelog-dev.yaml` — new file, dev-specific changelog
- `server/smp/src/main/resources/application-dev.yaml` — selects dev changelog, includes local-fixtures
- `server/smp/src/main/resources/application-local-fixtures.yaml` — new profile config
- `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/LocalFixturesRunner.kt` — new runtime credential generator
- `scripts/check-liquibase-seed-secrets.sh` — new CI guardrail
- `server/smp/src/test/kotlin/com/profiletailors/smp/infrastructure/db/LiquibaseBaselineChangelogTest.kt` — regression tests for production/dev isolation + credential seeds
- `server/smp/src/test/kotlin/com/profiletailors/smp/identity/infrastructure/LocalFixturesRunnerTest.kt` — unit tests for creation and idempotence
- Removed: committed BCrypt credential CSV and its loader

---

## Verification Results

| Check | Result |
|-------|--------|
| `LiquibaseBaselineChangelogTest` — production/dev isolation + credential seed regression | ✅ PASS |
| `LocalFixturesRunnerTest` — creation and idempotence | ✅ PASS |
| `scripts/check-liquibase-seed-secrets.sh` — CI guardrail | ✅ PASS |
| Negative scan (forbidden CSV with BCrypt hash) | ✅ Script correctly FAILED |
| `just backend-test-fast` | ✅ BUILD SUCCESSFUL |
| `git diff --check` | ✅ PASS |

---

## Acceptance Criteria Status

| Criterion | Status |
|-----------|--------|
| Production master never includes development seeds | ✅ |
| Dev profile explicitly selects dev-only changelog | ✅ |
| No BCrypt hash or credential seed in committed Liquibase data | ✅ |
| CI rejects future credential seed material | ✅ |
| Local dev preserves `dev@profiletailors.com` login without versioned hashes | ✅ |

---

## Key Decisions

1. **Two changelog roots over conditional includes** — Explicit production and dev changelogs provide compile-time certainty instead of runtime profile filtering. The dev changelog composes the production baseline, ensuring both stay in sync while keeping dev seeds unreachable from production.

2. **Runtime-generated credential over committed hash** — `LocalFixturesRunner` hashes the password at startup using the active `PasswordHasher`. This means the hash algorithm automatically tracks production (currently BCrypt, future Argon2id via `PasswordHasher`). No offline-cracking artifact exists in version control.

3. **Profile-gated runner over conditional Liquibase include** — Using `@Profile("local-fixtures")` keeps the credential logic in code (type-safe, testable, evolvable) rather than in Liquibase markup. The runner is idempotent and logs transparently.

---

## Deferred Work

### BCrypt → Argon2id Migration
- **Status:** Tracked — GitHub issue #247
- **Reason:** Out of scope for this change. The `PasswordHasher` abstraction was designed for algorithm swapping; `LocalFixturesRunner` uses it and will automatically pick up the new algorithm when migration happens.
- **Impact on seed security:** None — the migration is a separate concern. Once the active `PasswordHasher` produces Argon2id hashes, `LocalFixturesRunner` will follow suit.

---

## Source of Truth Updated

The following spec now reflects the new Liquibase seed security behavior:
- `openspec/specs/liquibase-seed-security/spec.md`

---

## SDD Cycle Complete

The change has been fully planned (proposal), specified (spec), designed (design), tasked (tasks), implemented (apply), verified (verify), and archived. Ready for the next change.
