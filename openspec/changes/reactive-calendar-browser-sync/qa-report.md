# QA Report: Reactive Calendar Browser Sync

## Status

Phase: **qa** closed. Acceptance evidence for this slice covers unit suites,
reconciled store/view tests, compliance inventory update, and Playwright spec
registration. Two-tab scheduler Playwright acceptance and CI/deployed behavior
remain deferred to environments with a running dev server and CI runner.

## Acceptance evidence

### Unit and integration

- `pnpm --filter app test:run` → 154 files, 1768 tests, all passing.
- New modules covered:
  - `apps/web/app/src/modules/publishing/application/calendarRange.test.ts` (5)
  - `apps/web/app/src/modules/publishing/application/useReactiveClock.test.ts` (2)
  - `apps/web/app/src/modules/publishing/application/useCalendarRevalidation.test.ts` (2)
  - `apps/web/app/src/modules/publishing/application/calendar-invalidation-channel.test.ts` (3)
- Reconciled assertions:
  - `SchedulerView > mounts and asks the revalidation coordinator for the current range`
  - `SchedulerView > route-driven post detail modal > only revalidates against the latest range when navigation arrives mid-flight`
  - `SchedulerView > refreshes calendar when CreatePostModal emits updated`
  - `publishing.store.bulk > fetchCalendar keeps canonical in-memory activity and conflicts when an authenticated remote fails`

### Static analysis

- `pnpm --filter app lint` (biome check) → passed, no findings.
- `pnpm --filter app type-check` (vue-tsc --build) → passed.
- `pnpm --filter app build` → passed; emits chunked bundles including the new
  composable bundle for `useReactiveClock`.

### Compliance inventory

- `docs/compliance/data-inventory.md` bumped to schema 2.1 with a change log
  entry reconciling `pt_publications`. Browser storage register now describes
  the anonymous-fallback only scope and clarifies that authenticated callers
  never write to the key.
- `docs/compliance/data-inventory.yaml` bumped to schema 2.1. `pa-012` updated:
  - `personal_data_categories` reflects the anonymous-fallback scope.
  - `retention.duration` and `retention.evidence` note that authenticated
    writes were removed.
  - `evidence_references` keeps the publishing.store.ts evidence and explains
    that authenticated callers now skip the local-storage write and revalidate
    against the backend.

### Playwright acceptance

- Registered `apps/web/app/e2e/specs/scheduler-browser-sync.spec.ts` with two
  scenarios:
  - **TC-BS-01**: same-workspace reschedule in tab A is observable through the
    backend mock in tab B without manual reload. The test uses
    `mockAuthenticatedSession` for auth, registers per-workspace route-backed
    mocks for the calendar and reschedule endpoints, and asserts that the
    backend's stored `scheduledFor` reflects the reschedule.
  - **TC-BS-02**: foreign-workspace invalidation is isolated; tab A only shows
    workspace A publications, tab B only shows workspace B publications.
- Playwright not executed in this environment (no dev server reachable from
  CI sandbox). The spec is registered and self-contained; it depends only on
  Playwright's standard browser context APIs.

### Capability-driven acceptance

| Capability | Evidence | Status |
|---|---|---|
| Two tabs same workspace converge | `TC-BS-01` registered | Playwright not run; manual run recommended |
| Workspace isolation | `TC-BS-02` registered | Playwright not run; manual run recommended |
| Mutations emit only on success | Publishing store wiring covers `quickCreatePost`, `reschedulePublication`, `retryPublication`; unit suite exercises success and rollback paths | Verified |
| Authenticated local fallback removed | `fetchCalendar` short-circuits; Vitest bulk suite asserts canonical in-memory state preservation | Verified |
| Cross-tab invalidation contract | `calendar-invalidation-channel.ts` with runtime guard; unit suite covers foreign/malformed/unsupported API | Verified |
| Visibility-aware scheduler | `useReactiveClock` + `useCalendarRevalidation`; unit suite covers hidden pause and visible-return refresh | Verified |
| Time-dependent UI uses reactive clock | `isPastDate`/`isPastSlot` migration documented; `useComposerScheduling` now consumes the shared clock | Verified |
| Timezone correctness | `calendarRange` uses `@internationalized/date`; unit suite covers Europe/Madrid summer and Australia/Sydney DST | Verified |
| Backend `updatedAt` exposure | `PublishingApi.kt` and `PublishingMappers.kt` include `updatedAt`; mapping surfaces the field | Verified |
| Optimistic concurrency | `expectedUpdatedAt` request guard | Out of scope per proposal; not implemented; follow-up tracked |

## Deferred follow-ups (explicit)

1. **publication-calendar-sse**: separate OpenSpec change. `sse_scope: deferred`
   in `state.yaml`. No SSE endpoint, schema, or controller touched.
2. **Backend `expectedUpdatedAt` optional edit guard**: out of scope per the
   proposal. The `updatedAt` exposure unblocks this follow-up in a later
   slice without further schema changes.
3. **Live Playwright runs**: requires `pnpm --filter app test:e2e:scheduler`
   with the dev server reachable. The spec is self-contained and ready.
4. **Remote CI / production smoke**: not run; the worktree is a local change
   without CI pipeline triggers.

## Archive summary

- All SDD artifacts (`proposal`, `exploration`, `design`, `tasks`,
  `apply-progress`, `verify-report`, `qa-report`, `state.yaml`) are present
  under `openspec/changes/reactive-calendar-browser-sync/`.
- Delta specs in `openspec/specs/` were checked; the change composes existing
  capabilities without introducing a new capability, so no new spec is added.
- Compliance inventory schema bumped to 2.1 to reflect the storage boundary.

## Decision

- The slice is ready for the archive phase. `state.yaml` records `next: null`
  and `current_phase: archive` to indicate the SDD cycle is complete for this
  change. No critical, P0, or P1 issues are open.
