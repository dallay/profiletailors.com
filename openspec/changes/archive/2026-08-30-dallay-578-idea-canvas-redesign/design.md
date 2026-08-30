# Design: DALLAY-578 — Redesign Idea Canvas and Unify Idea Composer with Publishing Composer UX

## Technical Approach

Decompose `IdeasView.vue` (808 lines) into `IdeaBoard→IdeaLane→IdeaCard` + `useIdeaDragAndDrop`; keep `ideas.store.ts` workspace-scoped. Reuse publishing composables (`useMarkdownEditor`, `useComposerScheduling`, `MarkdownToolbar`, `ComposerSchedulePanel`) without a shared package. Handoff is frontend-only: prefill `CreatePostModal` with `title+"\n\n"+notes+"\n\n"+tags#` deduped, then `PATCH /api/ideas/{id}` to store `convertedToPublicationId`. Keep `POST /api/ideas/{id}/convert` as legacy `NOW`. No migration, no media FK.

Covers `idea-canvas` + `idea-composer` deltas, 3 PRs.

## Architecture Decisions

### Board decomposition

| Option | Tradeoff | Decision |
|---|---|---|
| Keep monolith | Zero churn, 808 lines untestable | Reject |
| `IdeaBoard/Lane/Card` + `useIdeaDragAndDrop` (store-injected) | Mirrors `useComposerMediaPicker`; isolates `monitorForElements`/`draggable`/`dropTargetForElements`/`getDropIndex` | **Accept** |
| Shared drag abstraction | Premature, scheduler differs | Reject |

Preserves half-height `getDropIndex` + optimistic `moveIdea`; enables per-component Vitest.

### IdeaComposerModal reuses publishing primitives

| Option | Tradeoff | Decision |
|---|---|---|
| Factor `shared/web` | Breaks browser-neutral rule; premature coupling | Reject (defer) |
| Duplicate markdown/schedule | Diverges truth | Reject |
| `IdeaComposerModal` + `useIdeaComposer` importing `useMarkdownEditor`/`markdown.ts`, `useComposerScheduling`/`ComposerSchedulePanel` via props | Publishing owns; ideas via composition root | **Accept** |

### Handoff via frontend prefill

| Option | Tradeoff | Decision |
|---|---|---|
| Extend `POST /convert` → DRAFT | Breaks BDD, versioning, leaks publishing validation | Reject |
| Keep immediate convert only | Surprising `first ACTIVE→NOW` | Reject as sole path |
| Additive: keep `POST /convert` 200; new `POST /ideas` if unsaved → prefill → `CreatePostModal` → `PATCH {convertedToPublicationId}` | No backend change; publishing owns authority (AC 26–33) | **Accept** |

## Data Flow

```
Board move: Card drag → useIdeaDragAndDrop(getDropIndex half-height)
  └→ store.moveIdea → snapshot → normalizeIdeas(next) optimistic
       └→ PATCH /move → fail restore+toast | success keep
Columns → PUT /columns → fallback minBy order remap → normalizeIdeas

Composer+handoff: Top Add / Lane + / Card → IdeaComposerModal(create|edit)
  ├ title required, duplicate guard, chip tags lower-dedupe, https links, delete confirm
  ├ create POST /ideas | edit PATCH /ideas/{id}
  └ Create Post → if unsaved POST /ideas → prefill → CreatePostModal
       publishing owns channel/schedule/media
       success PATCH {convertedToPublicationId} | cancel null same lane
Legacy POST /convert → NOW first ACTIVE (kept)
```

State: store `ideas/columns/ideasByColumn/saving` + `normalize*`; `useIdeaComposer` draft + `useMarkdownEditor`; `useIdeaDragAndDrop` `columnElements/cardElements/draggedIdeaId`. Workspace `apiFetch workspaceScoped` + `X-Workspace-Id`.

Overflow: `IdeaBoard` h-scroll, lanes 280–320px; `IdeaLane` v-scroll bounded; header sticky; empty `No ideas yet` + `+ Add idea`. A11y: trap focus, keyboard DnD, `draggable` clickable, Escape confirms if dirty.

## File Changes

| File | Action | Description |
|---|---|---|
| `…/views/IdeasView.vue` | Modify | Thin orchestrator + workspace watch |
| `…/components/IdeaBoard.vue` | Create | H-scroll, skeleton lanes |
| `…/components/IdeaLane.vue` | Create | 280–320px, count, v-scroll, empty, `data-dnd-column` |
| `…/components/IdeaCard.vue` | Create | Title/clamp, 3 tags +N, `🔗 N`, badge, `data-dnd-draggable` |
| `…/application/useIdeaDragAndDrop.ts` | Create | `monitorForElements`/`draggable`/`dropTarget`, `getDropIndex`, watch+cleanup |
| `…/components/IdeaComposerModal.vue` | Create | Single Dialog, `MarkdownToolbar`, `ComposerSchedulePanel`, chips, `https` links |
| `…/application/useIdeaComposer.ts` | Create | Draft, `useMarkdownEditor`, `normalizeForSubmission`, guards, dedupe |
| `…/infrastructure/ideas.store.ts` | Modify | Add `associatePublication` via `PATCH`; no auto-move |
| `…/i18n/locales/{en,es}/ideas.ts` | Modify | Composer/handoff/empty copy; no fixed widths |
| `server/smp/.../ideas/**` | Modify docs | Mark `POST /convert` legacy; no behavior change |
| `…/features/ideas-canvas.feature` | Modify | Add handoff `@ideas @smoke @fast` |
| `…/tests/**` `…/e2e/**` | Modify/Add | Vitest + Playwright |

## Interfaces / Contracts

```kotlin
POST /api/ideas/{id}/convert -> ConvertIdeaResult(publicationId) // legacy NOW
PATCH /api/ideas/{id} { convertedToPublicationId?: String }       // associate
```
```ts
function buildPrefill(i: Idea): string {
  const tags = dedupeHashtags(i.tags.map(t=>`#${t}`).join(' '))
  return [i.title, i.notes, tags].filter(Boolean).join('\n\n')
}
```
Empty-channel: disable Create Post + CTA; hide `InvalidIdeaColumnsException`.

## Testing Strategy

| Layer | What to Test | Approach |
|---|---|---|
| Unit | `Board/Lane/Card`, skeleton, clamp, +N, `getDropIndex` | Mock adapter, `data-dnd-*` |
| Unit | `ComposerModal` guard, dedupe, `https`, confirm, trap, Escape | Mount + `markdown.ts` |
| Integration | `moveIdea` rollback, column fallback, `associatePublication` | `apiFetch` mock |
| BDD | reorder, cross-column, rollback, guard, handoff | `@ideas @smoke @fast` |
| E2E | create→edit→move→handoff→associate→no-auto-move, empty CTA, keyboard | `media:mocked` |

## Migration / Rollout

No migration. 3 chained PRs (400-line budget):

| PR | Scope | Budget | Verify |
|---|---|---|---|
| PR1 | `Board/Lane/Card` + `useIdeaDragAndDrop` | ~230 | Vitest board + BDD move/rollback |
| PR2 | `ComposerModal` + `useIdeaComposer` + schedule/markdown + i18n | ~340 | Vitest composer, a11y; `POST /convert` untouched |
| PR3 | Prefill via composition root, empty-channel CTA, `PATCH associate`, legacy docs | ~140 | Playwright handoff, BDD associate, legacy 200 |

Each `git revert` safe.

## Open Questions

- [ ] Channel in composer hidden vs read-only? Keep hidden (publishing owns).
- [ ] Board defaults `raw/in-progress/done` unchanged — redesign defaults deferred.
- [ ] Media reuse deferred (no `assetId` FK).
- [ ] `shared/composer` extraction after PR3.
