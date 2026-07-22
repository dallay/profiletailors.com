# Tasks: Publication Edit Asset Preservation

## Review Workload Forecast

| Field                   | Value                                                    |
|-------------------------|----------------------------------------------------------|
| Estimated changed lines | 220-340                                                  |
| 400-line budget risk    | Medium                                                   |
| Chained PRs recommended | No                                                       |
| Suggested split         | Single PR in grouped publication-edit-hardening delivery |
| Delivery strategy       | single-pr                                                |
| Chain strategy          | size-exception                                           |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: size-exception
400-line budget risk: Medium

### Suggested Work Units

| Unit | Goal                               | Likely PR  | Notes                                                |
|------|------------------------------------|------------|------------------------------------------------------|
| 1    | Backend PATCH tri-state contract   | grouped PR | Tests first; preserves CREATE and #224/#225 behavior |
| 2    | Frontend edit hydration/submission | grouped PR | Tests first; depends on backend contract shape       |
| 3    | Regression verification            | grouped PR | Focused commands plus SDD verify readiness           |

## Phase 1: Backend RED Tests

- [x] 1.1 Add failing PATCH absent/null preserve cases in
  `server/smp/src/test/kotlin/.../publishing/infrastructure/http/PublishingControllersTest.kt`
  and/or handler coverage.
- [x] 1.2 Add failing PATCH `assetIds: []` clears and `[ids]` replaces cases in
  `PublishingHandlersTest.kt`.
- [x] 1.3 Add failing CREATE compatibility cases in `PublishingApiTest.kt`/
  `PublishingControllersTest.kt`: omitted/default assets stay empty, provided IDs persist.
- [x] 1.4 Run RED:
  `./gradlew :server:smp:test --tests '*PublishingControllersTest' --tests '*PublishingApiTest' --tests '*PublishingHandlersTest' -PexcludeTags=modularity,postgres`.

## Phase 2: Backend GREEN Implementation

- [x] 2.1 Update
  `server/smp/src/main/kotlin/.../publishing/infrastructure/http/PublishingControllers.kt`: nullable
  `PublicationUpsertRequest.assetIds`; CREATE maps `?: emptyList()`, EDIT passes nullable.
- [x] 2.2 Update `server/smp/src/main/kotlin/.../publishing/application/PublishingApi.kt`: only
  `EditPublicationCommand.assetIds` becomes `List<String>? = null`.
- [x] 2.3 Update `server/smp/src/main/kotlin/.../publishing/application/PublishingHandlers.kt`: null
  preserves `current.assetIds`; empty/list replace exactly; validate only final non-empty IDs.
- [x] 2.4 Run GREEN backend focused command from 1.4.

## Phase 3: Frontend RED Tests

- [x] 3.1 Add failing `apps/web/app/src/components/CreatePostModal.test.ts` cases for edit hydration
  previews, missing asset graceful behavior, untouched omission, clear `[]`, and replacement IDs.
- [x] 3.2 Add failing `apps/web/app/src/stores/publishing.test.ts` cases for `updatePost` omitting
  absent `assetIds` and serializing `[]`/replacement IDs.
- [x] 3.3 Run RED:
  `pnpm --filter app vitest run src/components/CreatePostModal.test.ts src/stores/publishing.test.ts`.

## Phase 4: Frontend GREEN Implementation

- [x] 4.1 Update `apps/web/app/src/stores/publishing.ts` with typed `PublicationUpdate` and
  conditional PATCH body spreading for `assetIds?: string[]`.
- [x] 4.2 Update `apps/web/app/src/components/CreatePostModal.vue`: await edit hydration before
  dangling load; skip missing assets without clearing valid IDs.
- [x] 4.3 Add edit-only `assetsTouched` state in `CreatePostModal.vue`; hydration does not touch,
  user add/remove/clear/upload-success does.
- [x] 4.4 Serialize edit save as omit when untouched, `[]` after explicit clear, and selected IDs
  after replacement; run GREEN frontend command from 3.3.

## Phase 5: Focused Regression Verification

- [x] 5.1 Run backend regressions: `just backend-test-fast` plus the focused Gradle command from
  1.4, confirming #224/#225 workspace/edit tests still pass.
- [x] 5.2 Run frontend regressions: `just frontend-test` plus focused Vitest command from 3.3,
  confirming #224/#225 scheduler tests still pass.
- [ ] 5.3 If focused suites pass, record exact commands/results in
  `openspec/changes/2026-07-02-publication-edit-assets-fix/verify-report.md` during SDD verify.

## Phase 6: SDD Verify / Archive Readiness

- [ ] 6.1 Run `sdd-verify` against proposal, `specs/publishing/spec.md`, design, and this checklist.
- [ ] 6.2 Prepare archive only after PASS/PASS WITH WARNINGS; sync publishing delta into
  `openspec/specs/publishing/spec.md`.
