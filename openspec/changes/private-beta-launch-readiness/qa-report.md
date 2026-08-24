# Acceptance QA Report: private-beta-launch-readiness

## Overview

### Identity

- **Change:** `private-beta-launch-readiness`
- **Unit:** `apply-unit-2-publishing-controls` — DALLAY-555/557
- **Mode:** OpenSpec
- **QA phase:** `qa` — Phase 2 acceptance gate
- **Date:** 2026-08-23
- **Execution:** `fallback` — no `sdd-quality-runner`/FSM was available; direct local commands and
  the authorized read-only production inspection are not deterministic runner evidence.
- **Read-only observation time:** `2026-08-23T10:27:18Z` UTC
- **Release state:** The current Phase 2 implementation is uncommitted in the worktree; no
  deployment was performed.
- **Mutation boundary:** No deploy, restart, environment edit, migration, provider call, publish,
  data mutation, commit, or push was performed.

### Sources of Truth and Technical Verification Handoff

### Sources

- Proposal: `openspec/changes/private-beta-launch-readiness/proposal.md`
- Product delta specifications:
  `openspec/changes/private-beta-launch-readiness/specs/publishing/spec.md`, `specs/iam/spec.md`,
  `specs/lead-capture-waitlist/spec.md`, and `docs/testing/e2e/invitee-private-beta.md`
- Main capability specification: `openspec/specs/private-beta-launch-readiness/spec.md`
- Design: `openspec/changes/private-beta-launch-readiness/design.md`
- Tasks: `openspec/changes/private-beta-launch-readiness/tasks.md`
- Technical verification: `openspec/changes/private-beta-launch-readiness/verify-report.md`
- Phase state: `openspec/changes/private-beta-launch-readiness/state.yaml` (read during QA;
  acceptance remains active while the verdict is `BLOCKED`)
- Configuration: `openspec/config.yaml`
- Product truth: `apps/web/PRODUCT.md`, `apps/web/app/PRODUCT.md`, and `apps/web/admin/PRODUCT.md`
- Operational contract: `docs/infrastructure/private-beta-launch-readiness-runbook.md`
- Production inspection evidence: authorized read-only
  `ssh -o BatchMode=yes -o ConnectTimeout=10 fenix` transcript, observed at `2026-08-23T10:27:18Z`
- Release-provenance evidence: read-only Fenix image-label observation plus local `smp@v0.4.1` tag,
  ancestry, and tagged-tree inspection recorded below

## Changes

### Technical evidence handed off by `sdd-verify`

The following is technical/test evidence, not product acceptance evidence:

| Evidence                                                                                                                                                                                                                                     | Result                                                      | Boundary                                                                                                                                                                                                                                                                                  |
|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `node scripts/gradle-run.mjs :server:smp:test --tests 'com.profiletailors.smp.publishing.*' --tests 'com.profiletailors.smp.platformadmin.infrastructure.http.PublishingStaleJobsControllerTest' --rerun-tasks --no-build-cache --no-daemon` | **PASS**, exit 0, `BUILD SUCCESSFUL` on the completed rerun | Local focused unit, persistence, worker, handler, and controller behavior only. The first 120-second attempt timed out before a final status; it was rerun with a longer timeout and completed successfully. `verify-report.md` records 140 selected tests with no skips/failures/errors. |
| `just backend-bdd-fast`                                                                                                                                                                                                                      | **PASS**, exit 0, `BUILD SUCCESSFUL`                        | Local BDD contract evidence. `verify-report.md` records 203/203 scenarios and 8 stale-job scenarios in this lane.                                                                                                                                                                         |
| `just backend-bdd-postgres`                                                                                                                                                                                                                  | **PASS**, exit 0, `BUILD SUCCESSFUL`                        | Local PostgreSQL-backed BDD evidence. `verify-report.md` records 203/203 scenarios and 8 stale-job scenarios in this lane.                                                                                                                                                                |
| Existing `verify-report.md` evidence: `just backend-test-fast`, `just backend-check`, `just backend-build`, coverage, Modulith, Spotless, and `git diff --check`                                                                             | **PASS** as recorded by verification                        | Technical conformance only; not managed-VPS, provider, operator, or user acceptance.                                                                                                                                                                                                      |
| `just swarm-config`                                                                                                                                                                                                                          | **UNAVAILABLE**, exit 1                                     | Local render could not run because `DASHBOARD_IMAGE` is missing from `swarm/.env`; no local rendered-stack evidence exists. The remote deployed service inventory below was observed separately and does not make the local renderer pass.                                                |

The local test lanes support the implementation handoff and preserve the reported `TEST_VERIFIED`
classification. They do not establish that a deployed operator or user observed the behavior.

### Authorized read-only production inspection (environment evidence only)

The inspection produced `VPS_OBSERVED` environment evidence. It is not an acceptance result and no
row below is a scenario `PASS`:

| Check                                   | Observation                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            | Boundary                                                                                                                                                                                          |
|-----------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| SSH target                              | `ssh -o BatchMode=yes -o ConnectTimeout=10 fenix` succeeded through Tailscale; the host reported `fenix-icloud`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       | Establishes read-only target reachability only.                                                                                                                                                   |
| Docker stack and service convergence    | Stack `profiletailors-smp-dz2yer` was present with `backend 1/1`, `cloudflared 1/1`, and `postgresql 1/1`; the backend task was `Running 8 days ago`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  | Inventory/readiness evidence only; no rollout, restart, or behavior transition was exercised.                                                                                                     |
| Deployed backend release                | Service image was `ghcr.io/dallay/profiletailors-smp:v0.4.1@sha256:990c7341441c7362b4a29b2d933b8438b4b1dc137c603f99bc125fbcacaba165`; service updated `2026-08-14`; local image created `2026-08-08T21:52:44.851150213Z`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | Release identity was observed, but the deployed artifact has not been proven to contain the current uncommitted Phase 2 code.                                                                     |
| Fenix image labels                      | Read-only image metadata reported `org.opencontainers.image.version=v0.4.1` and `org.opencontainers.image.source=https://github.com/dallay/profiletailors.com`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        | The labels identify the deployed release and source repository, but do not by themselves prove the image digest-to-commit mapping.                                                                |
| Local release tag                       | `smp@v0.4.1` resolves to commit `50429460991d81205dbafbf6f664b945fd89ace5`, dated `2026-08-08 23:48:43 +0200`, subject `chore: release main (#545)`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   | The tagged release predates the current Phase 2 stale-jobs endpoint and permission changes.                                                                                                       |
| Registry SLSA provenance                | Read-only GHCR inspection of the deployed OCI index `sha256:990c7341441c7362b4a29b2d933b8438b4b1dc137c603f99bc125fbcacaba165` mapped `linux/amd64` manifest `sha256:27e17279952316f514288bedd33e628050c0ebcbb04255220d6b7d36400cb21f` to config `sha256:2d3cfbe0461d23bd57f0106f49d8ed0d56fa0fe927e7cb772916612a38111860` and provenance blob `sha256:4921f7f64bf5b406878bd63d98b805002eb2e11e0e99cdfbb1b7ab2659644579`; `linux/arm64` manifest `sha256:b9ecb1892651266a42f7cab36de97c6b2a4d34c7d404fffac930eac16bb1dcb7` maps to config `sha256:6a83a9c67e1ca74d7642e582344ea5e76db5da6350287959322cee8491a75db7` and provenance blob `sha256:975e446a729ea109cea0329e63f0cbf3590154d41aff752f5ad2edbfe8300b2d`. Both attest `vcs.revision=50429460991d81205dbafbf6f664b945fd89ace5`. | The registry provenance maps both platform manifests to the `v0.4.1` release commit, confirming the deployed digest is the pre-change release.                                                    |
| Tag ancestry and tagged-tree inspection | `smp@v0.4.1` is an ancestor of current `HEAD`. `git grep` at the tag finds `lease_expires_at` schema/persistence references, but no `ListStaleJobsQuery` and no `PUBLISHING_STALE_READ`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               | Together with the v0.4.1 image labels, the tag evidence confirms a release mismatch/pre-change deployment for this QA gate; the current Phase 2 implementation is not present in the tagged tree. |
| Current implementation/deployment state | Phase 2 changes are uncommitted in the local worktree; no deployment was performed.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    | No claim is made that the observed Fenix image contains the current Phase 2 implementation.                                                                                                       |
| Publishing worker environment           | Service inspection printed only `SMP_PUBLISHING_WORKER_ENABLED=true`; no other publishing worker environment values were set in the service.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           | Effective values for omitted settings were not validated and are not inferred.                                                                                                                    |
| Backend readiness                       | `http://127.0.0.1:9091/actuator/health/readiness` returned `{"status":"UP"}`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          | Readiness is not proof of publishing behavior, route privacy, or code/release acceptance.                                                                                                         |
| API connectivity check                  | A request to localhost port `8080` was not made successfully; the connection was refused.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | No successful API response or live stale-jobs endpoint behavior was observed.                                                                                                                     |
| Mutation audit                          | No deploy, restart, environment edit, migration, provider call, publish, or data mutation was performed.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               | Read-only scope was preserved; safe-off/re-enable and recovery could not be exercised.                                                                                                            |

## Usage

### Target, Environment, Permissions, and Limitations

- **Target:** Fenix production, reached through the `fenix` SSH alias; host reported `fenix-icloud`.
  This resolves the prior report's inaccurate claim that no acceptance target existed, but it
  provides only a read-only production target, not an acceptance window.
- **Environment:** Docker Swarm stack `profiletailors-smp-dz2yer` with backend, Cloudflare tunnel,
  and PostgreSQL services observed at `1/1`. Backend readiness was `UP`. The local checkout is
  macOS/Darwin at `/Users/acosta/Dev/dallay/worktrees/p0`, branch `p0`, HEAD `e4795ca4`.
- **Deployed release/provenance:**
  `ghcr.io/dallay/profiletailors-smp:v0.4.1@sha256:990c7341441c7362b4a29b2d933b8438b4b1dc137c603f99bc125fbcacaba165`,
  updated `2026-08-14`, with reported OCI labels `org.opencontainers.image.version=v0.4.1` and
  `org.opencontainers.image.source=https://github.com/dallay/profiletailors.com`. Read-only GHCR
  SLSA provenance maps both platform manifests (`linux/amd64`
  `sha256:27e17279952316f514288bedd33e628050c0ebcbb04255220d6b7d36400cb21f`; `linux/arm64`
  `sha256:b9ecb1892651266a42f7cab36de97c6b2a4d34c7d404fffac930eac16bb1dcb7`) to
  `vcs.revision=50429460991d81205dbafbf6f664b945fd89ace5`, the local `smp@v0.4.1` tag. That tag is
  an ancestor of current `HEAD` and lacks `ListStaleJobsQuery` and `PUBLISHING_STALE_READ` while
  retaining `lease_expires_at` schema/persistence references. QA therefore treats the observed
  v0.4.1 release as a confirmed mismatch/pre-change deployment for Phase 2. The current Phase 2
  implementation remains uncommitted and no deployment was performed.
- **Credentials/permissions:** BatchMode SSH over Tailscale was authorized for read-only inspection.
  No production change window or permission to deploy, restart, edit environment, mutate data,
  invoke a provider, publish, rehearse rollback, or perform backup/restore was available for this QA
  run.
- **Observed configuration:** Only `SMP_PUBLISHING_WORKER_ENABLED=true` was set in the inspected
  service. No other publishing worker environment values were set, and their effective values were
  not inferred.
- **Limitations:** Fenix is production. Safe-off/re-enable, stale recovery, provider delivery,
  backup/restore, rollback, user acceptance, and operator acceptance require an explicit production
  change window and a separately approved release procedure proving the deployed release contains
  this change. The readiness probe is environment evidence only. The refused localhost `8080`
  connection means no live API response was observed. The current Phase 2 implementation is
  uncommitted and no deployment was performed. This QA report does not recommend a deployment or
  restart without that explicit production change window and separately approved release procedure.
  `sdd-quality-runner` and its strict-TDD module were unavailable, so this report uses `fallback`
  execution.

### Capability Inventory

| Capability                                            | Availability | Selected?                              | Rationale / rejection reason                                                                                                                                                       |
|-------------------------------------------------------|--------------|----------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Focused JUnit/WebTestClient tests                     | available    | selected                               | Provides local observable contract evidence for the worker, repository, handler, and admin endpoint; retained as technical handoff evidence, not live acceptance.                  |
| Cucumber `@smoke @fast` BDD                           | available    | selected                               | Exercises the stale-jobs HTTP contract and database-backed fixtures locally; not a deployed product observation.                                                                   |
| Cucumber PostgreSQL BDD                               | available    | selected                               | Exercises persistence-backed stale visibility, authorization, redaction, no-silent-publication, and empty-state scenarios locally.                                                 |
| Local persistence inspection/Testcontainers           | available    | selected                               | Can observe local row state in tests; cannot represent the managed beta database or a production restart.                                                                          |
| Authorized SSH/Tailscale production inspection        | available    | selected                               | Observed target reachability, stack/service inventory, image identity, worker env presence, and readiness without mutation; produces `VPS_OBSERVED` environment evidence only.     |
| Release provenance and repository ancestry inspection | available    | selected                               | Compared Fenix image labels with the local `smp@v0.4.1` tag, ancestor relationship, and tagged-tree symbols; this establishes release-mismatch evidence, not a QA acceptance pass. |
| Private readiness probe                               | available    | selected                               | Returned `{"status":"UP"}` from the private backend readiness endpoint; does not prove application behavior or release contents.                                                   |
| Production API/client request                         | unavailable  | rejected                               | The attempted localhost `8080` connection was refused; no successful API target or response was available for acceptance.                                                          |
| Production change/restart capability                  | unavailable  | rejected                               | Fenix is production and the authorized scope was read-only; no explicit change window was supplied.                                                                                |
| Swarm configuration renderer                          | unavailable  | rejected                               | `just swarm-config` failed because `DASHBOARD_IMAGE` is missing locally. Remote service inventory was observed, but no local rendered stack was produced.                          |
| Browser/Playwright/Chrome DevTools                    | available    | rejected                               | Phase 2 is backend/operator-control work and no operator browser surface or running admin target was supplied.                                                                     |
| Accessibility, responsive, and locale checks          | available    | rejected                               | No applicable browser target exists for this unit; these checks remain untested rather than inferred from source or API tests.                                                     |
| Managed operator manual or exploratory run            | unavailable  | rejected                               | Read-only inventory was possible, but the safe-off, re-enable, stale-recovery, rollback, and evidence-ledger workflow requires a production change window.                         |
| Provider-side delivery evidence                       | unavailable  | rejected                               | No provider call or publish was authorized; local WireMock/test evidence cannot be provider verification.                                                                          |
| Backup/restore and rollback rehearsal                 | unavailable  | rejected                               | These are production state-changing operations and were explicitly not performed.                                                                                                  |
| `sdd-quality-runner`/FSM                              | unavailable  | rejected                               | Not present in the repository/session; direct commands ran in explicit `fallback` mode.                                                                                            |
| Static inspection                                     | available    | rejected as sole acceptance capability | Used to map target and limitations only. Static inspection cannot produce a QA `PASS`.                                                                                             |

### Scenario Matrix

The results below are acceptance results. The Fenix observations are cited as environment evidence
only. Local passing tests are not converted into product acceptance, and no static or readiness
observation is reported as `PASS`.

| ID       | Capability                              | Acceptance scenario                                                                                                                                               | Result         | Evidence or reason                                                                                                                                                                                                                                                                                 |
|----------|-----------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| P2-QA-01 | Managed operations                      | Operator sets worker safe-off, redeploys, and due persisted jobs cause no new provider delivery while remaining recoverable.                                      | **BLOCKED**    | Fenix is production; no explicit change window or mutation permission was supplied. Read-only inventory observed `SMP_PUBLISHING_WORKER_ENABLED=true`, but safe-off was not performed. The observed v0.4.1 release is confirmed as a Phase 2 mismatch/pre-change deployment from the tag evidence. |
| P2-QA-02 | Managed operations                      | Operator re-enables the worker and observes polling resume, stale claims release before claiming, and recoverable jobs remain unmarked as published.              | **BLOCKED**    | Requires a production restart, live logs/data, and an explicitly approved release containing this change. None was authorized; no deployment was performed. Readiness `UP` does not establish the lifecycle transition.                                                                            |
| P2-QA-03 | API/data                                | Authorized operator lists a stale claim and sees publication ID, workspace ID, age, attempt, and `RELEASE_AND_RETRY`.                                             | **BLOCKED**    | No successful product API request was observed; localhost `8080` refused the connection. The image label identifies v0.4.1, while the corresponding local tag lacks `ListStaleJobsQuery`; the deployed endpoint contents and live operator authorization were not acceptance-tested.               |
| P2-QA-04 | Persistence/state transition            | A claim older than the stale threshold is automatically reset to `PENDING` before the next claim and remains retryable.                                           | **BLOCKED**    | Requires live queue state and a controlled worker lifecycle transition. No restart, queue mutation, or recovery observation was permitted.                                                                                                                                                         |
| P2-QA-05 | Safety/state transition                 | Stale visibility or recovery does not silently publish the publication; status and `published_at` remain safe until a real delivery succeeds.                     | **BLOCKED**    | No live queue/provider path was exercised. No provider call or publish was performed, and readiness alone cannot prove the persisted state boundary.                                                                                                                                               |
| P2-QA-06 | Authorization                           | An unauthenticated request to the global stale-jobs endpoint returns `401` and does not dispatch the query.                                                       | **BLOCKED**    | No successful live API endpoint was available; local controller and BDD evidence is technical handoff evidence only.                                                                                                                                                                               |
| P2-QA-07 | Authorization/security                  | An authenticated auditor without `PUBLISHING_STALE_READ` receives `403` with `PLATFORM_ACCESS_DENIED`.                                                            | **BLOCKED**    | No live admin API response or acceptance permission set was observed. The read-only SSH permission does not establish HTTP authorization behavior.                                                                                                                                                 |
| P2-QA-08 | Negative/boundary input                 | Malformed, zero, and negative ISO-8601 stale thresholds return a safe validation error and do not dispatch the query.                                             | **BLOCKED**    | No successful live API request was possible; local validation tests do not establish the deployed endpoint behavior.                                                                                                                                                                               |
| P2-QA-09 | Negative/boundary input                 | Limits below `1` or above `100` return a safe validation error, while a valid bounded limit is honored.                                                           | **BLOCKED**    | No successful live API request was possible; local controller/BDD coverage remains technical evidence only.                                                                                                                                                                                        |
| P2-QA-10 | Security/redaction                      | A stale-jobs response contains no raw token, bearer credential, URL, provider payload, exception, stack trace, or storage path.                                   | **BLOCKED**    | No live response or deployed log trace was available. Local safe-shape assertions cannot prove redaction in the observed production artifact.                                                                                                                                                      |
| P2-QA-11 | Empty state                             | An empty queue returns HTTP 200 with `total = 0` and an empty `staleJobs` list.                                                                                   | **BLOCKED**    | No successful product endpoint request or live queue observation was available.                                                                                                                                                                                                                    |
| P2-QA-12 | Global scope                            | The endpoint is global, does not require or infer a workspace context, and an authorized operator can review claims across workspaces without workspace leakage.  | **BLOCKED**    | The target was reachable for SSH inventory, but no successful endpoint request or cross-workspace operator review was authorized. Source and local fixtures are not deployed acceptance evidence.                                                                                                  |
| P2-QA-13 | Repeated/interrupted behavior           | A release failure is visible, does not silently continue to claim work, and the next lifecycle tick can retry the release.                                        | **BLOCKED**    | Requires a live failure/retry lifecycle and logs. No production worker transition or provider interaction was performed.                                                                                                                                                                           |
| P2-QA-14 | Persistence                             | After a managed restart, queue state, claim columns, safe-off state, and recovery action are consistent and recoverable.                                          | **BLOCKED**    | No production restart, queue inspection, or state-changing operation was authorized; the observed v0.4.1 deployment is a confirmed pre-change/mismatched release for the current Phase 2 implementation.                                                                                           |
| P2-QA-15 | Contract alignment                      | BDD field expectations and the stale-job response schema remain aligned for all eight stale-job scenarios in both BDD lanes.                                      | **NOT TESTED** | `verify-report.md` records 203/203 technical scenarios in each lane, but no live product response from the observed release was captured. This is not a second technical `PASS`.                                                                                                                   |
| P2-QA-16 | Browser/accessibility/responsive/locale | Any operator-facing browser surface renders the controls and errors accessibly, responsively, and in supported locales.                                           | **NOT TESTED** | No browser target is part of this Phase 2 unit and no running admin application was supplied. No accessibility, responsive, or localization claim is made.                                                                                                                                         |
| P2-QA-17 | Manual/exploratory operator workflow    | An operator follows the runbook, captures UTC/release/scope/classification evidence, and records safe-off, stale recovery, and re-enable outcomes.                | **BLOCKED**    | The read-only inspection captured UTC, host, stack, release labels/tag provenance, and readiness, but not the required operational transitions. Fenix is production; no explicit change window or separately approved release procedure was supplied.                                              |
| P2-QA-18 | Managed boundary                        | Public/private route restrictions, provider delivery, backup/restore, rollback rehearsal, operator acceptance, and user acceptance are proven with live evidence. | **BLOCKED**    | These require explicit production operations and separately classified provider/operator/user evidence. None was authorized or supplied; the read-only inventory is not acceptance.                                                                                                                |

## Troubleshooting

### Untested Scope

- **Scope:** Managed worker safe-off/re-enable, live stale visibility and recovery, deployed
  persistence/restart behavior, endpoint authorization/validation/redaction/empty-state behavior,
  provider delivery, public/private routing and origin exposure, backup/restore, rollback rehearsal,
  runbook execution, operator acceptance, user acceptance, browser behavior, accessibility,
  responsive behavior, and locale behavior.
- **Reason:** A production target now exists and was observed read-only; the remaining constraint is
  not target availability. Fenix is production, no explicit change window or separately approved
  release procedure was supplied, the current Phase 2 code is uncommitted, and the observed v0.4.1
  image is confirmed as a pre-change/mismatched release because its corresponding ancestor tag lacks
  `ListStaleJobsQuery` and `PUBLISHING_STALE_READ`. Only the worker-enabled env value was observed,
  and the live API check on localhost `8080` was refused. No production mutation, provider call,
  publish, or data inspection requiring broader permission was performed. Local Swarm rendering also
  remains unavailable because `DASHBOARD_IMAGE` is not set.
- **Re-run prerequisite:** Obtain an approved production change window, least-privilege operator
  permissions, and a separately approved release procedure. The release owner must produce and prove
  an artifact containing the current Phase 2 code before any controlled deployment or restart; QA
  must not recommend or perform either action outside that approval. Then validate effective
  publishing configuration rather than inferring omitted environment values, and run the runbook's
  safe-off, re-enable, stale-visibility, stale-recovery, route, backup/restore, and rollback checks
  with redacted provenance. Capture separate provider-side, operator, and user outcomes, retaining
  `USER_REPORTED_OPERATIONAL` where no provider evidence exists. If local rendered-stack evidence is
  required, provide the approved non-secret `DASHBOARD_IMAGE`/stack inputs and rerun
  `just swarm-config`.

### Findings

| ID     | Severity | Scenario / location                                                                                           | Evidence                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            | Status                                                                |
|--------|----------|---------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------|
| QA-001 | **P1**   | Acceptance target availability                                                                                | Authorized read-only SSH through Tailscale succeeded; Fenix reported host `fenix-icloud`, stack `profiletailors-smp-dz2yer`, service inventory, release identity, and readiness.                                                                                                                                                                                                                                                                                                                                                                                                                    | **RESOLVED — target availability only; not an acceptance pass**       |
| QA-002 | **P2**   | Local Swarm configuration rendering                                                                           | `just swarm-config` exited 1 because `DASHBOARD_IMAGE` is missing locally. Remote deployed service inventory was observed separately, but no local rendered stack was produced.                                                                                                                                                                                                                                                                                                                                                                                                                     | **OPEN — warning**                                                    |
| QA-003 | **P1**   | Production change window and operational permissions                                                          | Fenix is production and inspection was read-only. Safe-off, re-enable, stale recovery, backup/restore, rollback, provider calls, publish, and data mutations were not authorized.                                                                                                                                                                                                                                                                                                                                                                                                                   | **OPEN — blocking**                                                   |
| QA-004 | **P1**   | Deployed artifact and effective configuration provenance — confirmed release mismatch / pre-change deployment | Fenix image labels report `v0.4.1` and source `https://github.com/dallay/profiletailors.com`; local `smp@v0.4.1` resolves to `50429460991d81205dbafbf6f664b945fd89ace5` (`2026-08-08 23:48:43 +0200`, `chore: release main (#545)`), is an ancestor of current `HEAD`, and its tree has `lease_expires_at` references but no `ListStaleJobsQuery` or `PUBLISHING_STALE_READ`. The current Phase 2 implementation is uncommitted and no deployment was performed. Only `SMP_PUBLISHING_WORKER_ENABLED=true` was set; omitted publishing values and their effective configuration were not validated. | **OPEN — blocking; confirmed release mismatch/pre-change deployment** |
| QA-005 | **P1**   | Managed publishing and final launch gate                                                                      | Live stale behavior, route/privacy, provider delivery, backup/restore, rollback, operator acceptance, and user acceptance remain unobserved; tasks `4.x` and `5.x` are pending.                                                                                                                                                                                                                                                                                                                                                                                                                     | **OPEN — blocking**                                                   |
| QA-006 | **P2**   | QA execution determinism                                                                                      | `sdd-quality-runner` and strict-TDD runner/module were unavailable; local commands and the read-only inspection ran in explicit `fallback` mode.                                                                                                                                                                                                                                                                                                                                                                                                                                                    | **OPEN — warning**                                                    |
| QA-007 | **P2**   | Change-wide readiness scope                                                                                   | Phase 3 invitee E2E and Phases 4–5 managed evidence/final gate remain pending; Phase 2 technical completion and Fenix readiness must not be treated as whole-change readiness.                                                                                                                                                                                                                                                                                                                                                                                                                      | **OPEN — gate dependency**                                            |

No product failure was observed in the read-only inventory/readiness inspection, but that
observation is not acceptance evidence. No `CRITICAL` or `P0` finding is asserted; the unresolved P1
findings prevent acceptance closure.

### Verdict

**BLOCKED**

### Rationale

The prior target-availability statement was incorrect: Fenix is a reachable production target, and
the authorized read-only inspection produced auditable `VPS_OBSERVED` environment evidence for the
host, stack, service convergence, release identity, worker-enabled setting, and readiness. That
resolves QA-001 only. New provenance evidence refines QA-004 from merely unproven provenance to a
confirmed release mismatch/pre-change deployment for this gate: the Fenix image is labeled v0.4.1,
while the corresponding local ancestor tag predates and lacks the current Phase 2
`ListStaleJobsQuery` and `PUBLISHING_STALE_READ` changes. The current Phase 2 implementation remains
uncommitted and no deployment was performed. The attempted localhost `8080` connection was refused,
and omitted publishing settings were not validated. Because Fenix is production and no explicit
change window or separately approved release procedure was available, safe-off/re-enable, stale
recovery, provider delivery, backup/restore, rollback, route/privacy, operator acceptance, and user
acceptance remain `BLOCKED` or `NOT TESTED`. This QA report does not recommend a deployment or
restart without that explicit production change window and separately approved release procedure.
The correct gate result remains `BLOCKED`, not `PASS` or `PASS WITH WARNINGS`.

### Limitations and Implementation Handoff

- QA did not modify production/test source, deploy configuration, `state.yaml`, or the
  implementation, and did not fix findings.
- The read-only inspection was performed through `ssh -o BatchMode=yes -o ConnectTimeout=10 fenix`
  over Tailscale; no deploy, restart, env edit, migration, provider call, publish, data mutation,
  commit, or push occurred.
- The observed Fenix v0.4.1 release is a confirmed pre-change/mismatched deployment for the current
  Phase 2 worktree. Any remediation must use an explicit production change window and a separately
  approved release procedure; QA does not authorize or recommend an unapproved deployment or
  restart.
- This report is the acceptance audit record; it does not claim product acceptance for the harness,
  local fixtures, Fenix readiness, or the observed deployment.
- Keep the Phase 2 implementation handoff as technical/test evidence plus `VPS_OBSERVED` environment
  evidence. Preserve `USER_REPORTED_OPERATIONAL` for any live publishing result without provider
  evidence.
- Resolve the confirmed release-mismatch provenance and production-change-window prerequisites
  through the separately approved release procedure, execute the runbook with redacted provenance,
  and rerun this QA phase. Keep the local `just swarm-config` warning visible; remote service
  inventory does not replace rendered-stack or acceptance evidence. Do not advance to `sdd-archive`
  while acceptance-relevant `BLOCKED`/`NOT TESTED` scope and P1 findings remain.

#### Phase handoff

- **Status:** blocked
- **Executive summary:** Fenix production is confirmed as a read-only target with healthy service
  inventory and backend readiness, but its labeled v0.4.1 release is a confirmed
  pre-change/mismatched deployment for the current Phase 2 stale-jobs endpoint and permission work.
  Acceptance remains blocked because the current implementation is uncommitted, no deployment was
  performed, and no explicit production change window or separately approved release procedure
  exists for any deployment/restart, safe-off, recovery, provider, rollback, or user/operator
  acceptance.
- **Artifact:** `openspec/changes/private-beta-launch-readiness/qa-report.md`
- **Next recommended:** obtain the separately approved release procedure and explicit production
  change window, prove the release artifact contains Phase 2, then rerun `sdd-qa`; do not recommend
  or perform a deployment/restart outside that approval, and do not run `sdd-archive` yet.
- **Risks:** Confirmed pre-change/mismatched deployed artifact, unvalidated effective publishing
  configuration, no safe-off or recovery rehearsal, unavailable
  provider/backup/restore/rollback/route/user/operator evidence, local Swarm rendering warning,
  pending change-wide phases, and unavailable deterministic runner enforcement.
- **Skill resolution:** `fallback-path` — the `sdd-qa` executor prompt was provided in the launch
  context and the executor skill file was read directly; no additional project skill was required.

## References

- `openspec/changes/private-beta-launch-readiness/specs/publishing/spec.md`
- `openspec/changes/private-beta-launch-readiness/verify-report.md`
- `docs/infrastructure/private-beta-launch-readiness-runbook.md`
