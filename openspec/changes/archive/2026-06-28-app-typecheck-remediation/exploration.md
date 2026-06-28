## Exploration: Complete `apps/web/app` type-check failure baseline

### Current State

Running the exact command `pnpm --filter app type-check` twice on 2026-06-27 produced the same
deterministic baseline: **21 TypeScript errors in 12 files across 7 error codes**. The command runs
`vue-tsc --build` over `tsconfig.node.json` and `tsconfig.app.json`; application unit/spec files
under `src/` are included, while the current uncommitted CAS Playwright files under `e2e/` are not
included by either config.

The current uncommitted CAS E2E slice changes only `Justfile`, `apps/web/app/package.json`, six new
`apps/web/app/e2e/**` files, and OpenSpec artifacts. None of those files appears in either
type-check run. Therefore **0 of 21 errors are caused by the current uncommitted CAS E2E files (PR
1)**. The package script additions do not alter `type-check`. Some failures do originate from the
already-committed CAS implementation in `baac461c`, which is distinct from the uncommitted E2E
slice.

#### Exact counts by error code

| Error code |  Count | Primary cause                                                          |
|------------|-------:|------------------------------------------------------------------------|
| TS2345     |     11 | nullability, incomplete router mock, DOM/Node stream mismatch          |
| TS2322     |      5 | widened strings, stale route/status unions, timer environment mismatch |
| TS2416     |      1 | Node `ReadableStream` override incompatible with DOM `File.stream()`   |
| TS2304     |      1 | missing explicit Vitest import                                         |
| TS2367     |      1 | stale `PROCESSING` status versus CAS status union                      |
| TS2353     |      1 | stale scheduler activity fixture shape                                 |
| TS2769     |      1 | Vite config typed without Vitest config augmentation                   |
| **Total**  | **21** |                                                                        |

#### Exact counts by file

| File                                     | Count | Codes                | Ownership / root cause                                                                                |
|------------------------------------------|------:|----------------------|-------------------------------------------------------------------------------------------------------|
| `src/composables/useFileHash.test.ts`    |     5 | TS2416 ×1, TS2345 ×4 | Committed CAS (`baac461c`); Node stream imported into DOM `File` test double                          |
| `src/components/CreatePostModal.vue`     |     4 | TS2345 ×3, TS2322 ×1 | Pre-existing composer typing drift; helpers widened/changed without preserving store contracts        |
| `src/i18n/i18n-keys.test.ts`             |     2 | TS2345 ×2            | Committed CAS (`baac461c`); `noUncheckedIndexedAccess` on regex capture groups                        |
| `src/views/MediaLibraryView.test.ts`     |     2 | TS2322 ×2            | Media UI fixture drift after committed CAS status model removed `PROCESSING`                          |
| `vite.config.ts`                         |     1 | TS2769 ×1            | Pre-existing Vite/Vitest config typing mismatch                                                       |
| `src/composables/useCalendarUrl.test.ts` |     1 | TS2345 ×1            | Pre-existing test mock missing the established `as unknown as Router` boundary                        |
| `src/composables/useCalendarUrl.ts`      |     1 | TS2322 ×1            | Pre-existing route/domain mismatch: day route exists but `SchedulerSurface` excludes it               |
| `src/lib/media-api.test.ts`              |     1 | TS2304 ×1            | Committed CAS (`baac461c`); `beforeAll` used but not imported                                         |
| `src/stores/media.ts`                    |     1 | TS2345 ×1            | Committed CAS (`baac461c`); nullable active workspace passed to required API argument                 |
| `src/views/MediaLibraryView.vue`         |     1 | TS2367 ×1            | UI still models `PROCESSING`; committed CAS API union models `PENDING_UPLOAD`/`UPLOADING` instead     |
| `src/views/SchedulerView.test.ts`        |     1 | TS2353 ×1            | Pre-existing fixture uses legacy `{scheduled,published,blocked}` instead of `{density,count}`         |
| `src/views/SettingsView.vue`             |     1 | TS2322 ×1            | Pre-existing mixed Node/DOM timer typing; repo already uses `ReturnType<typeof setTimeout>` elsewhere |

#### Ranked root-cause hypotheses

1. **Committed CAS contract migration was incomplete (9 direct errors plus 3 related media-view
   errors; high confidence).** `baac461c` introduced the stream tests, i18n test, missing hook
   import, nullable workspace call, and the new `MediaStatus` union. The view/tests retained the old
   `PROCESSING` vocabulary. This is committed mainline ownership, not uncommitted PR 1.
2. **Refactors widened values and lost narrowing (4 errors; high confidence).**
   `resolveScheduledDate()` returns `undefined` while submit helpers accept `null`;
   `resolveScheduleMode()` returns `string` instead of the publishing store literal union; optional
   chaining weakens an ID already guarded by control flow.
3. **Scheduler route/fixture drift accumulated across URL-addressability work (3 errors; high
   confidence).** A day route was added without adding or intentionally canonicalizing its surface,
   one router mock skipped the local casting pattern, and one activity test retained an obsolete
   DTO.
4. **Tooling environment types are mixed (2 errors; high confidence).** Vite config uses Vitest
   fields through Vite's `defineConfig`, and Node globals influence timer typing in a DOM component.
5. **Strict index checking exposes unchecked regex captures (2 errors; high confidence).**
   `noUncheckedIndexedAccess` correctly treats `match[1]` as optional even though the regex contains
   a capture.

### Affected Areas

- `apps/web/app/vite.config.ts`, `tsconfig.node.json`, `tsconfig.app.json` — config ownership and
  why `src` tests, but not nested `e2e`, enter the build.
- `apps/web/app/src/components/CreatePostModal.vue`, `src/stores/publishing.ts` — composer/store
  scheduling contract and nullability.
- `apps/web/app/src/composables/useCalendarUrl.ts`, `useCalendarUrl.test.ts`,
  `src/router/index.ts` — route surface contract and mock seam.
- `apps/web/app/src/composables/useFileHash.test.ts`, `useFileHash.ts` — browser `File.stream()`
  regression seam.
- `apps/web/app/src/lib/media-api.ts`, `media-api.test.ts`, `src/stores/media.ts`,
  `src/stores/workspace.ts` — committed CAS API/status/workspace contracts.
- `apps/web/app/src/views/MediaLibraryView.vue`, `MediaLibraryView.test.ts` — stale status
  presentation and fixtures.
- `apps/web/app/src/views/SchedulerView.test.ts`, `src/stores/publishing.ts` — stale activity
  response fixture.
- `apps/web/app/src/views/SettingsView.vue`, `src/components/UploadProgressToast.vue`,
  `src/composables/useConnectMessage.ts` — timer typing and established pattern.
- `apps/web/app/src/i18n/i18n-keys.test.ts` — strict regex capture handling.
- `apps/web/app/package.json`, `apps/web/app/e2e/**`, `Justfile` — inspected uncommitted PR 1
  ownership; no baseline errors originate here.

### Approaches

1. **Minimal contract-aligned fix groups (recommended)** — fix each boundary without changing
   runtime branches or product behavior.
    - Pros: Small reviewable changes; retains strict checking; follows existing patterns; isolates
      committed CAS debt from unrelated scheduler/tooling debt.
    - Cons: Requires focused tests for several independent seams; media status vocabulary needs an
      explicit product-contract decision in proposal/spec.
    - Effort: Medium

2. **Relax/exclude type-check scope** — exclude tests, add broad casts, or weaken strict compiler
   options.
    - Pros: Fastest route to a green command.
    - Cons: Hides real contract drift, loses regression protection, and would not remediate the
      baseline.
    - Effort: Low

3. **Broad type/model redesign** — centralize all DTOs, router abstractions, timers, and test
   utilities before fixing errors.
    - Pros: Could reduce future drift.
    - Cons: High behavioral and review risk; disproportionate to 21 concrete errors; violates
      minimal remediation intent.
    - Effort: High

### Recommendation

Use Approach 1, split into the following minimal groups and preserve runtime behavior:

1. **Tooling config (1 error):** type `vite.config.ts` through Vitest's config entry/augmentation
   rather than suppressing `test`. TDD/regression seam: config loading plus the full type-check.
   Verify: `pnpm --filter app exec vitest run --config vite.config.ts src/App.test.ts` and
   `pnpm --filter app type-check`.
2. **Composer contract (4 errors):** make scheduled-date absence consistent (`undefined` end-to-end
   or normalize once), return the exact schedule-mode union, and pass the guarded publication ID
   directly. TDD seam: existing `CreatePostModal.test.ts` cases for now/next/custom/edit and error
   paths. Verify: `pnpm --filter app exec vitest run src/components/CreatePostModal.test.ts`.
3. **Calendar URL contract (2 errors):** explicitly decide whether day is a supported
   `SchedulerSurface` or canonicalizes to week; use the file's established router cast or a narrow
   controller port for the isolated mock. TDD seam: add/adjust day-route normalization and
   canonicalization cases before production edits. Verify:
   `pnpm --filter app exec vitest run src/composables/useCalendarUrl.test.ts src/router/index.spec.ts src/router/index.guard.test.ts`.
4. **Browser stream test double (5 errors):** use the DOM `ReadableStream`/`File.stream` return type
   instead of `node:stream/web`; do not cast the production function. TDD seam: existing threshold,
   native-path, 64-byte, and 65-byte digest assertions already protect behavior. Verify:
   `pnpm --filter app exec vitest run src/composables/useFileHash.test.ts`.
5. **Strict test hygiene (3 errors):** import `beforeAll` explicitly and guard/assert regex capture
   group presence. TDD seam: existing media API and i18n key tests. Verify:
   `pnpm --filter app exec vitest run src/lib/media-api.test.ts src/i18n/i18n-keys.test.ts`.
6. **Workspace nullability (1 error):** fail/return before `putAsset` when no active workspace,
   matching the API's required workspace contract rather than asserting non-null. TDD seam: first
   add a media-store test proving no API call and deterministic failure/return when workspace is
   absent. Verify: `pnpm --filter app exec vitest run src/stores/media.test.ts`.
7. **Media status vocabulary (3 errors):** align filters/counts/tests with the actual CAS states (
   `PENDING_UPLOAD` and `UPLOADING`) or deliberately map both to a UI-level “processing” bucket
   without adding an invalid API enum. TDD seam: add view tests for both in-progress API statuses,
   selection exclusion, filtering, and counts before changing the view. Verify:
   `pnpm --filter app exec vitest run src/views/MediaLibraryView.test.ts src/stores/media.test.ts`.
8. **Scheduler activity fixture (1 error):** update the stale test fixture to
   `{date,density,count}`; no production change. TDD seam: strengthen assertion to verify rendered
   density/count behavior rather than `wrapper.exists()`. Verify:
   `pnpm --filter app exec vitest run src/views/SchedulerView.test.ts`.
9. **Timer environment type (1 error):** use the established `ReturnType<typeof setTimeout>` pattern
   and matching clear call. TDD seam: fake-timer test for success visibility then dismissal/unmount
   cleanup. Verify:
   `pnpm --filter app exec vitest run src/views/SettingsView.spec.ts src/views/SettingsView.validation.spec.ts`.
10. **Final deterministic gate:** run `pnpm --filter app type-check` twice, then
    `pnpm --filter app test:run`; keep the uncommitted CAS E2E suite independently verified with
    `pnpm --filter app test:e2e:media:mocked` so remediation does not regress PR 1.

### Risks

- The media UI's old `PROCESSING` label may represent desired presentation even though it is not an
  API status; replacing it blindly could alter visible behavior. Prefer a typed UI bucket over
  expanding the backend-derived union without evidence.
- The day scheduler route exists while the surface union excludes it. Adding day to the union can
  expand runtime behavior; canonicalizing it can preserve the current week fallback. This needs an
  explicit requirement in the proposal.
- A non-null assertion for workspace ID would silence the compiler but leave a real
  unauthenticated/no-workspace failure path.
- Config typing changes can affect Vitest/Vite module resolution; validate both config loading and
  the full suite.
- The working tree contains unrelated uncommitted CAS E2E work. Implementation must avoid staging or
  rewriting those files while still rerunning their focused suite.

### Ready for Proposal

Yes — propose a behavior-preserving remediation with 10 focused groups, explicit decisions for media
in-progress presentation and day-route canonicalization, TDD first for each regression seam, and
acceptance criteria of two identical zero-error type-check runs plus focused/full unit tests and the
independent CAS mocked E2E gate.
