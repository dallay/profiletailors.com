# Apply Progress — #671 Takedown Governance Admin (PR 1)

## Layer

PR 1 of `feature-branch-chain` — governance ports + DTO + asset-status reader. No HTTP admin.

| Field | Value |
|-------|-------|
| Trunk | `main` |
| Parent branch | `main` |
| Base | `origin/main` (`0e9107f5`) |
| Branch | `issue-671-governance-ports` |
| Position | 1 of 3 |
| Delivery strategy | `auto-chain` / `feature-branch-chain` |
| Scope implemented | Phase 1 tasks 1.1–1.8 |
| size:exception | not used |

## Completed Tasks (this batch)

- [x] 1.1 Ports + DTO in `governance.application` (`AdminTakedownQueryPort` / `CommandPort`, `AdminTakedownPage`, status `String`, `assetStatus` detail-only)
- [x] 1.2 RED postgres tests for global `findByReportId`, paged `findAll`, `count`
- [x] 1.3 GREEN repository methods; `findByWorkspace` unchanged
- [x] 1.4 RED `MediaAssetStatusReader` tests (READY/SUSPENDED/null, no media types on the port)
- [x] 1.5 GREEN `MediaAssetStatusReader` + `config/bridges/MediaAssetStatusReaderDelegate`
- [x] 1.6 RED adapter tests (approve/reject lifecycle, not-found, not-reviewable, operator id, no `ResourceContext`)
- [x] 1.7 GREEN query/command adapters using domain `approve`/`dismiss`, updater, `AuditHook`, emails
- [x] 1.8 HexagonalArchTest: ports in `governance.application`; `TakedownReport` stays inside governance

## Files Created

| File | Purpose |
|------|---------|
| `server/smp/src/main/kotlin/com/profiletailors/smp/governance/application/AdminTakedownPorts.kt` | Query/command ports, DTOs, page type, admin 404/409 exceptions |
| `server/smp/src/main/kotlin/com/profiletailors/smp/governance/application/AdminTakedownMappings.kt` | Aggregate → DTO mapping |
| `server/smp/src/main/kotlin/com/profiletailors/smp/governance/application/AdminTakedownQueryAdapter.kt` | Cross-workspace list/get |
| `server/smp/src/main/kotlin/com/profiletailors/smp/governance/application/AdminTakedownCommandAdapter.kt` | Approve/reject without ResourceContext |
| `server/smp/src/main/kotlin/com/profiletailors/smp/governance/application/MediaAssetStatusReader.kt` | Read port returning status `String?` |
| `server/smp/src/main/kotlin/com/profiletailors/smp/config/bridges/MediaAssetStatusReaderDelegate.kt` | Bridge to `MediaAssetRepository` |
| `server/smp/src/test/kotlin/com/profiletailors/smp/governance/application/AdminTakedownPortsTest.kt` | Port/DTO contract tests |
| `server/smp/src/test/kotlin/com/profiletailors/smp/governance/application/AdminTakedownQueryAdapterTest.kt` | Query adapter tests |
| `server/smp/src/test/kotlin/com/profiletailors/smp/governance/application/AdminTakedownCommandAdapterTest.kt` | Command adapter tests |
| `server/smp/src/test/kotlin/com/profiletailors/smp/governance/application/MediaAssetStatusReaderTest.kt` | Reader port tests |
| `server/smp/src/test/kotlin/com/profiletailors/smp/config/bridges/MediaAssetStatusReaderDelegateTest.kt` | Delegate tests |

## Files Edited

| File | Change |
|------|--------|
| `server/smp/src/main/kotlin/com/profiletailors/smp/governance/domain/TakedownReportRepository.kt` | `findByReportId`, paged `findAll`, `count` |
| `server/smp/src/main/kotlin/com/profiletailors/smp/governance/infrastructure/R2dbcTakedownReportRepository.kt` | Implementations; `findByWorkspace` unchanged |
| `server/smp/src/test/kotlin/com/profiletailors/smp/governance/infrastructure/R2dbcTakedownReportRepositoryTest.kt` | Global get + two-workspace paged/count tests |
| `server/smp/src/test/kotlin/com/profiletailors/smp/HexagonalArchTest.kt` | TakedownReport isolation + port package rules |
| `openspec/changes/671-takedown-governance-admin/tasks.md` | Phase 1 marked `[x]` |

Untouched: workspace `ApproveTakedownHandler` / `RejectTakedownHandler`, platformadmin, frontend, BDD.

## Commands Run

| Command | Exit | Evidence |
|---------|------|----------|
| `:server:smp:test --tests AdminTakedownPortsTest` (RED) | fail | Unresolved `AdminTakedownQueryPort` |
| `:server:smp:test --tests AdminTakedownPortsTest` (GREEN) | 0 | BUILD SUCCESSFUL |
| `:server:smp:compileTestKotlin` after 1.2 tests | fail | Unresolved `findByReportId` / `findAll` / `count` |
| `:server:smp:test --tests R2dbcTakedownReportRepositoryTest` | 0 | BUILD SUCCESSFUL (~1m19s, Testcontainers) |
| `:server:smp:test --tests MediaAssetStatusReaderTest` (RED) | fail | Unresolved `MediaAssetStatusReader` |
| `:server:smp:test --tests MediaAssetStatusReaderTest` (GREEN) | 0 | BUILD SUCCESSFUL |
| `:server:smp:test --tests MediaAssetStatusReaderDelegateTest` (RED) | fail | Unresolved `MediaAssetStatusReaderDelegate` |
| `:server:smp:test --tests MediaAssetStatusReaderDelegateTest` + reader | 0 | BUILD SUCCESSFUL |
| `:server:smp:compileTestKotlin` after 1.6 tests | fail | Unresolved adapters |
| `:server:smp:test --tests AdminTakedownQueryAdapterTest --tests AdminTakedownCommandAdapterTest` | 0 | BUILD SUCCESSFUL |
| `:server:smp:test --tests HexagonalArchTest --tests ComponentScanArchTest` | 0 | BUILD SUCCESSFUL (~2m45s) |
| `:server:smp:test --tests governance.application.* --tests MediaAssetStatusReaderDelegateTest` (post-format) | 0 | BUILD SUCCESSFUL (57s) |
| `:server:smp:detekt` + `:server:smp:spotlessCheck` | 0 | BUILD SUCCESSFUL after renaming page factory `of` → `from` (FunctionNameMinLength) and spotlessApply |

## Not Run

- `just backend-test-fast` unfiltered (user asked focused checks; architecture owners run explicitly)
- `just backend-check` / full CI
- BDD / admin frontend (PR3)
- platformadmin HTTP (PR2)

## Review Workload

- Forecast from tasks.md: High; PR1 ~300
- Actual: ~950 lines including tests (production ~400, tests ~550)
- Budget concern: over 400 including tests. Production slice is near budget. Tests stay with the work unit.
- Chain strategy: feature-branch-chain
- Next: PR2 `issue-671-admin-api` base=`issue-671-governance-ports`

## Deviations

None from design except `AdminTakedownPage.from` instead of `of` to avoid a new Detekt `FunctionNameMinLength` baseline entry (`PagedResult.of` is already baselined).

## Issues

None blocking. ADR-0023 remains untracked from design (T-5.2 / PR3 cite). Not part of PR1 code.

---

# Apply Progress — #671 Takedown Governance Admin (PR 2 + Review Fixes + BDD + Frontend)

## Layer

PR2 (platformadmin API) was committed as `79bb83d3` on branch `issue-671-admin-api`. This batch adds
PR2 follow-ups from review findings, the BDD suite, and the admin frontend (GovernanceView).

## Completed

- `JacksonConfigurationIdempotencyCodec` owns a private `ObjectMapper` with `JavaTimeModule`
  registered. Rationale: Spring injects the shared Jackson-2 `ObjectMapper` bean (Kotlin module only)
  into the codec constructor, ignoring the Kotlin default argument — so the constructor default never
  took effect at runtime and `encode(AdminTakedownReport)` threw `InvalidDefinitionException` on
  `Instant` fields. Unit-proven with an `AdminTakedownReport` round-trip test.
- `010-create-configuration-idempotency.yaml` left untouched; new changeset
  `011-widen-configuration-idempotency-command.yaml` widens `command` 32 → 128
  (`approve_takedown:<reportId>` needs ~66 chars). Immutable migration history preserved.
- `RetryNotificationHandler`-style outcome audits already present in `AdminTakedownHandlers`
  (SUCCESS/REJECTED/FAILED); no change needed.
- BDD `takedown-admin.feature` (14 scenarios) + `TakedownAdminBddSteps.kt` glue:
  reporter principal seeding (FK), `items` response shape, `principalHasRole` clear-then-seed for
  SUPPORT_AGENT/AUDITOR guard scenarios, seeded report for AUDITOR mutate, reviewed-field seeding
  for DISMISSED/APPROVED fixtures (domain invariants), deterministic empty-query via unmatched
  recipient filter, values-only payload redaction assertion.
- `GovernanceView.vue` + spec, live `/governance` + `/governance/:reportId` routes,
  `nav-registry` governance `live` on `platform.governance.read`, EN+ES labels.
- `docs/architecture/c4/03-component.md`: `Rel(platformadmin, governance, "Admin takedown ports")`.

## Verification

- `compileKotlin` / `compileTestKotlin`, `detekt`, `spotlessCheck`: green.
- Focused unit suites green (handlers, controller, codec, consumer, ports/adapters, permissions,
  audit actions, redaction, arch/modularity).
- Admin: `type-check`, `biome check`, `test:run`, `build` green.
- `bddFastTest` takedown feature: PENDING (running at time of writing; outcome to be recorded here).

---

# Apply Progress — #671 Takedown Governance Admin (PR2 + Review Fixes + BDD + Frontend)

## Layer

PR2 (`AdminTakedownHandlers`, `AdminTakedownController`, permissions, dual audit) was committed as
`79bb83d3` on branch `issue-671-admin-api`. This batch adds review-driven fixes, the BDD suite,
and the admin frontend (GovernanceView).

## Review Findings Fixed (all verified against current code; docs-restructure/produces/time-window
findings skipped with reasons in-session)

- `JacksonConfigurationIdempotencyCodec` owns a private `ObjectMapper` with `JavaTimeModule`.
  Root cause of approve/reject HTTP 500: Spring injects the shared Jackson-2 `ObjectMapper` bean
  (Kotlin module only) into the codec constructor, ignoring the Kotlin default argument — so
  `encode(AdminTakedownReport)` threw `InvalidDefinitionException` on `Instant` fields. Proven by a
  new round-trip unit test (failed before, green after). `R2dbcNotificationAdminQueryAdapter`-style
  standalone mappers were audited; only this codec serializes `Instant`-holding DTOs.
- New Liquibase changeset `011-widen-configuration-idempotency-command.yaml` widens `command`
  32 → 128 (`approve_takedown:<reportId>` needs ~66 chars). `010` left untouched (immutable history).
- BDD glue: reporter-principal seeding (FK), `items` response shape, clear-then-seed role guards
  (`principalHasRole`), seeded report for AUDITOR mutate, reviewed-field seeding for
  DISMISSED/APPROVED fixtures (domain invariants), deterministic empty-query, values-only payload
  redaction assertion, `requireNotNull` instead of `!!`.
- Feature `takedown-admin.feature`: 14 scenarios; role-guard scenarios use clear-then-seed steps.
- Frontend: `GovernanceView.vue` (list/filter/detail/approve/reject, manage-gated, `Idempotency-Key`,
  confirm), live `/governance` + `/governance/:reportId` routes, `nav-registry` governance `live`
  on `platform.governance.read`, EN+ES labels, view spec (6 tests).
- `docs/architecture/c4/03-component.md`: `Rel(platformadmin, governance, "Admin takedown ports")`.

## Verification (all green before PR)

- `compileKotlin` / `compileTestKotlin`, `detekt`, `spotlessCheck`: green.
- Focused unit suites green: takedown handlers/controller, governance ports/adapters, codec
  round-trip, permissions, audit actions, redaction, arch/modularity.
- `bddFastTest`: 291/291 green (takedown feature 14/14).
- `bddPostgresTest`: 291/291 green, BUILD SUCCESSFUL (takedown feature 14/14).
- Admin: `type-check`, `biome check`, `test:run` (115), `build` green.

## Residual Risks (documented, not blocking)

- Concurrent same-key approve/reject relies on the DB unique constraint (sequential reuse → 409).
- `notifications` table deliberately absent from BDD cleanup; takedown scenarios use unique
  workspaces/filters and stay deterministic.
