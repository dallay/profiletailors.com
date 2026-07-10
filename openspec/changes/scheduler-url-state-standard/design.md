# Design: Scheduler-first URL-state standard

## Technical Approach
Make the scheduler URL the only durable state owner for scheduler navigation and detail context. Extend `apps/web/app/src/composables/useCalendarUrl.ts` from filter/date codec into a typed scheduler route contract for `surface`, `date`, `timezone`, `status`, `q`, `channels[]`, and `postId`. `SchedulerView.vue` will stop owning post-detail open/close state directly; instead it will resolve the selected publication from URL state plus the currently fetched publication set, then canonicalize stale URLs after fetch reconciliation.

## Architecture Decisions
| Decision | Alternatives considered | Rationale |
|---|---|---|
| Keep one scheduler URL controller in `useCalendarUrl.ts` | Separate modal-state composable | One contract avoids split ownership for canonicalization, history semantics, and tests. |
| Canonical path set is week/month/list; day route becomes compatibility redirect | Keep `/scheduler/calendar/day` as a real surface | Current composable already maps day → week. Keeping a fake surface preserves drift; redirecting removes ambiguity. |
| `postId` invalidation happens after relevant fetch settles, not immediately on route change | Close modal before refetch; keep stale modal indefinitely | Immediate close flickers during valid transitions; indefinite stale state breaks shareability and back/forward. |
| Use `push` for context navigation and `replace` for refinements/canonical cleanup | Push everything; replace everything | Back button should traverse view/date/open-detail milestones, not every filter normalization. |

## Data Flow
1. Router enters scheduler route.
2. `useCalendarUrl` normalizes path/query to `SchedulerUrlState` and exposes helpers: `setSurface`, `setDate`, `setFilters`, `openPostDetail(postId)`, `closePostDetail()`, `canonicalize()`.
3. `SchedulerView` watches normalized state, computes fetch window, calls `publishingStore.fetchCalendar(...)`.
4. After fetch resolves, `SchedulerView` derives `selectedPublication = filteredPublications.find(pub.id===postId) ?? null`.
5. If `postId` exists and the fetch corresponding to current route has settled with no matching publication, call `closePostDetail({ replace: true })`.

```text
route -> useCalendarUrl -> SchedulerView fetch plan -> publishingStore.fetchCalendar
   \-> canonical query/path ---------------------------> filtered publications
                                                        -> selectedPublication -> PostDetailModal
```

## File Changes
| File | Action | Description |
|---|---|---|
| `apps/web/app/src/composables/useCalendarUrl.ts` | Modify | Expand route state type with `postId`; centralize parse/serialize/canonicalization/history policy. |
| `apps/web/app/src/views/SchedulerView.vue` | Modify | Derive detail modal state from route + fetched publications; defer invalidation until active fetch settles. |
| `apps/web/app/src/router/index.ts` | Modify | Redirect `/scheduler` to week as today; redirect `/scheduler/calendar/day` to canonical week path preserving query. |
| `apps/web/app/src/components/layout/AppShell.vue` | Modify | Keep sidebar channel navigation aligned with canonical `channels[]` contract via controller helpers. |
| `apps/web/app/src/components/CalendarHeader.vue` | Modify | Stay contract-consistent; still emits scheduler filters, with single-select UI mapped to first `channelIds` entry for iteration 1. |
| `apps/web/app/src/composables/useCalendarUrl.test.ts` | Modify | Add typed contract, `postId`, canonical redirect, and push/replace tests. |
| `apps/web/app/src/views/SchedulerView.test.ts` | Modify | Add route-driven modal resolution and invalidation timing tests. |
| `apps/web/app/e2e/specs/scheduler-url-addressable.spec.ts` | Modify | Cover deep-link restoration and canonical URL cleanup. |
| `apps/web/app/e2e/specs/scheduler-post-interaction.spec.ts` | Modify | Cover open/close/back-forward modal behavior. |
| `docs/architecture/scheduler-url-state-standard.md` | Create | Durable frontend URL-state guidance. |
| `docs/README.md` | Modify | Index new scheduler URL-state doc. |

## Interfaces / Contracts
```ts
interface SchedulerUrlState {
  surface: 'calendar-week' | 'calendar-month' | 'list'
  date: string
  timezone: string
  status: 'all' | 'queued' | 'published' | 'cancelled'
  q: string
  channelIds: string[]
  postId: string | null
}
```
Canonical query rules: omit defaults, trim `q`, dedupe `channels[]`, preserve `postId` only when non-empty, compare legacy `channels` as equivalent input but always emit `channels[]`.

## Testing Strategy
| Layer | What to Test | Approach |
|---|---|---|
| Unit | Query parse/serialize, canonical equivalence, `postId` helpers, day-route redirect behavior, push vs replace | Vitest on `useCalendarUrl.test.ts` with injected mock router/route. |
| View / Integration | Modal opens from `postId`; closes only after refetch proves invalid; delete/context/date/filter transitions remove stale `postId` with `replace` | Vue Test Utils in `SchedulerView.test.ts` with controlled fetch promises/store state. |
| E2E | Deep link opens detail after refresh, back/forward restores modal, stale `postId` is removed when filters/workspace/date exclude the post | Playwright scheduler specs using real router behavior. |

## Migration / Rollout
No data migration required. Roll out behind normal app deployment. Compatibility window: accept legacy `channels` and `/scheduler/calendar/day` on read, but immediately redirect to canonical route/query on first render.

## Open Questions
- [ ] Should iteration 1 document `channels[]` as future-multi-select while UI remains single-select, or explicitly scope behavior to first entry only?
- [ ] Do we want a small fetch-status token in `SchedulerView` or store-level request identity to guard against late responses clearing `postId` incorrectly?
