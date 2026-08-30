# Tasks: DALLAY-578 Canvas Redesign

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~710 (PR1 ~230, PR2 ~340, PR3 ~140) |
| 400-line budget risk | Medium (PR2 near limit) |
| Chained PRs recommended | Yes |
| Suggested split | PR1 → PR2 → PR3 stacked |
| Delivery strategy | auto-chain |
| Chain strategy | github-stacked-prs |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: github-stacked-prs
400-line budget risk: Medium

### Suggested Work Units

| Unit | Goal | PR | Notes |
|------|------|----|-------|
| 1 | Board + DnD | PR1 | `578/pr1-board` base `main`; Vitest+BDD |
| 2 | Composer unified | PR2 | `578/pr2-composer` base `pr1-board`; reuse publishing, no shared pkg |
| 3 | Handoff + guards | PR3 | `578/pr3-handoff` base `pr2-composer`; PATCH associate, Playwright |

## Phase 1: PR1 Board (~230)

- [x] 1.1 IdeaCard — title/clamp, 3 tags+N, `🔗 N`, badge, `data-dnd-draggable` | AC7-10 | `IdeaCard.vue` ~40 TDD:yes
- [x] 1.2 IdeaLane — 280-320px, sticky header, v-scroll, empty `No ideas yet`+Add, `data-dnd-column` | AC3 | `IdeaLane.vue` ~45 TDD:yes
- [x] 1.3 IdeaBoard — h-scroll, skeleton, no spinner | AC1-3 | `IdeaBoard.vue` ~50 TDD:yes
- [x] 1.4 useIdeaDragAndDrop — store-injected `monitorForElements/draggable/dropTarget`, `getDropIndex` half-height, watch+cleanup | AC4-6 | `useIdeaDragAndDrop.ts` ~55 TDD:yes
- [x] 1.5 Slim IdeasView — thin orchestrator, workspace watch, remove capture/Sheet/settings | AC1-6,34 | `IdeasView.vue` ~30 TDD:yes
- [x] 1.6 Rollback+fallback — optimistic `moveIdea` snapshot/restore+toast, `PUT /columns` minBy remap | AC6,34-35 | `ideas.store.ts` ~20 TDD:yes

## Phase 2: PR2 Composer (~340)

- [x] 2.1 useIdeaComposer — draft, `useMarkdownEditor`+`markdown.ts`, `normalizeForSubmission`, dedupe, https guard, dup-save guard | AC16-20,22 | `useIdeaComposer.ts` ~70 TDD:yes
- [x] 2.2 IdeaComposerModal shell — single Dialog create|edit, composition-root props, `MarkdownToolbar`+`SchedulePanel` | AC11-15 | `IdeaComposerModal.vue` ~90 TDD:yes
- [x] 2.3 Tags/links UX — chips trim/dedupe, links `label|url` https validate | AC19-20 | `IdeaComposerModal.vue` ~45 TDD:yes
- [x] 2.4 Column+validation — column selector persist, title required+error, dup guard disables | AC16-18,22 | `IdeaComposerModal.vue` ~40 TDD:yes
- [x] 2.5 Delete+a11y — confirm dialog, cancel preserves, DELETE, trap focus, Escape if dirty | AC21 | `IdeaComposerModal.vue` ~30 TDD:yes
- [x] 2.6 i18n+schedule — `useComposerScheduling` parity, en/es `ideas.ts` no fixed width | AC37-38 | `ideas.ts` ~25 TDD:yes

## Phase 3: PR3 Handoff (~140)

- [x] 3.1 buildPrefill — `title+"\n\n"+notes+"\n\n"+#tags` dedupe hashtags | AC23-25 | `useIdeaComposer.ts` ~15 TDD:yes
- [x] 3.2 Handoff wiring — unsaved POST /ideas then prefill→CreatePostModal via root, publishing owns channel | AC23-24,26-28 | `IdeasView.vue` ~35 TDD:yes
- [x] 3.3 Empty-channel guard — disable Create Post+CTA, hide exception, ES i18n | AC28 | `IdeaComposerModal.vue` ~20 TDD:yes
- [x] 3.4 Associate — PATCH `convertedToPublicationId`, no auto-move/delete, cancel null | AC29-33 | `ideas.store.ts` ~20 TDD:yes
- [x] 3.5 Legacy+BDD — keep POST /convert 200 legacy, feature `@ideas @smoke @fast` | AC legacy | `IdeasController.kt`+feature ~15 TDD:yes

## Phase 4: Verification

- [x] 4.1 Playwright E2E — create→edit→move→handoff→associate→no-move, empty CTA, keyboard | AC39 | `ideas-handoff.spec.ts` ~40 TDD:yes
- [x] 4.2 Vitest+a11y — board/card/composer Vitest, trap/Escape/keyboard, no shared/web | AC38,40-42 | `tests/**` ~25 TDD:yes
