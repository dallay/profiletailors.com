# Acceptance QA Report: 670-notification-admin-operations

## Identity

- Change: `670-notification-admin-operations` (GitHub issue dallay/profiletailors.com#670, epic #656)
- Mode: openspec
- QA phase: acceptance gate after `sdd-verify`, before `sdd-archive`
- Date: 2026-09-21
- HEAD under test: `main` @ `0e9107f53c835ab7ba350d6ad466176d89249d78` (squash-merge of PR #1119)
- GitHub issue #670: still OPEN (not closed by this QA)
- Verdict: **PASS WITH WARNINGS**
- Runner: **fallback** (`openspec/quality-runner.json` absent; no deterministic QA FSM envelope)

This report is the audit record of observable operator/API acceptance. It does not claim product acceptance of a live Back Office, and it does not claim that the harness itself has product acceptance.

## Sources of Truth

| Artifact | Status |
|---|---|
| `proposal.md` | Read. Success criteria + capabilities used as acceptance baseline |
| `specs/platform-notifications/spec.md` | Read. REQ-PN-001..007 and 7 scenarios traced |
| `specs/admin-authorization/spec.md` | Read. read/manage permissions, role mapping, 6 scenarios traced |
| `specs/platform-admin-audit/spec.md` | Read. `NOTIFICATION_RETRIED` SUCCESS/REJECTED/DISPATCH_FAILED + registry |
| `design.md` | Read. HTTP contract, eligibility whitelist, redaction, audit wiring |
| `tasks.md` | Read. 17/23 checkboxes `[x]`; 4.1–6.2 still `[ ]` though implemented on main |
| `apply-progress.md` | Read. Phases 1–9 claimed complete; remaining-work section stale |
| `verify-report.md` | Read. **PASS WITH WARNINGS** on identical commit `0e9107f5` |
| `state.yaml` | Read. `current_phase: verify`, `next: qa` at session start |
| `openspec/config.yaml` | Read. Capability catalog, `evidence_policy`, `archive_blockers` applied |
| GitHub #670 body | Read. Five ACs + two Gherkin scenarios used as issue contract |

Technical verification handoff (verify, same HEAD, no production-code drift — `git status` shows only `state.yaml` modified plus untracked `verify-report.md`):

- Local focused backend: **66 passed / 0 failed / 0 skipped** (`NotificationRetryEligibilityTest`, `PayloadRedactorTest`, `NotificationCanRetryTest`, `RetryNotificationHandlerTest`, `DispatchNotificationRetryConsumerTest`, `AdminNotificationControllerWebTestClientTest`, `R2dbcNotificationAdminQueryAdapterTest`, `OperatorAccessResolverTest`)
- Local `just admin-check` (vue-tsc): Passed
- Local `just admin-test`: Passed — 12 files, **105 tests**
- Remote PR #1119 merge commit `0e9107f5`: Lint, Backend Unit Tests, Backend BDD, Backend Postgres, Frontend Unit Tests, Production Builds, **CI Gate SUCCESS**, Quality Gate SUCCESS, Security PR SUCCESS, SonarCloud / codecov/patch SUCCESS

This QA session did **not** re-run the 66 backend tests (identical HEAD, zero production drift, cost/benefit negative). It **did** re-run the admin nav/auth subset (QA-EXEC-01).

## Target and Environment

- Target: **none supplied**. No deployed Back Office URL, no staging/prod credentials, no operator session. None invented.
- Environment: repository worktree on `main` @ `0e9107f5` (Linux). No admin/marketing/dev servers started. No `infra-up`. No live HTTP against a running SMP.
- Credentials/permissions: no live operator roles. Authorization exercised through executable suites (BDD role tokens, WebTestClient, `OperatorAccessResolverTest`, admin `auth.store` matrix).
- Limitations:
  - No live target ⇒ browser, a11y, responsive, locale-visual, real-redeploy, and exploratory-on-deploy scenarios are `NOT TESTED`.
  - BDD lane tagged `@postgres` as well as `@fast`; not re-executed locally. Remote `🔨 Backend BDD` SUCCESS on the exact squash is the runtime envelope.
  - `openspec/quality-runner.json` absent → **fallback**. No runner `FAIL`/`UNAVAILABLE`/`BLOCKED` envelope to map. Prose cannot invent a runner pass.
  - Static inspection is cited only as traceability, never as PASS evidence.

## Capability Inventory

Source: `openspec/config.yaml` `testing.capabilities` plus browser/API/data/a11y/responsive/locale/persistence/exploratory dimensions.

| Capability | Availability | Selected? | Rationale / rejection reason |
|---|---|---|---|
| `backend_bdd_fast` (Cucumber/WebTestClient/Testcontainers) | available (remote) | **selected (inherited evidence)** | 10 `notifications-admin.feature` scenarios are the operator HTTP contract. Remote `🔨 Backend BDD` SUCCESS on exact squash. Not re-run locally (tagged `@postgres`, no drift) |
| `backend_unit` (handler/adapter/consumer/redactor/permissions) | available | **selected (inherited evidence)** | VERIFY-HO 66/66 PASS on identical HEAD; covers eligibility, redaction, retry outcomes, audit, 401/403/400/404/200/409 |
| `backend_postgres_integration` | available (remote) | **rejected (inherited only)** | Remote `🐘 Backend Postgres` SUCCESS. Local Testcontainers SQL (`R2dbcNotificationAdminQueryPostgresTest`) not re-run |
| `frontend_unit` (Vitest, admin) | available | **selected (executed now)** | QA-EXEC-01: `nav-registry.spec.ts` + `auth.store.test.ts` → **2 files, 29/29 PASS**, 2.58 s |
| `frontend_e2e` (Playwright) | unavailable | **unavailable** | No admin Playwright spec for notifications; no target URL. ⇒ `NOT TESTED` |
| Browser / Chrome DevTools walkthrough | unavailable | **unavailable** | No target. Also no `NotificationsView.vue` and no live child route. ⇒ `NOT TESTED` |
| Accessibility | unavailable | **unavailable** | Requires live UI + AT. No admin a11y E2E. ⇒ `NOT TESTED` |
| Responsive | n/a | **rejected** | No layout/breakpoint AC in specs. Non-applicable |
| Locale / i18n visual | unavailable | **unavailable** | No notifications view/copy surface to render. ⇒ `NOT TESTED` |
| Persistence / real restart | unavailable | **unavailable** | Requires deployed backend. BDD/handler cover append-only new-row retry. Real redeploy ⇒ `NOT TESTED` |
| Exploratory / concurrent race beyond specs | available as known gap | **rejected as executable** | Concurrent same-key 500 is documented (F-04); open-ended probing needs live target |
| `backend_coverage` (Kover) | available (remote) | **rejected** | Not an AC of #670; remote Quality Gate + codecov/patch SUCCESS |
| `full_ci` | available (remote) | **rejected as local re-run** | Exact-squash remote CI already green |

Runner-envelope note: no automated QA-runner envelope exists. **fallback** limitation: this report maps remote CI conclusions and in-session Vitest output; it cannot claim deterministic FSM enforcement.

## Scenario Matrix

Allowed results: `PASS`, `FAIL`, `BLOCKED`, `NOT TESTED`.

Evidence legend:

- **QA-EXEC-01** = this session: `pnpm --filter admin test:run src/router/nav-registry.spec.ts src/stores/auth.store.test.ts` → 2 files PASS, 29 tests PASS, 2.58 s
- **VERIFY-HO** = verify handoff runtime on identical commit `0e9107f5` (66 backend + 105 admin)
- **REMOTE-CI** = GitHub Actions SUCCESS on PR #1119 squash (`🔨 Backend BDD`, `🐘 Backend Postgres`, `🧪 Backend Unit Tests`, `✅ CI Gate`)

Static inspection is traceability only.

### Issue #670 acceptance criteria

| ID | Capability | Acceptance scenario | Result | Evidence or reason |
|---|---|---|---|---|
| AC-01 | backend_bdd_fast | Administrators can list and filter notifications by operationally relevant fields | PASS | REMOTE-CI BDD: Query unmatched empty 200; query returns rows; filter by status FAILED; filter by channel EMAIL. VERIFY-HO WebTestClient filter/pagination/max page size |
| AC-02 | backend_bdd_fast + backend_unit | Sensitive payload values are redacted | PASS | REMOTE-CI BDD “Sensitive fields are redacted” (no payload contains token/password/acceptUrl). VERIFY-HO `PayloadRedactorTest` 15 cases. Keys retained as `[REDACTED]` — see F-05 |
| AC-03 | backend_bdd_fast + backend_unit | Eligible failed notifications can be retried safely (password-recovery / password-reset only) | PASS | REMOTE-CI BDD retry eligible → 200 and new notification. VERIFY-HO handler creates PENDING + `NotificationRetryRequested`; consumer marks SENT/FAILED; original row unchanged |
| AC-04 | backend_bdd_fast + backend_unit | Unsafe retries (invitation / token-bound) are rejected | PASS | REMOTE-CI BDD invitation template → 400 not-retryable. VERIFY-HO handler `NotificationNotRetryableException` + REJECTED audit, no save |
| AC-05 | backend_unit | Retry operations are audited (`NOTIFICATION_RETRIED`) | PASS | VERIFY-HO `RetryNotificationHandlerTest`: SUCCESS / REJECTED / DISPATCH_FAILED metadata. REMOTE-CI unit lane SUCCESS. BDD does not assert audit rows |

### platform-notifications

| ID | Capability | Acceptance scenario | Result | Evidence or reason |
|---|---|---|---|---|
| PN-01 | backend_bdd_fast | Query failed notifications with filters (200, paginated EMAIL FAILED) | PASS | REMOTE-CI BDD filter-by-status + filter-by-channel; VERIFY-HO WebTestClient pagination metadata |
| PN-02 | backend_bdd_fast + backend_unit | Inspect redacted payload (secrets not exposed) | PASS | REMOTE-CI BDD redaction scenario; VERIFY-HO redactor. Spec wording “does NOT contain token field” vs keys kept as `[REDACTED]` — F-05 (warning, not FAIL: secret values not exposed; BDD green) |
| PN-03 | backend_bdd_fast + backend_unit | Retry eligible password-recovery: 200, new row, original unchanged, event + consumer | PASS | REMOTE-CI BDD 200 + new notification; VERIFY-HO handler + `DispatchNotificationRetryConsumerTest` |
| PN-04 | backend_bdd_fast + backend_unit | Retry invitation denied 400, no new row, REJECTED audit | PASS | REMOTE-CI BDD 400 not-retryable; VERIFY-HO handler REJECTED. Denial copy is `Notification {id} is not retryable: …` not the spec sentence — F-06 |
| PN-05 | backend_unit | Delivery state observability GET-by-id (status, error, channel, template, timestamps) | PASS | VERIFY-HO WebTestClient GET-by-id 200 with details. Dedicated PENDING/SMS fixture not in BDD — NT-05 |
| PN-06 | backend_unit | Query denied without `platform.notifications.read` → 403 | PASS | VERIFY-HO WebTestClient 403 without read. Spec actor “AUDITOR lacks read” contradicts admin-authorization (AUDITOR has read); implementation follows admin-authorization — F-06 |
| PN-07 | backend_unit | Retry denied without `platform.notifications.manage` → 403 | PASS | VERIFY-HO handler AUDITOR denied + WebTestClient 403 without manage |

### admin-authorization

| ID | Capability | Acceptance scenario | Result | Evidence or reason |
|---|---|---|---|---|
| AA-01 | backend_unit + frontend_unit | AUDITOR can query (has read) | PASS | VERIFY-HO `OperatorAccessResolverTest` AUDITOR has READ not MANAGE. HTTP 200-as-AUDITOR not in BDD; 403-without-read is proven. QA-EXEC-01 auth.store matrix includes AUDITOR read |
| AA-02 | backend_unit | VIEWER cannot query | PASS (equivalent) | No `VIEWER` role in `PlatformRole` (OWNER/OPERATOR/SUPPORT_AGENT/AUDITOR). 403 without permission proven. SUPPORT_AGENT has read (user/context + `PLATFORM_ROLE_PERMISSIONS`) |
| AA-03 | backend_bdd_fast | OPERATOR can retry eligible | PASS | REMOTE-CI BDD retry 200 as PLATFORM_OPERATOR. admin-authorization says **202**; platform-notifications + impl **200** — F-06 |
| AA-04 | backend_unit | AUDITOR cannot retry (403) | PASS | VERIFY-HO handler + WebTestClient 403 |
| AA-05 | backend_unit | Permission registry includes both keys | PASS | VERIFY-HO `PlatformPermission.NOTIFICATIONS_READ/MANAGE` + resolver tests |
| AA-06 | backend_unit | OWNER has both; AUDITOR read-only | PASS | VERIFY-HO `PLATFORM_OWNER → entries`; AUDITOR read-only test. No dedicated OWNER HTTP scenario |

### platform-admin-audit

| ID | Capability | Acceptance scenario | Result | Evidence or reason |
|---|---|---|---|---|
| AU-01 | backend_unit | Retry success audited `NOTIFICATION_RETRIED` SUCCESS with context | PASS | VERIFY-HO handler SUCCESS metadata (notificationId, channel, templateId, priorStatus, priorError) |
| AU-02 | backend_unit | Retry rejected eligibility audited REJECTED, no save | PASS | VERIFY-HO handler REJECTED audit, no save |
| AU-03 | backend_unit | `priorError` containing “token” redacted to `[REDACTED]` | PASS | VERIFY-HO handler `authentication token expired` → `[REDACTED]` |
| AU-04 | backend_unit | DISPATCH_FAILED audited when dispatch throws | PASS | VERIFY-HO handler DISPATCH_FAILED branch |
| AU-05 | backend_unit | Event type registry includes `NOTIFICATION_RETRIED` | PASS | VERIFY-HO `AdminAuditAction.NOTIFICATION_RETRIED` + handler assertions |

### Frontend / operator surface

| ID | Capability | Acceptance scenario | Result | Evidence or reason |
|---|---|---|---|---|
| FE-01 | frontend_unit | Nav item `notifications` live, gated on `platform.notifications.read` | PASS | QA-EXEC-01: `marks notifications as live with correct permission` green |
| FE-02 | frontend_unit | auth.store mirrors read/manage for OWNER/OPERATOR; AUDITOR + SUPPORT_AGENT read-only | PASS | QA-EXEC-01 auth.store tests green; ROLE_PERMISSIONS inspected as traceability only |
| FE-03 | frontend_e2e / browser | Operator can open a Notifications view, list/filter, retry with manage-gated button | NOT TESTED | No `NotificationsView.vue`; `router/index.ts` only auto-routes `plannedNavEntries()`, so live `notifications` has **no child route**. See F-01 / NT-02 |
| FE-04 | frontend_e2e | Keyboard / screen-reader / responsive / ES visual | NOT TESTED | No view and no target — NT-03 |

### Cross-cutting (happy / negative / boundary / repeated / unauthorized)

| ID | Capability | Acceptance scenario | Result | Evidence or reason |
|---|---|---|---|---|
| XT-01 | backend_bdd_fast | Happy path list + retry | PASS | REMOTE-CI BDD |
| XT-02 | backend_bdd_fast | Negative: invitation 400; unauthenticated 401; expired/ineligible 400 | PASS | REMOTE-CI BDD |
| XT-03 | backend_bdd_fast | Repeated same idempotency key → 409 (sequential) | PASS | REMOTE-CI BDD “Retry with same idempotency key returns 409” |
| XT-04 | backend_unit | Concurrent same key | NOT TESTED (known gap) | Sequential 409 proven; concurrent UNIQUE race can 500. No concurrent BDD. F-04 |
| XT-05 | backend_unit | Unauthorized 401/403 | PASS | REMOTE-CI unauthenticated 401; VERIFY-HO 403 missing read/manage |
| XT-06 | backend_bdd_fast | Boundary: unmatched filter empty 200 | PASS | REMOTE-CI BDD empty-result probe (workaround for cleanup omitting `notifications`) |

## Untested Scope

| ID | Scope | Reason | Re-run prerequisite |
|---|---|---|---|
| NT-01 | Live Back Office walkthrough of list/filter/retry/audit on a deployed env | No target, credentials, or permissions supplied; nothing invented | Staging/prod Back Office with OWNER/OPERATOR session + `platform.notifications.*` |
| NT-02 | Real notifications UI: route, `NotificationsView`, retry-button gating | Nav is live without a router record or view. Catch-all only maps `planned` entries | Implement view + live route (or revert nav to `planned`), then Playwright/admin E2E |
| NT-03 | Browser a11y, responsive, locale visual | No view and no target | Served admin build + AT/screenshots |
| NT-04 | Local `just backend-bdd-fast` / `backend-test-postgres` / `backend-check` re-run | Cost; covered by REMOTE-CI SUCCESS on exact squash + VERIFY-HO | `just backend-bdd-fast` (feature is also `@postgres`), `just infra-up` + postgres lanes if archive wants belt-and-braces |
| NT-05 | GET-by-id PENDING/SMS fixture exactly as spec | WebTestClient GET-by-id 200 proven; SMS/PENDING combo not in BDD | Add BDD or WebTestClient fixture for PENDING+SMS |
| NT-06 | HTTP 200 as AUDITOR (positive read) | Resolver grants read; HTTP 403-without-read proven; no AUDITOR bearer list scenario | Add BDD “AUDITOR lists notifications → 200” |
| NT-07 | Real restart/redeploy durability | Append-only new-row retry proven in-process; no deploy | Ops restart against persisted `notifications` rows |

## Findings

| ID | Severity | Scenario / location | Evidence | Status |
|---|---|---|---|---|
| F-01 | P2 | FE-03 / NT-02 — nav live, no `NotificationsView`, no live child route | `nav-registry.ts` `status: 'live'`; `router/index.ts` maps only `plannedNavEntries()`; `apps/web/admin/src/views/` has no Notifications view. QA-EXEC-01 proves the live flag, not a working page | OPEN (warning) |
| F-02 | P2 | NT-01 — no live operator target | Contract: no target ⇒ NOT TESTED. All executable API suites green | OPEN (warning) |
| F-03 | P3 | BDD cleanup omits `notifications` | `BddDatabaseSupport` deletes `notification_events` not `notifications`. Empty-query uses unmatched recipient filter | OPEN (warning) |
| F-04 | P2 | XT-04 — concurrent same idempotency key can 500 | Sequential reuse 409 (BDD). Pre-save lookup race vs UNIQUE has no ProblemDetail mapping | OPEN (warning) |
| F-05 | P3 | PN-02 — redaction keeps keys as `[REDACTED]` | Spec “does NOT contain token field”; impl retains key with `[REDACTED]` value. Secret values not leaked; BDD green | OPEN (warning) |
| F-06 | P3 | Spec drift: 200 vs 202; AUDITOR read contradiction; invitation denial copy; expired scenario Then named “not found” but asserts 400 | platform-notifications 200 vs admin-authorization 202; AUDITOR has read in admin-authorization; invitation 400 text is structured not-retryable; expired fixture is invitation template | OPEN (warning) |
| F-07 | P3 | `tasks.md` 4.1–6.2 still `[ ]`; apply-progress “remaining work” stale | Tracking only; implementation is on main | OPEN (warning) |
| — | CRITICAL / P0 / P1 | none | No FAIL scenario. No denied-but-undetected path. No secret leak in passing redaction BDD | NONE |

## Verdict

**PASS WITH WARNINGS**

### Rationale

Every GitHub #670 AC and every applicable spec scenario traces to executable runtime evidence on the shipped commit `0e9107f5`: remote BDD SUCCESS for the 10 `notifications-admin.feature` operator HTTP scenarios (list, filter, redaction, eligible retry 200, invitation 400, unauthenticated 401, sequential idempotency 409); verify-session 66/66 unit/WebTestClient covering eligibility whitelist (`platform.password-recovery` / `platform.password-reset`), consumer SENT/FAILED, audit SUCCESS/REJECTED/DISPATCH_FAILED, and 401/403/400/404; this-session QA-EXEC-01 29/29 admin nav/auth tests. Remote CI Gate on PR #1119 is SUCCESS. No scenario FAILED. Nothing is BLOCKED.

WARNINGS are P2/P3 only: live nav without a notifications view (F-01), no deployed walkthrough (F-02), BDD cleanup isolation (F-03), concurrent idempotency 500 (F-04), redaction key retention and spec copy/status drift (F-05/F-06), stale task checkboxes (F-07). Per orchestrator: these known gaps are not P0.

`openspec/config.yaml` `archive_blockers` includes acceptance-relevant `NOT TESTED`. The explicit rationale to proceed is the same class as 672: (a) missing scope is live-target/browser observation plus a deferred UI view, which this environment cannot produce without inventing a target (forbidden) and which #670 ACs do not require as a shipped page; (b) every issue AC already has executable HTTP/unit evidence on the merged commit with zero production-code drift between verify and QA; (c) residual risk is operator-UI completeness and a concurrent-409 mapping, owned as follow-ups, not API-contract failure. No CRITICAL/P0/P1 findings.

## Limitations and Handoff

- QA does not fix code. No source, tests, or specs were modified.
- Product acceptance is not claimed without a live target. This report does not claim harness product acceptance.
- Follow-up for implementation (do not block archive of the API contract):
  1. Either ship `NotificationsView` + a live router record, or set nav `notifications` back to `planned`.
  2. `DELETE FROM notifications` in BDD cleanup.
  3. Map concurrent unique-violation on `idempotency_key` to 409.
  4. Align delta specs (AUDITOR/VIEWER vs SUPPORT_AGENT, 200 vs 202, strip vs redact) before or during archive merge.
- Next: `sdd-archive`. Archive must see this file alongside `verify-report.md`. Do not close GitHub #670 from this gate. Do not commit unless the orchestrator/user asks.
