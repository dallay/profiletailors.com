# Design: Composer Media Picker Shell

## Technical Approach
Build a composer-scoped presentational dialog shell at `apps/web/app/src/components/composer/ComposerMediaPickerShell.vue`, using the existing shadcn-vue/Reka `Dialog` primitives and app form controls. `CreatePostModal.vue` remains the owner of open state and later data wiring; the shell only renders localized controls, deterministic presentation states, and typed interaction events. This satisfies the shell-only contract in the proposal and spec requirements for opening/dismissal, localized accessibility, deterministic states, parent-owned data flow, asset-region presentation, and focused testability.

## Architecture Decisions
| Decision | Alternatives considered | Rationale |
|---|---|---|
| Use the existing `Dialog` wrapper (`@/components/ui/dialog`) instead of another Teleport/custom modal | Extend `CreatePostModal`’s custom Teleport/focus-trap pattern; introduce a new modal dependency | The app already wraps Reka dialog primitives with focus trap, close behavior, and accessible structure. Reusing that lowers a11y risk and directly supports spec scenarios for keyboard dismissal, focus containment, and focus return. |
| Keep the picker fully controlled by props + emits | Read Pinia `mediaStore` directly; fetch via `media-api`; keep internal selection/search state | The spec explicitly forbids store/API coupling, fetching, mutation, upload, delete, and selection persistence. A controlled shell preserves parent ownership and keeps later media-library integration additive instead of redesigning the component. |
| Reuse `MediaAssetSummary` as the asset item shape and add shell-local filter/state types beside the component | Invent a new asset DTO; pass loose `Record<string, unknown>` data | `MediaAssetSummary` is already the frontend media contract in `src/lib/media-api.ts` and is used by the media store/tests. Reusing it minimizes mapping work later while keeping TypeScript strict and avoiding `any`. |

## Data Flow
Requirement mapping: opening/dismissal, deterministic states, parent-owned contract, asset-region presentation.

```text
CreatePostModal
  ├─ owns: isOpen, query, filter, state, assets, disabled
  ├─ passes typed props
  └─ handles emits (close/search/filter)
          ↓
ComposerMediaPickerShell
  ├─ Dialog shell + localized controls
  ├─ state-specific body
  └─ emits only when enabled
```

Sequence for interactions:

```text
trigger click/keyboard → parent sets open=true → shell renders dialog
search/filter input → shell emits typed payload → parent updates props
Esc/close button/outside dismiss → dialog closes → parent sets open=false
```

## File Changes
| File | Action | Description |
|---|---|---|
| `apps/web/app/src/components/composer/ComposerMediaPickerShell.vue` | Create | Presentational dialog shell with header, close action, search/filter controls, asset-grid region, and loading/empty/error/ready/disabled states. |
| `apps/web/app/src/components/composer/composer-media-picker.types.ts` | Create | Strict prop/event support types: view state, filter option, emitted payloads, and optional helper labels. |
| `apps/web/app/src/components/CreatePostModal.vue` | Modify | Add the picker trigger and own the shell’s open state plus temporary stub props/event handlers without store/API coupling. |
| `apps/web/app/src/components/CreatePostModal.test.ts` | Modify | Add focused assertions for trigger open/close, accessible names, focus return, and shell event observation. |
| `apps/web/app/src/components/composer/ComposerMediaPickerShell.test.ts` | Create | Component tests for localized labels, deterministic states, disabled suppression, and ready-state asset rendering. |
| `apps/web/app/src/i18n/index.ts` | Modify | Add `composer.mediaPicker.*` keys in both `en` and `es`, preserving the repo’s in-file locale-object convention and i18n parity test. |

## Interfaces / Contracts
```ts
import type { MediaAssetSummary } from '@/lib/media-api'

export type ComposerMediaPickerViewState = 'loading' | 'empty' | 'error' | 'ready'
export type ComposerMediaPickerFilter = 'all' | 'image' | 'video' | 'document'

export interface ComposerMediaPickerProps {
  open: boolean
  disabled?: boolean
  state: ComposerMediaPickerViewState
  searchQuery: string
  selectedFilter: ComposerMediaPickerFilter
  filterOptions: ReadonlyArray<{ value: ComposerMediaPickerFilter; labelKey: string }>
  assets: ReadonlyArray<MediaAssetSummary>
  errorMessage?: string | null
}

export interface ComposerMediaPickerSearchChange { query: string }
export interface ComposerMediaPickerFilterChange { filter: ComposerMediaPickerFilter }
```
Emits: `update:open(boolean)` for dialog ownership, `close()`, `search-change(payload)`, `filter-change(payload)`. When `disabled === true`, search/filter controls render disabled and MUST NOT emit. Ready-state asset cards stay non-attaching in this change; no asset-selected emit yet, matching spec scope.

## Testing Strategy
| Layer | What to Test | Approach |
|---|---|---|
| Unit/component | Shell open/close, localized accessible names, state rendering, disabled suppression, emitted search/filter/close events | Vitest + Vue Test Utils, matching current component-test style (`$t` mocked to keys, dialog interactions asserted from rendered DOM). |
| Integration | Trigger wiring inside `CreatePostModal.vue`, focus return to trigger, composer text preserved while opening/dismissing shell | Extend `CreatePostModal.test.ts` with real parent-owned state assertions. |
| E2E | None in this change | Spec/proposal only require focused Vitest coverage; no API/store behavior exists yet. |

## Migration / Rollout
No migration required. Rollout is additive inside the composer UI and does not change API, Pinia persistence, or backend contracts.

## Open Questions
- [ ] Should the initial filter set mirror existing media-library type groupings exactly (`IMAGE/VIDEO/PDF/OTHER`) or use the narrower shell labels above and map later in the parent?
- [ ] Should the ready-state grid reuse an existing asset-card fragment from `MediaLibraryView.vue` in a follow-up, or intentionally stay lighter here to avoid premature shared abstraction?
