# Acceptance QA Report: private-beta-launch-readiness

## Overview

The private-beta implementation remains blocked for deployed, operator, provider, and user-acceptance evidence. PR #883 contains the current activation fixes and was merged on 2026-08-28, while the latest verified production deployment is SMP v0.4.7, whose release tag predates that merge and does not contain the fixes.

## Changes

### Current follow-up audit — 2026-08-28

The private-beta activation implementation adds the missing invite-only registration handoff needed for a fresh invitee when public registration is disabled. The frontend now sends the backend's `token` contract for invitation acceptance, public capabilities expose `invitationAcceptanceEnabled`, and registration atomically validates and consumes an invitation while preserving its workspace membership. The marketing configuration now uses the Astro-consumed `WAITLIST_ENABLED` and `WAITLIST_API_BASE` variables. The implementation was committed and published as PR #883, merged on 2026-08-28. The review follow-up is committed and published separately as PR #887 and remains open.

Local evidence for this follow-up is complete: 60 focused Vitest tests, Vue type-check, changed-file Biome checks, targeted SMP Gradle tests, Detekt, Spring context, two PostgreSQL/Testcontainers registration cases, the full 8-scenario mocked Chromium invitee suite including fresh registration and the first schedule-now post, the 6-scenario mocked Chromium waitlist suite, and a successful Astro production build containing the active waitlist form. Temporary validation symlinks were removed and `git diff --check` passes.

This does not change the acceptance verdict. Production has SMP v0.4.7 deployed, but that release predates the PR #883 fixes; PR #883 is merged and PR #887 contains only the review follow-up tests and QA documentation. Live waitlist submission/email, deployed invitee activation, operator evidence, provider delivery, and deployed post-accept scheduling/publishing remain outstanding.

## Usage

### Identity

- **Change:** `private-beta-launch-readiness`
- **Unit:** `apply-unit-2-publishing-controls` — DALLAY-555/557
- **Mode:** OpenSpec
- **QA phase:** `qa` — Phase 2 acceptance gate
- **Date:** 2026-08-28
- **Execution:** `fallback` — no `sdd-quality-runner`/FSM was available; direct local commands and the authorized read-only production inspection are not deterministic runner evidence.
- **Historical read-only observation time:** `2026-08-23T10:27:18Z` UTC
- **Release/deployment evidence snapshot:** `2026-08-28` UTC; deployment PR #8 merged at `2026-08-28T09:29:28Z` UTC and PR #883 merged at `2026-08-28T11:08:48Z` UTC.
- **Release state:** The Phase 2 implementation and the Phase 3 invitee frontend journey are committed and published as PR #883, merged on 2026-08-28; the review follow-up is committed and published as PR #887 and remains open. The latest verified production deployment is SMP v0.4.7, which predates PR #883.
- **Mutation boundary:** No deploy, restart, environment edit, migration, provider call, publish, or data mutation was performed during this QA follow-up; commits and pushes were limited to the implementation PR #883 and review follow-up PR #887.

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
| Deployed backend release | The latest verified deployment evidence is the merged deployment PR #8, which updated production to `ghcr.io/dallay/profiletailors-smp:v0.4.7` on 2026-08-28. | The deployed release identity is known, but its tag predates the PR #883 merge and therefore does not establish acceptance of the current activation fixes. |
| Fenix image labels | No new read-only Fenix image-label inspection was performed for the v0.4.7 rollout. | The deployment PR identifies the intended release, but no fresh runtime digest-to-commit inspection is being treated as acceptance evidence. |
| Local release tag | `smp@v0.4.7` resolves to commit `6ae4da5de1f51ad4b86168ec0a5f10a95f07377a`, dated `2026-08-28 10:09:13 +0200`, subject `chore(main): release smp 0.4.7 (#881)`. | The tagged release is an ancestor of the PR #883 merge and predates the current activation fixes. |
| Registry SLSA provenance | No fresh deployed OCI-index provenance inspection was performed for v0.4.7 in this follow-up. | Release and deployment identity are recorded separately from deployed behavioral acceptance. |
| Backend readiness | Readiness endpoint returned `UP`. | Health only; readiness does not prove invitation, publishing, operator, or provider behavior. |
| Worker configuration | Observed `SMP_PUBLISHING_WORKER_ENABLED=true`. | Configuration observation only; safe-off/re-enable and persisted-job behavior were not exercised. |

### Scenario Matrix

The results below are acceptance results. The Fenix observations are cited as environment evidence only. Local passing tests are not converted into deployed product acceptance, and no static or readiness observation is reported as `PASS`.

| ID | Capability | Acceptance scenario | Result | Evidence or reason |
|---|---|---|---|---|
| P2-QA-01 | Managed operations | Operator sets worker safe-off, redeploys, and due persisted jobs cause no new provider delivery while remaining recoverable. | **BLOCKED** | Fenix is production; no explicit change window or mutation permission was supplied. Read-only inventory observed `SMP_PUBLISHING_WORKER_ENABLED=true`, but safe-off was not performed. The v0.4.7 tag contains the stale-job implementation, but no production lifecycle acceptance was performed. |
| P2-QA-02 | Managed operations | Operator re-enables the worker and observes polling resume, stale claims release before claiming, and recoverable jobs remain unmarked as published. | **BLOCKED** | Requires a production restart, live logs/data, and an explicitly approved release containing this change. None was authorized; no deployment was performed. Readiness `UP` does not establish the lifecycle transition. |
| P2-QA-03 | API/data | Authorized operator lists a stale claim and sees publication ID, workspace ID, age, attempt, and `RELEASE_AND_RETRY`. | **BLOCKED** | No successful product API request was observed; localhost `8080` refused the connection. The v0.4.7 release tag contains `ListStaleJobsQuery`, but the deployed endpoint contents and live operator authorization were not acceptance-tested. |
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
| P3-QA-07 | Post-accept product behavior | After acceptance, the invitee can schedule and publish according to the private-beta capability gates. | **PARTIAL — LOCAL FRONTEND ACCEPTANCE** | Chromium scenario 3.1c now carries a fresh invitee through registration into the accepted workspace and creates a schedule-now post against scheduler mocks. It does not establish deployed backend behavior, provider delivery, or a real publish result. |

## Troubleshooting

### Verdict

**BLOCKED — local frontend acceptance evidence improved, but the overall acceptance gate remains blocked.**

The invitee frontend scenarios 3.1–3.6 and the fresh-registration first-post scenario 3.1c now have focused local Chromium evidence, in addition to the 8/8 invitation view unit tests and Biome checks. This is a meaningful improvement to the implementation handoff, but it is not managed-beta, deployed-backend, provider-side, operator, or end-user acceptance evidence. Archive remains prohibited until the change is exercised against an approved matching managed-beta release and the remaining operational/provider/post-accept scenarios are completed or explicitly waived by the product owner.

#### Blocking conditions

- No approved managed-beta environment matching the current implementation was supplied.
- Fenix is production, not a disposable acceptance environment, and no approved production change window or mutation permission was supplied.
- The deployed `v0.4.7` image predates the PR #883 private-beta activation implementation and does not include its frontend changes.
- No provider-side test account, credential, or request/outcome evidence was supplied.
- No operator walkthrough, rollback exercise, backup/restore drill, or user acceptance evidence was supplied.
- Deployed post-accept scheduling and provider publishing behavior remains untested; local schedule-now UI coverage uses mocks.

#### Required next evidence

1. Build and deploy an approved release containing the current backend and frontend changes to a managed beta environment.
2. Re-run P2-QA-01 through P2-QA-08 in that environment with disposable data, provider test credentials, correlation IDs, and rollback coverage.
3. Execute the invitee journey 3.1–3.6 against the matching deployed environment without replacing backend/auth behavior with test mocks.
4. Run post-accept scheduling/publishing acceptance against the matching deployed backend and provider boundary for P3-QA-07.
5. Obtain product-owner/operator sign-off or an explicit written waiver for any acceptance scenario intentionally deferred.

### Risks

- Treating local mocked auth acceptance as deployed end-to-end acceptance would overstate launch readiness.
- Exercising worker or provider behavior on Fenix without an approved change window could affect production publications.
- Archiving now would hide unresolved operational, provider, and post-accept evidence gaps.

## References

- `openspec/changes/private-beta-launch-readiness/proposal.md`
- `openspec/changes/private-beta-launch-readiness/design.md`
- `openspec/changes/private-beta-launch-readiness/tasks.md`
- `openspec/changes/private-beta-launch-readiness/verify-report.md`
- `docs/infrastructure/private-beta-launch-readiness-runbook.md`
