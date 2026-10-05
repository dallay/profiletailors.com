# Verification Report

**Change**: 670-notification-admin-operations **Issue**: dallay/profiletailors.com#670 (epic #656)
**PR**: [#1119](https://github.com/dallay/profiletailors.com/pull/1119) MERGED `0e9107f5` at
2026-09-21T09:39:59Z **GitHub issue #670**: still OPEN (not closed by this verify)
**Version**: delta specs in `openspec/changes/670-notification-admin-operations/specs/`
**Mode**: openspec (fallback — `openspec/quality-runner.json` absent; deterministic runner not
available)
**Strict TDD config**: `openspec/config.yaml` `testing.strict_tdd: true` (strict-tdd-verify module
file not present in skill directory)
**Date**: 2026-09-21 **Branch verified**: `main` (merged implementation)

This report is technical conformance only. It does not claim user/operator acceptance. Next owner is
`sdd-qa`.

---

### Completeness

| Metric                                     | Value                                  |
|--------------------------------------------|----------------------------------------|
| Tasks listed in `tasks.md`                 | 23 checkbox items (summary claimed 30) |
| Tasks marked `[x]` in `tasks.md`           | 17                                     |
| Tasks still `[ ]` in `tasks.md`            | 6 (4.1, 4.2, 5.1, 5.2, 6.1, 6.2)       |
| Tasks with implementation evidence on main | 23/23 core items present               |
| Cleanup / UI view beyond task list         | NotificationsView missing (known gap)  |

`tasks.md` checkboxes for phases 4–6 are stale. `apply-progress.md` and main source show
controllers, permissions, nav live, and auth.store mirrors already landed. Incomplete checkboxes are
a tracking WARNING, not missing core code.

Unchecked in `tasks.md` (implemented on main):

- 4.1 `GET /api/admin/notifications` — `AdminNotificationController.listNotifications`
- 4.2 `POST /api/admin/notifications/{id}/retry` — `AdminNotificationController.retryNotification`
- 5.1 `platform.notifications.read` — `PlatformPermission.NOTIFICATIONS_READ`
- 5.2 `platform.notifications.manage` — `PlatformPermission.NOTIFICATIONS_MANAGE`
- 6.1 nav item live — `apps/web/admin/src/router/nav-registry.ts`
- 6.2 auth.store permission mirror — `apps/web/admin/src/stores/auth.store.ts`

---

### Build & Tests Execution

**Runner**: fallback (no `openspec/quality-runner.json` / `sdd-quality-runner.mjs`)

**Build**: ➖ Not-run locally (avoided broad `:server:smp:check` / `just backend-build`). Remote: PR
#1119 `🔨 Production Builds` SUCCESS.

**Detekt / Spotless**: ➖ Not-run locally. Remote: PR #1119 `🧹 Lint` SUCCESS.

**Coverage**: ➖ Not configured (`openspec/config.yaml` has no `rules.verify.coverage_threshold`).
Remote: `codecov/patch` SUCCESS, Quality Gate SUCCESS.

#### Local (this verify session)

| Command                                                                                                                                                                                                                                                                    | Scope                                                       | Result                                 |
|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------|----------------------------------------|
| `node scripts/gradle-run.mjs :shared:notifications:test --tests NotificationRetryEligibilityTest,PayloadRedactorTest,NotificationCanRetryTest`                                                                                                                             | eligibility / redactor / canRetry                           | Passed — 26 tests, 0 failed, 0 skipped |
| `node scripts/gradle-run.mjs :server:smp:test -PexcludeTags=modularity,postgres --tests RetryNotificationHandlerTest,DispatchNotificationRetryConsumerTest,AdminNotificationControllerWebTestClientTest,R2dbcNotificationAdminQueryAdapterTest,OperatorAccessResolverTest` | handler / consumer / controller / adapter DTO / permissions | Passed — 40 tests, 0 failed, 0 skipped |
| Combined focused backend                                                                                                                                                                                                                                                   |                                                             | **66 passed / 0 failed / 0 skipped**   |
| `just admin-check` (`vue-tsc --build`)                                                                                                                                                                                                                                     | admin type-check                                            | Passed                                 |
| `just admin-test` (`vitest run`)                                                                                                                                                                                                                                           | admin unit                                                  | Passed — 12 files, **105 tests**       |

`R2dbcNotificationAdminQueryAdapterTest` is a DTO/pagination unit test, not Testcontainers SQL. Real
SQL adapter coverage is `R2dbcNotificationAdminQueryPostgresTest` (`@Tag("postgres")`) — local
Not-run; remote `🐘 Backend Postgres` SUCCESS.

#### Local Not-run (reason)

| Gate                           | Reason                                                                     |
|--------------------------------|----------------------------------------------------------------------------|
| `just backend-check`           | Broad/expensive; CI Backend Unit Tests + Lint already SUCCESS on PR #1119  |
| `just backend-bdd-fast`        | Feature tagged `@postgres` as well as `@fast`; CI `🔨 Backend BDD` SUCCESS |
| `just backend-test-postgres`   | Testcontainers; CI `🐘 Backend Postgres` SUCCESS                           |
| `just admin-build`             | Type-check passed; CI Production Builds SUCCESS                            |
| Hexagonal/Modulith full suites | Covered by CI backend unit tests, not re-run here                          |

#### Remote (PR #1119, merge commit `0e9107f5`)

| Check                                                 | Result  |
|-------------------------------------------------------|---------|
| 🧹 Lint                                               | SUCCESS |
| 🧪 Backend Unit Tests                                 | SUCCESS |
| 🔨 Backend BDD                                        | SUCCESS |
| 🐘 Backend Postgres                                   | SUCCESS |
| 🧪 Frontend Unit Tests                                | SUCCESS |
| 🔨 Production Builds                                  | SUCCESS |
| ✅ CI Gate                                            | SUCCESS |
| Quality Gate                                          | SUCCESS |
| Security PR (gitleaks, semgrep, codeql, trivy, biome) | SUCCESS |
| SonarCloud / codecov/patch                            | SUCCESS |

---

### Spec Compliance Matrix

Behavioral status uses **local passing tests** where run, plus **CI BDD SUCCESS** for the 10
`notifications-admin.feature` scenarios (not re-executed locally).

#### platform-notifications

| Requirement      | Scenario                                 | Test                                                                                                                                   | Result                                                                                                                                                                |
|------------------|------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| REQ-PN-001       | Query failed notifications with filters  | Local `AdminNotificationControllerWebTestClientTest` filter/pagination; CI BDD filter-by-status and filter-by-channel                  | ✅ COMPLIANT                                                                                                                                                          |
| REQ-PN-002       | Inspect redacted notification payload    | Local `PayloadRedactorTest` (15); CI BDD “Sensitive fields are redacted”                                                               | ⚠️ PARTIAL — secrets replaced with `[REDACTED]` (keys remain); spec wording says strip / “does NOT contain token field”                                               |
| REQ-PN-003 / 004 | Retry eligible password-recovery success | Local `RetryNotificationHandlerTest` create+event+SUCCESS audit; `DispatchNotificationRetryConsumerTest` SENT/FAILED; CI BDD retry 200 | ✅ COMPLIANT                                                                                                                                                          |
| REQ-PN-004       | Retry invitation template denied         | Local handler invitation → `NotificationNotRetryableException` + REJECTED audit; CI BDD 400                                            | ⚠️ PARTIAL — HTTP 400 yes; denial text is `Notification {id} is not retryable: status=..., template=...` not “Retry not allowed for token-bound invitation templates” |
| REQ-PN-006       | Query denied without read permission     | Local WebTestClient 403 without read                                                                                                   | ⚠️ PARTIAL — 403 without read is proven; spec actor “AUDITOR lacks read” contradicts admin-authorization (AUDITOR has read)                                           |
| REQ-PN-007       | Retry denied without manage permission   | Local handler AUDITOR denied; WebTestClient 403 without manage                                                                         | ✅ COMPLIANT                                                                                                                                                          |
| REQ-PN-005       | Delivery state observability for pending | Local WebTestClient GET-by-id 200 with details                                                                                         | ⚠️ PARTIAL — GET-by-id covered; no dedicated PENDING/SMS assertion                                                                                                    |

#### admin-authorization

| Requirement          | Scenario                    | Test                                            | Result                                                                                                             |
|----------------------|-----------------------------|-------------------------------------------------|--------------------------------------------------------------------------------------------------------------------|
| notifications.read   | AUDITOR can query           | `OperatorAccessResolverTest` AUDITOR has READ   | ⚠️ PARTIAL — permission mapping proven; no HTTP 200 as AUDITOR                                                     |
| notifications.read   | VIEWER cannot query         | (none — no VIEWER role)                         | ⚠️ PARTIAL — 403 without permission proven; `PlatformRole` has no VIEWER; SUPPORT_AGENT has read                   |
| notifications.manage | OPERATOR can retry eligible | WebTestClient retry 200; handler success        | ⚠️ PARTIAL — behavior yes; admin-authorization says **202**, implementation and platform-notifications say **200** |
| notifications.manage | AUDITOR cannot retry        | Handler + WebTestClient 403                     | ✅ COMPLIANT                                                                                                       |
| Permission registry  | registry includes both keys | `PlatformPermission` enum + resolver tests      | ✅ COMPLIANT                                                                                                       |
| Role mapping         | OWNER has both              | `PLATFORM_OWNER` → `PlatformPermission.entries` | ⚠️ PARTIAL — structural; no dedicated OWNER HTTP test                                                              |
| Role mapping         | AUDITOR read-only           | `OperatorAccessResolverTest`                    | ✅ COMPLIANT                                                                                                       |

#### platform-admin-audit

| Requirement          | Scenario                                | Test                                                    | Result       |
|----------------------|-----------------------------------------|---------------------------------------------------------|--------------|
| NOTIFICATION_RETRIED | Retry success audited with full context | `RetryNotificationHandlerTest` SUCCESS metadata         | ✅ COMPLIANT |
| NOTIFICATION_RETRIED | Retry rejected eligibility audited      | handler REJECTED audit, no save                         | ✅ COMPLIANT |
| NOTIFICATION_RETRIED | Redacted priorError in audit event      | handler `authentication token expired` → `[REDACTED]`   | ✅ COMPLIANT |
| Event type registry  | includes NOTIFICATION_RETRIED           | `AdminAuditAction.NOTIFICATION_RETRIED` + handler tests | ✅ COMPLIANT |

**Compliance summary**: 9 ✅ COMPLIANT / 9 ⚠️ PARTIAL / 0 ❌ FAILING / 0 ❌ UNTESTED among the 18 named
scenarios. Partials are message/status/actor/UI mismatches, not missing endpoints.

---

### Correctness (Static — Structural Evidence)

| Requirement                                  | Status         | Notes                                                                                                                                                           |
|----------------------------------------------|----------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| REQ-PN-001 query port + filters + pagination | ✅ Implemented | `NotificationAdminQuery` + `R2dbcNotificationAdminQueryAdapter` dynamic WHERE; max page size coerced                                                            |
| REQ-PN-002 payload redaction                 | ⚠️ Partial     | `redactPayload` in `shared/notifications`; denylist includes inviteLink/resetLink/resetUrl; Map+Iterable; values `[REDACTED]` not stripped                      |
| REQ-PN-003 retry via event + new PENDING row | ✅ Implemented | `RetryNotificationHandler` saves PENDING, publishes `NotificationRetryRequested`; `DispatchNotificationRetryConsumer` marks SENT/FAILED; original row unchanged |
| REQ-PN-004 eligibility whitelist             | ✅ Implemented | Exact-match `platform.password-recovery` + `platform.password-reset`; invitations denied                                                                        |
| REQ-PN-005 delivery fields                   | ✅ Implemented | DTO exposes status, errorMessage, channel, templateId, sentAt, createdAt, failedAt                                                                              |
| REQ-PN-006 read permission                   | ✅ Implemented | Controller checks `NOTIFICATIONS_READ` via `OperatorAccessResolver`                                                                                             |
| REQ-PN-007 manage permission                 | ✅ Implemented | Controller + handler check `NOTIFICATIONS_MANAGE`                                                                                                               |
| NOTIFICATION_RETRIED audit                   | ✅ Implemented | SUCCESS / REJECTED / DISPATCH_FAILED; priorError redacted                                                                                                       |
| Admin GET/POST media type                    | ✅ Implemented | OpenAPI `application/vnd.api.v1+json`; BDD uses same Accept                                                                                                     |
| Frontend nav + permissions                   | ⚠️ Partial     | Nav `live` + auth.store keys; **no** `NotificationsView.vue` and **no** live child route in `router/index.ts`                                                   |

Hexagonal shape on main: application port
`platformadmin.application.contracts.NotificationAdminQuery`, infrastructure adapter + thin
controller, domain eligibility in `shared/notifications`. Handler does not call R2DBC directly.

---

### Coherence (Design)

| Decision                                        | Followed?                                                   | Notes                                                                                                                |
|-------------------------------------------------|-------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------|
| Query port in application layer                 | ✅ Yes                                                      | Port in application; adapter in infrastructure                                                                       |
| Retry via existing NotificationService.notify() | ⚠️ Deviated                                                 | Spec/REQ-PN-003 won: domain event + `DispatchNotificationRetryConsumer` (safer than calling notify() in the handler) |
| Template whitelist PASSWORD_RECOVERY only       | ⚠️ Deviated                                                 | Spec technical notes won: exact `platform.password-recovery` + `platform.password-reset`                             |
| PayloadRedactor in platformadmin infrastructure | ⚠️ Deviated                                                 | Lives in `shared/notifications` domain (`redactPayload`) — reusable, still testable                                  |
| New row + idempotency key (no attempts table)   | ✅ Yes                                                      | Caller key or `retry-{id}-{uuid}`; sequential reuse → 409                                                            |
| `@RequiresPlatformPermission` annotation        | ⚠️ Deviated                                                 | Imperative `OperatorAccessResolver` / permission set check (spec allows equivalent)                                  |
| Retry HTTP 200                                  | ✅ vs platform-notifications; ⚠️ vs admin-authorization 202 | Implementation 200                                                                                                   |
| Frontend notifications route/view               | ⚠️ Deviated                                                 | Nav live without router component (falls through; planned catch-all does not include `live` entries)                 |

---

### TDD Compliance Audit

| Metric                               | Status                                                                                  |
|--------------------------------------|-----------------------------------------------------------------------------------------|
| RED→GREEN→REFACTOR evidence per task | ⚠️ Partial — `apply-progress.md` records tests alongside code, not explicit RED watches |
| Tests committed before or with code  | ⚠️ Cannot verify on `main` (squash merge of #1119)                                      |
| RED phase (failing test) verified    | ⚠️ No                                                                                   |
| Strict TDD module                    | ⚠️ Not loaded — `strict-tdd-verify.md` missing from skill directory                     |

Not treated as CRITICAL: squash merge erases per-task commit order; apply-progress does not show
implementation-before-tests.

---

### Issues Found

**CRITICAL** (must fix before archive):

None for backend query/retry/audit/authorization contracts that this verify covers.

**WARNING** (should fix; do not block this verify if documented):

1. **No NotificationsView / no live router record** — `nav-registry` marks `notifications` `live`
   with `platform.notifications.read`, but `router/index.ts` only auto-routes `plannedNavEntries()`.
   Clicking Notifications has no `NotificationsView.vue` and is not in the planned catch-all.
   Frontend mirror for retry-button enablement is absent.
2. **OpenSpec tracking lag** — `state.yaml` was still `tasks` / `next apply` after merge; `tasks.md`
   still has 4.1–6.2 unchecked.
3. **BDD cleanup omits `notifications`** — `BddDatabaseSupport` deletes `notification_events` but
   not `notifications`. Cross-feature residue forced empty-query BDD to use an unmatched recipient
   filter.
4. **Concurrent same idempotency key** — sequential reuse returns 409
   (`NotificationRetryConflictException`). Concurrent insert can still hit UNIQUE and surface as raw
   500 (lookup-then-insert race).
5. **Spec actor/status conflicts** — platform-notifications uses AUDITOR as lacking read;
   admin-authorization grants AUDITOR read. OPERATOR retry 202 vs 200. Invitation denial copy not
   the spec sentence.
6. **Redaction keeps keys** — values `[REDACTED]`; spec scenario “does NOT contain token field”.
7. **BDD “expired notification”** — fixture is an invitation template; 400 is not-retryable, while
   the Then step is named “not found” and asserts 400 (handler maps true not-found to 404).
8. **Zero-comment policy** — KDoc on `NotificationRetryEligibility`, `Notification.canRetry`, and
   `DispatchNotificationRetryConsumer` on merged main.

**SUGGESTION**:

- Add `DELETE FROM notifications` to BDD cleanup.
- Map unique-violation on idempotency_key to 409.
- Promote a real notifications route only together with a view, or keep nav `planned` until then.
- Align delta specs (AUDITOR/VIEWER, 200 vs 202, strip vs redact) before archive.

---

### Known gaps (orchestrator-supplied, confirmed)

| # | Gap                                                 | Confirmed                                                                                         |
|---|-----------------------------------------------------|---------------------------------------------------------------------------------------------------|
| 1 | No `NotificationsView.vue`; nav live; no real route | Yes — `apps/web/admin/src/views/` has no Notifications view; router children omit `notifications` |
| 2 | `state.yaml` still tasks/next apply                 | Yes at verify start; this report updates it                                                       |
| 3 | `notifications` table not in BDD cleanup            | Yes — `BddDatabaseSupport` reset SQL                                                              |
| 4 | Concurrent same idempotency key → raw 500           | Yes — pre-save lookup only; UNIQUE is backstop without ProblemDetail mapping                      |

---

### Verdict Table

| Finding                                                      | Judge A | Judge B | Severity | Status                     |
|--------------------------------------------------------------|---------|---------|----------|----------------------------|
| Backend GET/POST + eligibility + audit + permissions on main | ✅      | ✅      | —        | Confirmed implemented      |
| Local focused tests 66/66 + admin 105/105                    | ✅      | ✅      | —        | Confirmed this session     |
| PR #1119 CI Gate SUCCESS                                     | ✅      | ✅      | —        | Remote evidence            |
| Missing NotificationsView / live nav without route           | ✅      | ✅      | WARNING  | Confirmed                  |
| tasks.md/state.yaml tracking lag                             | ✅      | ✅      | WARNING  | Confirmed                  |
| BDD cleanup omits notifications                              | ✅      | ✅      | WARNING  | Confirmed                  |
| Concurrent idempotency 500                                   | ✅      | ✅      | WARNING  | Confirmed (sequential 409) |
| Spec 202 vs impl 200; AUDITOR read contradiction             | ✅      | ✅      | WARNING  | Confirmed                  |
| TDD RED phase on squash merge                                | ✅      | ❌      | WARNING  | Suspect / unverifiable     |

---

### Verdict

**PASS WITH WARNINGS**

Backend admin query, exact-match retry eligibility, event-driven re-dispatch, redaction,
permissions, and `NOTIFICATION_RETRIED` audit match the delta specs with passing local
unit/WebTestClient evidence and green PR #1119 CI. Remaining gaps are tracking, BDD isolation,
concurrent idempotency mapping, spec copy/status drift, and the live nav without a notifications
view — none of which fail the implemented API contract.

Hand-off: `sdd-qa` owns acceptance scenarios and `qa-report.md`. Do not archive yet. Do not close
GitHub #670 from this phase.
