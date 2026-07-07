# Design: Create Post Media Attachment Picker

## Technical Approach

Keep `CreatePostModal.vue` as the parent-owned coordinator and replace the current dropzone-first attachment area with a compact rail plus one `Add Media` trigger. The modal will stage selection locally, use `useMediaStore()` only for library asset cache/upload state, and commit `draftAttachmentIds` into publication `assetIds` only on apply/submit. This matches the proposal and the spec deltas for `composer-media-picker`, `media-library`, `media-provider-unsplash`, and `publishing`.

## Architecture Decisions

### Decision: Keep picker session state local to `CreatePostModal`

**Choice**: Add modal-local `draftAttachmentIds`, `pickerSelectionIds`, picker open/source/status flags, and derived `effectiveAttachmentLimit`; keep `mediaStore.assetsById/assetIds/uploads` as shared library data only.
**Alternatives considered**: Reuse `mediaStore.selectedAssetIds`; add a new global picker store.
**Rationale**: The current store selection is global draft state today. Reusing it would leak staged changes across cancel/dismiss. A new store violates the constraint and is unnecessary because the picker is scoped to one modal.

### Decision: Apply replace-set semantics, never incremental commit

**Choice**: Opening copies `draftAttachmentIds -> pickerSelectionIds`; select/deselect mutates staged IDs only; cancel discards; apply replaces `draftAttachmentIds` exactly.
**Alternatives considered**: Live-binding picker clicks to draft; merge-on-apply.
**Rationale**: This directly satisfies the picker lifecycle requirements and preserves predictable undo-by-dismiss behavior.

### Decision: Reconcile uploads/imports by upsert + targeted readiness polling

**Choice**: Reuse CAS `createAndUpload()` and add store helpers for `upsertAsset`, `loadAsset`, and optional targeted refresh/poll for returned asset IDs. Auto-stage once the uploaded/imported asset resolves to a selectable persisted asset.
**Alternatives considered**: Full library reload after every action; optimistic staging before persistence.
**Rationale**: Targeted reconciliation is cheaper than full reloads and respects CAS reality: `PENDING_UPLOAD`/`UPLOADING` assets are visible but not selectable.

### Decision: Preserve shell boundaries, move upload orchestration to modal

**Choice**: `ComposerMediaPickerShell` stays presentation/event-only; `CreatePostModal` owns upload action, Unsplash import, capability checks, and apply logic.
**Alternatives considered**: Making the shell fetch/upload directly; adding Upload as a first-class browsable tab.
**Rationale**: This preserves the existing shell architecture and keeps Upload as an action, not a fake provider.

## Data Flow

```text
CreatePostModal
  draftAttachmentIds ──open──> pickerSelectionIds
         │                          │
         │                    select/deselect
         │                          ▼
         │                 ComposerMediaPickerShell
         │                    │            │
         │               Library grid   Upload/Unsplash actions
         │                    │            │
         └────apply<──────────┴──────CreatePostModal──────> mediaStore/assets API
                                      │                         │
                                      │                    CAS / import
                                      ▼                         ▼
                               draftAttachmentIds        assetsById/uploads
                                      │
                                      └──submit──> publishingStore.schedulePost/updatePost(assetIds)
```

Sequence for async creation:
1. Upload/import action starts in `CreatePostModal`.
2. Store upserts or loads the persisted asset record.
3. If status is `PENDING_UPLOAD`/`UPLOADING`, picker shows placeholder card from store data/upload item.
4. Modal polls/refreshes that asset until `READY` or `FAILED`.
5. On `READY`, modal auto-adds the asset ID to `pickerSelectionIds` once.

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `apps/web/app/src/components/CreatePostModal.vue` | Modify | Own draft vs picker session state, Add Media trigger, compact rail, picker orchestration, capability validation, and submit mapping to persisted `assetIds`. |
| `apps/web/app/src/components/CreatePostModal.test.ts` | Modify | Cover open/cancel/apply lifecycle, limit enforcement, async upload/import readiness, and invalid-after-channel-change behavior. |
| `apps/web/app/src/stores/media.ts` | Modify | Expose targeted asset reconciliation helpers and preserve uploads/assets cache without owning picker selection. |
| `apps/web/app/src/lib/media-api.ts` | Modify | Reuse existing CAS/get/list primitives; add only minimal helper surface if targeted refresh polling needs shared API logic. |
| `apps/web/app/src/components/composer/ComposerMediaPickerShell.vue` | Create | Parent-owned dialog shell for tabs, staged grid selection, apply/cancel actions, and non-ready asset presentation. Verified absent from the current tree during design review. |
| `apps/web/app/src/components/composer/ComposerMediaPickerShell.test.ts` | Create | Verify typed event contract, selection rendering, apply/cancel events, and disabled non-ready assets. Verified absent from the current tree during design review. |
| `apps/web/app/src/components/composer/composer-media-picker.types.ts` | Create | Shared picker props/events/types including asset card state and session actions. Verified absent from the current tree during design review. |
| `apps/web/app/src/features/media-composer/providers/MediaProviderPanel.vue` | Create | Parent-owned Unsplash panel used inside the picker when feature-flagged. Verified absent from the current tree during design review. |

## Interfaces / Contracts

```ts
interface ComposerPickerSession {
  draftAttachmentIds: string[]
  pickerSelectionIds: string[]
  autoStagedAssetIds: string[]
  effectiveAttachmentLimit: number
}

interface PickerAssetViewModel {
  assetId: string
  status: MediaAssetStatus
  previewUrl: string | null
  selectable: boolean
  selected: boolean
  sourceType: 'UPLOADED' | 'EXTERNAL'
}
```

Rules:
- `library assets/store data` = `mediaStore.assetsById`, `assetIds`, `uploads`
- `pickerSelectionIds` = transient modal session only
- `draftAttachmentIds` = modal draft only
- persisted publication `assetIds` = derived from draft on submit only

Channel limit contract:
- `effectiveAttachmentLimit = min(selectedChannels[].maxAttachments)`
- if current `draftAttachmentIds.length > effectiveAttachmentLimit`, keep attachments, mark composer invalid, disable publish/schedule, and show resolution guidance

## Testing Strategy

| Layer | What to Test | Approach |
|-------|-------------|----------|
| Unit | Limit aggregation, staged selection reducer, readiness-to-auto-stage guard | Vitest for modal helpers/types. |
| Integration | Modal + picker open/apply/cancel, upload/import reconciliation, non-ready asset rendering | Vue Test Utils on `CreatePostModal` and `ComposerMediaPickerShell`. |
| E2E | Add media, apply selection, channel change invalidation | Extend Playwright composer coverage if modal flow already exists; otherwise defer to later verification. |

## Migration / Rollout

No migration required. Roll out behind the existing Unsplash feature flag for provider visibility only.

## Resolved Planning Decisions

- Unsplash import orchestration lives in `CreatePostModal.vue` as the parent-owned coordinator. `MediaProviderPanel.vue` remains provider-specific presentation that emits search/import intent only. `ComposerMediaPickerShell.vue` remains presentation/event-only and does not own import logic.
- `maxAttachments` MUST be verified before apply. Implementation may proceed only after confirming one of these paths:
  - A. frontend channel models already expose comparable `maxAttachments` capability metadata, in which case the modal consumes it directly;
  - B. backend/channel contracts expose the capability but the current frontend mapping omits it, in which case the frontend contract must be extended before limit enforcement is implemented;
  - C. capability metadata is absent, in which case the change must introduce a temporary explicit frontend capability registry with a follow-up alignment path.

## Operational Policy: Targeted Polling and Cancellation

- Use at most one active poller per asset ID.
- Start targeted polling only for assets created through the active picker session that are not yet selectable persisted assets.
- Stop polling when any of these occurs:
  - the asset becomes selectable persisted state;
  - the asset becomes `FAILED`;
  - the picker closes without apply;
  - `CreatePostModal.vue` unmounts;
  - a bounded timeout/max-attempt policy is reached.
- Transient fetch errors SHOULD retry within the bounded polling policy instead of failing immediately.
- When bounded polling times out, keep the asset visible in its latest non-selectable state, stop active polling, and allow later refresh/reopen actions to reconcile it.
- Auto-stage MUST happen at most once per asset created through the active session. If the author manually deselects an auto-staged asset, later refreshes or poll completions MUST NOT auto-stage it again.
