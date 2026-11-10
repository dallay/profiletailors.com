# Apply Progress

## Commands Run

| Command | Result |
|---|---|
| `git status --short --branch` | Exit 0 before this slice's implementation; current changes are the in-progress Work Unit 1 artifacts and source/tests created in these apply slices. |
| `just -l` | Exit 0; confirmed `backend-test-fast`, `backend-check`, and `backend-test-postgres` recipes. |
| `docker info --format '{{.ServerVersion}}'` | Exit 0; Docker server `29.6.1` is available. |
| Focused `ExternalIdentityLinksPostgresSchemaTest` against the original changelog | Exit 1 as expected for RED; compilation and Testcontainers setup succeeded; PostgreSQL reported SQLSTATE `42P01`, relation `external_identity_links` does not exist. |
| Focused `ExternalIdentityResolutionServiceTest` with the fail-closed seam | Exit 1 as expected for RED; 5 tests ran, with 3 failures for approved-link resolution, unverified-email resolution, and conflict resolution. Failures were behavioral assertions, not compilation/configuration failures. |
| Focused `ExternalIdentityResolutionServiceTest` after resolver implementation | Exit 0; all 5 tests passed. |
| Focused `ExternalIdentityLinksPostgresSchemaTest` after migration | Exit 0 before schema tests were split into focused cases; migration initially exposed a FK type mismatch during UUID conversion. The migration now registers `external_identity_links.principal_id` in the UUID-native conversion mapping. |
| Focused schema and R2DBC gateway PostgreSQL tests | Exit 0; migration, FK, pair uniqueness, duplicate concurrency, link lookup, verified sync, and email conflict behavior passed. |
| `./gradlew :server:smp:spotlessKotlinApply --no-daemon` | Exit 0; applied configured Kotlin formatting. |
| `just backend-test-fast` | Exit 0; unfiltered fast backend suite passed. |
| `just backend-test-postgres` | Exit 0; unfiltered PostgreSQL integration suite passed. |
| `just backend-check` | Exit 0 after the final source/test edits; includes formatting, Detekt, unfiltered backend unit tests, and unfiltered PostgreSQL integration tests. XML reports show `3368` unit tests (`0` failures, `0` errors, `2` skipped) and `522` PostgreSQL integration tests (`0` failures, `0` errors, `0` skipped). |
| `git diff --check` | Exit 0 after the final implementation edits. |

## Task Evidence

| Task | State | Evidence |
|---|---|---|
| 1.1 | Complete | `.agents/sdd/changes/keycloak-human-authentication/identity-and-consumer-inventory.md` records the earlier inventory. |
| 1.2 | RED complete | Unit tests cover `(issuer, subject)` lookup, approved link selection, no email lookup, and missing, ambiguous, disabled, and disallowed links. The approved link resolves only its mapped existing principal. |
| 1.3 | RED/GREEN complete | PostgreSQL schema tests prove valid link insertion, preserved `principals.subject`/`provider`, unique issuer+subject including concurrent duplicate inserts, and principal FK rejection. The test does not assert `principal_id` uniqueness or cardinality. Migration adds no principal uniqueness constraint. |
| 1.4 | Complete | `ExternalIdentityResolutionServiceTest.kt:107-122` proves disabled/suspended local principals return no resolved identity; existing unit and PostgreSQL test outcomes prove verified-email conflict skips sync, preserves local email and mapped principal, and does not prevent identity resolution, while unverified email does not sync. `server/smp/src/test/resources/features/resource-preview.feature:17-22` already proves valid JWT authentication is independent from per-resource denial (403 for a disallowed target scope), so no redundant scenario is needed. Session refusal remains a Work Unit 2 behavior. |
| 1.5 | GREEN complete | Liquibase migration creates `external_identity_links`, pair primary key, principal FK, link status check/default, and UUID-native `principal_id` conversion. Existing local principal fields and email uniqueness remain unchanged. |
| 1.6 | GREEN complete | Framework-free identity-link model, application gateway/service contract, and R2DBC adapter resolve only one approved validated pair and sync only verified nonblank email. Email conflicts do not change the resolved local principal. The service checks user principal status/account eligibility and does not perform resource authorization or email-based principal selection. |
| 1.7 | Complete | The identity model and R2DBC migration are verified by the recorded identity unit and PostgreSQL persistence tests. `just backend-check` passed after the final source/test edits, including formatting, Detekt, unfiltered unit tests, and unfiltered PostgreSQL integration tests. Session-creation gating and validated-claims trust wiring remain Work Unit 2 scope; per-resource authorization is independently evidenced by the existing resource-preview feature. |

## TDD Evidence

| Behavior | RED | GREEN | REFACTOR |
|---|---|---|---|
| External identity schema | Observed SQLSTATE `42P01` for absent relation after the baseline changelog applied. | Focused PostgreSQL schema tests passed after the migration and UUID-native mapping were added. | Full unfiltered PostgreSQL suite passed after final test cleanup and formatting. |
| Identity resolution and email sync | 3 of 5 focused unit tests failed against the fail-closed test seam; failure was the missing approved-link resolution behavior. | All 5 unit tests passed after implementing the application resolver. PostgreSQL gateway tests passed for lookup, verified email update, and conflict preservation. | `just backend-check` passed, including formatting, Detekt, unit tests, and PostgreSQL integration tests. |

The schema RED and application RED were observed before their respective behavior implementations. The test-facing application contract was introduced as a fail-closed seam so the tests compiled without relying on nonexistent OIDC callback APIs. No OAuth callback, BFF session, or Work Unit 2 behavior was implemented. `ValidatedExternalIdentity` is publicly constructible, so Work Unit 2 must wire only verified claims obtained from Spring Security's validated OIDC boundary; constructing this model alone does not establish claim trust.

## Review Workload

- Forecast: High.
- Strategy: `feature-branch-chain`.
- Boundary: `trunk=main`; `parent_branch=main`; `base=main`; `branch=feature/keycloak-auth-migration`; `position=1`; `issue=none`.
- Implemented slice: Work Unit 1 external identity relation, R2DBC persistence, and application resolution/email-sync contract only.
- Budget concern: no scope expansion into Work Unit 2.

## Remaining Tasks

- [ ] 2.3–2.4: Add and implement Work Unit 2 tests and behavior for session-creation gating on local authentication/account eligibility and validated-claims trust at Spring Security's validated OIDC boundary.
- [ ] 2.7: Verify Work Unit 2 session gating and validated-claims wiring alongside its listed backend checks. No Work Unit 1 task remains open in this apply-progress record.
