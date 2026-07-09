## Exploration: Maintainable Playwright E2E tests for composer media attachments

### Current State
The requested plan is broad (30 browser scenarios) but the repository already has a useful media testing foundation: separate mocked and real-media Playwright configs, per-test `MediaRouteState`, generated deterministic PNG fixtures, authentication helpers, a composer page object, and an initial `media-composer.spec.ts`. The frontend already implements immediate blob previews, upload progress/finishing labels, scoped removal, library staging, attachment limits, four-card overflow, social-preview reconciliation, and DEV/test-only Unsplash stubs.

The current composer E2E suite is not sufficient as the implementation of the plan. It covers only basic attach/remove/failure/publish payload behavior; one test mutates Pinia internals directly, locators target old single-preview markup, and the media mock completes uploads immediately. Existing mocks cannot deterministically hold and advance upload progress, fail the binary POST, model polling transitions, seed external assets, or vary channel limits/provider flags. No dedicated composer real-smoke project/spec exists, and the real fixture is coupled to CAS-created assets and shared `dev-workspace-001` cleanup.

A realistic first scope is browser-observable composer behavior in two lanes: deterministic Chromium mocked coverage for transient/stateful scenarios, plus a small serial real-backend smoke set for immediate preview, picker selection/removal, overflow, and provider-disabled behavior. Backend state-machine/storage invariants remain outside Playwright. Provider-enabled real coverage must remain separately gated because Unsplash is currently a DEV/test stub rather than a production integration.

### Affected Areas
- `openspec/specs/e2e/composer-media-attachments-test-plan.md` — source requirements and lane boundaries.
- `apps/web/app/e2e/playwright.media-mocked.config.ts` — existing deterministic lane already includes `media-composer.spec.ts`; likely needs clearer composer tags/project naming rather than another overlapping config.
- `apps/web/app/e2e/playwright.media-real.config.ts` — reusable serial real-media lane, but its `testMatch` and base URL/data assumptions need explicit composer smoke support.
- `apps/web/app/e2e/specs/media-composer.spec.ts` — existing partial suite should be reorganized/expanded rather than duplicated.
- `apps/web/app/e2e/fixtures/media-mocked-test.ts` — good per-context isolation and auth foundation; should expose composer-specific state controls.
- `apps/web/app/e2e/fixtures/media-mocks.ts` — reusable CRUD/CAS state, but lacks delayed upload, progress control, binary-upload failure, transition queues, external assets, and provider/limit controls.
- `apps/web/app/e2e/fixtures/media-real-test.ts` — reusable authentication, run IDs, request ledger, and cleanup; currently fixed to a shared workspace and only generates three CAS fixtures.
- `apps/web/app/e2e/fixtures/media-files.ts` — deterministic generated PNGs are reusable; missing second named inline image, invalid text, ordered multi-file manifest, and a practical large fixture for progress tests.
- `apps/web/app/e2e/pages/compose-modal-page.ts` — existing entry/actions are reusable, but media locators assume one old `Selected media preview` image and lack picker cards, overlay/progress, overflow, dropzone, remove-by-name, and social-preview source APIs.
- `apps/web/app/src/components/CreatePostModal.vue` — actual inline blob/upload/overflow/preview behavior under test; some stable accessible labels exist, but additional stable test hooks may be needed to avoid utility-class coupling.
- `apps/web/app/src/composables/useComposerMediaPicker.ts` — owns staging, limits, provider flags, polling, imports, and apply semantics; reveals that provider search/import is currently synthetic in DEV/test.
- `apps/web/app/src/components/composer/ComposerMediaPickerShell.vue` — picker source/card/apply UI should be exercised through user-visible locators.
- `apps/web/app/src/features/media-composer/providers/MediaProviderPanel.vue` — has useful test IDs, but no direct covering tests and only synthetic provider behavior.
- `apps/web/app/src/components/composer/PostPreviewPanel.vue` — social preview boundary; currently has no direct covering tests.

### Approaches
1. **Extend the existing media E2E architecture** — evolve the current fixtures, mocks, page object, and `media-composer.spec.ts`; split scenarios by tags/describes and add only a small real composer smoke spec.
   - Pros: Reuses established auth, generated files, route isolation, cleanup, reporting, and commands; avoids duplicate mock stacks; keeps composer and CAS behavior aligned.
   - Cons: Requires careful refactoring of legacy composer tests and richer mock-control APIs; shared media mocks can become bloated without composer-focused helpers.
   - Effort: Medium

2. **Create a fully separate composer test stack** — new configs, fixtures, mocks, page object, and specs dedicated to the plan.
   - Pros: Strong isolation and scenario-specific APIs; easier short-term mapping from plan IDs to files.
   - Cons: Duplicates auth/media protocol/data setup, increases maintenance, risks divergence from existing CAS tests, and creates overlapping Playwright discovery/commands.
   - Effort: High

3. **Use mostly real-backend browser tests** — seed assets through APIs and induce upload states against live services.
   - Pros: Highest integration realism for happy paths.
   - Cons: Progress/failure timing is nondeterministic, provider flags are unavailable, shared workspace cleanup is risky, execution is slow/serial, and browser tests would overclaim backend invariants.
   - Effort: High

### Recommendation
Choose Approach 1. Keep one composer page object and one generated fixture catalog, add composer-focused capabilities to the existing mocked fixture/state, and organize coverage into `@composer-ui-mocked`, `@composer-smoke-real`, and separately gated `@composer-provider-real` tags. Replace Pinia-internal mutation with seeded API/mock state and user interactions. Model transient upload behavior through explicit test-controlled deferred responses/state transitions; do not use arbitrary sleeps. Add stable semantic/test IDs only where accessible roles/names cannot identify attachment cards, progress overlays, overflow, or preview media reliably.

Proposal scope should prioritize: immediate select/drop preview, first-valid-file semantics, removal, upload overlay/progress/failure/focus, picker selection/deselection/apply/limits, overflow, provider-disabled behavior, and blob-to-persisted preview swap. Real smoke should cover only stable happy paths. Provider-enabled real tests and backend invariants should be explicit follow-up/gated work unless a real Unsplash environment and isolated workspace seeding API are supplied.

### Risks
- Upload progress is driven by client upload callbacks; Playwright route fulfillment alone may not generate granular browser progress, so the design may require a narrow injectable transport seam or deterministic frontend test control.
- Existing composer tests and page-object selectors reflect older single-attachment markup and may produce false confidence until rewritten around current UI semantics.
- Real tests use a fixed shared workspace; running selection/removal/cleanup scenarios without isolated seeding can delete or depend on developer data.
- Attachment limits depend on selected channel fixtures; current scheduler mocks must expose deterministic `maxAttachments` values.
- Unsplash is currently DEV/test synthetic behavior and absent in the observed real environment; a passing mocked provider test cannot prove production integration.
- Utility-class assertions for selected cards are brittle; computed-style or visual assertions need controlled baselines.
- Blob URL lifecycle/source swapping can race with fast mocked completion unless upload completion is explicitly deferred.
- The broad plan should not be implemented as one serial spec; excessive cross-scenario setup would make failures hard to diagnose and maintain.

### Ready for Proposal
Yes — propose extending the existing media Playwright infrastructure with deterministic composer controls, a rewritten mocked composer suite, and a narrowly scoped real smoke lane. The proposal should explicitly defer real Unsplash integration and backend-only invariants unless the required environment and isolation APIs become available.
