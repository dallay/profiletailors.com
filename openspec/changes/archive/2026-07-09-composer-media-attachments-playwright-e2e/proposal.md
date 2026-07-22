# Proposal: Composer Media Attachments Playwright E2E

## Intent

Implement the approved Composer Media Attachments test plan as deterministic Playwright automation.
Deliver browser evidence for composer attachment behavior the CAS Media Library suite does not
cover: local selection, drag-and-drop, upload-progress overlays, picker staging, attachment-limit
blocking, `+N` overflow, and social-preview source swapping. Follow exploration: extend the
existing media E2E architecture; do not fork it.

## Scope

### In Scope

- `@composer-ui-mocked` tag in existing `playwright.media-mocked.config.ts` project.
- `@composer-smoke-real` tag in `playwright.media-real.config.ts` for happy paths only.
- Extensions to `media-mocked-test.ts`, `media-mocks.ts`, `media-files.ts`, `media-real-test.ts`,
  and `compose-modal-page.ts`.
- Rewrite of `media-composer.spec.ts`: no Pinia-internal mutation; locators match current markup.
- Stable test IDs only where accessible roles/names cannot identify cards, overlays, overflow,
  or preview media.
- 26 of 30 plan items browser-observable today.

### Out of Scope

- Product behavior changes, API redesigns, lifecycle renames, storage/DB changes.
- Real Unsplash coverage; provider-enabled is environment-gated and deferred.
- Backend-contract coverage of upload state machines, provider imports, attachment limits.
- Pixel-perfect visual regressions; card delta via computed style or screenshot diff only.
- Provider-enabled scenarios in PR CI.

## Capabilities

### New Capabilities

None. The contract already exists in
`openspec/specs/e2e/composer-media-attachments-test-plan.md`. A new `e2e` capability would
duplicate it.

### Modified Capabilities

None. Browser tests implement against existing product contracts in `composer-media-picker`,
`composer-preview`, `media-library`, and `media-provider-unsplash`. Any required test seam is
raised as a separate follow-up against the owning capability.

## Approach

1. Extend the existing media harness. Reuse `MediaRouteState`, deterministic PNGs, auth helpers,
   composer page object.
2. Rewrite `media-composer.spec.ts` against current markup; drive scenarios via API seeding and
   user interactions; tag each scenario with `@composer-ui-mocked` or `@composer-smoke-real`.
3. Extend `media-mocks.ts` with deferred responses, advanceable progress queues, binary-upload
   failure, transition queues, external-asset seeding, channel-limit provider.
4. Extend `media-files.ts` with a second inline image, `invalid.txt`, an ordered multi-file
   manifest, larger PNG.
5. Extend `compose-modal-page.ts` with picker cards, overlay/progress text, overflow `+N`,
   dropzone, remove-by-name, preview source (`blob:` vs `/api/media/assets/...`).
6. Any required frontend test seam becomes a separate SDD change; do not silently expand scope.
7. `@composer-smoke-real` covers happy paths with run-scoped data; never mutate the shared
   `dev-workspace-001`.
8. Mocked lane in PR CI; real smoke and provider in scheduled/manual jobs.

## Affected Areas

| Area                                                                | Impact                                                                                          |
|---------------------------------------------------------------------|-------------------------------------------------------------------------------------------------|
| `apps/web/app/e2e/specs/media-composer.spec.ts`                     | Rewrite; lane tags                                                                              |
| `apps/web/app/e2e/pages/compose-modal-page.ts`                      | Picker, overflow, overlay, dropzone, preview-source APIs                                        |
| `apps/web/app/e2e/fixtures/media-mocks.ts`                          | Deferred upload, progress, binary failure, transitions, external assets, channel-limit provider |
| `apps/web/app/e2e/fixtures/media-mocked-test.ts`                    | Composer-specific state controls                                                                |
| `apps/web/app/e2e/fixtures/media-real-test.ts`                      | Run-scoped isolation; channel-limit seed                                                        |
| `apps/web/app/e2e/fixtures/media-files.ts`                          | Second inline image, invalid file, ordered manifest, larger fixture                             |
| `apps/web/app/e2e/playwright.media-mocked.config.ts`                | `@composer-ui-mocked` tags                                                                      |
| `apps/web/app/e2e/playwright.media-real.config.ts`                  | `@composer-smoke-real` project; isolation guarantees                                            |
| `apps/web/app/package.json`                                         | `just` recipes for composer lanes                                                               |
| `.github/workflows/ci.yml`                                          | Mocked in PR CI; real smoke and provider in scheduled/manual jobs                               |
| `apps/web/app/src/components/CreatePostModal.vue`                   | Test seam only; tracked separately                                                              |
| `apps/web/app/src/components/composer/ComposerMediaPickerShell.vue` | Test seam only; tracked separately                                                              |
| `apps/web/app/src/components/composer/PostPreviewPanel.vue`         | Test seam only; tracked separately                                                              |

## Risks

| Risk                                                                   | Lik  | Mitigation                                                          |
|------------------------------------------------------------------------|------|---------------------------------------------------------------------|
| Client-driven upload progress cannot be granular via route fulfillment | High | Track test-controlled upload transport seam as follow-up; no sleeps |
| Existing selectors reflect old single-attachment markup                | High | Rewrite selectors; audit before merge                               |
| Shared workspace causes destructive cleanup in real-smoke              | High | Unique run ID; isolation contract; no shared mutation               |
| Attachment limits depend on channel `maxAttachments`                   | Med  | Deterministic channel-limit provider in mocks; assert in setup      |
| Unsplash is DEV/test synthetic and absent in real environment          | High | Provider-enabled opt-in only and out of PR CI                       |
| Utility-class assertions on selected cards are brittle                 | Med  | Computed-style or screenshot diff; no raw class-name coupling       |
| Blob URL lifecycle races with fast mocked completion                   | Med  | Mocks defer final response on demand                                |
| One serial spec produces hard-to-diagnose failures                     | Med  | Tag per lane; align `describe` boundaries with plan IDs             |
| Test seams added to product blur scope                                 | Med  | Any required seam is a separate SDD change                          |

### Test Seams (tracked but not pre-approved)

Required frontend seams become separate SDD changes against the owning capability. This proposal
does not authorize product code changes. Candidates: stable identity for the upload-overlay region
in `CreatePostModal.vue`; stable identity for the `+N` overflow card so its count is readable; a
test-controlled upload transport seam in `useComposerMediaPicker.ts`; stable identity on
`PostPreviewPanel.vue` media source kind (`blob:` vs persisted URL).

Follow-up seam references:

- `composer-media-picker`: `data-testid="media-dropzone"`,
  `data-testid="upload-overlay-local-upload"`, `data-testid="attachment-overflow"`
- `composer-preview`: `data-media-src-kind` on the preview media element
- `useComposerMediaPicker.ts`: transport-level seam for deterministic upload-progress callbacks
  without browser-timing dependence

## Rollback Plan

Revert in a single revert commit. Composer spec, page object, and fixture extensions are additive
and confined to `apps/web/app/e2e/`. Remove new `just` recipes and CI matrix entries. If a
frontend test seam was merged, revert it separately.

## Dependencies

- Frontend dev server at `https://pt-app.localhost:1355`.
- Test user with local credentials and a per-run seedable workspace.
- At least one channel with `maxAttachments <= 4`.
- Existing media E2E architecture.
- Real Unsplash environment plus isolated workspace seeding API for the provider-enabled lane
  (out of scope).

## Success Criteria

- [ ] `media-composer.spec.ts` does not mutate Pinia internals; locators match current markup.
- [ ] `@composer-ui-mocked` runs deterministically in parallel and reports clear failures.
- [ ] `@composer-smoke-real` validates happy paths with run-scoped data.
- [ ] Scenarios cover 26 of 30 plan items browser-observable today; the remaining 4 are deferred
  with rationale recorded.
- [ ] Any required frontend test seam is tracked as a separate follow-up proposal, not inlined.
- [ ] Mocked lane passes locally and in PR CI; real smoke passes in its scheduled/manual lane.