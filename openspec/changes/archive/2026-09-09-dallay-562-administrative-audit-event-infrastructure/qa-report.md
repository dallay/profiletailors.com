# Acceptance QA Report: dallay-562-administrative-audit-event-infrastructure

## Identity
- Change: dallay-562-administrative-audit-event-infrastructure
- Mode: openspec
- QA phase: qa (after verify)
- Date: 2026-09-09
- Runner mode: `fallback` — no deterministic runner/FSM envelope exists for this harness; evidence is direct `just`/Gradle output and JUnit XML reports executed by the QA executor on 2026-09-09. Limitation: results are observable command output, not a signed runner envelope.

## Sources of Truth
- Proposal: `openspec/changes/dallay-562-administrative-audit-event-infrastructure/proposal.md`
- Specifications: `openspec/changes/dallay-562-administrative-audit-event-infrastructure/spec.md` (flat file; no `specs/` directory for this change)
- Design: `openspec/changes/dallay-562-administrative-audit-event-infrastructure/design.md`
- Tasks: `openspec/changes/dallay-562-administrative-audit-event-infrastructure/tasks.md`
- Technical verification: `openspec/changes/dallay-562-administrative-audit-event-infrastructure/verify-report.md` (verdict `PASS`, 2026-09-06; known non-blocking deviations: `[REDACTED]` masking vs spec "removal" wording; broader `auth`/`bearer` substring coverage)
- Exploration: `openspec/changes/dallay-562-administrative-audit-event-infrastructure/exploration.md`
- Config: `openspec/config.yaml`
- Linear acceptance (per orchestrator handoff): audit events persistable with actor/action/target/timestamp/correlation ID/safe metadata; secrets and raw tokens excluded; capability slices can publish via reusable infra; automated tests verify persistence + redaction.
- Gherkin (per orchestrator handoff): persist event with actor/action/target/timestamp; raw one-time token must not be stored.

## Target and Environment
- Target: backend persistence seam `R2dbcAdminAuditRepository.publish()` + `redact()` in `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/persistence/`; `platform_admin_audit_events` table via Liquibase changelogs under `server/smp/src/main/resources/db/changelog/`. Backend-only change, no UI surface.
- Environment: worktree `/Users/acosta/Dev/dallay/worktrees/clean-specs`; OpenJDK (Gradle toolchain); Docker 29.0.1 available (server responsive, Testcontainers usable); PostgreSQL integration tests run against ephemeral Testcontainers PostgreSQL with `spring.liquibase.enabled=true`.
- Credentials/permissions: none required; test token fixtures and ephemeral containers only. No production data touched.
- Limitations: no running application server (no live API target); no browser target exists for this change; persistence assertions limited to what the query API returns (`toSummary()` omits metadata, so stored-metadata content is asserted only at unit level — see QA-F1).

## Capability Inventory
| Capability | Availability | Selected? | Rationale / rejection reason |
|---|---|---|---|
| backend_unit (JUnit, `just backend-test-fast`) | available | selected | Narrowest executable capability for redact() acceptance; produces JUnit XML evidence. |
| backend_postgres_integration (Testcontainers, `postgresIntegrationTest`) | available | selected | Only capability producing observable persist/read-back evidence for the audit seam; Docker present. |
| backend_bdd_fast (Cucumber) | available | rejected | No redaction/audit-token scenarios exist in any feature file (`rg -i "redact|token.*stor|sensitive"` over audit feature files: no matches); a full BDD run would duplicate verify-owned technical conformance without new acceptance evidence. |
| backend_architecture (ArchUnit/Modulith) | available | rejected | Owned by verify; no acceptance-observable behavior added by re-running. |
| API/client manual requests | unavailable | rejected | No running server target; publisher-port behavior covered by integration tests. |
| browser / Playwright / Chrome DevTools | unavailable | rejected | No UI surface in scope (proposal Out of Scope: query API/UI). |
| accessibility / responsive | unavailable | rejected | Non-applicability: backend-only change, no rendered surface. |
| locale / i18n | unavailable | rejected | Non-applicability: no user-facing copy in scope. |
| static inspection | available | rejected (as evidence) | Used only to identify the target and inform scenarios; per policy it MUST NOT produce PASS evidence. |

## Scenario Matrix
| ID | Capability | Acceptance scenario | Result | Evidence or reason |
|---|---|---|---|---|
| QA-01 | backend_postgres_integration | Happy path: event with actor/action/target/timestamp/correlation ID + safe metadata persists and reads back | PASS | `R2dbcAdminAuditRepositoryPostgresIntegrationTest`: 9 tests, 0 failures, 0 errors (`TEST-...xml`, timestamp 2026-09-09T10:44:06Z); includes `publish persists event and list reads it back` asserting eventId/occurredAt/operator/action/target/result/reason/correlationId/requestId round-trip. Command: `node scripts/gradle-run.mjs :server:smp:postgresIntegrationTest --no-daemon --tests "...R2dbcAdminAuditRepositoryPostgresIntegrationTest"` → BUILD SUCCESSFUL in 38s. |
| QA-02 | backend_unit | Security: sensitive keys (password/token/secret/key/credential/auth/bearer, case variants) are masked so raw values never reach the stored map | PASS | `RedactSensitiveMetadataTest`: 7 tests, 0 failures/errors/skipped (XML timestamp 2026-09-09T10:43:16Z): case-insensitive match, password/token/credential masking, mixed map, empty map, non-sensitive passthrough. Enforcement call site `R2dbcAdminAuditRepository.kt:28` (`val redactedMetadata = redact(event.metadata)`) serializes only the redacted map. |
| QA-03 | backend_unit + backend_postgres_integration | Gherkin: raw one-time token must not be stored | PASS | Unit: `session_token → [REDACTED]` (`redact masks value when key contains token`); stored map therefore contains no raw token value. Integration publish round-trip green (QA-01), so the masked map is what gets serialized. Acceptance "excluded" satisfied by masking (see QA-F2 for wording note). |
| QA-04 | backend_unit + backend_postgres_integration | Negative: event with no sensitive metadata is stored intact | PASS | Unit `redact leaves non-sensitive entries unchanged`; IT `publish persists non-sensitive metadata as-is` green. |
| QA-05 | backend_postgres_integration | Boundary: empty metadata map publishes without error (binds null) | PASS | `event()` fixture uses default empty metadata; `publish persists event and list reads it back` green; unit `redact returns empty map for empty input` green. |
| QA-06 | backend_postgres_integration | Repeated/state-transition: ordering, operator/action/date filtering, pagination validation | PASS | IT cases `list orders by occurred_at descending`, `list filters by operator and action`, `list filters by date range`, `list rejects invalid page size` — all green in the 9/9 run. |
| QA-07 | backend_unit | Capability slices publish via reusable infra unmodified (handlers still emit through `AdministrativeAuditPublisher`) | PASS | 8 handler files reference `AdministrativeAuditPublisher` (rg, 2026-09-09); full `just backend-test-fast` → BUILD SUCCESSFUL in 1m 42s on 2026-09-09, including handler tests asserting audit emission (e.g. `AssignPlatformRoleHandlerTest`, `InviteWaitlistEntryHandlerTest`). |
| QA-08 | backend_unit (compile+tests) | Orphaned `administrative` context deleted; V006 rolled back; V007 metadata column present | PASS | `rg "com.profiletailors.smp.administrative" server/smp/src/` → no matches (exit 1); `platform-admin/` changelog contains no `006` file and `db.changelog-master.yaml` has no `006` include (lines 137–149 list 001–005 + 2×007); `007-add-metadata-to-platform-admin-audit-events.yaml` adds `metadata TEXT`; IT ran with Liquibase enabled against the migrated schema → green, i.e. the migration applies. |
| QA-09 | — | Browser / responsive / accessibility / locale behavior | NOT TESTED | Non-applicability: backend-only change with no rendered or user-facing surface (proposal explicitly out of scope: query API/UI). |
| QA-10 | — | BDD-lane acceptance scenarios for redaction | NOT TESTED | No redaction scenarios exist in feature files (verified by grep over `governance-audit-events.feature`, `platform-admin.feature`, `platformadmin/`); nothing executable to run for this acceptance risk. |

Allowed results: `PASS`, `FAIL`, `BLOCKED`, `NOT TESTED`.

## Untested Scope
- Scope: stored-metadata byte-content assertion at the persistence layer (i.e. reading back the raw `metadata` JSON column and asserting `[REDACTED]` present / raw secret absent); live-server API behavior; browser/a11y/i18n.
- Reason: query API summaries omit metadata by current design, so the existing IT can only assert row existence for the redaction case; no live server or UI target exists for this change; no BDD scenarios cover redaction.
- Re-run prerequisite: strengthen `publish redacts sensitive metadata fields before persisting` to publish genuinely sensitive keys and assert on the stored `metadata` column directly (new query or direct `DatabaseClient` read), then re-run `postgresIntegrationTest` for the audit class; for BDD, add a redaction scenario + glue, then run `just backend-bdd-fast`.

## Findings
| ID | Severity | Scenario / location | Evidence | Status |
|---|---|---|---|---|
| QA-F1 | P2 | QA-02/QA-03 — `R2dbcAdminAuditRepositoryPostgresIntegrationTest.kt:164` (`publish redacts sensitive metadata fields before persisting`) uses only non-sensitive keys (`email`, `ip`, `phone`, `non_sensitive`) and asserts just row existence (`assertNotNull`), so no persistence-layer assertion proves a sensitive value was masked at rest | Test source lines 164–179 read 2026-09-09; `toSummary()` (`R2dbcAdminAuditRepository.kt:125–137`) omits metadata, so the test as written cannot assert stored content | Open — test hardening recommended; non-blocking (behavior evidenced by unit masking + enforced call site) |
| QA-F2 | P3 | QA-03 — implementation masks values (`[REDACTED]`, `RedactSensitiveMetadata.kt:15–17`) while spec scenarios describe keys as "removed" | Carried from verify-report (accepted as non-blocking); Linear/Gherkin acceptance requires only that raw secrets/tokens are *excluded from storage*, which masking satisfies | Accepted — no code change; suggest aligning spec wording ("masked" vs "removed") at archive |
| QA-F3 | P3 | QA-08 — duplicate `007-` filename prefix: `007-add-invitation-target.yaml` and `007-add-metadata-to-platform-admin-audit-events.yaml` | `ls platform-admin/` + changelog includes lines 147/149, read 2026-09-09; changeset IDs unique, ordering via master include | Open (informational) — functionally harmless; suggest unique numbering convention for future migrations |

Allowed severities: `CRITICAL`, `P0`, `P1`, `P2`, `P3`.

## Verdict
`PASS WITH WARNINGS`

### Rationale
All applicable acceptance scenarios (QA-01–QA-08) have fresh observable evidence from 2026-09-09: 7/7 unit tests green, 9/9 PostgreSQL integration tests green (including end-to-end publish→persist→read-back with actor/action/target/timestamp/correlation ID), full `backend-test-fast` green (handlers emit unmodified), orphaned context/migration state verified on disk and through a Liquibase-migrated test container. The Gherkin security property holds: raw token values cannot reach storage because `publish()` serializes only the `redact()`ed map, and `redact()` is proven to mask every denylisted key shape. Remaining items are non-blocking warnings: one P2 test-hardening gap (no stored-content assertion at rest), two P3 notes (spec wording, migration numbering). No CRITICAL/P0/P1, no FAIL, no acceptance-relevant BLOCKED. Per `openspec/config.yaml` archive policy (blockers: unresolved CRITICAL/P0/P1, acceptance-relevant BLOCKED/NOT TESTED), the NOT TESTED items (QA-09/QA-10) are non-acceptance-relevant with recorded rationale, so archive MAY proceed with this report's visible warning.

## Limitations and Handoff
- QA does not fix code.
- Product acceptance is not claimed without a target and observable evidence.
- Follow-up for implementation:
  - Strengthen the persistence redaction test per QA-F1 (sensitive keys in, direct `metadata`-column assertion) — recommended before the next audit-scope change, not required to release this one.
  - Align spec wording on masking (QA-F2) when archiving/syncing specs.
  - Note: `tasks.md` checkboxes T5.1 (backend-check), T5.2 (no refs), T6.1 (integration-test prerequisite) are unchecked, but QA independently re-evidenced their substance on 2026-09-09 (fast suite green, zero `administrative` refs, V007 present + IT green); T6.1's stated prerequisite (metadata column) is now resolved. Suggest the archiver reconcile `tasks.md`/`state.yaml` (`current_phase` still `verify`) without changing this verdict.
