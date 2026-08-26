# Acceptance QA Report: private-beta-launch-readiness

## Overview

### Identity

- **Change:** `private-beta-launch-readiness`
- **Unit:** `apply-unit-2-publishing-controls` — DALLAY-555/557
- **Mode:** OpenSpec
- **QA phase:** `qa` — Phase 2 acceptance gate
- **Date:** 2026-08-25
- **Execution:** `fallback` — no `sdd-quality-runner`/FSM was available; direct local commands and the authorized read-only production inspection are not deterministic runner evidence.
- **Read-only observation time:** `2026-08-23T10:27:18Z` UTC
- **Release state:** The current Phase 2 implementation and the Phase 3 invitee frontend journey are uncommitted in the worktree; no deployment was performed.
- **Mutation boundary:** No deploy, restart, environment edit, migration, provider call, publish, data mutation, commit, or push was performed.

### Sources of Truth and Technical Verification Handoff

### Sources

- Proposal: `openspec/changes/private-beta-launch-readiness/proposal.md`
- Delta specifications: `openspec/changes/private-beta-launch-readiness/specs/publishing/spec.md`, `specs/iam/spec.md`, `specs/e2e/spec.md`, `specs/lead-capture-waitlist/spec.md`, and `specs/cloudflare-tunnel-ingress/spec.md`
- Main capability specification: `openspec/specs/private-beta-launch-readiness/spec.md`
- Design: `openspec/changes/private-beta-launch-readiness/design.md`
- Tasks: `openspec/changes/private-beta-launch-readiness/tasks.md`
- Technical verification: `openspec/changes/private-beta-launch-readiness/verify-report.md`
- Phase state: `openspec/changes/private-beta-launch-readiness/state.yaml` (read during QA; acceptance remains active while the verdict is `BLOCKED`)
- Configuration: `openspec/config.yaml`
- Product truth: `apps/web/PRODUCT.md`, `apps/web/app/PRODUCT.md`, and `apps/web/admin/PRODUCT.md`
- Operational contract: `docs/infrastructure/private-beta-launch-readiness-runbook.md`
- Production inspection evidence:
  - `openspec/changes/private-beta-launch-readiness/evidence/fenix-read-only-inspection.md`

### Technical evidence handed off by `sdd-verify`

The following is technical/test evidence, not product acceptance evidence:

| Evidence | Result | Boundary |
|---|---|---|
| `node scripts/with-db-password-gradle.mjs :server:smp:test --tests 'com.profiletailors.smp.publishing.*' --tests 'com.profiletailors.smp.platformadmin.infrastructure.http.PublishingStaleJobsControllerTest' --rerun-tasks --no-build-cache --no-daemon` | **PASS**, exit 0, `BUILD SUCCESSFUL` on the completed rerun | Local focused unit, persistence, worker, handler, and controller behavior only. The first 120-second attempt timed out before a final status; it was rerun with a longer timeout and completed successfully. `verify-report.md` records 140 selected tests with no skips/failures/errors. |
| `just backend-bdd-fast` | **PASS**, exit 0, `BUILD SUCCESSFUL` | Local BDD contract evidence. `verify-report.md` records 203/203 scenarios and 8 stale-job scenarios in this lane. |
| `just backend-bdd-postgres` | **PASS**, exit 0, `BUILD SUCCESSFUL` | Local PostgreSQL-backed BDD evidence. `verify-report.md` records 203/203 scenarios and 8 stale-job scenarios in this lane. |
| Existing `verify-report.md` evidence: `just backend-test-fast`, `just backend-check`, `just backend-build`, coverage, Modulith, Spotless, and `git diff --check` | **PASS** as recorded by verification | Technical conformance only; not managed-VPS, provider, operator, or user acceptance. |
| `just swarm-config` | **UNAVAILABLE**, exit 1 | Local render could not run because `DASHBOARD_IMAGE` is missing from `swarm/.env`; no local rendered-stack evidence exists. The remote deployed service inventory below was observed separately and does not make the local renderer pass. |
| `pnpm exec vitest run src/modules/invitation/presentation/AcceptInvitationView.spec.ts` from `apps/web/app` | **PASS**, 8/8 tests | Local unit evidence for the invitee acceptance view. It does not establish deployed user acceptance. |
| `pnpm exec biome check e2e/specs/invitee-private-beta.spec.ts src/modules/invitation/presentation/AcceptInvitationView.vue src/modules/invitation/presentation/AcceptInvitationView.spec.ts src/shared/i18n/locales/en/invitation.ts src/shared/i18n/locales/es/invitation.ts` from `apps/web/app` | **PASS** | Local formatting and lint evidence for the five changed frontend files. |
| `PLAYWRIGHT_PORT=5211 PLAYWRIGHT_REUSE_EXISTING_SERVER=true pnpm exec playwright test e2e/specs/invitee-private-beta.spec.ts -c e2e/playwright.config.ts --grep "3.1|3.2|3.3|3.4|3.5|3.6" --project chromium --workers=1 --reporter=line --timeout=15000` from `apps/web/app`, against an isolated Vite server | **PASS**, 6/6 scenarios in 8.4s | Local Chromium acceptance evidence for invitee scenarios 3.1–3.6. The test mocks auth refresh/profile responses and does not establish acceptance against the deployed backend or managed beta environment. |

The local test lanes support the implementation handoff and preserve the reported `TEST_VERIFIED` classification. They do not establish that a deployed operator or user observed the behavior.

### Authorized read-only production inspection (environment evidence only)

The inspection produced `VPS_OBSERVED` environment evidence. It is not an acceptance result and no row below is a scenario `PASS`:

| Check | Observation | Boundary |
|---|---|---|
| SSH target | `ssh -o BatchMode=yes -o ConnectTimeout=10 fenix` succeeded through Tailscale; the host reported `fenix-icloud`. | Establishes read-only target reachability only. |
| Docker stack and service convergence | Stack `profiletailors-smp-dz2yer` was present with `backend 1/1`, `cloudflared 1/1`, and `postgresql 1/1`; the backend task was `Running 8 days ago`. | Inventory/readiness evidence only; no rollout, restart, or behavior transition was exercised. |
| Deployed backend release | Service image was `ghcr.io/dallay/profiletailors-smp:v0.4.1@sha256:990c7341441c7362b4a29b2d933b8438b4b1dc137c603f99bc125fbcacaba165`; service updated `2026-08-14`; local image created `2026-08-08T21:52:44.851150213Z`. | Release identity was observed, but the deployed artifact has not been proven to contain the current uncommitted Phase 2 code. |
| Fenix image labels | Read-only image metadata reported `org.opencontainers.image.version=v0.4.1` and `org.opencontainers.image.source=https://github.com/dallay/profiletailors.com`. | The labels identify the deployed release and source repository, but do not by themselves prove the image digest-to-commit mapping. |
| Local release tag | `smp@v0.4.1` resolves to commit `50429460991d81205dbafbf6f664b945fd89ace5`, dated `2026-08-08 23:48:43 +0200`, subject `chore: release main (#545)`. | The tagged release predates the current Phase 2 stale-jobs endpoint and permission changes. |
| Registry SLSA provenance | Read-only GHCR inspection of the deployed OCI index confirmed provenance metadata for the observed image. | Provenance for the old image does not establish current-change acceptance. |
| Backend readiness | Readiness endpoint returned `UP`. | Health only; readiness does not prove invitation, publishing, operator, or provider behavior. |
| Worker configuration | Observed `SMP_PUBLISHING_WORKER_ENABLED=true`. | Configuration observation only; safe-off/re-enable and persisted-job behavior were not exercised. |

### Scenario Matrix

The results below are acceptance results. The Fenix observations are cited as environment evidence only. Local passing tests are not converted into deployed product acceptance, and no static or readiness observation is reported as `PASS`.

| ID | Capability | Acceptance scenario | Result | Evidence or reason |
|---|---|---|---|---|
| P2-QA-01 | Managed operations | Operator sets worker safe-off, redeploys, and due persisted jobs cause no new provider delivery while remaining recoverable. | **BLOCKED** | Fenix is production; no explicit change window or mutation permission was supplied. Read-only inventory observed `SMP_PUBLISHING_WORKER_ENABLED=true`, but safe-off was not performed. The observed v0.4.1 release is confirmed as a Phase 2 mismatch/pre-change deployment from the tag evidence. |
| P2-QA-02 | Managed operations | Operator re-enables the worker and observes polling resume, stale claims release before claiming, and recoverable jobs remain unmarked as published. | **BLOCKED** | Requires a production restart, live logs/data, and an explicitly approved release containing this change. None was authorized; no deployment was performed. Readiness `UP` does not establish the lifecycle transition. |
| P2-QA-03 | API/data | Authorized operator lists a stale claim and sees publication ID, workspace ID, age, attempt, and `RELEASE_AND_RETRY`. | **BLOCKED** | No successful product API request was observed; localhost `8080` refused the connection. The image label identifies v0.4.1, while the corresponding local tag lacks `ListStaleJobsQuery`; the deployed endpoint contents and live operator authorization were not acceptance-tested. |
| P2-QA-04 | Persistence/state transition | A claim older than the stale threshold is automatically reset to `PENDING` before the next claim and remains retryable. | **BLOCKED** | Requires live queue state and a controlled worker lifecycle transition. No restart, queue mutation, or provider call was authorized. |
| P2-QA-05 | Security/redaction | Operator diagnostics expose operationally useful metadata without raw access tokens or provider secrets. | **BLOCKED** | Local tests verify the redaction contract, but no deployed endpoint response was obtained from a matching release. |
| P2-QA-06 | Authorization | A permitted operator can use the stale-jobs endpoint while an unauthorized principal is denied. | **BLOCKED** | Local tests verify authorization behavior, but no matching deployed API and live principals were acceptance-tested. |
| P2-QA-07 | Provider behavior | A safe-off/re-enable cycle is observed through provider request outcomes and correlation IDs. | **BLOCKED** | No provider-side call, credential, test account, or authorized change window was supplied. |
| P2-QA-08 | Operator readiness | The runbook, dashboards, alerts, rollback, and backup/restore procedure are exercised by an operator. | **BLOCKED** | Documentation exists, but no operator walkthrough or recovery drill was supplied. |
| P3-QA-01 | Invitee journey | An invitee opens a valid token, accepts the invitation, hydrates the session, and reaches the accepted workspace dashboard. | **PASS — LOCAL FRONTEND ACCEPTANCE** | Chromium Playwright scenario 3.1 passed against an isolated local Vite server. The test mocks the post-accept auth refresh/profile responses; deployed backend and managed-beta acceptance remain untested. |
| P3-QA-02 | Invitee validation | An empty invitation token shows the missing-token error without calling the API. | **PASS — LOCAL FRONTEND ACCEPTANCE** | Chromium Playwright scenario 3.2 passed against an isolated local Vite server. |
| P3-QA-03 | Capability gate | Acceptance is blocked when `invitationAcceptanceEnabled` is false. | **PASS — LOCAL FRONTEND ACCEPTANCE** | Chromium Playwright scenario 3.3 passed against an isolated local Vite server. |
| P3-QA-04 | Error handling | A backend invitation acceptance error surfaces the canonical not-acceptable copy. | **PASS — LOCAL FRONTEND ACCEPTANCE** | Chromium Playwright scenario 3.4 passed against an isolated local Vite server. |
| P3-QA-05 | Token safety | Raw invitation tokens do not appear in the rendered DOM. | **PASS — LOCAL FRONTEND ACCEPTANCE** | Chromium Playwright scenario 3.5 passed against an isolated local Vite server. |
| P3-QA-06 | Safe failure | A 5xx failure surfaces generic copy without exposing raw tokens. | **PASS — LOCAL FRONTEND ACCEPTANCE** | Chromium Playwright scenario 3.6 passed against an isolated local Vite server. |
| P3-QA-07 | Post-accept product behavior | After acceptance, the invitee can schedule and publish according to the private-beta capability gates. | **NOT TESTED** | The focused journey covers acceptance and auth hydration only. Scheduling and publishing after acceptance require additional application/provider acceptance coverage. |

## Verdict

**BLOCKED — local frontend acceptance evidence improved, but the overall acceptance gate remains blocked.**

The invitee frontend scenarios 3.1–3.6 now have focused local Chromium evidence, in addition to the 8/8 invitation view unit tests and Biome checks. This is a meaningful improvement to the implementation handoff, but it is not managed-beta, deployed-backend, provider-side, operator, or end-user acceptance evidence. Archive remains prohibited until the change is exercised against an approved matching managed-beta release and the remaining operational/provider/post-accept scenarios are completed or explicitly waived by the product owner.

### Blocking conditions

- No approved managed-beta environment matching the current implementation was supplied.
- Fenix is production, not a disposable acceptance environment, and no approved production change window or mutation permission was supplied.
- The deployed `v0.4.1` image predates the current Phase 2 implementation and does not include the current uncommitted Phase 3 frontend changes.
- No provider-side test account, credential, or request/outcome evidence was supplied.
- No operator walkthrough, rollback exercise, backup/restore drill, or user acceptance evidence was supplied.
- Post-accept scheduling and publishing behavior remains untested.

### Required next evidence

1. Build and deploy an approved release containing the current backend and frontend changes to a managed beta environment.
2. Re-run P2-QA-01 through P2-QA-08 in that environment with disposable data, provider test credentials, correlation IDs, and rollback coverage.
3. Execute the invitee journey 3.1–3.6 against the matching deployed environment without replacing backend/auth behavior with test mocks.
4. Add and run post-accept scheduling/publishing acceptance coverage for P3-QA-07.
5. Obtain product-owner/operator sign-off or an explicit written waiver for any acceptance scenario intentionally deferred.

## Risks

- Treating local mocked auth acceptance as deployed end-to-end acceptance would overstate launch readiness.
- Exercising worker or provider behavior on Fenix without an approved change window could affect production publications.
- Archiving now would hide unresolved operational, provider, and post-accept evidence gaps.
