# Tasks: Reactive Calendar Browser Sync

## Review Workload Forecast

| Field | Value |
|---|---|
| Review budget | 400 changed lines unless project configuration says otherwise |
| Estimated workload | High; frontend lifecycle, backend contract, tests, and compliance evidence cross boundaries |
| Chained PRs recommended | Yes |
| Chain strategy | github-stacked-prs or dependency-ordered feature branches |
| Work-unit balance | Unit 1 owns pure contracts and backend revision metadata; Unit 2 owns store synchronization and storage hardening; Unit 3 owns UI lifecycle, editor conflict UX, E2E, and docs |
| Decision needed before apply | Yes — confirm the three-unit delivery shape and the conflict UX wording before source implementation |

### Suggested Work Units

| Unit | Goal | Likely review slice | Dependencies |
|---|---|---|---|
| 1 | Pure browser contracts plus backend `updatedAt`/expected-revision contract | Small contract-focused slice | None |
| 2 | Store revalidation, invalidation, and authenticated storage boundary | State/infrastructure slice | Unit 1 |
| 3 | Scheduler/editor wiring, acceptance tests, and compliance reconciliation | User-visible integration slice | Units 1–2 |

## Phase 1: Contracts and RED tests

- [ ] 1.1 RED: add calendar-range tests covering week Sunday-to-next-Sunday exclusive bounds, month first-to-next-month exclusive bounds, timezone conversion, DST/positive-offset day stability, and malformed URL dates.
- [ ] 1.2 GREEN: create `calendarRange.ts` using `@internationalized/date`; make `SchedulerView` integration wait until the utility contract is stable.
- [ ] 1.3 RED: add reactive-clock tests with fake timers and synthetic `visibilitychange` events proving visible ticks, hidden pause, visible-return signal, and cleanup.
- [ ] 1.4 GREEN: create `useReactiveClock.ts` and preserve the current composer scheduling behavior through the shared clock seam.
- [ ] 1.5 RED: add broadcast contract tests for minimal serialized payloads, allowed reasons, invalid optional identifiers, unknown message types/fields, foreign workspace filtering, and absent `BroadcastChannel`.
- [ ] 1.6 GREEN: create `calendarBroadcast.ts` with runtime guards and feature detection; no unsafe casts or lint suppressions.
- [ ] 1.7 RED: add backend tests proving `updatedAt` is serialized for calendar/mutation responses and `expectedUpdatedAt` is accepted on edit requests.
- [ ] 1.8 GREEN: add compatible API fields, map persisted `PublicationDraft.updatedAt`, and bind the optional edit revision in `PublishingControllers.kt`.
- [ ] 1.9 RED: add handler tests for matching revision, mismatching revision with no repository write, omitted expected revision compatibility, and workspace-scoped lookup.
- [ ] 1.10 GREEN: implement the existing error-convention revision conflict in the edit handler; do not add a parallel authorization path or migration.

## Phase 2: Store synchronization and storage hardening

- [ ] 2.1 RED: add store tests proving authenticated startup/fetch/mutation never reads or writes unscoped `pt_publications` and an authenticated REST failure retains canonical in-memory data rather than substituting local data.
- [ ] 2.2 GREEN: isolate authenticated publication state from `pt_publications`; retain or remove anonymous fallback only according to the explicit validated branch documented in design.
- [ ] 2.3 RED: add store tests for a successful local mutation emitting one minimal invalidation with the active workspace identity and no content fields.
- [ ] 2.4 GREEN: wire store mutation success paths to the broadcast publisher and keep server response data as the local snapshot.
- [ ] 2.5 RED: add coalescing tests proving several same-workspace invalidations schedule one fetch, foreign messages schedule none, and URL/range changes use the latest range at execution time.
- [ ] 2.6 GREEN: add the narrow store revalidation coordinator, reusing `fetchCalendar()` and `latestCalendarFetchId`; expose only the feature's required public action.
- [ ] 2.7 RED: add overlapping fetch tests proving a slower older response cannot overwrite a newer response after invalidation or visibility refresh.
- [ ] 2.8 GREEN: connect the coordinator to the existing stale-response guard without introducing a competing request ID or response merge path.

## Phase 3: Scheduler and composer lifecycle

- [ ] 3.1 RED: extend `SchedulerView.test.ts` for one shared range calculation across initial load, URL change, mutation completion, visibility return, and invalidation; include the final visible day.
- [ ] 3.2 GREEN: replace duplicated `SchedulerView.vue` range arithmetic with `calendarRange.ts` and route all refreshes through the coalesced store action.
- [ ] 3.3 RED: add component tests proving the scheduler stops hidden ticker work, refreshes once on visible return, and cleans up listeners/channel subscriptions.
- [ ] 3.4 GREEN: wire `useReactiveClock`, `document.visibilityState`, and `calendarBroadcast` into scheduler lifecycle with graceful unavailable-channel behavior.
- [ ] 3.5 RED: add composer scheduling tests proving the shared clock still updates minimum valid time and midnight rollover without an unconditional hidden-tab interval.
- [ ] 3.6 GREEN: migrate `useComposerScheduling.ts` to the shared reactive clock while preserving its public scheduling API.

## Phase 4: Stale-editor protection

- [ ] 4.1 RED: add `CreatePostModal` tests proving an opened publication revision is tracked, a remote revision change marks a conflict, and dirty text/assets/schedule values remain untouched.
- [ ] 4.2 GREEN: add editor revision/dirty-state tracking and an accessible conflict state with explicit user choices; never copy remote values into dirty controls automatically.
- [ ] 4.3 RED: add submit tests proving the app sends `expectedUpdatedAt`, does not auto-retry a conflict, and preserves the form after a 409-style response.
- [ ] 4.4 GREEN: map `updatedAt`/conflict errors through the store and update the editor submission path.
- [ ] 4.5 RED: add tests for a clean editor receiving a remote update and for explicit discard/refresh behavior, if that action is included in the approved UX.
- [ ] 4.6 GREEN: implement only the approved explicit action and keep conflict handling local to the editor surface.

## Phase 5: Acceptance and documentation

- [ ] 5.1 RED: update scheduler mock fixtures so authenticated E2E setup no longer depends on `pt_publications`; prove the test fails if the route-backed source is not updated.
- [ ] 5.2 GREEN: use route-backed calendar fixtures or live Pinia state injection with explicit workspace identity; remove authenticated local-storage canonical writes.
- [ ] 5.3 RED: add Playwright coverage for returning from hidden state, same-workspace cross-tab invalidation, burst coalescing, foreign-workspace isolation, and malformed/unavailable channel fallback.
- [ ] 5.4 GREEN: make scheduler browser-sync acceptance scenarios pass with deterministic waits and no arbitrary sleeps.
- [ ] 5.5 RED: add Playwright stale-editor coverage for remote change while dirty and explicit non-overwrite behavior.
- [ ] 5.6 GREEN: finish editor conflict wiring and page-object assertions.
- [ ] 5.7 Update `docs/compliance/data-inventory.md` and `data-inventory.yaml` to describe the implemented authenticated `pt_publications` boundary; do not document planned behavior as implemented.
- [ ] 5.8 Run focused app/backend tests and inspect changed-code static analysis before broad gates.
- [ ] 5.9 Run `pnpm --filter app lint`, `pnpm --filter app type-check`, app build, focused/backend checks, and the scheduler Playwright lane; record local results exactly.
- [ ] 5.10 Verify that `publication-calendar-sse` files and behavior were not modified and note it as deferred in the final report.
- [ ] 5.11 Complete OpenSpec verification and acceptance QA artifacts; archive only after no blocking findings remain.

## Completion Criteria

- [ ] All requirements in the three delta specs have at least one passing test or explicitly recorded unavailable evidence.
- [ ] Strict-TDD order is visible in the implementation history or apply report: RED, GREEN, then refactor.
- [ ] No new source comments, suppressions, unsafe casts, explicit `any`, weakened tests, or configuration bypasses.
- [ ] No database migration was added because the existing `updated_at` column is reused and verified.
- [ ] Local, CI, remote, and deployed evidence are distinguished in verification/QA reports.
