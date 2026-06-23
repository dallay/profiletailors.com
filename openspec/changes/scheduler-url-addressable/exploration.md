## Exploration: scheduler calendar URL addressability

### Current State
The scheduler is a single authenticated `/scheduler` route rendered by `SchedulerView.vue`. The route carries no path params or query params today, so refresh/back-forward cannot restore calendar sub-view, date, or filters.

`SchedulerView.vue` owns `calendarView` (`month|week|day`) and `currentBaseDate` as local refs. It calls `publishingStore.fetchCalendar()` only once on mount using a fixed `now -> +3 months` range, with no watcher on view/date/filter changes. That means navigation and filters do not currently drive refetches.

`publishing.ts` already stores scheduler-adjacent state for `viewMode`, `userTimezone`, `filterTag`, `filterChannel`, `filterPostType`, and `filterSocialAccountId`. The store computes `calendarFilters` only for backend-supported fields (`status`, `socialAccountId`) and `fetchCalendar()` always injects the store timezone into the request. Local fallback filtering also reads store state directly.

`CalendarHeader.vue` mutates Pinia refs directly via `v-model` and button clicks (`viewMode`, `userTimezone`, `filterSocialAccountId`, `filterPostType`) instead of routing events upward. `AppShell.vue` sidebar channel actions also mutate store filters directly, then push `/scheduler`. Sidebar active state is keyed off `filterChannel`, which is UI-only and not part of backend calendar filters.

Current router architecture is flat: `/scheduler` is the only scheduler route in `src/router/index.ts`. Auth guard redirects to `/login?redirect=<fullPath>`, so preserving scheduler deep links will work once those routes exist.

### Affected Areas
- `apps/web/app/src/router/index.ts` — define scheduler route family, redirects, and canonical names.
- `apps/web/app/src/views/SchedulerView.vue` — move local scheduler state to route-driven state, fetch by route, and canonicalize params.
- `apps/web/app/src/stores/publishing.ts` — decide which fields remain mutable store state vs derived request helpers; likely add typed scheduler query parsing/serialization helpers or accept explicit timezone/filters in `fetchCalendar()`.
- `apps/web/app/src/components/CalendarHeader.vue` — stop mutating route-relevant store refs directly; emit intent or bind to route-backed computed setters.
- `apps/web/app/src/components/layout/AppShell.vue` — sidebar channel clicks should navigate with query params instead of mutating store-first.
- `apps/web/app/src/components/sidebar/SidebarChannelsSection.vue` — active state currently depends on `filterChannel`; should derive from route-backed selected channel/provider.
- `apps/web/app/src/router/index.guard.test.ts` — update auth redirect assertions for deep scheduler URLs.
- `apps/web/app/src/router/index.spec.ts` — cover new route definitions/redirect behavior.
- `apps/web/app/src/views/SchedulerView.test.ts` — add route-driven initialization, refetch, and back/forward restoration coverage.
- `apps/web/app/src/components/CalendarHeader.test.ts` — update expectations from direct store mutation to navigation/event behavior.
- `apps/web/app/src/stores/publishing.test.ts` — update tests if scheduler filters contract changes.
- `apps/web/app/e2e/**` — add deep-link, refresh, and navigation history coverage for scheduler routes.

### Approaches
1. **Route as source of truth, store as data/cache only** — keep scheduler navigation state in route path/query; derive UI state from `useRoute()` and pass explicit request args to store actions.
   - Pros: cleanest ownership model, refresh/back-forward work naturally, no double-write drift, easier to reason about canonical URLs.
   - Cons: requires touching several components that currently mutate store refs directly; store tests may need refactor.
   - Effort: Medium

2. **Bidirectional route-store sync layer** — keep existing store refs, add watchers that hydrate store from route and push route when store changes.
   - Pros: smaller surface change inside components already bound to store refs.
   - Cons: high risk of watcher loops, duplicate state, race conditions during init, harder canonicalization, and hidden bugs when sidebar/header mutate store before route stabilizes.
   - Effort: High

### Recommendation
Use **route as the source of truth**.

Concretely:
- Introduce canonical routes:
  - `/scheduler` → redirect to `/scheduler/calendar/week`
  - `/scheduler/calendar/:range(week|month)`
  - `/scheduler/list`
- Keep `day` as a **query-level detail** (`date=YYYY-MM-DD`) instead of a top-level path, unless product explicitly wants shareable `/day`. The requested route list omits `/day`, but the current UI supports it. Best fit is: month/week remain addressable in path, and clicking a day in month route keeps `mode=calendar` path plus `date` query to focus fetch/selection state. If strict parity is required, a follow-up decision is needed on whether day view stays supported.
- Add a scheduler query codec/composable (for example `useSchedulerRouteState` or `schedulerRoute.ts`) that parses/serializes:
  - `date` as canonical local date string
  - `channels[]` as repeated query keys
  - `timezone`, `status`, `q`, `mode`
- Derive route-backed computed setters in `SchedulerView` (or a composable) and pass them to `CalendarHeader`; header emits changes, parent updates route with `router.push/replace`.
- Keep only **non-route UI/cache state** in Pinia. Good candidates to remain in store: fetched publications/activity/conflicts/channels, async loading/errors, reconnect flags. Route-derived scheduler controls (`viewMode`, timezone, filters, base date) should either leave the store entirely or remain as read-only mirrors updated from route for compatibility during transition.
- Prefer a short-lived compatibility phase where `publishing.fetchCalendar(from, to, { status, socialAccountId, timezone })` accepts explicit args and stops implicitly reading scheduler route state from internal refs.

### Risks
- **Current day view mismatch**: issue route goals specify week/month/list only, but UI has a real `day` mode. Need a product decision: de-scope day, encode it via query, or add `/scheduler/calendar/day` even though it was not requested.
- **Channel filter mismatch**: current sidebar uses `filterChannel` (provider-level) while backend fetch supports `socialAccountId` only. Query goal says `channels[]`, so the team must choose whether query values represent provider names, account IDs, or both. Account IDs fit backend fetch; provider names fit current sidebar UX.
- **Fetch invalidation gap**: today scheduler only fetches on mount. Route-driven state will expose the missing refetch behavior immediately; fetch ranges must be recomputed for week/month/list and refired on route changes.
- **Timezone semantics**: `date` and visible period computations depend on timezone, but current base date math uses native `Date` without timezone-aware conversions. Route restore may show off-by-one behavior near UTC boundaries unless normalized carefully.
- **History noise**: every filter keystroke for `q` should likely use `router.replace`, while deliberate navigation (week/month/list/date jumps) should use `router.push`. Without this distinction, back/forward becomes noisy.
- **Graceful redirects**: existing `/scheduler` links in app code and tests will need canonical redirects that preserve query params.
- **Sidebar active styling**: current active state depends on store `filterChannel`; it will drift unless migrated to route-derived selection logic.

### Ready for Proposal
Yes — proceed to proposal with a route-first design, but call out two decisions explicitly: how to represent `channels[]` in the URL, and whether day view remains supported or is folded into the new canonical route model.
