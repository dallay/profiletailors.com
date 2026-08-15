# Design: Semantic PR Cancellation Source Fix

## Technical Approach

Fix the policy at its owner in `dallay/common-actions`, not in the consumer repository. Keep the reusable Semantic PR workflow's existing PR-scoped concurrency group and change only `cancel-in-progress` from `true` to `false`. Publish that source change through the shared repository's normal semantic-release process, then update this repository's caller to the resulting immutable commit SHA. The caller remains a thin `pull_request_target` adapter and does not duplicate validation jobs or add a competing concurrency rule.

## Architecture Decisions

| Decision | Choice | Alternatives / rationale |
|---|---|---|
| Concurrency ownership | Keep the policy in `dallay/common-actions/.github/workflows/semantic-pr.yml`. | A caller-level concurrency block cannot override a called workflow's internal policy. Duplicating the job would create drift and weaken the shared action boundary. |
| Cancellation behavior | Preserve the existing group expression and set `cancel-in-progress: false`. | Removing concurrency allows overlapping validations; retaining the group preserves PR-scoped serialization while allowing an already-running validation to finish. GitHub may still replace an older pending run during a burst. |
| Consumer pinning | Pin the released workflow by commit SHA and retain the release tag in a comment. | A mutable tag is not reproducible or auditable. |
| Scope | Change Semantic PR only. | CI, quality-gate, security, labels, and other workflows have independent policies for replaceable work and are out of scope. |

## Data Flow

```text
PR opened/edited/synchronized
        -> .github/workflows/semantic-pull-request.yml
        -> common-actions semantic-pr workflow at immutable SHA
        -> existing PR-scoped concurrency group
        -> title validation and sticky PR status/comment
```

For overlapping events, a currently running validation is allowed to complete. A newer event can wait as the group's pending run; if more events arrive before it starts, GitHub can replace the pending run. This removes the observed false cancellation of an active validation without promising that every intermediate event gets its own execution.

## File Changes

| File | Action | Description |
|---|---|---|
| `dallay/common-actions/.github/workflows/semantic-pr.yml` (external repository) | Modify | Keep the current `concurrency.group`; set `cancel-in-progress: false`. |
| `dallay/common-actions` release metadata/tag | Create | Publish the source change using the shared repository's semantic-release process and record its commit SHA. |
| `.github/workflows/semantic-pull-request.yml` | Modify | Replace the `v2.2.2` SHA with the released source SHA; update the human-readable version comment. |
| `openspec/changes/semantic-pr-cancellation-source-fix/design.md` | Modify | Record this implementation design and verification contract. |

No application, backend, database, or product documentation changes are required because this is CI workflow policy only.

## Interfaces / Contracts

The source workflow's concurrency contract is the existing group expression plus:

```yaml
concurrency:
  group: <existing Semantic PR workflow/PR group expression>
  cancel-in-progress: false
```

The consumer contract remains a reusable-workflow call pinned as:

```yaml
uses: dallay/common-actions/.github/workflows/semantic-pr.yml@<immutable-commit-sha> # <release>
```

## Testing Strategy

| Layer | What to Test | Approach |
|---|---|---|
| Static workflow | YAML syntax, reusable-workflow reference, SHA pin, and unchanged event/permission contract | Run the repository's applicable workflow/security validation and inspect the exact diff. |
| Source policy | `cancel-in-progress: false` and unchanged group semantics | Verify the released `common-actions` revision before changing the caller pin. |
| Integration / remote | Repeated PR events do not cancel an active Semantic PR run | Trigger or observe a real PR sequence with overlapping `synchronize` events; verify the earlier active run completes and the final run reports validation success. |

No unit-test changes are expected for declarative workflow configuration.

## Migration / Rollout

No data migration or feature flag is required. Roll out in dependency order: release `common-actions`, update the SHA pin, validate the caller, then observe the next repeated-event PR. Rollback is the previous `6548f0dfaad9e40648cd56e85566e5bc2d707dfb` pin, which restores the prior cancellation policy.

## Open Questions

- [ ] What release tag and commit SHA will `dallay/common-actions` publish for the source change?
- [ ] Is a real overlapping-event PR available after release for end-to-end confirmation?
