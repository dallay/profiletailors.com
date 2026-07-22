## Exploration: scheduler-url-state-standard

### Current State

The scheduler already uses canonical routes via `vue-router` and a domain composable (
`useCalendarUrl.ts`) for `surface`, `date`, `timezone`, `status`, `q`, and `channels[]`.
`AppShell.vue` and `SidebarChannelsSection.vue` already treat the URL as the source of truth for
channel selection, and `SchedulerView.vue` refetches calendar data when URL state changes.

The main gap is that the current URL-state contract is narrower and partially drifted from the
archived `scheduler-url-addressable` intent. `useCalendarUrl.ts` only supports statuses
`all|queued|published|cancelled`, does not model modal state, and still contains compatibility logic
for `/scheduler/calendar/day` even though the main spec says day is not a top-level route.
`SchedulerView.vue` owns post-detail modal state locally (`isDetailModalOpen`, `detailPublication`)
with no `postId` query param, so refresh/share/back-forward cannot restore an opened post. There is
also no canonicalization path that closes the modal when `surface`, `date`, `timezone`, `status`,
`q`, or `channels[]` make the selected post non-resolvable in the current filtered context.

There is also no durable docs page in `docs/` describing the scheduler-first URL-state standard for
future SPA work. Existing documentation standards require an English `docs/` page with the repo’s
Overview → Changes → Usage → Troubleshooting → References structure, and `docs/README.md` would need
an index entry once that guidance is added.

### Affected Areas

- `apps/web/app/src/composables/useCalendarUrl.ts` — current route-state codec; needs expansion to
  cover the iteration-1 standard and modal query state.
- `apps/web/app/src/views/SchedulerView.vue` — currently owns detail modal state locally and will
  need route-driven `postId` resolution plus auto-close/canonicalization behavior.
- `apps/web/app/src/components/PostDetailModal.vue` — modal remains presentation-focused, but
  behavior will be affected because open/close becomes URL-driven instead of local-only.
- `apps/web/app/src/components/layout/AppShell.vue` — already scheduler-aware; likely remains the
  integration point for route-owned channel selection and must stay aligned with the expanded URL
  contract.
- `apps/web/app/src/components/sidebar/SidebarChannelsSection.vue` — active state assumes a single
  `activeChannelId`; iteration 1 includes `channels[]`, so multi-select semantics need to be
  clarified in specs/design.
- `apps/web/app/src/router/index.ts` — currently still defines `/scheduler/calendar/day`; this
  conflicts with the archived direction and should be reconciled in the next phases.
- `apps/web/app/src/composables/useCalendarUrl.test.ts` — current tests cover route basics but are
  weak on typed normalization and have no modal-state or canonical-close coverage.
- `apps/web/app/src/views/SchedulerView.test.ts` — has coverage for URL-driven fetches and local
  modal opening, but not for `postId` in the URL, invalid-detail closure, or URL canonicalization
  after filter/date changes.
- `apps/web/app/e2e/specs/scheduler-url-addressable.spec.ts` — validates canonical scheduler routes
  and URL filters, but not shareable/restorable post-detail links.
- `apps/web/app/e2e/specs/scheduler-post-interaction.spec.ts` — exercises modal behavior today; good
  base for adding deep-link and auto-close regression scenarios later.
- `openspec/changes/archive/2026-06-24-scheduler-url-addressable/*` — prior archive establishes the
  route-first baseline and highlights where the new change should extend, not replace, the existing
  model.
- `docs/README.md` — should gain an entry for the durable scheduler URL-state guidance.
- `docs/architecture/` or a new focused `docs/` page — recommended location for durable frontend
  guidance about scheduler-first URL state.

### Approaches

1. **Extend the existing scheduler route codec** — keep `vue-router` + `useCalendarUrl`, add
   iteration-1 fields and modal ownership to the same scheduler domain composable.
    - Pros: matches team direction, builds on shipped route-first architecture, minimizes conceptual
      churn, keeps scheduler behavior centralized.
    - Cons: requires careful refactor of `useCalendarUrl.ts` and `SchedulerView.vue`; current tests
      will need meaningful upgrades.
    - Effort: Medium

2. **Add a second composable just for post-detail URL state** — keep current route codec for
   filters/surface/date and layer a dedicated `postId` composable on top.
    - Pros: smaller isolated surface for modal behavior, potentially easier incremental rollout.
    - Cons: risks splitting one scheduler URL contract across multiple owners, increases
      coordination/canonicalization complexity when filters invalidate `postId`.
    - Effort: Medium

### Recommendation

Use **Approach 1: extend the existing scheduler route codec**.

The route-first foundation from `scheduler-url-addressable` is already in place, so the clean next
step is to evolve `useCalendarUrl.ts` into the single scheduler URL-state contract for iteration 1.
That means: normalize the allowed filter set to the desired standard, add `postId` query ownership,
remove or explicitly deprecate the lingering `/scheduler/calendar/day` compatibility path, and let
`SchedulerView.vue` derive modal visibility from route-resolved publication state. For durable
guidance, add a docs page under `docs/architecture/` — recommended name:
`docs/architecture/scheduler-url-state-standard.md` — and link it from `docs/README.md`.

### Risks

- `postId` canonicalization is not trivial: a post may disappear because of filters, date range,
  timezone shifts, deletion, or refetch timing, so the spec/design must define when to wait for data
  vs when to close immediately.
- Current `channelIds` support is plural in the URL but several UI/store flows still behave like
  single-channel filtering (`channelIds[0]`, `activeChannelId`), which can create ambiguity for
  iteration-1 semantics.
- The leftover `/scheduler/calendar/day` route indicates drift between implementation and prior
  spec/archive; proposal/spec should resolve whether it is removed, redirected, or retained only as
  compatibility.
- Existing unit tests around `useCalendarUrl` are fairly shallow and may give false confidence
  unless strengthened during later phases.
- Durable docs location needs an explicit decision so guidance does not get lost between
  `docs/architecture/` and generic top-level docs.

### Ready for Proposal

Yes — proceed to proposal. Tell the user the codebase already has the route-first scheduler
baseline, and this change should focus on standardizing the iteration-1 query contract, adding
`postId`-driven modal addressability with auto-close canonicalization, reconciling the lingering
day-route drift, and documenting the standard durably in
`docs/architecture/scheduler-url-state-standard.md` plus `docs/README.md`.
