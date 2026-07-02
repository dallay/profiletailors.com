# Design: Publication Edit Create-ID Sync

## Technical Approach

Make backend `PublicationResult` the authoritative value for every successful authenticated LinkedIn create. A single mapper will normalize mutation results into the existing `Publication` model, including persisted ID, account, status, schedule mode, assets, and the mode-specific scheduling instant. Synthetic IDs remain only for unauthenticated/local-only paths. Edit initialization will consume normalized fields without changing the #223 untouched/clear/replace asset contract. Backend #224 workspace filtering and #225 update-only 404 behavior remain unchanged and are covered by existing focused tests.

## Architecture Decisions

| Decision | Alternatives considered | Rationale |
|---|---|---|
| Return and map `PublicationMutationResult` from authenticated create calls | Refetch calendar; backend PATCH fallback | The POST response already contains server truth; direct reconciliation is smallest, deterministic, and preserves the update-only contract. |
| Construct synthetic records only after choosing a local path | Optimistic placeholder then replace; keep `pub-${Date.now()}` everywhere | Current UI does not require optimistic insertion. Avoiding placeholders removes identity races while retaining explicit offline behavior. |
| Normalize schedule display in one mutation mapper | Modal-only coercion; duplicate mapping per action | Store records should be valid for every consumer. `SCHEDULED_AT` uses `scheduledFor`; `NEXT_SLOT` uses `nextSlotAfter`; `NOW` uses a safe display instant while preserving `scheduleMode`. |
| Preserve asset tri-state at the edit boundary | Always send backend `assetIds` | `assetsTouched=false` omits `assetIds`; explicit clear sends `[]`; replacement sends selected IDs. Hydration remains `loadAsset` then selection, with missing assets skipped. |

## Data Flow

Before:

    Composer -> store creates pub-${Date.now()} -> POST (response ignored)
                    -> localStorage -> edit -> PATCH synthetic ID -> 404

After:

    Composer -> store selects path
                 | authenticated LinkedIn -> POST -> PublicationResult
                 |                           -> mapper -> store/localStorage
                 ` local/offline ----------> synthetic local Publication
                                              |
                                              -> edit mode -> PATCH persisted ID

Create failures on authenticated backend-backed paths propagate and do not insert a local placeholder. Existing unauthenticated/local-only behavior persists. PATCH errors, including 404, continue to surface through the modal; no create-on-edit recovery is introduced.

## File Changes

| File | Action | Description |
|---|---|---|
| `apps/web/app/src/stores/publishing.ts` | Modify | Make `syncPublicationWithApi` return `PublicationMutationResult`; map `publicationId`, status/account/assets and mode-specific time; reconcile `schedulePost` and `quickCreatePost`; restrict synthetic IDs to local fallback. |
| `apps/web/app/src/stores/publishing.test.ts` | Modify | TDD create-response identity/schedule tests, local fallback tests, and failed authenticated create non-insertion. |
| `apps/web/app/src/components/CreatePostModal.vue` | Modify | Initialize NOW/NEXT_SLOT/CUSTOM from normalized mode/time without changing asset touch tracking or hydration. |
| `apps/web/app/src/components/CreatePostModal.test.ts` | Modify | TDD mode initialization plus untouched/clear/replace and missing-asset hydration regression assertions. |
| `apps/web/app/e2e/fixtures/scheduler-mocks.ts` | Modify | Return backend-shaped create results with stable persisted IDs and retain strict unknown-PATCH 404. |
| `apps/web/app/e2e/specs/scheduler-create-post.spec.ts` | Modify | Browser create-to-edit flow asserts PATCH uses POST-returned ID. |
| `apps/web/app/e2e/specs/scheduler-edit-post.spec.ts` | Modify | Verify NOW/NEXT_SLOT/CUSTOM prefill and asset edit behavior. |

No backend production files change. Existing `PublishingWorkspaceIsolationIntegrationTest` (#224), `PublishingHandlersTest`/`PublishingControllersTest`, and `PublishingProblemDetailsHandlerTest` (#225) are verification gates.

## Interfaces / Contracts

`PublicationMutationResult` remains aligned with backend `PublicationResult`. `Publication` keeps its public shape; `scheduledAt` becomes the display/edit instant selected by schedule mode. The mapper must set `id = publicationId` and preserve `assetIds`, `accountId = socialAccountId`, `status`, `scheduleMode`, and priority.

## Testing Strategy

| Layer | What to Test | Approach |
|---|---|---|
| Unit | Authenticated create/quick-create reconciliation; schedule mapping; local IDs only offline; errors do not insert | Add failing Vitest store cases first, then implement. |
| Component | Three edit modes and #223 hydration/tri-state | Mount modal with normalized publications; inspect selected mode and `updatePost` payload. |
| Integration | #224 isolation and #225 404 unchanged | Run focused backend tests without modifying backend contracts. |
| E2E/browser | POST ID is reused by subsequent PATCH; no invalid custom prefill | Stateful Playwright fixture records POST result and PATCH URL/body; verify UI, network status, and console. |

## Migration / Rollout

No data migration required. Existing synthetic localStorage records are not rewritten; authenticated calendar refresh replaces them with workspace-scoped server results. Roll back frontend mapper/action changes if regressions appear.

## Open Questions

None.
