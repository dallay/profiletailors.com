# Proposal: Fix Semantic PR Cancellation at Source

## Intent

Prevent valid Semantic PR validations from appearing as cancelled when repeated PR events overlap. The caller cannot override the reusable workflow's internal concurrency policy, so option B fixes the shared workflow and rolls the immutable revision into this repository.

## Scope

### In Scope
- Change `dallay/common-actions` Semantic PR concurrency to retain PR-scoped serialization while setting `cancel-in-progress: false`.
- Publish and verify a new immutable shared-workflow revision/tag.
- Update `.github/workflows/semantic-pull-request.yml` to pin that revision by SHA.
- Verify concurrent PR events no longer cancel an already-running Semantic PR validation.

### Out of Scope
- Replacing the reusable workflow with duplicated caller-side jobs.
- Changing cancellation policies for CI, quality-gate, security, or other replaceable workflows.
- Retrofitting historical cancelled runs or changing GitHub check-rollup behavior.

## Capabilities

### New Capabilities
- `semantic-pr-validation`: PR title validation is serialized per pull request without cancelling an in-flight validation when a newer event arrives.

### Modified Capabilities
- None.

## Approach

Update the external reusable workflow's concurrency declaration, preserve its existing PR/workflow grouping, and release it through the repository's normal semantic-release process. After the source revision is available, update the local caller's SHA pin and validate YAML, immutable pinning, and a real PR event sequence. Document that `cancel-in-progress: false` prevents cancellation of the running job but GitHub may replace an older pending run during bursts.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `dallay/common-actions/.github/workflows/semantic-pr.yml` | Modified | Source concurrency policy. |
| `dallay/common-actions` release metadata | New | Publish the source fix and immutable revision. |
| `.github/workflows/semantic-pull-request.yml` | Modified | Consume the released SHA. |
| `openspec/specs/semantic-pr-validation/spec.md` | New | Durable validation and concurrency contract. |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| External release or SHA rollout is unavailable | Med | Keep the consumer update gated on source review, release, and syntax verification. |
| Queued validations increase feedback time during bursts | Med | Retain PR-scoped grouping and monitor the first rollout. |

## Rollback Plan

Revert the caller to the previous known-good SHA. If the source release causes broader regressions, revert its source change through `dallay/common-actions` governance and repin consumers as needed.

## Dependencies

- Review and release access to `dallay/common-actions`.
- A released immutable SHA before updating this repository.

## Success Criteria

- [ ] The source workflow no longer cancels an in-flight Semantic PR run for the same PR group.
- [ ] The caller pins the released reusable workflow by immutable SHA.
- [ ] YAML/reusable-workflow checks pass and a real repeated-event validation completes without a cancellation caused by concurrency.
