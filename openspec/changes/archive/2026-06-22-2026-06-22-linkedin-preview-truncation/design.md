# Design: LinkedIn Preview Truncation

## Technical Approach

Split the modal’s right column into a shared shell and a provider-owned renderer.
`CreatePostModal.vue` remains the source of composer state (text, selected channel, previewable
media, schedule controls). It passes normalized preview props to `PostPreviewPanel.vue`, which
selects `LinkedInPostPreview.vue` for the current provider. This fixes issue #132 while creating the
first seam for future network-specific previews without expanding scope beyond LinkedIn.

## Architecture Decisions

### Decision: Keep preview state in the modal

| Option                                                    | Tradeoff                                                                                  | Decision |
|-----------------------------------------------------------|-------------------------------------------------------------------------------------------|----------|
| Move preview state into child components                  | Cleaner children, but duplicates/relocates composer knowledge                             | No       |
| Keep state in `CreatePostModal` and pass normalized props | Slight prop plumbing, but preserves current ownership and avoids side effects in children | Yes      |

**Rationale**: The modal already computes `selectedChannel`, initials, and media preview URL.
Reusing those computations avoids hidden coupling with stores inside preview components.

### Decision: Shared panel + LinkedIn child now

| Option                                     | Tradeoff                                                           | Decision |
|--------------------------------------------|--------------------------------------------------------------------|----------|
| Inline fix in `CreatePostModal.vue`        | Smallest diff, but deepens modal coupling                          | No       |
| `LinkedInPostPreview` only                 | Good first step, but leaves shell duplication in modal             | No       |
| `PostPreviewPanel` + `LinkedInPostPreview` | Slightly larger change, but establishes the intended provider seam | Yes      |

**Rationale**: This is the narrowest refactor that fixes the bug and matches the requested
architecture direction.

## Data Flow

```text
CreatePostModal
  ├─ computes preview model from store + local refs
  ├─ renders PostPreviewPanel(provider, previewModel)
  └─ keeps schedule/footer controls outside preview renderer

PostPreviewPanel
  ├─ renders panel header + card frame
  └─ delegates body to LinkedInPostPreview

LinkedInPostPreview
  ├─ renders LinkedIn header/body/media/actions
  └─ applies bounded text truncation + passive "...more"
```

Sequence:
`postText/media/channel change → modal computed props update → PostPreviewPanel receives normalized props → LinkedInPostPreview re-renders bounded card`

## File Changes

| File                                                               | Action | Description                                                                                                               |
|--------------------------------------------------------------------|--------|---------------------------------------------------------------------------------------------------------------------------|
| `apps/web/app/src/components/CreatePostModal.vue`                  | Modify | Remove inline LinkedIn preview markup; build and pass normalized preview props; keep modal layout and scheduling controls |
| `apps/web/app/src/components/composer/PostPreviewPanel.vue`        | Create | Shared right-column shell, preview title/header, provider switch, empty fallback handling                                 |
| `apps/web/app/src/components/composer/LinkedInPostPreview.vue`     | Create | LinkedIn card renderer for header, text, media, and action row                                                            |
| `apps/web/app/src/components/CreatePostModal.test.ts`              | Modify | Regress long-text behavior and modal-to-preview delegation                                                                |
| `apps/web/app/src/components/composer/LinkedInPostPreview.test.ts` | Create | Focused truncation and placeholder/media rendering tests                                                                  |
| `apps/web/app/src/i18n/index.ts`                                   | Modify | Add preview affordance copy only if `...more` is localized instead of hardcoded                                           |

## Interfaces / Contracts

```ts
interface PostPreviewModel {
  provider: 'linkedin'
  authorName: string
  authorHandle: string
  authorAvatarUrl?: string | null
  authorInitials: string
  text: string
  mediaImageUrl?: string | null
}
```

`CreatePostModal` owns this model. `PostPreviewPanel` accepts `{ provider, preview }`.
`LinkedInPostPreview` accepts only the LinkedIn-shaped preview data and MUST NOT read Pinia stores
directly.

## Testing Strategy

| Layer       | What to Test                                         | Approach                                                                                                              |
|-------------|------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------|
| Unit        | LinkedIn truncation, placeholder text, media section | `LinkedInPostPreview.test.ts` with short, multiline, and long text fixtures                                           |
| Integration | Modal delegates preview props correctly              | Update `CreatePostModal.test.ts` to assert preview shell/card output with teleported DOM                              |
| E2E         | None for this slice                                  | Existing compose modal E2E remains unchanged; defer browser coverage unless regression escapes unit/integration tests |

## Migration / Rollout

No migration required. Roll out as an internal UI refactor plus bug fix.

## Open Questions

- [ ] Should `...more` be localized via i18n, or treated as LinkedIn-specific chrome and kept
  literal?
- [ ] Do we want the preview shell under `components/composer/` now, or keep all new files flat
  under `components/` for consistency with the current tree?
