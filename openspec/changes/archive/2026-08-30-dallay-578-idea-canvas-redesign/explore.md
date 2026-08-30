# Exploration: DALLAY-578 — Redesign Idea Canvas and unify Idea Composer with publishing composer UX

Branch hint: `feature/dallay-578-ideas-redesign-idea-canvas-and-unify-idea-composer-with`
Linear: https://linear.app/dallay/issue/DALLAY-578/ideas-redesign-idea-canvas-and-unify-idea-composer-with-publishing

## Current State

Frontend `IdeasView.vue` (808 lines at `apps/web/app/src/modules/ideas/presentation/views/IdeasView.vue`) is confirmed monolith. Single SFC owns board rendering, columns, cards, DnD registration, quick-capture Dialog, detail Sheet, column-settings Dialog, tag/link parsing, convert and CRUD wiring. Template uses `Card` x3 columns with `data-dnd-column/card` refs and inline `draggable` attribute. All business logic lives in setup script.

Store `ideas.store.ts` (462 lines) is well-tested infrastructure adapter. Defines `DEFAULT_COLUMNS = raw/in-progress/done`, `normalizeColumnOrder`/`normalizeIdeas`/`reorderWithinList`, optimistic `moveIdea` with snapshot rollback on PATCH `/api/ideas/{id}/move` failure, and workspace-scoped `apiFetch` via `useAuthStore` + `useWorkspaceStore`. `createIdea` appends at column length, `convertIdea` expects `{ publicationId }`, `updateColumns` re-maps orphan ideas to fallback column. Helpers have unit coverage; store actions have BDD + unit coverage.

DnD uses `@atlaskit/pragmatic-drag-and-drop@1.8.1` element adapter exactly as described: `monitorForElements` global, `draggable` + `dropTargetForElements` per card/column, `getDropIndex` via `getBoundingClientRect` half-height, `watch(boardColumns)` re-registers with cleanup. Same pattern used in `SchedulerView/CalendarCell/ContentPipeline` — team has live precedent.

Tags/links handling: CSV `parseTags` (comma split) and line-based `parseLinks` (`label|url` or url-only). Domain `ideas/domain/idea.ts` defines `IdeaLink{url,label}`, `Idea{tags: string[], links: IdeaLink[], convertedToPublicationId}`. No markdown today; detail uses plain `Input`+`Textarea`.

Backend bounded context `server/smp/src/main/kotlin/com/profiletailors/smp/ideas/` is hexagonal-correct. Domain: `Idea`, `IdeaColumn`, `IdeaBoardConfig`, `IdeaBoardDefaults` (raw/in-progress/done). Application: `CreateIdeaHandler` (ensemble board via `boardConfigRepository`, picks `minBy order` column), `UpdateIdeaHandler`, `MoveIdeaHandler` (clamp + `normalizeIdeasInColumns` double-write), `DeleteIdeaHandler`, `ConvertIdeaHandler`, `UpdateColumnsHandler` + `IdeasQueryHandlers`/`IdeasMappers`/`IdeasExceptions`. Infrastructure: `IdeasController` (9 endpoints under `/api/ideas` v1), `R2dbcIdeaRepositories` (tags_json/links_json as JSON text, `converted_to_publication_id` FK to publications), `IdeasProblemDetailsHandler` (404/400). Module metadata `allowedDependencies = identity :: application, publishing :: application, tenancy :: application` — ideas→publishing allowed by design.

`ConvertIdeaHandler` confirmed picking first active channel: `mediator.send(ListConnectedChannelsQuery.active()).channels.firstOrNull()?.socialAccountId ?: throw InvalidIdeaColumnsException("At least one active social channel is required...")` then `mediator.send(CreatePublicationCommand.now(socialAccountId, title=idea.title, bodyText=fallbackBody))` where `fallbackBody = title + "\n\n" + notes + "\n\n" + tags.join #tag`. Returns `ConvertIdeaResult(publicationId)`. Controller contract `POST /api/ideas/{id}/convert -> ConvertIdeaResult`. No channel picker, no draft — immediate NOW publication.

Publishing context lives at `server/smp/.../publishing/` (domain `PublishingModels/PublishingPolicies`, application `PublishingApi` with `CreatePublicationCommand(now())`, `ListConnectedChannelsQuery`, infrastructure `PublishingControllers`). Frontend publishing composer `CreatePostModal.vue` is 1826 lines but already refactored: extractable composables `useComposerMediaPicker` (24k lines, store-injected), `useComposerScheduling`, `useComposerValidation`, `useComposerTextFormatting`, `useMarkdownEditor`, helpers `markdown.ts` (strip/normalize). Presentation primitives: `ComposerChannelSelector`, `ComposerSchedulePanel`, `ComposerMediaPickerShell`, `MarkdownToolbar`, `HashtagSuggestionPanel`, `PostPreviewPanel/LinkedInPostPreview`. Scheduling modes `NOW/NEXT_SLOT/SCHEDULED_AT` shared with backend `ScheduleMode`.

Shared design system: Nothing-inspired dark-first, Tailwind 4 (`@tailwindcss/vite@4.3.3`), `shadcn-vue@2.8.2`, tokens `bg-bg-primary/surface`, `text-text-display/body/secondary`, `border-border-visible/subtle`. Primitives used in IdeasView (`Button`, `Card`, `Badge`, `Input`, `Textarea`, `Dialog`, `Sheet`, `Select`) match composer. Light/dark supported.

Testing: frontend Vitest 3.2.7 + Playwright 1.62 (`test:e2e:scheduler/media:mocked`), IdeasView tests 666 lines mock DnD properly, store tests 316 lines cover helpers+loadBoard. Backend JUnit5+MockK+Kotest, Cucumber BDD `ideas-canvas.feature` (5 scenarios @ideas @smoke @fast, uses `BddDatabaseSupport` with `Bearer valid-token`, `Accept: application/vnd.api.v1+json`, `X-Workspace-Id`). `IdeasCommandHandlersTest` 400+ lines covers convert without notes/tags. No additional ideas migrations beyond `001-create-ideas.yaml` (idea_board_configs + ideas tables, FK to workspaces/publications, index workspace_column_order).

### Affected Areas
- `apps/web/app/src/modules/ideas/presentation/views/IdeasView.vue` — monolith to decompose; owns board, DnD, capture, detail, column settings, convert
- `apps/web/app/src/modules/ideas/infrastructure/ideas.store.ts` — add IdeaComposer draft state, scheduling, markdown normalize, handoff to publishing; keep workspace scoping
- `apps/web/app/src/modules/ideas/domain/idea.ts` — extend with composer fields (markdown body, scheduleMode) if idea composition needs richer shape; currently plain text
- `server/smp/src/main/kotlin/com/profiletailors/smp/ideas/application/IdeasCommandHandlers.kt` — ConvertIdeaHandler coupling to NOW; need handoff semantics (draft vs immediate)
- `server/smp/src/main/kotlin/com/profiletailors/smp/ideas/infrastructure/http/IdeasController.kt` — current convert contract immediate; proposal must decide deprecate vs extend
- `server/smp/src/main/kotlin/com/profiletailors/smp/ideas/domain/IdeaModels.kt` — Idea/IdeaColumn/IdeaBoardConfig unchanged unless board redesign changes defaults
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingApi.kt` — ListConnectedChannelsQuery/CreatePublicationCommand reused by convert/handoff
- `apps/web/app/src/modules/publishing/presentation/components/CreatePostModal.vue` — source of reusable primitives; extract shared layer if IdeaComposer imports them
- `apps/web/app/src/modules/publishing/application/useComposer*` + `presentation/components/composer/*` — direct reuse candidates (scheduling, validation, markdown, media picker)
- `apps/web/app/src/shared/i18n/locales/{en,es}/ideas.ts` — 62 keys today; composer unification adds new copy
- `server/smp/src/test/resources/features/ideas-canvas.feature` + `bdd/glue/IdeasBddSteps.kt` — must extend for new composer/handoff flows
- `openspec/specs/publishing/spec.md` + `composer-media-picker/spec.md` + `composer-preview/spec.md` — publishing specs own the source primitives

### Approaches
1. **Decompose board + extract IdeaComposer as publishing-composer twin (recommended)** — Board decomposition first: `IdeaBoard.vue` + `IdeaColumn.vue` + `IdeaCard.vue` + `useIdeaDragAndDrop.ts` (mirrors publishing extraction of `useComposerMediaPicker`). Then IdeaComposer: new `IdeaComposer.vue` Dialog/Sheet reusing `MarkdownToolbar`, `useMarkdownEditor`, `useComposerScheduling`, `useComposerValidation`-shape, `ComposerChannelSelector` (read-only or hidden for ideas), `ComposerSchedulePanel`. Links/Tags become structured fields with markdown body. Handoff PR: idea→composer prefill instead of immediate `POST /convert`; keep `POST /convert` for backward compat but add frontend handoff that opens publishing `CreatePostModal` prefilled via props or shared store.
   - Pros: reuses proven extractable composables; no backend breaking change; board becomes testable; publishing primitives stay ownership-clean; aligns with Linear 3-PR slicing
   - Cons: IdeaComposer initially duplicates some publishing styling (could diverge); need to decide store dependency direction (ideas importing publishing store)
   - Effort: Medium (board low, composer medium, handoff low)

2. **Shared composer package** — Factor common composer UI into `shared/web` or `app/src/shared/composer` (shared `ComposerShell`, `useComposerFoundation`, schedule panel, markdown toolbar) consumed by both `CreatePostModal` and `IdeaComposer`.
   - Pros: DRY, single schedule/validation truth; future providers benefit
   - Cons: creates shared abstraction before stabilization; violates `shared/web` framework-neutral rule (currently browser-safe, not Vue-Pinia); risks premature coupling; Boy Scout Rule violation — extract after second consumer stabilizes
   - Effort: High

3. **Backend-first convert redesign (draft publications)** — Change `ConvertIdeaHandler` to create DRAFT publication with selectable channel/schedule instead of NOW, extend `POST /ideas/{id}/convert` to accept `channelId/scheduleMode/scheduledFor` or deprecate convert entirely in favor of frontend handoff. Add migration for idea→publication draft linkage.
   - Pros: enables explicit channel picker and scheduled handoff; removes surprising immediate publish
   - Cons: breaks existing BDD+frontend contract; requires new endpoint versioning; publish-side validation (email verification TODO) leaks into ideas; highest coordination cost
   - Effort: High

### Recommendation
Approve **Approach 1** sliced as **3 chained PRs** exactly as Linear proposes: PR1 board decomposition, PR2 IdeaComposer twin, PR3 handoff. This is viable and low-risk — verified: publishing scheduling/validation/markdown primitives are already composable and store-injected; board DnD pattern is isolated; ideas→publishing dependency is allowed at Modulith level and frontend can import publishing store via feature barrel or composition root (router-level handoff keeps dependency explicit and rollback-safe). Do NOT create a shared package in the same change (Approach 2) — defer until IdeaComposer proves stable, then extract. Do NOT break `POST /convert` in PR1/PR2; mark it legacy in PR3 while introducing prefill handoff.

### Risks
- **DALLAY-435 still In Progress since 2024-07-31** — follow-up 578 may conflict with unfinished scope; verify Linear parent/blocks before merging PR1
- **Convert surprise** — current convert publishes NOW immediately with first active channel; users may have relied on it; handoff change needs product decision: keep legacy endpoint vs deprecate
- **Frontend feature cross-import** — ideas importing `publishing.store` internally violates import rules unless via barrel/composition root; need explicit PR3 dependency note
- **Workspace/channel coupling** — convert throws if no ACTIVE channel; IdeaComposer must handle empty-channel state gracefully (disable convert, show connect CTA)
- **i18n length** — Spanish copy longer than English (ideas.ts shows drift); new composer copy must avoid fixed-width containers
- **400-line review budget** — board decomposition + IdeaComposer together exceed 400 lines; chained PRs required (PR1 ~200-250, PR2 ~350, PR3 ~150 with tests)
- **Linear spec not in repo** — 42 acceptance criteria + mockups live only in Linear; proposal must snapshot them into `openspec/changes/.../proposal.md` or risk drift

### Ready for Proposal
Yes — current reality verified, reuse map confirmed, slicing validated. Next: `sdd-propose` to snapshot Linear criteria into proposal, define handoff UX (prefill vs draft), and lock PR1 board component boundaries (IdeaBoard/IdeaColumn/IdeaCard/useIdeaDragAndDrop).

### Open Questions for Proposal
- Should `POST /api/ideas/{id}/convert` remain as legacy NOW path or be deprecated behind handoff?
- Does IdeaComposer need media attachment reuse now or defer (composer media picker is ready but ideas has no asset FK)?
- Channel selection in IdeaComposer: reuse publishing channel selector read-only, or allow picker before handoff?
- Board defaults: keep `raw/in-progress/done` or apply redesign mockup defaults; migration needed?
- Do tags/links stay CSV/line format or become structured chips/links with validation?

### Reuse Opportunities Confirmed
- `apps/web/app/src/modules/publishing/application/useMarkdownEditor.ts` + `markdown.ts` — strip/normalize, toolbar wiring
- `useComposerScheduling.ts` / `ComposerSchedulePanel.vue` — NOW/NEXT/SCHEDULED_AT panel
- `useComposerValidation.ts` — char limit + attachment validation pattern (adapt for ideas)
- `ComposerChannelSelector.vue` — active channel chip
- `MarkdownToolbar.vue` + `PostPreviewPanel.vue` — preview parity
- `useComposerMediaPicker.ts` pattern — store-injected composable for testability (apply to `useIdeaDragAndDrop`)

### Evidence Snapshot
- `apps/web/app/src/modules/ideas/presentation/views/IdeasView.vue:1-808` — monolith verified
- `server/smp/.../ideas/application/IdeasCommandHandlers.kt:136-182` — first active channel → NOW publication verified
- `server/smp/.../ideas/infrastructure/http/IdeasController.kt:86-88` — convert contract verified
- `server/smp/.../db/changelog/ideas/001-create-ideas.yaml` — schema verified
- `apps/web/app/src/modules/publishing/presentation/components/CreatePostModal.vue:1-1826` — primitive catalog verified
- `server/smp/src/test/resources/features/ideas-canvas.feature:1-35` — BDD coverage verified
