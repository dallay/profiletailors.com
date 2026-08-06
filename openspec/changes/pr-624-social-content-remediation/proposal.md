# Proposal: PR #624 Social Content Foundation Remediation

## Intent

Remediate valid CodeRabbit findings on PR #624 (`feature/linkedin-pages-01-foundation`) so the LinkedIn page social-content foundation is type-safe, bounded, workspace-safe, and testable without changing PR #625 or `shared/shield/ratelimit`. The change must also close or explicitly answer review comments that are reply-only, stale, or out of scope.

## Scope

### In Scope
- Replace untyped capability failures with typed, operation-specific failures and preserve safe denial behavior.
- Split `SocialContentFoundationHandlers` into dedicated command/query handlers; bound posts/comments pagination, detect repeated cursors, and define checkpoint advancement/resume semantics.
- Specify reply idempotency for existing `SUCCEEDED`, `FAILED`, and `PROCESSING` results; add ByteArray value equality and missing domain invariants.
- Validate `apiVersion` as a real calendar month; enforce workspace-scoped `social_accounts` foreign-key integrity.
- Clean imports/naming/tests, add direct fake-repository tests, and decide/document Cucumber and Liquibase integration-test coverage.
- Require review replies for likely reply-only/outdated comments: missing `architecture-docs-sync.md`, unrelated ratelimit feedback, and already-addressed `mutationAllowed`.

### Out of Scope
- Any change to PR #625, `shared/shield/ratelimit`, or unrelated shared rate-limiting behavior.
- New LinkedIn product capabilities, UI/API expansion, or broad publishing refactors.
- Treating a review reply as code remediation when evidence shows the comment is stale or unrelated.

## Capabilities

### New Capabilities
- None — this is remediation of the PR #624 social-content foundation.

### Modified Capabilities
- `publishing`: typed capability denial, bounded/resumable social-content synchronization, deterministic reply idempotency, domain invariants, and workspace-safe social-content persistence.

## Approach

Start with failing focused domain/application tests, then extract query and command handlers while preserving ports and workspace scope. Introduce explicit max-page and repeated-cursor guards, checkpoint only after successful bounded completion, and document restart behavior. Define reply state transitions before changing repository/handler behavior. Use direct fakes for unit coverage; make an explicit Cucumber decision and run Liquibase integration coverage only if the migration constraint requires a live database. Reply to stale/out-of-scope comments with file/behavior evidence instead of modifying unrelated code.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `server/smp/src/main/kotlin/.../publishing/{domain,application}` | Modified | Typed failures, invariants, handler split, pagination/checkpoints, reply policy. |
| `server/smp/src/main/kotlin/.../publishing/infrastructure/{fake,persistence,socialcontent}` | Modified | Fake seams, FK migration support, month validation. |
| `server/smp/src/test/.../publishing` | Modified | Direct fake tests, domain/application regressions, coverage decisions. |
| `server/smp/src/main/resources/db/changelog/publishing` | Modified | Workspace-scoped social-account FK if required. |
| PR review thread responses | New | Required replies for stale/out-of-scope findings. |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Handler extraction changes wiring or semantics | Med | Preserve ports, add compile/tests first, verify application configuration. |
| Cursor/checkpoint guards skip or duplicate data | High | Define bounds/repeated-cursor failure and checkpoint only on successful completion. |
| FK migration conflicts with existing data | Med | Validate data and use focused Liquibase/integration decision before rollout. |
| Review comments are misclassified | Med | Reply with evidence; do not alter PR #625 or ratelimit. |

## Rollback Plan

Revert the remediation commits and any isolated Liquibase change; restore the original PR #624 handlers, tests, and validation without touching PR #625 or `shared/shield/ratelimit`. If migration data prevents rollback, stop before deployment and use the documented forward-compatible migration path.

## Dependencies

- Existing Kotlin/Gradle, JUnit/Kotest, Cucumber, R2DBC/Testcontainers, Liquibase, and PR #624 review context.

## Success Criteria

- [ ] Every in-scope valid finding has a tested fix or an explicit documented decision.
- [ ] Posts/comments are bounded, repeated cursors are detected, and checkpoints have explicit safe semantics.
- [ ] Reply behavior for `SUCCEEDED`/`FAILED`/`PROCESSING` is deterministic and tested.
- [ ] Focused backend tests and selected integration checks pass without application changes outside scope.
- [ ] Required replies classify `architecture-docs-sync.md`, ratelimit, and `mutationAllowed` comments with evidence.
