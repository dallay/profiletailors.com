# Design: Composer Media Attachments Playwright E2E

## Technical Approach

Extend the existing media Playwright harness — no new configs, no new mock stacks. Add
deterministic composer control to `media-mocks.ts`, expand the fixture catalog, grow the
page object, and split the existing `media-composer.spec.ts` into two tagged projects
under the existing configs: `@composer-ui-mocked` (deterministic Chromium, fully
parallel) and `@composer-smoke-real` (real backend, serial, happy paths only).
Provider-enabled coverage stays out of scope per proposal. Tests align `describe` IDs
with the 26 browser-observable plan items; 5 provider-deferred items are tracked as
follow-up.

## Architecture Decisions

| #   | Choice                                                                                                                                                              | Tradeoff                                                                                                      | Decision                                                                                             |
|-----|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------|
| D1  | Tag scenarios; add `grep`-filtered project entries inside existing `playwright.media-*.config.ts`.                                                                  | New config = duplicated setup, overlapping `testMatch`; forking media stacks muddles reporting.               | **Extend existing.** Reuses auth, HAR, scheduler mocks, media CRUD.                                  |
| D2  | `DeferredUploadController` on `MediaRouteState`: `enqueueDeferred({…})` returns `PENDING_UPLOAD` and holds binary `POST /upload` until `complete()` / `failNext()`. | Route fulfillment is request-level — cannot synthesize `XHR.upload.onprogress`. Sleep-based polling is flaky. | **Hold the binary response.** Drives the modal's `onProgress` deterministically without sleeps.      |
| D3  | Same controller; `failNext({ status: 503 })` flows through the store's existing rejection path.                                                                     | Reject at fetch level = couples to transport, not product.                                                    | **Reject at mock seam** the production code reads from.                                              |
| D4  | `MediaRouteState.transitionQueue`: tests push responses one at a time via `controller.advance(id, response)`.                                                       | Awaiting real `setTimeout` ticks is slower than mocking the clock.                                            | **Real ticks + queue.** Tests assert post-`advance()` state, never "did it ever become READY?".      |
| D5  | `MockChannelsProvider` returns the scheduler mock channel list with a per-test `maxAttachments` override on `/api/publishing/channels`.                             | Pinia mutation is forbidden by the proposal.                                                                  | **Mock the API**, not the store.                                                                     |
| D6  | Out of scope. `MockProviderFlag` toggles `isUnsplashProviderEnabled` for tab-visibility assertions only.                                                            | Unsplash is `DEV/test synthetic` per the live baseline.                                                       | **Defer real provider.** Tab-visibility contract is assertable; real provider needs the environment. |
| D7  | Drive hidden `<input type="file">` with `setInputFiles()`; one explicit `dataTransfer` drop locks the drop-event branch.                                            | Drag event construction in tests is fragile.                                                                  | **setInputFiles + one real drop** to cover both branches.                                            |
| D8  | Follow-up seam `data-testid="attachment-overflow"`; page object has structural fallback on the 118×118 dashed div with `+N` text.                                   | Class-name coupling is brittle.                                                                               | **Seam + fallback.**                                                                                 |
| D9  | Follow-up seam `data-testid="upload-overlay-local-upload"`; structural fallback.                                                                                    | Same as D8.                                                                                                   | **Seam + fallback.**                                                                                 |
| D10 | `getByRole` / `getByLabel` / `getByText` first; `data-testid` only when no accessible name exists.                                                                  | Testid-only is faster but breaks a11y drift.                                                                  | **Match existing page-object style.**                                                                |
| D11 | Unique `X-Run-Id` header, run-scoped `runFiles`, unique filename prefix. Existing teardown deletes any `dev-workspace-001` item matching the run ID.                | Shared workspace cleanup is destructive.                                                                      | **Reuse proven isolation; never mutate shared fixtures.**                                            |
| D12 | Test names mirror plan items (`ML-COMPOSER-NNN`); 5 provider-deferred items get one `test.skip(…, 'Deferred')` group.                                               | Renumbering breaks plan traceability.                                                                         | **Mirror plan IDs.** Keeps the "26 of 30" claim auditable.                                           |

## Data Flow — Mocked Upload

`setInputFiles` → `addFiles` → `uploadPreviewBlob = blob:…` (no network, overlay
hidden). On `clickScheduleNow()`: `PUT /assets/{new}` returns `201 PENDING_UPLOAD`;
binary `POST /upload` is held. As the test calls `controller.advance()` the modal's
`onProgress` fires (`Uploading… X%` → `Finishing up…`); `complete()` resumes the held
route → `200 READY`; `socialPreviewMediaImg.src` swaps from `blob:` to
`/api/media/assets/{id}/preview`.

## File Changes

| File                                                        | Action         | Why                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
|-------------------------------------------------------------|----------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `apps/web/app/e2e/fixtures/media-files.ts`                  | Modify         | Add `inlineImage2`, `inlineImageLarge`, `invalidTxt`, ordered `multiFirstValid` manifest.                                                                                                                                                                                                                                                                                                                                                                    |
| `apps/web/app/e2e/fixtures/media-mocks.ts`                  | Modify         | Add `DeferredUploadController`, `TransitionQueue`, `MockChannelsProvider` (per-test `maxAttachments`), `MockProviderFlag`. `MediaRouteState.reset()` clears new fields.                                                                                                                                                                                                                                                                                      |
| `apps/web/app/e2e/fixtures/media-mocked-test.ts`            | Modify         | Expose `deferredUpload`, `channelsProvider`, `providerFlag` fixtures.                                                                                                                                                                                                                                                                                                                                                                                        |
| `apps/web/app/e2e/fixtures/media-real-test.ts`              | Modify         | Add `composerRunFiles`, `composerFixturePrefix`.                                                                                                                                                                                                                                                                                                                                                                                                             |
| `apps/web/app/e2e/pages/compose-modal-page.ts`              | Modify         | Add locators/actions: `mediaDropzone`, `uploadOverlay`, `overflowCard`, `socialPreviewMediaImg`, `pickerShell`, `libraryTab`, `unsplashTab`, `pickerApply`, `pickerApplyWarning`, `limitWarning`, `libraryAssetCard(id)`, `attachmentByIndex(i)`, `removeAttachmentByName(name)`, `attachMediaFiles(paths[])`, `dropFiles(page, paths[])`, `previewMediaSrcKind()`, `expectUploadOverlayText()`, `expectUploadProgressBetween()`. Keep all existing entries. |
| `apps/web/app/e2e/specs/media-composer.spec.ts`             | Rewrite        | Replace 5 legacy tests with 23 plan-aligned tests tagged `@composer-ui-mocked`. Skip 5 provider-deferred items.                                                                                                                                                                                                                                                                                                                                              |
| `apps/web/app/e2e/specs/media-composer-smoke.spec.ts`       | Create         | 5 happy-path tests tagged `@composer-smoke-real`.                                                                                                                                                                                                                                                                                                                                                                                                            |
| `apps/web/app/e2e/playwright.media-mocked.config.ts`        | Modify         | Add `media-mocked-composer` project (filter `--grep @composer-ui-mocked`). Keep existing project.                                                                                                                                                                                                                                                                                                                                                            |
| `apps/web/app/e2e/playwright.media-real.config.ts`          | Modify         | Add `media-real-composer` project (filter `--grep @composer-smoke-real`; `fullyParallel: false`, `workers: 1`).                                                                                                                                                                                                                                                                                                                                              |
| `apps/web/app/package.json`                                 | Modify         | Add `test:e2e:media:mocked:composer` and `test:e2e:media:real:composer` scripts.                                                                                                                                                                                                                                                                                                                                                                             |
| `justfile`                                                  | Modify         | Add `app-test-e2e-media-mocked-composer` and `app-test-e2e-media-real-composer`; extend `app-test-e2e-media` to chain mocked-composer.                                                                                                                                                                                                                                                                                                                       |
| `.github/workflows/ci.yml`                                  | Modify         | Add `app-e2e-mocked-composer` job (chromium only) gated on `frontend` paths filter. Real smoke and provider stay in scheduled/manual jobs.                                                                                                                                                                                                                                                                                                                   |
| `apps/web/app/src/components/CreatePostModal.vue`           | Follow-up seam | Add `data-testid="upload-overlay-local-upload"` and `data-testid="attachment-overflow"` (separate proposal against `composer-media-picker`).                                                                                                                                                                                                                                                                                                                 |
| `apps/web/app/src/components/composer/PostPreviewPanel.vue` | Follow-up seam | Expose `data-media-src-kind` for `blob:` vs `/api/media/...` swap (separate proposal against `composer-preview`).                                                                                                                                                                                                                                                                                                                                            |

## Interfaces / Contracts

```ts
// media-mocks.ts — additions only
interface DeferredUploadController {
  enqueueDeferred(input?: { mediaType?: string }): string
  advance(assetId: string, transition: MockPutResponse): void
  complete(assetId: string): Promise<void>
  failNext(assetId: string, failure: { status: 5xx; code: string }): Promise<void>
  heldCount(): number
}
interface MockChannelsProvider { setMaxAttachments(limit: number | null): void; reset(): void }
interface MockProviderFlag     { setEnabled(enabled: boolean): void; reset(): void }
interface TransitionQueue       { enqueue(id, r): void; take(id): MockPutResponse | null; size(id): number }
```

```ts
// compose-modal-page.ts — additions (selected)
readonly mediaDropzone / uploadOverlay / overflowCard / socialPreviewMediaImg / pickerShell
readonly libraryTab / unsplashTab / pickerApply / pickerApplyWarning / limitWarning: Locator
libraryAssetCard(id: string): Locator
removeAttachmentByName(name: string): Promise<void>
attachMediaFiles(paths: string[]): Promise<void>
dropFiles(page: Page, paths: string[]): Promise<void>
previewMediaSrcKind(): Promise<'blob' | 'persisted' | 'none'>
expectUploadOverlayText(matcher: RegExp): Promise<void>
expectUploadProgressBetween(min: number, max: number): Promise<void>
```

## Testing Strategy

| Layer                             | Scope                                                                                                                                                                                                | How                                                                                                                               |
|-----------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------|
| Mocked `@composer-ui-mocked`      | 23 deterministic scenarios: progress %, `Finishing up…`, failure, focus retention, first-valid file, multi-file drop, `+N` overflow, attachment-limit blocking, source switching, provider-disabled. | `playwright test -c e2e/playwright.media-mocked.config.ts --project media-mocked-composer`. Parallel; retries 2 in CI.            |
| Real smoke `@composer-smoke-real` | 5 happy paths.                                                                                                                                                                                       | `playwright test -c e2e/playwright.media-real.config.ts --project media-real-composer`. Serial. Needs `E2E_MEDIA_EMAIL/PASSWORD`. |
| Provider real                     | Deferred.                                                                                                                                                                                            | Out of scope.                                                                                                                     |

Verification: `just app-test-e2e-media-mocked-composer`,
`just app-test-e2e-media-real-composer`, or targeted
`pnpm test:e2e:media:mocked:composer -- --grep "ML-COMPOSER-007"`.

## Migration / Rollout

No data migration. Config-only rollout: (1) land seam changes in `composer-media-picker`
and `composer-preview` (separate follow-up proposals) before unconditional testid
assertions; (2) land spec/fixture/page-object/just/CI in one PR; (3) enable
`media-mocked-composer` lane in PR CI; (4) enable `media-real-composer` and
`@composer-provider-real` only in scheduled/manual jobs. Rollback: revert the PR. No
product code or data is touched.

## Open Questions

- [ ] **Seam 1+2**: Upload-overlay and `+N` overflow `data-testid`s — part of this change or
  follow-up? Design assumes follow-up; page object has structural fallback.
- [ ] **Real smoke env**: Confirm `E2E_MEDIA_EMAIL/PASSWORD` and at least one channel with
  `maxAttachments ≤ 4`. If absent, overflow moves to mocked-only and real smoke drops to 4
  scenarios.
- [ ] **Provider toggle default**: Proposal implies disabled-default (matches live). Confirm before
  tasks phase.
