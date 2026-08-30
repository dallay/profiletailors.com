# Proposal: DALLAY-578 — Redesign Idea Canvas and Unify Idea Composer with Publishing Composer UX

## Intent

`IdeasView.vue` (808 lines) owns board/DnD, capture, detail, settings, convert. Detail is plain text (CSV tags, `label|url`). `ConvertIdeaHandler` → first `ACTIVE` + `now()` = surprising `NOW`. `CreatePostModal` (1826 lines) has reusable composables. Decompose board; unify composer; prefill handoff.

## Scope

### In Scope
- PR1 `IdeaBoard/IdeaLane/IdeaCard/useIdeaDragAndDrop`
- PR2 `IdeaComposerModal` reusing `useMarkdownEditor`, `useComposerScheduling/SchedulePanel`, `MarkdownToolbar`, read-only `ComposerChannelSelector`
- PR3 `idea → CreatePostModal` prefill; keep `POST /convert` legacy
- Store + i18n + BDD

### Out of Scope
- Shared `shared/web` package; backend DRAFT/breaking convert; media attachments; defaults migration; DALLAY-435 expansion

## Capabilities

### New Capabilities
- `idea-canvas`: board/lanes/cards/DnD/column settings
- `idea-composer`: markdown composer, tags/links, schedule parity

### Modified Capabilities
- None — `publishing`, `composer-media-picker`, `composer-preview` reused; `POST /convert` legacy

## Approach

3 chained PRs:

- **PR1 (~250)**: board primitives + store-injected DnD (mirrors `useComposerMediaPicker`); keep `getDropIndex`, optimistic `moveIdea`.
- **PR2 (~350)**: `IdeaComposerModal.vue` + `useIdeaComposer.ts`; reuse publishing panels; `markdown.ts`.
- **PR3 (~150)**: prefill props via composition root; empty-channel → disabled + CTA.

## Alternatives Considered

- **Shared package**: premature, violates `shared/web` neutrality — defer.
- **Backend DRAFT convert** (channel/schedule params + migration): breaks BDD, needs versioning — reject.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `.../ideas/presentation/views/IdeasView.vue` | Modified | Decompose |
| `.../ideas/presentation/components/IdeaBoard|Lane|Card.vue` | New | Board |
| `.../ideas/application/useIdeaDragAndDrop.ts` | New | DnD composable |
| `.../ideas/presentation/components/IdeaComposerModal.vue` | New | Composer |
| `.../ideas/infrastructure/ideas.store.ts` | Modified | Draft/handoff |
| `.../ideas/infrastructure/http/IdeasController.kt` | Modified | Keep legacy |
| `.../i18n/locales/{en,es}/ideas.ts` | Modified | Copy |
| `features/ideas-canvas.feature` | Modified | BDD |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| DALLAY-435 WIP drift (since 2024-07-31) | Med | Gate on parent/blocks; rebase PR1 |
| `ConvertIdea` legacy (first ACTIVE → NOW) | High | Keep legacy; additive prefill; deprecate docs PR3 |
| Empty-channel i18n (throws) | Med | Disable convert + CTA; ES no fixed width |
| Feature cross-import ideas→publishing | Med | Barrel/composition-root only |
| 400-line budget | High | 3 PRs required |

## Rollback Plan

Each PR reverts alone: PR3→immediate convert; PR2→plain detail; PR1→monolith. No migrations; `git revert` suffices.

## Dependencies

- DALLAY-435; publishing primitives; Modulith ideas→publishing; `BddDatabaseSupport`

## Success Criteria

- [ ] PR1 board/DnD (AC 1–10): 3 cols, `data-dnd-*`, rollback, settings
- [ ] PR2 composer (AC 11–28): markdown, chips, link validation, schedule, preview
- [ ] PR3 handoff (AC 29–36): prefill, read-only channel, empty CTA, legacy 200
- [ ] Cross-cutting (AC 37–42): en/es, a11y, Vitest + BDD `@ideas @smoke @fast` pass
- [ ] No `shared/web` extraction; 42 AC (6 screens, Gherkin) in `idea-canvas` + `idea-composer` deltas
