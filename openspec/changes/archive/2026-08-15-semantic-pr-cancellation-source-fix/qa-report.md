# Acceptance QA Report: semantic-pr-cancellation-source-fix

## 1. Identity

| Field | Value |
|---|---|
| Change | `semantic-pr-cancellation-source-fix` |
| Mode | OpenSpec capability-driven acceptance QA re-run |
| Phase | `qa` |
| Date | 2026-08-15 (re-run; prior FAIL re-evaluated with post-merge live evidence) |
| Consumer target | `dallay/profiletailors.com` — PR #801 was MERGED to main on 2026-08-15T11:49:24Z (merge commit `3aa9b61a1a1c68124d723aa8e521881589ea9b59`) |
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
- `/Users/acosta/Dev/dallay/common-actions/.github/workflows/semantic-pr.yml` (local checkout)
- `/Users/acosta/Dev/dallay/worktrees/actions-fix/.github/workflows/semantic-pull-request.yml` (local + remote `main`)

No change-local delta-spec directory exists at
`openspec/changes/semantic-pr-cancellation-source-fix/specs/`; the applicable contract is
`openspec/specs/semantic-pr-validation/spec.md`.

### Technical verification handoff

The preceding verification reports successful source focused tests, `actionlint`, focused caller
contract checks, immutable SHA resolution, and YAML/diff checks. The source release is `v2.2.3`
at `87129475c06d9f3354d398426926ba4345a4644d`. Verification left task 4.1 (post-fix overlapping
PR-event evidence) incomplete. This re-run closes 4.1 with live evidence and re-evaluates the
prior QA-001 finding against the merged configuration.

### Root-cause analysis of the prior QA-001 (false negative)

1. The prior QA re-run (runs at 11:33–11:36Z on 2026-08-15) executed BEFORE PR #801 was merged
   (11:49:24Z). For `pull_request_target`, GitHub executes the workflow file from the BASE branch
   (`main`), not from the PR branch. At that time `main` still pinned
   `6548f0dfaad9e40648cd56e85566e5bc2d707dfb` (v2.2.2, `cancel-in-progress: true`).
2. Verified proof: run logs for `31882473122` and `31882480587` both contain
   `Uses: dallay/common-actions/.github/workflows/semantic-pr.yml@6548f0dfaad9e40648cd56e85566e5bc2d707dfb`.
   The prior "post-fix overlapping-event" runs therefore used the OLD policy. The observed
   cancellation pattern (new run cancels an in-flight run, including a run whose job had just
   completed) is exactly the documented behavior of `cancel-in-progress: true`, not `false`.
   Contrast: old-policy run `31882470945` shows its in-flight `Set up job` step cancelled.
3. Conclusion: QA-001 was a false negative caused by testing the old configuration. It is
   **RESOLVED**; see Findings.

## 3. Target, Environment, Permissions, and Limitations

- **Target:** GitHub repository `dallay/profiletailors.com`, branch `main`. PR #801
  (`actions-fix`) was merged at 2026-08-15T11:49:24Z; the caller workflow on `main` pins
  `dallay/common-actions` at SHA `87129475c06d9f3354d398426926ba4345a4644d` (`v2.2.3`).
  Verified remotely: `main` blob SHA of
  `.github/workflows/semantic-pull-request.yml` is `36666503be47528d38fae00c1fbd79047281d43a`
  and its `uses:` line is `...@87129475c06d9f3354d398426926ba4345a4644d # v2.2.3`.
- **Source revision:** Remote `dallay/common-actions` tag `v2.2.3` resolves to
  `87129475c06d9f3354d398426926ba4345a4644d`; the fetched source declares the existing PR-scoped
  group and `cancel-in-progress: false`. Diff v2.2.2→v2.2.3 changes only the
  `cancel-in-progress` line (`true` → `false`); the concurrency group is unchanged.
- **Environment:** GitHub-hosted Ubuntu 24.04 runners; GitHub Actions API and `gh` CLI
  (authenticated as `yacosta738`).
- **Permissions:** An authenticated operator account with permission to create/close disposable
  test PRs, edit PR titles, and inspect workflow runs/logs. No separate unauthorized or
  read-only identity was available.
- **Test mutation (this re-run):** Disposable PR #803 (`test/semantic-pr-overlap`) with a valid
  semantic title was opened, then four rapid `edited` title edits were produced to create an
  overlap burst. PR #803 was closed and its branch deleted after the test. PR #801's title was
  not mutated during this re-run (it was already merged).
- **Limitations:** GitHub schedules short Semantic PR jobs quickly, so the test could not hold a
  job open deliberately; the burst still produced the required pending-replacement pattern.
  No `synchronize` event and no two-PR overlap were exercised.

## 4. Capability Inventory

| Capability | Status | Selection rationale / limitation |
|---|---|---|
| GitHub Actions run inspection | **selected** | Available and required to observe workflow conclusions, job steps, timestamps, and overlap behavior. |
| GitHub API / PR event generation | **selected** | Available; disposable PR #803 was opened and title-edited to generate real `pull_request_target` `opened`/`edited` events. |
| Workflow log retrieval | **selected** | Available; used to confirm the pinned `Uses:` SHA provenance of each run (old vs new policy). |
| GitHub API job/step inspection | **selected** | Available; used to distinguish cancelled runs whose job never started (`steps: []`) from in-flight cancellations (old policy). |
| Source/consumer repository inspection | **selected** | Available and used to identify the target revision and correlate live runs with the intended workflow. Static inspection is not acceptance evidence by itself. |
| Local Node/YAML test runner | **rejected** | Available, but rejected for acceptance because it cannot demonstrate GitHub scheduling behavior. |
| Browser/UI testing | **unavailable** | No browser-facing surface exists for this workflow/configuration change. |
| Accessibility testing | **rejected** | No rendered UI or user interaction surface exists; this capability is not applicable. |
| Responsive testing | **rejected** | No layout or viewport behavior exists; this capability is not applicable. |
| Internationalization/locale testing | **rejected** | No locale-dependent product behavior exists; this capability is not applicable. |
| Persistence/data verification | **rejected** | No application data or database is changed; this capability is not applicable. |
| Unauthorized/security identity testing | **unavailable** | No separate unauthorized credential or permission boundary was available; workflow permissions were inspected but not treated as a passing negative test. |
| Manual exploratory workflow testing | **selected** | Available and used to exercise the disposable-PR overlap burst and inspect run ordering and cancellation outcomes. |

## 5. Scenario Matrix

| ID | Scenario | Result | Evidence / reason |
|---|---|---|---|
| S-01 | Valid semantic title passes (`opened`) | **PASS** | Post-fix `opened` run `31884345568` (2026-08-15T12:20:19Z, `test/semantic-pr-overlap`): conclusion `success`; job `main / Validate PR title` ran ALL steps to completion (Set up job, semantic-pull-request action, sticky comment, Complete job). Log uses pin `87129475...` (v2.2.3). Evidence: https://github.com/dallay/profiletailors.com/actions/runs/31884345568 |
| S-02 | Valid semantic title passes (`edited`) | **PASS** | Post-fix `edited` runs `31884374535` (12:21:02Z) and `31884379096` (12:21:08Z): conclusion `success`; ALL steps ran to completion; logs use pin `87129475...`. Evidence: https://github.com/dallay/profiletailors.com/actions/runs/31884374535 and https://github.com/dallay/profiletailors.com/actions/runs/31884379096 |
| S-03 | Invalid semantic title fails with validation outcome | **PASS** | A temporary invalid title on PR #801 produced run `31882305525` (11:32:36Z) with conclusion `failure`. This run used the pre-merge pin, but v2.2.2→v2.2.3 changes ONLY the `cancel-in-progress` concurrency line; title-validation logic is identical, so the invalid-title outcome evidence remains valid for the released revision. Evidence: https://github.com/dallay/profiletailors.com/actions/runs/31882305525 |
| S-04 | `opened` event triggers validation on the pinned revision | **PASS** | Disposable PR #803 `opened` run `31884345568` used the released pin `87129475...` (verified in log) and completed `success` with all steps. |
| S-05 | `edited` event triggers validation on the pinned revision | **PASS** | The four rapid title edits on PR #803 produced runs `31884374535` (success), `31884376688` (cancelled, steps: 0), `31884377801` (cancelled, steps: 0), `31884379096` (success). The two `success` runs used pin `87129475...` and completed all steps. |
| S-06 | `synchronize` event triggers validation | **NOT TESTED** | No `synchronize` event was exercised in this re-run (would require a commit/force-push to a disposable branch). The caller contract for `synchronize` is unchanged and statically verified by `sdd-verify`. Rerun prerequisite: an approved disposable branch push on a test PR. |
| S-07 | New event preserves an active validation without cancellation | **PASS** | Post-fix overlap burst on PR #803: every run whose job STARTED completed ALL steps with `success` (`31884345568`, `31884374535`, `31884379096`); the two `cancelled` runs (`31884376688`, `31884377801`) have `steps: []` — their jobs NEVER started any step (pending runs replaced by a newer event before execution began). With `cancel-in-progress: false`, GitHub still replaces a PENDING run during a burst — documented behavior explicitly allowed by the spec ("GitHub may still replace an older pending run during a burst"). The critical requirement — an IN-FLIGHT validation is NOT cancelled — is met post-fix. This is the opposite of the old policy, where run `31882470945` had its in-flight `Set up job` step cancelled mid-execution. |
| S-08 | Newer event waits or replaces only an older pending event | **PASS** | The 12:21Z burst shows exactly this: `31884376688` and `31884377801` were cancelled while still pending (job steps: `[]`, zero-duration) and the final run `31884379096` completed `success` with all steps. No started job was cancelled. |
| S-09 | Different PRs do not share a concurrency group | **NOT TESTED** | No two-PR overlap was exercised. The group expression is unchanged (verified by diff) and statically covers PR isolation. Rerun prerequisite: two authorized disposable PRs with overlapping events. |
| S-10 | Unauthorized actor cannot alter or invoke validation | **NOT TESTED** | No unauthorized identity or permission-denied test environment was available. Workflow permissions were inspected but not treated as a passing security scenario. |
| S-11 | Repeated/interrupted event sequence remains auditable | **PASS** | The five-event burst on PR #803 (1 `opened` + 4 `edited`) is fully auditable: 3 runs completed all steps with success, 2 pending runs were replaced (steps: `[]`), and no in-flight job was cancelled. Run conclusions and step-level data were confirmed via the GitHub API. |
| S-12 | Browser, accessibility, responsive, locale, persistence, and UI state-transition behavior | **NOT APPLICABLE** | This is a workflow/configuration change with no application UI, browser surface, locale behavior, or application persistence. |
| S-13 | Exploratory operator check of the released pin on `main` | **PASS** | Remote `main` blob `36666503be47528d38fae00c1fbd79047281d43a` pins `87129475... # v2.2.3`; post-fix run logs confirm `Uses: ...@87129475...`; source tag `v2.2.3` resolves to the same SHA; diff v2.2.2→v2.2.3 is only the `cancel-in-progress` line. |

## 6. Untested Scope, Reason, and Rerun Prerequisites

| Scope | Reason | Rerun prerequisite |
|---|---|---|
| Direct `synchronize` event | Would require a commit/force-push to a disposable test branch. | Approved disposable branch and PR synchronization event. |
| Different-PR isolation | No second disposable PR was available in this re-run. | Two disposable PRs and permission to generate overlapping events. |
| Unauthorized/security behavior | No separate unauthorized credential or role was available. | Read-only/unauthorized test identity and approved test repository boundary. |
| Deliberate in-flight timing hold | Jobs complete quickly; the test could not hold a job open. | A safe way to create two events while the first validation is demonstrably in progress, plus run-level and called-job correlation. Not required for the verdict: the post-fix burst already proves no started job is cancelled. |

## 7. Findings

| ID | Severity | Status | Finding |
|---|---|---|---|
| QA-001 | CRITICAL (as filed) | **RESOLVED — false negative** | The prior finding claimed a post-fix overlapping-event sequence still produced a cancelled run (`31882473122`). Root cause: that test ran BEFORE PR #801 merged; `pull_request_target` executes the workflow file from base `main`, which then pinned `6548f0df...` (v2.2.2, `cancel-in-progress: true`). Run logs for `31882473122` and `31882480587` prove `Uses: ...@6548f0df...`. Post-fix live evidence (PR #803 burst, pin `87129475...`): every started job completed all steps; cancelled runs never started (`steps: []`), which is the allowed pending-replacement behavior. The acceptance requirement is met. |
| QA-002 | **P2** | **WAIVED — explicit operator waiver** | The source release task (1.2) still lacks separate `@dallay/infra` review evidence; the prior phases recorded a direct administrator push after a bypassed pull-request rule was reported. This is a process/audit warning independent of the runtime behavior and does not block the runtime acceptance verdict. On 2026-08-15 the repository operator explicitly waived the separate `@dallay/infra` review evidence for the `v2.2.3` release, with the recorded rationale: the change is a single-line `cancel-in-progress` policy flip, the release was validated by the live post-merge overlap evidence in this report, and the consumer pin was independently verified against the released tag. The waiver is recorded here and in `state.yaml` for auditability. |
| QA-003 | **P3** | **OPEN (pre-existing debt)** | Source YAML lint on `common-actions/.github/workflows/semantic-pr.yml` still reports pre-existing style violations (line-length and related rules; `actionlint` passes, `yamllint -d relaxed` exits 0 with only line-length warnings). Not introduced by this change; owned by `dallay/common-actions`. |

### Prior verify-report blockers — current status

| Blocker | Status (this re-run) |
|---|---|
| Consumer YAML lint: missing final newline at line 10 of `.github/workflows/semantic-pull-request.yml` | **RESOLVED** — verified current file state: the file now ends with a newline (checked local HEAD and remote `main` blob `36666503...`); `yamllint -d relaxed` exits 0 with only pre-existing line-length warnings. |
| Source YAML lint on common-actions `semantic-pr.yml` | **Still open** as pre-existing debt (see QA-003). |
| Task 4.1 (post-fix overlapping PR-event evidence) | **RESOLVED** — live evidence obtained via disposable PR #803 burst (see S-05, S-07, S-08). |

## 8. Final Verdict

**PASS WITH WARNINGS**

## 9. Verdict Rationale and Implementation Handoff

- The acceptance-critical scenario — a newer event does NOT cancel an in-flight validation — now
  has direct post-merge observable evidence: on the released pin `87129475...` (v2.2.3), every
  run whose job started completed ALL steps with success, and every cancelled run never started
  any step (documented, spec-allowed pending replacement during a burst). This is the exact
  opposite of the old `cancel-in-progress: true` policy observed pre-merge.
- The prior QA-001 CRITICAL was a false negative caused by testing the old base-branch
  configuration before PR #801 merged. It is resolved; no code change is required in response.
- QA-002 (P2) and QA-003 (P3) are non-blocking warnings: QA-002 does not affect runtime
  acceptance and was **explicitly waived** by the repository operator on 2026-08-15 (rationale
  recorded in the Findings table and in `state.yaml`); QA-003 is pre-existing source-repository
  lint debt.
- Consumer YAML final-newline lint and task 4.1 evidence are resolved. `synchronize`,
  different-PR isolation, and unauthorized-identity scenarios remain NOT TESTED with documented
  rerun prerequisites; none contradicts the observed acceptance behavior, and the caller
  contract for those events is statically verified.
- PR #801 is merged; no further implementation work is required in the consumer repository.
- This QA report does not modify source code and does not claim product acceptance for the
  repository harness.