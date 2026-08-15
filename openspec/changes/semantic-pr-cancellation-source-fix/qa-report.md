# Acceptance QA Report: semantic-pr-cancellation-source-fix

## 1. Identity

| Field | Value |
|---|---|
| Change | `semantic-pr-cancellation-source-fix` |
| Mode | OpenSpec capability-driven acceptance QA re-run |
| Phase | `qa` |
| Date | 2026-08-15 |
| Consumer target | `dallay/profiletailors.com`, PR #801 (`actions-fix`) |
| Consumer workspace | `/Users/acosta/Dev/dallay/worktrees/actions-fix` |
| Shared-workflow source | `dallay/common-actions` |
| Source workspace | `/Users/acosta/Dev/dallay/common-actions` |

This report records observable GitHub Actions/operator evidence only. This repository has no
application under test and no general product test runner; this report does not claim product
acceptance for the workflow harness.

## 2. Source Artifacts and Technical Verification Handoff

### Read artifacts

- `openspec/changes/semantic-pr-cancellation-source-fix/proposal.md`
- `openspec/specs/semantic-pr-validation/spec.md`
- `openspec/changes/semantic-pr-cancellation-source-fix/design.md`
- `openspec/changes/semantic-pr-cancellation-source-fix/tasks.md`
- `openspec/changes/semantic-pr-cancellation-source-fix/apply-progress.md`
- `openspec/changes/semantic-pr-cancellation-source-fix/verify-report.md`
- `openspec/changes/semantic-pr-cancellation-source-fix/state.yaml`
- `openspec/config.yaml`
- `/Users/acosta/Dev/dallay/common-actions/.github/workflows/semantic-pr.yml`
- `/Users/acosta/Dev/dallay/worktrees/actions-fix/.github/workflows/semantic-pull-request.yml`

No change-local delta-spec directory exists at
`openspec/changes/semantic-pr-cancellation-source-fix/specs/`; the applicable contract is
`openspec/specs/semantic-pr-validation/spec.md`.

### Technical verification handoff

The preceding verification reports successful source focused tests, `actionlint`, focused caller
contract checks, immutable SHA resolution, and YAML/diff checks. The source release is `v2.2.3`
at `87129475c06d9f3354d398426926ba4345a4644d`. Verification previously left task 4.1 incomplete
because it had no post-fix overlapping PR-event evidence. This re-run obtained live PR-event
evidence and found that the acceptance requirement still fails (see QA-001).

## 3. Target, Environment, Permissions, and Limitations

- **Target:** GitHub repository `dallay/profiletailors.com`, open PR #801, head SHA
  `b8b038b119c74257fb97a59a3c34e5ed08182dec`.
- **Workflow under test:** `Semantic PR`, triggered by `pull_request_target` for `opened`,
  `edited`, and `synchronize`; the PR branch pins `dallay/common-actions` at SHA
  `87129475c06d9f3354d398426926ba4345a4644d` (`v2.2.3`).
- **Source revision:** Remote `dallay/common-actions` tag `v2.2.3` resolves to
  `87129475c06d9f3354d398426926ba4345a4644d`; the fetched source declares the existing
  PR-scoped group and `cancel-in-progress: false`.
- **Environment:** GitHub-hosted Ubuntu 24.04 runners; GitHub Actions API and `gh` CLI.
- **Permissions:** An authenticated operator account with permission to update PR #801's title and
  inspect workflow runs/logs. No separate unauthorized or read-only identity was available.
- **Test mutation:** PR #801's title was temporarily changed to an invalid title and then restored;
  repeated valid title edits were used to generate overlapping `edited` events. The final title is
  `fix(ci): pin Semantic PR workflow v2.2.3`.
- **Limitations:** GitHub schedules short Semantic PR jobs quickly, so the run was not held open by
  the test. No disposable second PR was available for isolation testing, and no direct
  `opened`/`synchronize` event could be generated without creating or force-pushing a separate PR.

## 4. Capability Inventory

| Capability | Status | Selection rationale / limitation |
|---|---|---|
| GitHub Actions run inspection | **selected** | Available and required to observe workflow conclusions, job steps, timestamps, and overlap behavior. |
| GitHub API / PR event generation | **selected** | Available; PR title edits generated real `pull_request_target` `edited` events on PR #801. |
| Source/consumer repository inspection | **selected** | Available and used to identify the target revision and correlate live runs with the intended workflow. Static inspection is not acceptance evidence by itself. |
| Workflow log retrieval | **selected** | Available and used to confirm successful and canceled run/job transitions. |
| Local Node/YAML test runner | **rejected** | Available, but rejected for acceptance because it cannot demonstrate GitHub scheduling behavior. |
| Browser/UI testing | **unavailable** | No browser-facing surface exists for this workflow/configuration change. |
| Accessibility testing | **rejected** | No rendered UI or user interaction surface exists; this capability is not applicable. |
| Responsive testing | **rejected** | No layout or viewport behavior exists; this capability is not applicable. |
| Internationalization/locale testing | **rejected** | No locale-dependent product behavior exists; this capability is not applicable. |
| Persistence/data verification | **rejected** | No application data or database is changed; this capability is not applicable. |
| Unauthorized/security identity testing | **unavailable** | No separate unauthorized credential or permission boundary was available; workflow permissions were inspected but not treated as a passing negative test. |
| Manual exploratory workflow testing | **selected** | Available and used to exercise repeated title edits and inspect run ordering and cancellation outcomes. |

## 5. Scenario Matrix

| Scenario | Result | Evidence / reason |
|---|---|---|
| Valid semantic title passes | **PASS** | PR #801 validation run `31881912411` completed `success` at `2026-08-15T11:23:32Z`; job `main / Validate PR title` passed. Evidence: https://github.com/dallay/profiletailors.com/actions/runs/31881912411 |
| Invalid semantic title fails with validation outcome | **PASS** | A temporary invalid title on PR #801 produced run `31882305525` with conclusion `failure` at `2026-08-15T11:32:44Z`; the title was then restored. Evidence: https://github.com/dallay/profiletailors.com/actions/runs/31882305525 |
| `edited` event triggers validation on the pinned revision | **PASS** | The live runs above were created by title edits on PR #801 and used head SHA `b8b038b119c74257fb97a59a3c34e5ed08182dec`; the consumer branch was pinned to `v2.2.3` SHA `87129475c06d9f3354d398426926ba4345a4644d`. |
| `opened` event triggers validation | **NOT TESTED** | No disposable PR was available and recreating the existing PR would alter the target. Rerun prerequisite: a disposable test PR or an approved real PR-opening event. |
| `synchronize` event triggers validation | **NOT TESTED** | No force-push or new commit was made to PR #801 because that would change the change-under-test. Rerun prerequisite: an approved disposable branch update or real synchronization event. |
| New event preserves an active validation without cancellation | **FAIL** | Overlapping title edits on PR #801 produced run `31882470945` (`11:36:26Z`) and run `31882473122` (`11:36:29Z`) with conclusion `cancelled`. Run `31882473122` executed the validation step successfully, but the workflow run itself was later canceled at `11:36:43Z` while a successor run `31882480587` was starting. This is directly contrary to the observable requirement that an in-flight validation not appear canceled. Evidence: https://github.com/dallay/profiletailors.com/actions/runs/31882473122 and https://github.com/dallay/profiletailors.com/actions/runs/31882480587 |
| Newer event waits or replaces only an older pending event | **PASS** | A burst of three edits produced runs `31882349446` and `31882349659` canceled with zero-duration jobs, followed by run `31882350205` success. This is consistent with the specification's allowance for pending-run replacement; it does not offset the separate active-run cancellation in QA-001. Evidence: https://github.com/dallay/profiletailors.com/actions/runs/31882349446, https://github.com/dallay/profiletailors.com/actions/runs/31882349659, https://github.com/dallay/profiletailors.com/actions/runs/31882350205 |
| Different PRs do not share a concurrency group | **NOT TESTED** | No second disposable PR was available. Rerun prerequisite: two authorized test PRs with overlapping events. |
| Unauthorized actor cannot alter or invoke validation | **NOT TESTED** | No unauthorized identity or permission-denied test environment was available. The workflow permissions were not treated as a passing security scenario. |
| Repeated/interrupted event sequence remains auditable | **FAIL** | The repeated-event sequence is observable, but at least one post-fix Semantic PR workflow run is reported as `cancelled` (`31882473122`) despite successful validation steps. This leaves the acceptance-visible cancellation defect unresolved. |
| Browser, accessibility, responsive, locale, persistence, and UI state-transition behavior | **NOT APPLICABLE** | This is a workflow/configuration change with no application UI, browser surface, locale behavior, or application persistence. |
| Exploratory operator check of the released pin | **PASS** | Live run metadata used PR head `b8b038b...`; source tag `v2.2.3` and SHA `87129475...` resolve remotely, and the run invoked `main / Validate PR title`. The overlap outcome remains a failing acceptance result, not a static-inspection pass. |

## 6. Untested Scope, Reason, and Rerun Prerequisites

| Scope | Reason | Rerun prerequisite |
|---|---|---|
| Direct `opened` event | Would require creating a disposable PR or reopening the target. | Approved disposable PR in `dallay/profiletailors.com`. |
| Direct `synchronize` event | Would require an approved force-push/new commit to a test PR. | Approved disposable branch and PR synchronization event. |
| Different-PR isolation | No second test PR was available. | Two disposable PRs and permission to generate overlapping events. |
| Unauthorized/security behavior | No separate unauthorized credential or role was available. | Read-only/unauthorized test identity and approved test repository boundary. |
| Reliable active-run timing | The reusable workflow completes quickly; the test could not hold the job open. | A safe way to create two events while the first validation is demonstrably in progress, plus run-level and called-job correlation. |

## 7. Findings

| ID | Severity | Status | Finding |
|---|---|---|---|
| QA-001 | **CRITICAL** | **OPEN** | A post-fix overlapping PR-event sequence still produced a Semantic PR workflow run with conclusion `cancelled` (`31882473122`). Although its validation steps reached success, the observable workflow run was canceled while the successor run started. The required acceptance behavior is therefore not demonstrated and is contradicted by live evidence. |
| QA-002 | **P2** | **OPEN** | The source release task requires separate `@dallay/infra` review evidence, but the prior phase records a direct administrator push and no review evidence. This is a process/audit warning independent of the runtime cancellation failure. |

## 8. Final Verdict

**FAIL**

The valid-title and invalid-title outcomes work on the pinned release, and pending-run replacement
is observable. However, the acceptance-critical overlapping-event scenario failed: a post-fix run
was still reported as `cancelled` while a successor event was processed. The release/pin change
therefore cannot receive an acceptance pass from this QA re-run.

## 9. Verdict Rationale and Implementation Handoff

- Keep PR #801 open and do not archive this change.
- Investigate the cancellation owner at GitHub workflow-run level: correlate the caller run,
  reusable-workflow job, concurrency group, and event delivery for runs `31882470945`,
  `31882473122`, and `31882480587`. The source file contains `cancel-in-progress: false`, but the
  observable caller run still ended as canceled.
- Resolve QA-001 with an implementation/source-policy change or a documented GitHub platform
  explanation and an approved acceptance strategy; then rerun the active-overlap scenario with a
  demonstrably in-progress validation.
- Obtain or explicitly waive the required `@dallay/infra` review evidence for QA-002.
- Re-run QA for `opened`, `synchronize`, and different-PR isolation if those scopes remain part of
  the acceptance gate.
- This QA report does not modify source code and does not claim product acceptance for the
  repository harness.
