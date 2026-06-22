# Proposal: LinkedIn Preview Truncation

## Intent

Fix issue #132 where long post text makes the LinkedIn preview in `CreatePostModal` grow without bound. Do it in a way that creates the first provider-specific preview seam instead of deepening modal-only preview logic.

## Scope

### In Scope
- Clamp LinkedIn preview text so long content stays visually bounded in the Create Post modal.
- Extract preview rendering into a shared parent panel plus a LinkedIn-specific child component.
- Add regression coverage for long-text preview behavior and extracted preview rendering.

### Out of Scope
- Interactive expand/collapse behavior inside the preview.
- New provider previews beyond LinkedIn or broader composer redesign.

## Capabilities

### New Capabilities
- `composer-preview`: Shared compose-preview shell with provider-specific preview components, including bounded preview text rendering.

### Modified Capabilities
- None.

## Approach

Introduce a shared preview parent for the modal’s right column and move LinkedIn card rendering into a dedicated child. Keep `CreatePostModal` responsible for composer state and pass only normalized preview props downward. Implement passive truncation for long LinkedIn text so the preview remains stable without adding new interactive state. This slice is the right scope now because it fixes the bug and establishes the network-specific seam required for future previews without refactoring unrelated composer flows.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `apps/web/app/src/components/CreatePostModal.vue` | Modified | Replace inline preview with shared preview composition |
| `apps/web/app/src/components/` | New | Add shared preview panel and LinkedIn preview component |
| `apps/web/app/src/components/CreatePostModal.test.ts` | Modified | Add regression tests for bounded preview text and extraction seam |
| `apps/web/app/src/i18n/index.ts` | Modified | Add preview truncation affordance copy only if needed |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Truncation looks wrong with whitespace-heavy posts | Med | Prefer deterministic bounded rendering and test multiline content |
| Extraction breaks modal tests or preview props | Med | Keep child props narrow and add focused regression coverage |

## Rollback Plan

Revert the new preview components and restore the inline preview in `CreatePostModal`, then remove related test and i18n changes.

## Dependencies

- Existing publishing/channel state from `usePublishingStore`
- Existing media preview data already computed in `CreatePostModal`

## Success Criteria

- [ ] Long LinkedIn preview text no longer causes unbounded modal growth.
- [ ] Preview rendering is split into a shared parent and LinkedIn-specific child.
- [ ] Existing compose behavior remains intact with regression coverage for the bug.
