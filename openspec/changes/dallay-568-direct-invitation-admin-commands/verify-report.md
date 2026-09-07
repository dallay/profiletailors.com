# Verification Report: `dallay-568-direct-invitation-admin-commands`

## Change Summary

Direct invitation admin commands: `POST /api/admin/invitations/direct` (create, 201),
`POST /api/admin/invitations/{id}/direct-revoke` (revoke, 200),
`POST /api/admin/invitations/{id}/direct-resend` (resend, 200), plus
`platform.invitations.create` permission, role mapping, admin SPA UI, unit tests,
and 9 BDD scenarios in `invitations-direct.feature`.

## Completeness

| Phase | Tasks | Done | Evidence |
|-------|-------|------|----------|
| Phase 1 permission registry | 1.1–1.4 | 4/4 | `PlatformPermission.kt` has `INVITATIONS_CREATE/RESEND/REVOKE`; `PLATFORM_ROLE_PERMISSIONS` grants all three to OWNER + OPERATOR, denies SUPPORT_AGENT/AUDITOR; `AdminAuditEvent.kt` diff adds `INVITATION_CREATED` (`INVITATION_REVOKED` pre-existing); `auth.store.ts` diff adds `platform.invitations.create` to OWNER + OPERATOR |
| Phase 2 handlers + controller | 2.1–2.15 | 13/15 with deviations | Commands (`DirectInvitationCommands.kt`), results (`DirectInvitationResults.kt`), `hasActiveInvitationFor` port, `Invitation.revoke(expectedVersion)`, three handlers, `AdminInvitationController` direct routes, `DirectInvitationsView.vue` + `RevokeInvitationDialog.vue`, permission-gated UI. DEVIATIONS: no `InvitationNotificationAdapter` file (notification via `InvitationIssued` → `SendInvitationEmailConsumer`, plus `DirectInvitationResent` handling added); no `WorkspaceExistenceChecker` call in `CreateInvitationHandler`; `RevokeInvitationHandler` publishes no `InvitationRevoked` domain event |
| Phase 3 persistence | 3.1–3.4 | 4/4 (migration superseded) | `HAS_ACTIVE_INVITATION_FOR` query and `UPDATE_IF_VERSION_MATCHES` (`id` + `expectedVersion`) implemented; unique index already exists (`005-harden-invitations.yaml`: `uq_invitations_workspace_active_email`); no new migration — correct, redundant 006 removed; 409 mappings present in `AdminProblemDetailsHandler` (`INVITATION_ALREADY_ACTIVE`, `INVITATION_NOT_REVOCABLE`, `INVITATION_VERSION_CONFLICT`, `OPTIMISTIC_LOCK_CONFLICT`) |
| Phase 4 testing | 4.1–4.7 | 7/7 files exist, coverage gaps noted | `CreateInvitationHandlerTest` (3 tests), `RevokeInvitationHandlerTest` (6 tests), `ResendInvitationHandlerTest` (4 tests), `InvitationTest` (+2 revoke-version tests), `R2dbcInvitationRepositoryTest` (+`hasActive` cases per diff), `invitations-direct.feature` (9 scenarios), `PlatformAdminBddSteps` direct glue |
| Phase 5 verification | 5.1–5.6 | 1/6 | 5.2 done (BDD green). 5.1, 5.3–5.6 open — see findings |

## Build / Test / Coverage Evidence

| Check | Command / source | Result |
|-------|------------------|--------|
| BDD fast suite (prior full run, not re-run per instruction) | `server/smp/build/test-results/bddFastTest/TEST-feature_classpath_features-platformadmin-invitations-direct.feature.xml` (timestamp 2026-09-07T16:52:29Z) | **9/9 PASS**, 0 failures, 0 errors |
| Smoke: pure unit tests (run by this phase, 2026-09-07 ~17:01 UTC) | `./gradlew :server:smp:test --tests "...InvitationTest" --tests "...RevokeInvitationHandlerTest"` | BUILD SUCCESSFUL; `InvitationTest` 23/23 PASS, `RevokeInvitationHandlerTest` 6/6 PASS |
| Handler unit tests (prior run XML, 16:43) | `TEST-...CreateInvitationHandlerTest.xml`, `TEST-...ResendInvitationHandlerTest.xml` | 3/3 and 4/4 PASS |
| `just backend-check` (Detekt + tests) | NOT run by this phase | NOT RUN — Detekt report consumed instead; full gate still open (task 5.1) |
| Admin SPA gates | NOT run by this phase | NOT RUN — relied on apply-phase evidence |
| Unique index | `005-harden-invitations.yaml:63` `uq_invitations_workspace_active_email` | Present, no new migration needed — CONFIRMED |

## Spec Compliance Matrix

| Spec requirement | Artifact | Test | Status |
|------------------|----------|------|--------|
| Create success → 201 `{id, status}`, hash persisted, `ACTIVE` + `DIRECT`/`EXISTING_WORKSPACE` | `AdminInvitationController.createDirectInvitation` → 201; `CreateInvitationHandler` builds `DIRECT` aggregate, persists hash | BDD "Operator creates a direct invitation" (201 + ACTIVE) PASS | **PASS with deviation** (response shape — see CRITICAL-1) |
| Duplicate active → 409, no audit | `hasActiveInvitationFor` → `InvitationAlreadyActiveException` → 409 `INVITATION_ALREADY_ACTIVE` | BDD duplicate scenario PASS; unit duplicate test PASS | **PASS** |
| Create missing permission → 403 | `PlatformAccessDeniedException` → 403 | BDD AUDITOR scenario PASS; unit permission test PASS | **PASS** |
| Revoke success → 200 `{id, status}` | `revokeDirectInvitation` → 200 `RevokeInvitationResult(invitationId)` | BDD revoke scenario PASS; unit happy-path PASS | **PASS with deviation** (no `status` field in result — see WARNING-3) |
| Revoke already-revoked → 404 **or** 409 (invitations/spec.md:66-72) | `InvitationNotRevocableException` → 409 `INVITATION_NOT_REVOCABLE` | Unit "throws when invitation not active" PASS | **PASS** (task 4.2 text "200 or 404 per spec" misquotes the spec — task-text error, code matches spec) |
| Revoke missing → 404 | `InvitationNotFoundException` → 404 | BDD missing-revoke scenario PASS; unit not-found PASS | **PASS** |
| Revoke missing permission → 403 | `PlatformAccessDeniedException` → 403 | BDD AUDITOR revoke scenario PASS; unit permission test PASS | **PASS** |
| Version conflict → 409 | `InvitationVersionConflictException` → 409; `updateIfVersionMatches=false` → conflict | Unit version-conflict ×2 PASS | **PASS** |
| Unauthenticated → 401 (platformadmin/spec.md:84-88) | Controller returns 401 when `resolveOperator()` is null (all three direct endpoints) | **No BDD 401 scenario for direct endpoints**; only `getInvitation` 401 unit test | **GAP** (see WARNING-1) |
| Token never crosses API boundary; create returns `{id, status}` no token (platformadmin/spec.md:97-107; task 5.3) | `CreateInvitationResult` **contains `rawToken`** and controller returns it as the HTTP body | No test asserts absence of token | **FAIL** (see CRITICAL-1) |
| Audit events contain no raw token / full email | `AdminAuditEvent(INVITATION_CREATED/REVOKED)` carries IDs/roles only; `InvitationIssued` event carries `recipientEmail` + `rawToken` on the internal event bus (not audit) | BDD create/revoke PASS (event content not asserted) | **PASS** (audit), **NOTE** (bus event carries email+token by design for mail delivery — reconciled with DALLAY-566 delivery pattern) |
| Notification scheduled after persist; failure does not roll back | `eventPublisher.publish(InvitationIssued)` after `save`; `SendInvitationEmailConsumer` dispatches mail; `DirectInvitationResent` consumer added | **No notification-failure test** in `CreateInvitationHandlerTest` (3 tests only: happy, duplicate, permission) | **GAP** (see WARNING-2) |
| Counters `platform.invitations.created` / `.revoked` incremented | **No counter, MeterRegistry, or micrometer usage in any direct-invitation handler** (only `WaitlistQueryObservability` uses micrometer in platformadmin) | None | **FAIL** (see CRITICAL-2) |
| Unique index `(workspace_id, normalized_email, status='ACTIVE')` + conditional INSERT | Index exists (005); handler pre-checks `hasActiveInvitationFor` (check-then-insert, race closed by unique index → DB error path, not an explicit 409 mapping for constraint violation) | BDD duplicate PASS (sequential); concurrent scenario untested | **PASS** (concurrent-duplicate scenario from spec has no test — accepted, race-safe by index) |
| Resend flow (tasks 2.14–2.15, BDD) | `ResendInvitationHandler` + `direct-resend` route + `DirectInvitationResent` event + consumer | BDD resend ×3 PASS; unit 4/4 PASS | **PASS** (resend was out of scope in proposal but in tasks/design-adjacent specs — implemented and tested; scope expansion documented) |

## Correctness Table

| Requirement → artifact | Verdict |
|------------------------|---------|
| 1.1 enum values | PASS |
| 1.2 role mapping OWNER/OPERATOR grant, SUPPORT/AUDITOR deny | PASS |
| 1.3 audit event types | PASS (`INVITATION_CREATED` added, `INVITATION_REVOKED` pre-existing) |
| 1.4 admin SPA permission lists | PASS |
| 2.1–2.3 commands + results | PASS (shape deviations tracked separately) |
| 2.4 `hasActiveInvitationFor` port + impl | PASS |
| 2.5 `revoke(expectedVersion)` aggregate | PASS (version-mismatch → conflict, non-ACTIVE → not-revocable) |
| 2.6 create handler authorize/normalize/duplicate/token/persist/emit | PASS except workspace-existence check absent and `workspaceId!!` NPE risk on null (see WARNING-4) |
| 2.7 revoke handler authorize/load/workspace/assert/revoke/emit/persist | PASS except no `InvitationRevoked` domain event published (see WARNING-5) |
| 2.8 controller routes + status mapping | PASS except route paths differ from all three artifacts (see WARNING-6) and revoke body lacks `status` |
| 2.9–2.10 notification wiring | PASS functionally via existing consumer (`@Bean` wiring N/A — no adapter bean needed); file-level task text not literally matched |
| 2.11–2.13 admin SPA UI | PASS (files exist; visual acceptance is `sdd-qa` territory) |
| 3.1–3.4 persistence + 409 mapping | PASS |
| 4.1–4.7 tests | PASS with gaps (notification-failure, 401 BDD, concurrent duplicate) |

## Design Coherence Table

| Design decision | Implementation | Verdict |
|-----------------|----------------|---------|
| Token never leaves domain boundary (rejected: return raw token from controller) | Controller returns `CreateInvitationResult` **including `rawToken`**; design §Handlers step 7 and data-flow contradict the decision box by specifying `InvitationCreatedResponse(invitationId, token, …)` | **DEVIATED** — spec and design contradict each other; product decision required (CRITICAL-1) |
| Domain events drive notification (rejected: direct gateway call) | `InvitationIssued` published post-save; `SendInvitationEmailConsumer` delivers; failures async | **COHERENT** (event named `InvitationIssued`, not `InvitationCreated` — naming deviation only, documented in `InvitationIssued.kt`) |
| Duplicate detection via `hasActiveInvitationFor` | Implemented exactly | **COHERENT** |
| Two permissions, OWNER + OPERATOR only | Implemented exactly | **COHERENT** |
| Optimistic locking via `version` | `revoke(expectedVersion)` + `UPDATE … WHERE id AND version` | **COHERENT** |
| No migration needed | Confirmed — index from 005 | **COHERENT** |

## Issues

### CRITICAL

| Finding | Judge A | Judge B | Severity | Status |
|---------|---------|---------|----------|--------|
| CRITICAL-1: `rawToken` in create HTTP response contradicts spec MUST NOT. `DirectInvitationResults.kt:5-10` (`CreateInvitationResult` has `rawToken`), returned as body by `createDirectInvitation` (201). Spec platformadmin/spec.md:97-107 requires `{id, status}` with no token field; task 5.3 same. `ResendInvitationResult` likewise exposes `rawToken`. Spec deltas say NOTHING permitting token exposure on create — the permission is an explicit MUST NOT. (Design doc internally contradicts itself: decision box forbids it, §Handlers step 7 / data-flow mandates it.) | ✅ | ✅ | CRITICAL | Confirmed |
| CRITICAL-2: metrics counters absent despite spec MUST. platformadmin/spec.md requires `platform.invitations.created` / `.revoked` increments; proposal success criteria require them; no Micrometer/counter usage in any direct handler. | ✅ | ✅ | CRITICAL | Confirmed |

### WARNING

| Finding | Judge A | Judge B | Severity | Status |
|---------|---------|---------|----------|--------|
| WARNING-1: no 401 scenario for direct endpoints. Code returns 401 correctly, but BDD covers only 403; controller unit tests cover 401 only for `getInvitation`. | ✅ | ✅ | WARNING | Confirmed (gap, code correct) |
| WARNING-2: `CreateInvitationHandlerTest` missing task-4.1 cases: workspace-not-found and notification-failure-does-not-rollback (3 tests vs 5 required). | ✅ | ✅ | WARNING | Confirmed |
| WARNING-3: `RevokeInvitationResult` carries only `invitationId`, no `status` — task 5.4 expects `{id, status}`. | ✅ | ✅ | WARNING | Confirmed (minor) |
| WARNING-4: `CreateInvitationHandler` has no workspace-existence check (task 2.6 / design data-flow require it) and dereferences `command.workspaceId!!` twice — null `workspaceId` (legal per `CreateDirectInvitationRequest.workspaceId: String?`, e.g. NEW_WORKSPACE) throws NPE → 500 instead of 400. | ✅ | ❌ | WARNING (unprobed at runtime) | Suspect — needs a unit test to confirm |
| WARNING-5: `RevokeInvitationHandler` publishes audit event but no `InvitationRevoked` domain event; `InvitationRevoked.kt` exists but is unreferenced in `main`. Design §Handlers step 5 requires it. No consumer needs it today (mail on revoke not required), but dead event type + design drift. | ✅ | ✅ | WARNING | Confirmed |
| WARNING-6: route-path drift across artifacts. Proposal: `POST /api/admin/invitations` + `.../revoke`; design: `.../revoke` and `POST /api/admin/invitations/revoke`; tasks 2.8: `POST /api/admin/invitations` + `.../{id}/revoke`; implementation: `POST /direct`, `/{id}/direct-revoke`, `/{id}/direct-resend` (collision-avoidance with waitlist routes is the sensible rationale, but no artifact records it). | ✅ | ✅ | WARNING (docs) | Confirmed |
| WARNING-7: 9 of 10 Detekt findings are INTRODUCED by this change, contradicting the apply-phase claim of "0 introduced". New-file findings: `InvalidPackageDeclaration` (`DirectInvitationCommands.kt:1` — file lives in `contracts/`, package declares `command`), `LongMethod` (`CreateInvitationHandler.handle`, 62 vs 60), `ThrowsCount` (both direct handlers), `MaxLineLength` ×3 + `UnderscoresInNumericLiterals` ×2 (new handler tests). Only `LargeClass` on `PlatformAdminBddSteps` is pre-existing (HEAD = 636 lines > 600 threshold, verified via `git show HEAD:…| wc -l`). Per repo static-analysis DoD, new code must add zero findings. | ✅ | ✅ | WARNING (process-blocking per DoD, not behavioral) | Confirmed |

### SUGGESTION

| Finding | Judge A | Judge B | Severity | Status |
|---------|---------|---------|----------|--------|
| Task 4.2 text "already-revoked idempotency (200 or 404 per spec)" misquotes the spec, which says "HTTP 404 or 409". Implementation (409) matches the spec. Fix the task text, not the code. | ✅ | ❌ | SUGGESTION | Info |
| `DirectInvitationCommands.kt` filename vs `package …application.command` (singular) mismatch is the source of the `InvalidPackageDeclaration` finding; aligning file location or package name fixes WARNING-7 item 1. | ✅ | ✅ | SUGGESTION | Info |
| Resend was out of scope in the proposal but fully implemented per tasks + BDD. Record the scope expansion in the proposal or a follow-up change so `sdd-archive` does not flag scope drift. | ✅ | ❌ | SUGGESTION | Info |

## Verdict

**FAIL** — two MUST-level spec non-compliances (rawToken in API response; missing metrics counters) plus nine introduced Detekt findings against a zero-new-findings DoD. BDD 9/9 and unit smoke tests (23/23 + 6/6 fresh) prove the happy-path/duplicate/permission/version-conflict behavior works, so the failure is contract conformance, not functionality.

## Handoff to `sdd-qa`

Not yet recommended. Fix CRITICAL-1 (product decision), CRITICAL-2, and WARNING-7 first then re-verify; `sdd-qa` owns acceptance scenarios and `qa-report.md` after this report. No user/operator acceptance is claimed here.

## Addendum 2026-09-07 — CRITICAL-1 fixed (Option A, product-approved)

Product decision: strip `rawToken` from API responses (Option A). Applied:

- `DirectInvitationResults.kt`: `rawToken` removed from `CreateInvitationResult` and `ResendInvitationResult`. Events (`InvitationIssued`, `DirectInvitationResent`), email delivery, and acceptance flow untouched — verified by grep that the only removed surface is the HTTP response DTO.
- `CreateInvitationHandler` / `ResendInvitationHandler`: result construction drops the token; event publication keeps it.
- `DirectInvitationsView.vue`: token block removed, `CreatedInvitation` loses `rawToken?`, submit maps `{invitationId, status, expiresAt}` explicitly (also fixes a latent mismatch: view read `body.id`, API returns `invitationId`).
- `directInvitations.success.token` key removed from EN/ES `index.ts` and `types.ts`; missing ES `INVITATION_VERSION_CONFLICT` / `OPTIMISTIC_LOCK_CONFLICT` keys and `types.ts` errors gap fixed; biome formatting applied via formatter.
- Contract tests: BDD step `the invitation response should not contain the token` now also asserts absence of `rawToken`; create-success scenario includes the step (RED proven by code inspection: controller returned the field until stripped).
- TDD cycle: strip → `compileTestKotlin` RED with exactly 2 errors (`CreateInvitationHandlerTest:82`, `ResendInvitationHandlerTest:96`) → tests re-pinned to `{id, status, expiresAt}` → GREEN.
- Evidence: unit `Create` 3/3 + `Resend` 4/4; admin `type-check` clean, `lint` clean, Vitest 27/27 (incl. new no-token UI regression test); `:server:smp:bddFastTest` BUILD SUCCESSFUL, 233/233 total, direct feature 9/9 with strengthened assertion; Detekt unchanged at 10 pre-existing, 0 new from this fix.
- Remaining for re-verify: CRITICAL-2 (counters), WARNING-7 (9 introduced findings), WARNING-4/5/6, missing 401 direct scenario, `backend-check` 5.1.
