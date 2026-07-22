## Exploration: GitHub issue #225 — publication edit not found returns 500 vs 404

### Current State

`PublishingPublicationController.editPublication()` is a thin mediator pass-through for
`PATCH /api/publishing/publications/{publicationId}`. The actual lookup happens in
`EditPublicationHandler`, which requires the active workspace context and calls
`publicationRepository.findByWorkspaceAndId(workspaceId, publicationId)`. Both the in-memory test
repository and the R2DBC implementation scope that lookup by `workspace_id`, so a publication that
exists in another workspace is intentionally treated as not found in the current workspace. When the
lookup misses, the handler throws `PublicationNotFoundException`. The verified gap is HTTP mapping:
`PublishingProblemDetailsHandler` maps publication state conflicts, provider config, OAuth, media
availability, and asset readiness, but does not map `PublicationNotFoundException`, so the exception
currently falls through as an unhandled server error instead of a 404 contract.

### Affected Areas

- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingHandlers.kt` —
  edit/cancel/delete/retry/reschedule all throw `PublicationNotFoundException` after
  workspace-scoped lookup misses.
-

`server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingProblemDetailsHandler.kt` —
missing exception-to-404 mapping is the direct HTTP contract gap.

-

`server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingControllers.kt` —
PATCH endpoint is update-only and does not implement any create-on-save fallback.

-

`server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/R2dbcPublishingRepositories.kt` —
repository lookup explicitly filters by both `workspace_id` and publication id.

-

`server/smp/src/test/kotlin/com/profiletailors/smp/publishing/application/PublishingHandlersTest.kt` —
already verifies handler-level not-found behavior for edit and sibling commands.

-

`server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingProblemDetailsHandlerTest.kt` —
has no coverage yet for publication-not-found mapping.

-

`server/smp/src/test/kotlin/com/profiletailors/smp/publishing/integration/PublishingWorkspaceIsolationIntegrationTest.kt` —
verifies cross-workspace reads return null, confirming workspace isolation is intentional.

- `openspec/specs/publishing/spec.md` — already requires update-only writes to reject missing
  current-workspace targets as not found.

### Approaches

1. **Add publishing 404 problem-details mapping** — Map `PublicationNotFoundException` to HTTP 404
   in `PublishingProblemDetailsHandler` and add regression tests.
    - Pros: Fixes the verified bug at the HTTP boundary; aligns controller behavior with existing
      handler/repository semantics; automatically covers edit plus delete/cancel/retry/reschedule
      because they share the same exception.
    - Cons: Does not address any future ambiguity if product wants create-on-save behavior for a
      different flow.
    - Effort: Low

2. **Refactor application/controller contracts around typed results** — Replace exception-driven
   not-found flow with explicit result types or controller-local translation.
    - Pros: Makes API contracts more explicit; could reduce reliance on broad exception advice over
      time.
    - Cons: Much larger blast radius; unnecessary for the verified failure; risks changing
      established mediator and problem-details patterns.
    - Effort: Medium

### Recommendation

Use Approach 1. The codebase already proves the miss is workspace-scoped by design, not a
persistence corruption issue or wrong controller contract. The actual defect is that the publishing
HTTP layer never translates `PublicationNotFoundException` into a 404 `ProblemDetail`. Keep the
repository and handler semantics intact, add the missing mapping, and add focused tests for both the
handler advice and at least one update-only publication mutation path.

### Risks

- The same exception is reused by edit, cancel, delete, retry, and reschedule, so the new 404
  mapping changes all of those HTTP contracts at once; that is probably correct, but it should be
  called out explicitly.
- The publishing spec contains broader wording about create/save flows versus update-only flows;
  implementation work should avoid accidentally introducing create-on-save behavior into
  authenticated PATCH edit.

### Ready for Proposal

Yes — the orchestrator should tell the user the issue was independently verified as a real HTTP
contract bug caused by missing exception mapping, while workspace mismatch behavior is already
intentionally enforced by repository scoping rather than being the underlying defect.
