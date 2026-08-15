## Exploration: Semantic PR cancellation source fix

### Current State
The repository's `Semantic PR` caller is `.github/workflows/semantic-pull-request.yml`. It runs on `pull_request_target` for `opened`, `edited`, and `synchronize`, then delegates all work to `dallay/common-actions/.github/workflows/semantic-pr.yml` pinned to `v2.2.2` (`6548f0dfaad9e40648cd56e85566e5bc2d707dfb`). The caller has no local job-level concurrency configuration and cannot override concurrency declared inside the reusable workflow.

The pinned reusable workflow defines a concurrency group keyed by workflow name and PR number/ref, with `cancel-in-progress: true`. GitHub allows only one active run per group and cancels a currently running run when a newer event enters the same group. That matches the observed PR #787 evidence: two nearly simultaneous runs for the same SHA were created after repeated force-push events; the earlier run was cancelled and a later run succeeded. The validation itself did not fail. The current `main` branch of `dallay/common-actions` still contains the same `cancel-in-progress: true`, so merely changing this caller's pin cannot fix the source behavior unless a new shared-workflow revision is published.

The source repository is an organization-level reusable-workflow library. Its `CODEOWNERS` assigns `.github/workflows/` to `@dallay/infra`, and releases are semantic-release tags. The source workflow currently has no input for concurrency policy. Other caller workflows in this repository intentionally use cancellation for replaceable work, but Semantic PR is a validation check whose historical cancelled run can remain visible in a PR rollup even when a later run succeeds.

### Affected Areas
- `.github/workflows/semantic-pull-request.yml` — update the pinned reusable-workflow revision after the shared source fix is merged/tagged; this is the consumer-side rollout.
- `dallay/common-actions/.github/workflows/semantic-pr.yml` (external repository) — change the source concurrency policy so a newer PR-title validation run does not cancel an already-running validation for the same PR.
- `dallay/common-actions` release/tag metadata and workflow checks (external repository) — publish an immutable revision/tag and verify the reusable workflow syntax before consuming it.
- `.github/workflows/ci.yml`, `.github/workflows/quality-gate.yml`, `.github/workflows/security-pr.yml` — reference points showing that cancellation is used selectively elsewhere; they should not be changed as part of this focused fix.
- `openspec/changes/semantic-pr-cancellation-source-fix/exploration.md` — this exploration artifact.

### Approaches
1. **Source-level non-cancelling Semantic PR concurrency (approved option B)** — update `dallay/common-actions` so the Semantic PR reusable workflow keeps PR-scoped serialization but sets `cancel-in-progress: false` (or omits it, using the documented default), then publish a new immutable revision and update this repository's caller pin.
   - Pros: fixes the behavior at the shared source for every consumer; preserves one active validation per PR; avoids false historical cancellation; keeps the caller minimal and auditable.
   - Cons: requires a change and release in a second repository; queued runs can increase CI time during bursts; the shared workflow's source tests/review must be available.
   - Effort: Medium

2. **Caller-side local workflow replacement** — stop using the reusable workflow in this repository and duplicate the validation/comment steps locally with `cancel-in-progress: false`.
   - Pros: can be rolled out in one repository without waiting for a shared release; behavior is locally visible.
   - Cons: duplicates shared policy and action pins; drifts from all other consumers; contradicts the stated goal of fixing the reusable source; increases maintenance and review surface.
   - Effort: Medium

3. **Caller-side symptom suppression or group manipulation** — try to alter the caller's group/name or add a caller concurrency rule while leaving the reusable workflow unchanged.
   - Pros: small apparent diff.
   - Cons: cannot override the called workflow's internal concurrency; may only move or hide the collision; does not address the source race and is not a reliable fix.
   - Effort: Low, but technically ineffective

### Recommendation
Proceed with approved option B. In `dallay/common-actions`, retain the PR-number concurrency group but disable cancellation for the Semantic PR reusable workflow. The intent is serialization without terminating an in-flight validation: a second event should wait and then validate the latest event rather than leaving an explicit `cancelled` check from the previous run. Publish a new immutable tag/revision from the shared repository, then update `.github/workflows/semantic-pull-request.yml` to that exact SHA with the matching version comment. Validate the source workflow and the consumer diff; use a follow-up live PR/event to confirm that repeated `synchronize` activity no longer produces a cancelled Semantic PR run.

Do not modify the other repository workflows' concurrency policies. Their cancellation behavior is unrelated replaceable-work policy, while this change is specifically about the reusable Semantic PR validation contract.

### Risks
- The current worktree is `profiletailors.com`; the shared source repository is external and is not checked out here, so source implementation, source tests, release/tag creation, and remote verification cannot be completed from this worktree alone.
- Setting `cancel-in-progress: false` prevents active cancellation but still permits GitHub's default pending-run replacement behavior: if more than one run is queued in the same group, an older pending run may be removed. This is materially better than cancelling an in-flight check, but should be documented and tested against the desired latest-event semantics.
- Repeated force-pushes may queue several validations and temporarily delay the newest check; the PR-scoped group intentionally trades cancellation for reliable check history.
- The caller's `pull_request_target` and write permission are security-sensitive; keep the existing trusted reusable workflow and do not switch to executing untrusted PR code.
- A source release must be pinned by commit SHA, not a mutable branch or floating tag, to preserve the repository's action supply-chain convention.

### Ready for Proposal
Yes — the problem, approved direction, source/consumer boundaries, and rollout are clear. The proposal should explicitly treat this as a cross-repository CI contract change, require a new immutable common-actions revision, update the caller pin, and include source-level and consumer-level verification plus a live concurrency acceptance check.
