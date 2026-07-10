# Design: Publication Edit Not-Found Contract

## Technical Approach

Fix the contract at the publishing HTTP boundary, not in application flow. `EditPublicationHandler` and sibling mutation handlers already enforce workspace-scoped lookup via `publicationRepository.findByWorkspaceAndId(...)` and throw `PublicationNotFoundException` on misses. The design adds a narrow `@ExceptionHandler` in `PublishingProblemDetailsHandler` so update-only publication mutations return HTTP 404 `ProblemDetail` instead of falling through to 500. This preserves existing mediator, repository, and lifecycle behavior while aligning with the publishing spec’s update-only semantics.

## Architecture Decisions

| Decision | Choice | Alternatives considered | Rationale |
|---|---|---|---|
| Not-found translation layer | Map `PublicationNotFoundException` in `PublishingProblemDetailsHandler` | Translate in each controller; replace exceptions with typed results | Existing modules already centralize HTTP error mapping in `*ProblemDetailsHandler`; advice-level mapping fixes all shared endpoints with the smallest blast radius. |
| Scope of behavior change | Limit change to `PublicationNotFoundException` only | Broaden to `IllegalArgumentException`/generic 404 handling | Publishing already uses `IllegalArgumentException` for validation; broad mapping would risk converting real 400/500 paths into false 404s. |
| Payload shape | Reuse Spring `ProblemDetail` with publishing-specific title/detail, aligned with other handlers | Introduce custom DTO or new global error envelope | Keeps contract consistent with existing platform/tenancy/media error responses and avoids cross-context API churn. |

## Data Flow

`PATCH|DELETE|POST /cancel|POST /retry|PATCH /reschedule`
  → `PublishingPublicationController`
  → mediator command
  → handler (`Edit/Cancel/Delete/Retry/ReschedulePublicationHandler`)
  → `publicationRepository.findByWorkspaceAndId(workspaceId, publicationId)`
  → miss in active workspace
  → `PublicationNotFoundException`
  → `PublishingProblemDetailsHandler`
  → `404 ProblemDetail`

The repository remains the workspace-isolation guardrail. The HTTP layer only translates the already-intended application outcome.

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingProblemDetailsHandler.kt` | Modify | Add explicit `PublicationNotFoundException` → 404 mapping with publishing-specific title/detail. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingProblemDetailsHandlerTest.kt` | Modify | Add regression test for 404 mapping and payload shape. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingPublicationControllerTest.kt` | Modify/Create | Add focused HTTP test proving an update-only endpoint returns 404 when mediator/handler surfaces `PublicationNotFoundException`. |
| `openspec/changes/2026-07-01-publication-edit-not-found-contract/design.md` | Create | Records technical approach and guardrails. |
| `openspec/changes/2026-07-01-publication-edit-not-found-contract/state.yaml` | Modify | Mark design completed and keep next phase on spec/tasks as appropriate. |

## Interfaces / Contracts

```kotlin
@ExceptionHandler(PublicationNotFoundException::class)
fun handle(exception: PublicationNotFoundException): ProblemDetail =
    ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.message ?: "Publication not found").apply {
        title = "Publication not found"
    }
```

Contract notes:
- Applies to update-only publication mutations that already throw `PublicationNotFoundException`.
- Shared endpoint impact: edit, delete, cancel, retry, and reschedule all inherit the 404 contract because they share the same exception path.
- No change to create/save semantics; `POST /api/publishing/publications` remains separate.

## Testing Strategy

| Layer | What to Test | Approach |
|-------|-------------|----------|
| Unit | Advice maps `PublicationNotFoundException` to 404 with expected title/detail | Extend `PublishingProblemDetailsHandlerTest` with direct handler assertion. |
| Integration/Web | `PATCH /api/publishing/publications/{id}` returns 404 problem details on not-found | Focused controller/WebFlux test using publishing advice, without broad end-to-end rebuild. |
| Regression | Shared endpoint coverage remains intentional | Optionally one extra HTTP test for a sibling mutation or rely on handler tests proving shared exception source. |

## Migration / Rollout

No migration required. This is a contract correction at the HTTP adapter layer.

## Open Questions

- [ ] Should the 404 payload also include a publishing `errorCode`, or should publishing stay minimal and match tenancy’s plain not-found shape for now?
