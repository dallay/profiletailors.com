# Tasks: LinkedIn Preview Truncation

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 220-340 |
| 400-line budget risk | Medium |
| Chained PRs recommended | No |
| Suggested split | Single PR |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Medium

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Shared preview seam + LinkedIn truncation + regression tests | PR 1 | Single reviewable slice with component and modal coverage |

## Phase 1: Foundation / Preview Boundary

- [ ] 1.1 Create `apps/web/app/src/components/composer/PostPreviewPanel.vue` as the shared preview shell with title/header, bounded right-column container, provider switch, and empty fallback slot.
- [ ] 1.2 Create `apps/web/app/src/components/composer/LinkedInPostPreview.vue` with the LinkedIn card header, text block, media section, and static action row fed only by preview props.
- [ ] 1.3 In `apps/web/app/src/components/CreatePostModal.vue`, define a normalized LinkedIn preview model from `selectedChannel`, `selectedChannelInitials`, `postText`, and `selectedAssetPreviewUrl`.

## Phase 2: Core Implementation / Modal Wiring

- [ ] 2.1 Replace the inline LinkedIn preview markup in `apps/web/app/src/components/CreatePostModal.vue` with `PostPreviewPanel` and pass provider plus preview model props.
- [ ] 2.2 Implement bounded LinkedIn text rendering in `apps/web/app/src/components/composer/LinkedInPostPreview.vue` so long text clamps visually while composer state remains unchanged.
- [ ] 2.3 Show passive `...more` only when truncation applies in `apps/web/app/src/components/composer/LinkedInPostPreview.vue`, keeping the affordance attached to the text block.
- [ ] 2.4 Preserve media-compatible layout in `apps/web/app/src/components/composer/LinkedInPostPreview.vue` for image preview and text-only states without breaking the modal scroll container.
- [ ] 2.5 Update `apps/web/app/src/i18n/index.ts` only if the `...more` affordance is localized; otherwise keep the literal copy inside the LinkedIn renderer.

## Phase 3: Testing / Verification

- [ ] 3.1 Create `apps/web/app/src/components/composer/LinkedInPostPreview.test.ts` covering short text, long multiline text, truncation affordance visibility, and media-plus-text rendering.
- [ ] 3.2 Update `apps/web/app/src/components/CreatePostModal.test.ts` to assert the modal delegates to the shared preview shell and keeps preview output stable through empty, short, and long text states.
- [ ] 3.3 Add a regression assertion in `apps/web/app/src/components/CreatePostModal.test.ts` that long LinkedIn text does not replace or mutate the original composer text value.
- [ ] 3.4 Run `just frontend-test` for the Vue app test suite and confirm the new preview tests cover the bounded-text scenarios from `openspec/changes/2026-06-22-linkedin-preview-truncation/specs/composer-preview/spec.md`.
