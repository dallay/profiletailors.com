# Tasks: Fix Semantic PR Cancellation at Source

## Review Workload Forecast

| Field | Value |
|---|---|
| Estimated changed lines | 10–40 across the shared workflow and caller |
| 400-line budget risk | Low |
| Chained PRs recommended | No |
| Suggested split | Single PR: source release, SHA repin, and verification |
| Delivery strategy | ask-on-risk |
| Chain strategy | single-pr |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: single-pr
400-line budget risk: Low

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|---|---|---|---|
| 1 | Release the source policy fix and consume it immutably | PR 1 | Base `main`; source repository release must precede the caller pin; include static and live verification evidence |

## Phase 1: Shared Workflow Foundation

- [x] 1.1 In `dallay/common-actions/.github/workflows/semantic-pr.yml`, preserve the existing PR-scoped `concurrency.group` and set `cancel-in-progress: false`; add or update the source workflow validation needed to reject cancellation regressions.
- [x] 1.2 Run the shared repository’s workflow/YAML checks, obtain required `@dallay/infra` review, and release the source change through its semantic-release process; record the released tag and immutable commit SHA. Released `v2.2.3` at `87129475c06d9f3354d398426926ba4345a4644d`; source workflow test passed; `actionlint` passed for the source workflow; GitHub Release run `31879480716` passed. The release was pushed directly by the repository administrator because branch protection reported no protection while the remote push reported a bypassed pull-request rule; no separate `@dallay/infra` review evidence was available.

## Phase 2: Consumer Integration

- [x] 2.1 In `.github/workflows/semantic-pull-request.yml`, replace the `v2.2.2` SHA with the released source commit SHA and retain the release tag only as a human-readable comment. Do not change the caller before a real released SHA containing the fix exists. Pinned `87129475c06d9f3354d398426926ba4345a4644d` with human-readable comment `v2.2.3`.
- [x] 2.2 Confirm the caller still listens only to `pull_request_target` events `opened`, `edited`, and `synchronize`, preserves its permissions, and adds no caller-side concurrency or duplicated validation job. Focused contract test passed; `actionlint` passed.

## Phase 3: Static and Contract Verification

- [x] 3.1 Validate the source and caller YAML/workflow contracts, including the preserved concurrency group, `cancel-in-progress: false`, immutable 40-character SHA pin, event types, permissions, and absence of a duplicate caller job. Source and caller focused tests passed; `actionlint` passed for both workflows; `yamllint` passed with existing line-length warnings only.
- [x] 3.2 Confirm the pinned SHA resolves to the released source revision and that the fetched source workflow contains the corrected cancellation policy; record the command output in apply/verification evidence. `v2.2.3` resolves to `87129475c06d9f3354d398426926ba4345a4644d`; fetched source contains the unchanged group and `cancel-in-progress: false`.

## Phase 4: Remote Event Verification and Rollback Readiness

- [ ] 4.1 Exercise or observe overlapping `opened`/`edited`/`synchronize` events on a safe PR and verify that an active Semantic PR validation completes rather than being concurrency-cancelled; record the limitation that pending runs may still be replaced by GitHub.
- [x] 4.2 Run the focused repository checks, inspect the final diff, and document rollback to the previous known-good SHA `6548f0dfaad9e40648cd56e85566e5bc2d707dfb` if the new shared revision misbehaves. Focused source and caller contract tests, `actionlint`, `yamllint`, and `git diff --check` completed; remote event verification remains for QA/operator evidence. Rollback is a one-line repin to the previous SHA.

## External Dependency / Blocker

Resolved: `dallay/common-actions` released `v2.2.3` at `87129475c06d9f3354d398426926ba4345a4644d`, and the consumer now pins that immutable revision. Remote overlapping-event verification remains intentionally pending for `sdd-verify`/`sdd-qa`.
