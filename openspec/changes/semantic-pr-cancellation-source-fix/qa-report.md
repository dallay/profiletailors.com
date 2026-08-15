# Acceptance QA Report: semantic-pr-cancellation-source-fix

## 1. Identity

| Field | Value |
|---|---|
| Change | `semantic-pr-cancellation-source-fix` |
| Mode | OpenSpec capability-driven acceptance QA |
| Phase | `qa` |
| Date | 2026-08-15 |
| Consumer target | `/Users/acosta/Dev/dallay/worktrees/actions-fix` (`profiletailors.com`) |
| Shared-workflow source | `/Users/acosta/Dev/dallay/common-actions` (`dallay/common-actions`) |

This report records acceptance evidence only. It does not claim product acceptance for the
workflow harness or convert static inspection into a passing acceptance result.

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

No delta-spec directory was present at
`openspec/changes/semantic-pr-cancellation-source-fix/specs/`; the applicable contract available
for QA was `openspec/specs/semantic-pr-validation/spec.md`.

### Technical verification handoff

The preceding verification reports the source release `v2.2.3` at
`87129475c06d9f3354d398426926ba4345a4644d`, source workflow tests, `actionlint`, focused caller
contract checks, immutable SHA resolution, and diff/YAML checks as successful. It also explicitly
leaves task 4.1 incomplete: no post-fix overlapping `opened`/`edited`/`synchronize` PR event was
exercised. The verification report also records that separate `@dallay/infra` review evidence was
not available.

## 3. Target, Environment, Permissions, and Limitations

- Read-only GitHub CLI/API access was available and authenticated as `yacosta738`.
- The local consumer worktree contains the caller pin change, but it is not pushed: the worktree
  is on branch `actions-fix` with no upstream and has a modified
  `.github/workflows/semantic-pull-request.yml`.
- The remote consumer workflow currently visible from `main` still resolves the reusable workflow
  at the historical `v2.2.2` SHA `6548f0dfaad9e40648cd56e85566e5bc2d707dfb`, not the local `v2.2.3`
  pin.
- The source release is available remotely and its release workflow run `31879480716` succeeded,
  but that does not exercise the consumer's PR-event behavior.
- No PR number, disposable PR, pushed consumer branch, or other authorized event target was
  supplied for generating a real post-fix PR event sequence. Creating or mutating a PR solely to
  manufacture evidence would change external repository state and was not performed.
- This repository has no application under test and no general runtime test runner for this
  workflow-only change. Browser, responsive, accessibility, locale, persistence, and API scenarios
  do not apply to the changed surface.

## 4. Capability Inventory

| Capability | Status | Rationale / selection decision |
|---|---|---|
| GitHub Actions run inspection | selected | Narrowest available capability for observing PR validation outcomes and cancellation state; used read-only. |
| GitHub REST/CLI repository and release inspection | selected | Used to establish the deployed consumer pin, source release, and available historical runs. |
| Authorized live PR-event exercise | available but blocked | The platform can execute it, but no pushed consumer target or authorized PR event target was supplied. |
| Local shell/YAML/static inspection | rejected for acceptance | Static evidence belongs to technical verification and cannot yield a QA `PASS`. |
| Browser / Playwright / Chrome DevTools | unavailable / not applicable | The target is a GitHub Actions workflow, not a browser application. |
| API/client behavior | unavailable / not applicable | No product API is changed or exposed by this workflow-only change. |
| Data/persistence verification | unavailable / not applicable | No application data store or persistence behavior is in scope. |
| Accessibility verification | unavailable / not applicable | No user interface is changed. |
| Responsive verification | unavailable / not applicable | No visual surface is changed. |
| Locale/internationalization verification | unavailable / not applicable | No localized product surface or copy is changed. |
| Manual exploratory workflow exercise | available but blocked | Requires an authorized PR event target and a consumer revision that is actually available to GitHub Actions. |

## 5. Scenario Matrix

Every applicable acceptance scenario was attempted as far as the available target allowed. Static
workflow evidence is recorded as context only and is not treated as a scenario pass.

| Scenario | Result | Evidence / reason |
|---|---|---|
| Valid PR title on `opened` produces a passing Semantic PR check | BLOCKED | No post-fix PR event target was supplied. The remote consumer `main` workflow still points to `v2.2.2`; the local `v2.2.3` pin is unpushed. |
| Valid PR title on `edited` produces a passing Semantic PR check | BLOCKED | Same target/deployment constraint; no live post-fix `edited` event was exercised. |
| Valid PR title on `synchronize` produces a passing Semantic PR check | BLOCKED | Same target/deployment constraint; no live post-fix `synchronize` event was exercised. |
| Invalid PR title produces a failing Semantic PR check with the validation outcome | BLOCKED | No authorized PR target was available for a controlled invalid-title event. |
| A newer event for the same PR does not cancel an active validation | BLOCKED | This is the core acceptance scenario, but no post-fix overlapping event sequence was exercised. Historical run `31873560970` was cancelled before the fix and before the consumer pin update, so it is not post-fix evidence. |
| A newer same-PR validation waits or remains pending after the active run | NOT TESTED | Requires the same live overlap sequence; no target was available. |
| Different PRs remain isolated in different concurrency groups | NOT TESTED | No controlled concurrent PR pair was available; static group inspection is not acceptance evidence. |
| Repeated burst of `opened`/`edited`/`synchronize` events does not cancel active work | BLOCKED | Requires a pushed consumer revision and an authorized repeated-event sequence. |
| Unauthorized or permission-sensitive execution behaves safely | NOT TESTED | No permission-matrix target or isolated test repository was supplied; no claims are made from static `pull_request_target` inspection. |

## 6. Untested Scope, Reason, and Rerun Prerequisite

### Acceptance scope not completed

1. **Post-fix overlap behavior** — blocked because the consumer caller pin is only local and no
   authorized PR/event target was supplied. This prevents validating the observable invariant that
   an active Semantic PR validation is not cancelled by a newer same-PR event.
2. **Valid and invalid title outcomes on the new revision** — blocked for the same reason; source
   unit checks are technical verification, not consumer acceptance evidence.
3. **Different-PR isolation and burst/pending behavior** — not tested because no controlled event
   matrix exists.
4. **Permission-sensitive behavior** — not tested because there is no isolated target with an
   approved permission matrix.

### Rerun prerequisite

Publish the consumer caller change to an authorized branch/PR where
`pull_request_target` resolves the `v2.2.3` SHA, or provide an equivalent disposable repository
and permissions. Then capture run URLs and timestamps for:

- one valid title on each applicable event type;
- one invalid title;
- two rapid same-PR events while the first validation is active, showing the first run is not
  cancelled and the later run is queued/pending or completes according to GitHub's concurrency
  behavior;
- a separate-PR control to show group isolation.

Do not use a historical cancelled run as post-fix evidence.

### Explicitly non-applicable scope

Browser, responsive, accessibility, locale, persistence, and product API checks are not applicable
to a reusable GitHub Actions workflow and therefore require no rerun.

## 7. Findings

| ID | Severity | Status | Finding |
|---|---|---|---|
| QA-001 | P1 | OPEN / blocking | The core acceptance scenario—overlapping same-PR events with the active validation preserved—has no post-fix evidence. The only observed cancellation, run `31873560970`, predates the source fix and consumer pin. |
| QA-002 | P1 | OPEN / blocking | The consumer revision under QA is not available on the remote target: local caller pin is `87129475c06d9f3354d398426926ba4345a4644d`, while remote `main` still advertises `6548f0dfaad9e40648cd56e85566e5bc2d707dfb`. |
| QA-003 | P2 | OPEN / warning | The verification handoff records no separate `@dallay/infra` review evidence for the direct source release. This is a governance warning, not an observed runtime failure. |

No `CRITICAL` or `P0` finding was created. The P1 findings remain acceptance-blocking because the
requested behavior cannot be observed on the revised consumer target.

## 8. Final Verdict

**BLOCKED**

## 9. Verdict Rationale and Implementation Handoff

The source workflow release and static integration evidence are useful technical handoff, but QA
cannot claim `PASS` from static inspection. The revised caller is unpushed, the remote consumer
still exposes the old SHA, and no authorized PR event sequence was available to demonstrate the
observable cancellation fix. Archive should wait for the post-fix event evidence and resolution or
explicit disposition of the recorded P1 blockers.

Implementation handoff: keep the source policy and immutable pin unchanged while preparing an
authorized acceptance target. After the consumer revision is available to GitHub Actions, rerun the
specific overlap matrix above and attach the run URLs, run conclusions, and cancellation/queue
observations to this report. QA did not modify source code or repair any findings.
