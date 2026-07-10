# Tasks: Scheduler-first URL-state standard

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 420-560 |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 tests+router/controller → PR 2 SchedulerView/UI → PR 3 e2e+docs |
| Delivery strategy | feature-branch-chain |
| Chain strategy | feature-branch-chain |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: feature-branch-chain
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Lock canonical routes and URL codec with tests | PR 1 | Base to main; TDD on router + `useCalendarUrl` |
| 2 | Move modal ownership to route state | PR 2 | Depends on PR 1; `SchedulerView` + shell/header alignment |
| 3 | Prove deep-link behavior and publish guidance | PR 3 | Depends on PR 2; E2E + docs/index |

## Phase 1: Foundation / TDD

- [x] 1.1 Add failing route tests in `apps/web/app/src/composables/useCalendarUrl.test.ts` for canonical `week|month|list`, legacy `/scheduler/calendar/day` redirect, `channels[]`, `postId`, and push-vs-replace rules.
- [x] 1.2 Add failing route compatibility tests in `apps/web/app/src/router/index.spec.ts` coverage for `/scheduler` and `/scheduler/calendar/day` preserving query state.
- [x] 1.3 Implement the expanded scheduler codec in `apps/web/app/src/composables/useCalendarUrl.ts`: parse/serialize `date`,`timezone`,`status`,`q`,`channels[]`,`postId`, canonical cleanup, and controller helpers.
- [x] 1.4 Update `apps/web/app/src/router/index.ts` to redirect `/scheduler` and `/scheduler/calendar/day` to canonical week routes without dropping scheduler query params.

## Phase 2: Route-driven scheduler behavior

- [x] 2.1 Add failing view tests in `apps/web/app/src/views/SchedulerView.test.ts` for route-owned `postId` open, replace-based stale close after fetch settle, and back/forward-safe modal behavior.
- [x] 2.2 Refactor `apps/web/app/src/views/SchedulerView.vue` so selected post/detail modal state is derived from `useCalendarUrl()` and fetched publications instead of local-only modal flags.
- [x] 2.3 Implement fetch reconciliation in `apps/web/app/src/views/SchedulerView.vue` so date/filter/timezone/surface changes remove stale `postId` only after the active fetch proves the post unavailable.
- [x] 2.4 Align `apps/web/app/src/components/layout/AppShell.vue` and `apps/web/app/src/components/CalendarHeader.vue` with controller helpers so channel/date/surface interactions keep canonical `channels[]` and history semantics.

## Phase 3: Verification / regression coverage

- [x] 3.1 Extend `apps/web/app/e2e/specs/scheduler-url-addressable.spec.ts` to cover refresh/share restoration, `/scheduler/calendar/day` compatibility canonicalization, and query cleanup after clearing filters.
- [x] 3.2 Extend `apps/web/app/e2e/specs/scheduler-post-interaction.spec.ts` to cover click-to-open `postId`, refresh restore, back/forward modal restoration, and stale-selection auto-close.
- [x] 3.3 Run focused verification for app scheduler paths: Vitest on `useCalendarUrl` and `SchedulerView`, then Playwright scheduler specs, capturing any route/history regressions.

## Phase 4: Documentation / handoff

- [x] 4.1 Create `docs/architecture/scheduler-url-state-standard.md` with Overview, Changes, Usage, Troubleshooting, and References for canonical surfaces, query contract, and push-vs-replace rules.
- [x] 4.2 Update `docs/README.md` to index the scheduler URL-state standard and note the canonical scheduler route family for future SPA work.
