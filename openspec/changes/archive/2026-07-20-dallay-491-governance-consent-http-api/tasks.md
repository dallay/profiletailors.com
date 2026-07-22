# Tasks: Governance Consent HTTP API

## Review Workload Forecast

| Field                   | Value           |
|-------------------------|-----------------|
| Estimated changed lines | 320-380         |
| 400-line budget risk    | Low             |
| Chained PRs recommended | No              |
| Suggested split         | Single PR       |
| Delivery strategy       | single-pr       |
| Chain strategy          | stacked-to-main |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: stacked-to-main
400-line budget risk: Low

## Discarded from draft tasks (12 phases, 77 tasks)

- Phase 7 OpenAPI polish — `@Schema` annotations, `@SecurityRequirement`, `@Operation` examples,
  `@ApiResponse` error schemas: dropped. Existing controllers in repo do not require these for new
  endpoints.
- Phase 10 BDD — `consent-api.feature`, Cucumber glue, `just backend-bdd-postgres`: dropped. Adds
  steps, fixtures, and tag wiring that exceed budget.
- Phase 11 end-to-end — `ConsentApiAuthenticatedE2ETest` with `@SpringBootTest` and JWT-gen:
  dropped. Replaced by one Postgres integration test.
- Phase 9 cross-domain authorization suites — `WorkspaceOwnerConsentAccessTest`,
  `WorkspaceAdminConsentAccessTest`, `WorkspaceMemberConsentDeniedTest`: dropped. Permission
  enforcement covered by migration existence + handler-level authorization test.
- Phase 6 dedicated Problem Details handler test file (`GovernanceProblemDetailsHandlerEnumTest`) as
  separate class: dropped. Covered by `ConsentControllerWebTest` 400 scenarios.
- Phases 8.1, 8.3, 8.5 — three separate concurrent/withdrawal/re-consent Postgres tests: collapsed
  to **one** focused `R2dbcConsentLedgerIntegrationTest`.
- Liquibase backfill changelog (governance/006) — current projection has zero rows in dev, backfill
  is YAGNI: dropped.
- `FindHistoricalByIdentityTest` — replaced by the same Postgres integration test reading the
  ledger.
- Locale format regex validation — speculative; existing controllers rely on `@Size` + `@NotBlank`:
  dropped.
- Cross-domain `PermissionRegistry.kt`/`RoleDefinitions.kt` in-code edits — authorization is
  DB-backed (per design.md decision): dropped in favor of Liquibase.

## Corrected from draft tasks

- Phase 5.7 status logic — draft fix was correct in direction but wrong mechanism: now driven by
  `RecordConsentOutcome.created` (no `WITHDRAWN` inference).
- Phase 1.6/1.7 repository signature — draft used `recordAtomic(command)`; corrected to extend
  existing `ConsentRepository` with `recordFromHttp(...)` so the trusted waitlist path (
  `RecordConsentHandler.handle`) is untouched.
- Phase 5.8 controller dispatch — corrected: controllers use `Mediator` only (per design), no direct
  handler injections.
- Phase 4.6 — `GetConsentHistoryHandler` does NOT call `WorkspaceAuthorizationDecider` in draft;
  corrected to call it (per spec scenario: workspace:consent:read enforcement).
- Phase 2 — permission registration corrected to be Liquibase-only (no in-code registry edit),
  following existing 008/009 patterns.

## Added (not in draft)

- Explicit `RecordConsentOutcome(created, record)` return type on the application handler.
- `consent_record_events` append-only ledger as the source of truth for `findHistoricalByIdentity`.
- Partial unique index
  `(workspace_id, subject_kind, subject_value, purpose, policy_version) WHERE status = 'ACTIVE'` for
  database-enforced idempotency.
- `RecordConsentHandler` keeps an explicit-workspace overload for the waitlist adapter; HTTP path
  uses a new context-resolved variant so waitlist ownership direction stays clean.
- One Postgres `R2dbcConsentLedgerIntegrationTest` covering: insert-then-conflict (idempotency),
  withdrawal appends WITHDRAWN event, re-consent after withdrawal produces new ACTIVE event.

## Phase 1: Persistence — ledger + idempotency (foundation)

- [ ] 1.1 RED: Write `ConsentLedgerMigrationTest` asserting `consent_record_events` table and
  partial unique index exist after Liquibase runs
- [ ] 1.2 GREEN: Create `db/changelog/governance/005-create-consent-record-events.yaml` with
  `consent_record_events` (id PK, consent_id FK nullable, workspace_id, subject_kind, subject_value,
  purpose, policy_version, status, given_at, withdrawn_at, withdrawal_reason, created_at) and
  partial unique index `uq_consent_active` on
  `(workspace_id, subject_kind, subject_value, purpose, policy_version) WHERE status = 'ACTIVE'`
- [ ] 1.3 GREEN: Include changelog 005 in `db/changelog/db.changelog-master.yaml`
- [ ] 1.4 RED: Write `RecordConsentOutcomeTest` expecting `RecordConsentOutcome(created, record)`
  from existing `RecordConsentHandler`
- [ ] 1.5 GREEN: Add `RecordConsentOutcome` data class to
  `governance/application/ConsentCommands.kt`
- [ ] 1.6 GREEN: Change `RecordConsentHandler.handle` to return
  `RecordConsentOutcome(created = (existing == null), record = ...)`
- [ ] 1.7 RED: Write `R2dbcConsentRepositoryRecordFromHttpTest` expecting atomic insert + ledger
  append with `created` flag
- [ ] 1.8 GREEN: Add `recordFromHttp(workspaceId, ...): RecordConsentOutcome` to `ConsentRepository`
  interface
- [ ] 1.9 GREEN: Implement in `R2dbcConsentRepository` using
  `INSERT ... ON CONFLICT (uq_consent_active) DO NOTHING RETURNING *` then append `RECORDED` event
  row in one `R2dbcTransaction`; if RETURNING empty, read winner and return `created=false`
- [ ] 1.10 RED: Write `R2dbcConsentRepositoryWithdrawFromHttpTest` expecting ledger WITHDRAWN append
- [ ] 1.11 GREEN: Add `withdrawFromHttp(workspaceId, ...): ConsentRecord?` to interface
- [ ] 1.12 GREEN: Implement row-lock UPDATE on `consent_records` + append `WITHDRAWN` event in one
  transaction; return null when no active row

## Phase 2: Authorization permission seeding

- [ ] 2.1 RED: Write `WorkspaceConsentPermissionSeedTest` asserting row in `permissions` and links
  to `WORKSPACE_OWNER`/`WORKSPACE_ADMIN` (none to `WORKSPACE_MEMBER`)
- [ ] 2.2 GREEN: Create `db/changelog/authorization/010-seed-consent-permission.yaml` inserting
  `workspace:consent:read` and the role links via existing seed conventions
- [ ] 2.3 GREEN: Include changelog 010 in `db.changelog-master.yaml`

## Phase 3: Application — query handlers (authz + Flow.toList())

- [ ] 3.1 RED: Write `GetWorkspaceConsentRecordsHandlerAuthzTest` expecting
  `AuthorizationDeniedException` when decider denies
- [ ] 3.2 GREEN: Add `WorkspaceAuthorizationDecider.requirePermission(workspace:consent:read)` call
  before repository fetch in `GetWorkspaceConsentRecordsHandler`
- [ ] 3.3 GREEN: Fix Flow materialization by calling `.toList()` on the mapped `Flow<ConsentRecord>`
  in both query handlers
- [ ] 3.4 RED: Write `GetConsentHistoryHandlerAuthzTest` with same expectation
- [ ] 3.5 GREEN: Add authorization check + Flow.toList() to `GetConsentHistoryHandler`
- [ ] 3.6 REFACTOR: Extract `CONSENT_READ_PERMISSION = "workspace:consent:read"` constant in
  `governance/domain/ConsentPermission.kt`

## Phase 4: HTTP — controller (Mediator-only, 4 endpoints, Problem Details)

- [ ] 4.1 RED: Extend `ConsentControllerWebTest` with two cases: (a) invalid `subjectKind="CONTACT"`
  returns 400 Problem Details with `title="Bad Request"` and `detail` listing valid values; (b)
  missing record on withdraw returns 404 Problem Details with `title="Not Found"`
- [ ] 4.2 GREEN: Wrap `SubjectKind.valueOf(...)` and `ConsentType.valueOf(...)` in try/catch in
  `ConsentController`; throw a typed `EnumValidationException` that
  `GovernanceProblemDetailsHandler` maps to 400 with RFC 9457 body
- [ ] 4.3 GREEN: Confirm `ConsentRecordNotFoundException` already maps to 404 in
  `GovernanceProblemDetailsHandler`; add case if missing
- [ ] 4.4 RED: Extend test with two cases: (c) `record()` returns 201 when `outcome.created=true`,
  200 when false; (d) `withdraw()` returns 200 with `status=WITHDRAWN` body
- [ ] 4.5 GREEN: Replace inverted status logic in `record()` with
  `if (outcome.created) HttpStatus.CREATED else HttpStatus.OK`
- [ ] 4.6 GREEN: Switch `record()` and `withdraw()` to dispatch via `Mediator` (inject `Mediator`
  instead of direct handlers); keep controller signatures free of `principalId`/`workspaceId` (
  resolved by handlers from `ResourceContextProvider`)
- [ ] 4.7 GREEN: Confirm `/list` and `/history` endpoints already go through `Mediator` + decider;
  remove any draft-added direct handler injections
- [ ] 4.8 REFACTOR: Verify `ConsentController` constructor only depends on `Mediator`

## Phase 5: Integration — one Postgres test

- [ ] 5.1 RED: Write `R2dbcConsentLedgerIntegrationTest` (Testcontainers, `@Tag("postgres")`) with
  three scenarios: (i) two concurrent `recordFromHttp` calls produce one ACTIVE row + one RECORDED
  event + one outcome with `created=true`; (ii) `withdrawFromHttp` appends a WITHDRAWN event with
  matching `consent_id`; (iii) re-consent after withdrawal creates a new ACTIVE row + new RECORDED
  event, history endpoint returns both
- [ ] 5.2 GREEN: Make scenarios pass by iterating on repository impl from Phase 1.9/1.12
- [ ] 5.3 GREEN: Update `findHistoricalByIdentity` to read from `consent_record_events` ordered by
  `created_at, id`

## Phase 6: Verification

- [ ] 6.1 Run `just backend-test-fast` — all unit + WebFlux tests pass
- [ ] 6.2 With `just infra-up`, run
  `./gradlew :server:smp:test --tests "*postgres*" --tests "*ledger*"` — single Postgres integration
  test passes
- [ ] 6.3 Run `just backend-check` — detekt + tests clean
- [ ] 6.4 Update `state.yaml`: current_phase = apply, completed += tasks
