# Design: App Type-Check Remediation

## Technical Approach

Eliminate the deterministic 21-error/12-file/7-code baseline at existing contract boundaries,
without exclusions, compiler relaxation, dependency changes, or runtime expansion. Work in
dependency order: test/config primitives, production contract seams, then fixtures and gates. The
day route keeps its week fallback; CAS API statuses map to the existing “processing” presentation.
Before and after every group, capture `git status --short` and never edit/stage `Justfile`,
`apps/web/app/package.json`, or `apps/web/app/e2e/**`.

## Architecture Decisions

| Option                                                   | Tradeoff                                                  | Decision / rationale                                                                     |
|----------------------------------------------------------|-----------------------------------------------------------|------------------------------------------------------------------------------------------|
| Narrow contract fixes vs casts/config relaxation         | More focused edits, retains strictness                    | Narrow fixes; the specification requires full existing coverage.                         |
| UI processing bucket vs adding `PROCESSING` to API union | Small mapping vs false lifecycle value                    | Map `PENDING_UPLOAD` and `UPLOADING`; CAS architecture defines both and no `PROCESSING`. |
| Day surface vs week canonicalization                     | New behavior vs current fallback                          | Normalize day to `calendar-week`; preserves routing behavior.                            |
| Guard workspace vs non-null assertion                    | Explicit deterministic failure vs hidden invalid API call | Guard before upload tracking/API invocation; protects the required workspace contract.   |

## Data Flow

```text
vue-tsc --build → node config + DOM app/tests → focused contract fixes → zero errors
CAS status → presentation mapper → filter/count/style/selection (API union unchanged)
Upload request → active workspace guard → upload tracking → putAsset
Day route → normalizeSurface → calendar-week → canonical replace
```

## File Changes and Dependency Order

| Order | File                                     | Exact minimal fix                                                                                                                                                                                        |
|------:|------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
|     1 | `apps/web/app/vite.config.ts`            | Import `defineConfig` from `vitest/config`; retain the same Vite/Vitest object.                                                                                                                          |
|     2 | `src/composables/useFileHash.test.ts`    | Remove `node:stream/web`; use global DOM `ReadableStream`, matching `File.stream()`.                                                                                                                     |
|     2 | `src/lib/media-api.test.ts`              | Add `beforeAll` to the Vitest import.                                                                                                                                                                    |
|     2 | `src/i18n/i18n-keys.test.ts`             | Read each capture into a local and add only when defined.                                                                                                                                                |
|     2 | `src/composables/useCalendarUrl.test.ts` | Keep the established `as unknown as Router` mock boundary and add a day-route week-fallback assertion through the controller.                                                                            |
|     3 | `src/composables/useCalendarUrl.ts`      | Return `calendar-week` for `scheduler-calendar-day`; do not extend `SchedulerSurface`.                                                                                                                   |
|     3 | `src/components/CreatePostModal.vue`     | Type mode as `NonNullable<Publication['scheduleMode']>` (or equivalent literal alias), use `Date                                                                                                         | undefined` consistently, and pass the already-guarded `props.editingPublication.id`. |
|     3 | `src/stores/media.test.ts`               | Make workspace mock mutable; first add regression proving absent workspace rejects/returns deterministically and never calls `putAsset`. Replace stale helper status with a valid CAS status where used. |
|     4 | `src/stores/media.ts`                    | Guard `activeWorkspaceId` before creating upload state/UUID or calling `putAsset`; preserve all valid-workspace paths.                                                                                   |
|     4 | `src/views/MediaLibraryView.test.ts`     | Replace stale fixtures with both `PENDING_UPLOAD` and `UPLOADING`; assert shared processing filter/count/style and selection exclusion.                                                                  |
|     5 | `src/views/MediaLibraryView.vue`         | Add a typed `isProcessingStatus`; use it for filtering, counts, style, selection, and request `READY,PENDING_UPLOAD,UPLOADING,FAILED` while retaining the UI label/value `PROCESSING`.                   |
|     5 | `src/views/SchedulerView.test.ts`        | Change activity fixture to `{date,density,count}` and assert rendered activity meaning, not only mount success.                                                                                          |
|     5 | `src/views/SettingsView.spec.ts`         | Add fake-timer regressions for 3-second dismissal and unmount cleanup.                                                                                                                                   |
|     6 | `src/views/SettingsView.vue`             | Type timer as `ReturnType<typeof setTimeout>                                                                                                                                                             | undefined`; use matching `globalThis.clearTimeout`. |

Only existing files are modified; no new files are added by implementation.

## Interfaces / Contracts

No public interface changes. Internal contracts remain: scheduler surfaces exclude day; publishing
mode is `'NOW' | 'NEXT_SLOT' | 'SCHEDULED_AT'`; absent dates are `undefined`; media status remains
the CAS union. The processing concept is presentation-only.

## Testing Strategy

| Group              | Red/green seam and command                                                                                                                                                                                       |
|--------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Config             | Load unchanged test config: `pnpm --filter app exec vitest run --config vite.config.ts src/App.test.ts`.                                                                                                         |
| Composer           | Existing now/next/custom/edit/error cases: `pnpm --filter app exec vitest run src/components/CreatePostModal.test.ts`.                                                                                           |
| Calendar           | Add day fallback first: `pnpm --filter app exec vitest run src/composables/useCalendarUrl.test.ts src/router/index.spec.ts src/router/index.guard.test.ts`.                                                      |
| Hash/strict tests  | `pnpm --filter app exec vitest run src/composables/useFileHash.test.ts src/lib/media-api.test.ts src/i18n/i18n-keys.test.ts`.                                                                                    |
| Media              | Add absent-workspace and two-status tests first: `pnpm --filter app exec vitest run src/stores/media.test.ts src/views/MediaLibraryView.test.ts`.                                                                |
| Scheduler/settings | Strengthen fixtures/timers first: `pnpm --filter app exec vitest run src/views/SchedulerView.test.ts src/views/SettingsView.spec.ts src/views/SettingsView.validation.spec.ts`.                                  |
| Final              | `pnpm --filter app type-check && pnpm --filter app type-check && pnpm --filter app test:run`, then independently `pnpm --filter app test:e2e:media:mocked`. Final required gate: `pnpm --filter app type-check`. |

## Migration / Rollout

No migration or feature flag. Inspect
`git diff -- Justfile apps/web/app/package.json apps/web/app/e2e` and `git status --short` to prove
CAS E2E files were untouched/un-staged.

## Open Questions

None.
