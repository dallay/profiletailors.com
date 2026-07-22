# Design: Publication Edit Asset Preservation

## Technical Approach

Implement the selected long-term Option 2 contract: PATCH `assetIds` is tri-state while CREATE
remains non-null and default-empty. The HTTP boundary will use separate mapping helpers so edit can
preserve absent/null but create still sends `List<String>`. The domain aggregate keeps
`assetIds: List<String>`; only the edit command carries `List<String>?`.

## Architecture Decisions

| Option                               | Tradeoff                                               | Decision                                                                                  |
|--------------------------------------|--------------------------------------------------------|-------------------------------------------------------------------------------------------|
| Single nullable DTO field            | Small DTO diff but CREATE could leak null into command | Use nullable DTO plus create/edit mapping helpers; CREATE coerces `null` to `emptyList()` |
| Nullable domain assetIds             | Propagates optionality everywhere                      | Reject; optionality is PATCH-only, domain stays non-null                                  |
| Frontend always resends existing IDs | Avoids backend change but stale and not true PATCH     | Reject; use true tri-state contract                                                       |
| Edit-only dirty flag                 | Slight UI state cost                                   | Accept; required to distinguish untouched from explicit clear                             |

## Data Flow

```text
Edit modal opens
  ├─ clear global media selection
  ├─ hydrate existing pub.assetIds into media.assetsById
  ├─ add hydrated/known IDs to selectedAssetIds
  └─ then await loadDanglingAssets()

Save edit
  ├─ assetsTouched=false → omit assetIds
  ├─ assetsTouched=true + [] → send []
  └─ assetsTouched=true + ids → send ids

PATCH DTO assetIds null/absent ─→ EditPublicationCommand(assetIds=null)
                              └→ handler uses current.assetIds
PATCH DTO assetIds []/[ids] ───→ handler replaces exactly
```

## File Changes

| File                                                                                     | Action | Description                                                                                                                                                                                         |
|------------------------------------------------------------------------------------------|--------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `server/smp/src/main/kotlin/.../publishing/infrastructure/http/PublishingControllers.kt` | Modify | Make `PublicationUpsertRequest.assetIds: List<String>? = null`; create maps `request.assetIds ?: emptyList()`, edit passes nullable through.                                                        |
| `server/smp/src/main/kotlin/.../publishing/application/PublishingApi.kt`                 | Modify | Change only `EditPublicationCommand.assetIds` to `List<String>? = null`; keep `CreatePublicationCommand.assetIds: List<String> = emptyList()`.                                                      |
| `server/smp/src/main/kotlin/.../publishing/application/PublishingHandlers.kt`            | Modify | In edit, resolve `val updatedAssetIds = command.assetIds ?: current.assetIds`; validate media only when `updatedAssetIds.isNotEmpty()` and replace `current.copy(assetIds = updatedAssetIds, ...)`. |
| `apps/web/app/src/stores/publishing.ts`                                                  | Modify | Add a strongly typed `PublicationUpdate`/PATCH input with `assetIds?: string[]`; build PATCH body by conditionally spreading `assetIds` only when present, no casts/no `any`.                       |
| `apps/web/app/src/components/CreatePostModal.vue`                                        | Modify | Add edit-only `assetsTouched`; hydrate existing assets before dangling load; omit `assetIds` unless touched.                                                                                        |
| Test files listed below                                                                  | Modify | Add regression coverage first.                                                                                                                                                                      |

## Interfaces / Contracts

```kotlin
data class PublicationUpsertRequest(val assetIds: List<String>? = null, ...)
data class EditPublicationCommand(val assetIds: List<String>? = null, ...)
// CREATE: assetIds = request.assetIds ?: emptyList()
// EDIT: assetIds = request.assetIds
```

```ts
interface PublicationUpdate extends Partial<Omit<Publication, 'assetIds'>> {
  assetIds?: string[]
}
```

## Frontend Interaction Design

`assetsTouched` is reset to `false` in `initEditMode` and irrelevant for create. Programmatic edit
hydration must not mark touched. User add/remove/clear/upload-success interactions set it to `true`;
explicit clear sends `assetIds: []`. Existing IDs are hydrated with existing
`mediaStore.getAsset(id)`/store APIs and added to `assetsById`; missing/deleted assets are skipped
gracefully with no submission blocker.

Race avoidance: `initializeComposerForOpen` must `await initEditMode` hydration before
`loadDanglingAssets()`. Because `selectedAssetIds` is global, every modal open still begins with
`clearSelection()`; only the active composer owns selection. Do not redesign the selection store in
this change.

## Testing Strategy

| Layer               | What to Test                                                                            | Approach                                                                                         |
|---------------------|-----------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------|
| Unit backend        | CREATE default empty, PATCH absent/null preserve, [] clears, [ids] replaces             | Add failing tests in `PublishingControllersTest`, `PublishingApiTest`, `PublishingHandlersTest`. |
| Unit frontend store | `updatePost` omits `assetIds` when absent and serializes [] when provided               | Add tests in `apps/web/app/src/stores/publishing.test.ts`.                                       |
| Component           | Edit hydration visible, untouched submit omits, clear sends [], missing asset tolerated | Add tests in `CreatePostModal.test.ts`.                                                          |
| Non-regression      | workspace isolation and #224/#225 edit hardening                                        | Run focused existing publishing and scheduler tests.                                             |

TDD commands:
`./gradlew :server:smp:test --tests '*PublishingControllersTest' --tests '*PublishingApiTest' --tests '*PublishingHandlersTest' -PexcludeTags=modularity,postgres`;
`pnpm --filter app vitest run src/components/CreatePostModal.test.ts src/stores/publishing.test.ts`;
then `just backend-test-fast` and `just frontend-test` if focused tests pass.

## Migration / Rollout

No migration required. PATCH remains backward-compatible: existing clients sending `assetIds` still
replace; clients omitting/null now preserve. CREATE behavior is explicitly preserved.

## Open Questions

None.
