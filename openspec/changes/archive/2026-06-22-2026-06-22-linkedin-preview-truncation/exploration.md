## Exploration: LinkedIn preview truncation in Create Post modal

### Current State
`apps/web/app/src/components/CreatePostModal.vue` currently owns both the composer form and the full LinkedIn preview markup inline. The right-hand preview column is hard-coded as a LinkedIn card, and the post body renders `{{ postText }}` inside a `whitespace-pre-wrap break-words` container with no truncation or expansion control, so long text can grow the preview indefinitely. Media preview state is already computed in the parent (`selectedAssetPreviewUrl`, `selectedAssetIsImage`), and channel display data is also derived there (`selectedChannel`, `selectedChannelInitials`). Existing tests in `apps/web/app/src/components/CreatePostModal.test.ts` cover avatar fallback, media preview behavior, and submit flow, but there is no coverage for preview text truncation or preview component boundaries.

### Affected Areas
- `apps/web/app/src/components/CreatePostModal.vue` — current inline LinkedIn preview lives here; this is where preview extraction or truncation behavior starts.
- `apps/web/app/src/components/CreatePostModal.test.ts` — current modal test surface; should gain regression coverage for long-text preview truncation and preview rendering continuity.
- `apps/web/app/src/i18n/index.ts` — likely needs new strings for a preview expansion affordance such as `...more` / `...ver más` if the UI makes it explicit.
- `apps/web/app/src/stores/publishing.ts` — not functionally blocked, but defines the channel/provider model the preview components should consume; useful boundary for future provider-specific previews.
- `apps/web/app/src/views/SchedulerView.test.ts` — low-risk indirect surface because it mocks `CreatePostModal`; unlikely to need logic changes, but relevant when extracting child components.

### Approaches
1. **Inline truncation inside `CreatePostModal`** — keep the preview markup where it is and add LinkedIn-style clamping/`...more` behavior directly in the modal.
   - Pros: smallest diff; fastest regression fix; minimal test fallout.
   - Cons: deepens the existing all-in-one modal; moves away from the stated network-component architecture; makes future Instagram/Facebook/X previews harder to add cleanly.
   - Effort: Low

2. **Introduce `LinkedInPostPreview` only, keep layout in modal** — extract the current LinkedIn card into a child component that receives normalized preview props from `CreatePostModal`, and implement truncation there.
   - Pros: small safe step toward network-owned rendering rules; isolates LinkedIn-specific truncation and card structure; avoids a larger layout refactor now.
   - Cons: common preview shell still remains in `CreatePostModal`; future provider rollout will still need a shared parent later.
   - Effort: Low/Medium

3. **Introduce `PostPreviewPanel` + `LinkedInPostPreview` now** — move the right column into a shared parent component responsible for panel layout/header, and delegate card rendering rules to a LinkedIn child.
   - Pros: matches the desired architecture immediately; clean seam for future provider-specific previews; keeps common layout separate from provider rendering.
   - Cons: larger first change for a bug-level issue; more prop plumbing and tests; slightly higher risk of incidental UI regressions in the modal layout.
   - Effort: Medium

### Recommendation
Recommend **Approach 3 with a deliberately narrow scope** for this new change: introduce a shared `PostPreviewPanel` for the right-column shell and a `LinkedInPostPreview` child that owns LinkedIn text/media rendering, including long-text truncation with a passive `...more` affordance (preview-only, not interactive expansion unless design explicitly wants it). This is the smallest safe change that fixes issue #132 **and** respects the required architecture direction without dragging unrelated composer logic into the refactor. It keeps `CreatePostModal` as the orchestrator of form state while moving preview responsibilities behind a provider-oriented seam.

If implementation pressure is very high, Approach 2 is the fallback, but Approach 1 should be avoided because it fixes the symptom while making the known architectural problem worse.

### Risks
- The exact truncation behavior must be defined clearly: CSS-only line clamp is visually simple but may not produce a reliable LinkedIn/Buffer-style trailing `...more` treatment for arbitrary whitespace and line breaks.
- Extracting the preview can expose hidden coupling on modal-local computed values (`selectedChannel`, initials, media preview URL, placeholder copy), so prop design should stay narrow and explicit.
- Current tests inspect teleported DOM from the modal; extraction will require updating selectors or adding child-component-level tests to avoid brittle assertions.
- If `...more` becomes interactive, that expands scope into stateful preview behavior and extra i18n/accessibility work.

### Ready for Proposal
Yes — recommend proposal phase with change name `2026-06-22-linkedin-preview-truncation`, scoped to componentizing the preview shell (`PostPreviewPanel`) and LinkedIn renderer (`LinkedInPostPreview`) while adding long-text truncation regression coverage.
