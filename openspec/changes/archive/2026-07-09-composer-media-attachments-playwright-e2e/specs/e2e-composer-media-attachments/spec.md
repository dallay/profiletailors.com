# Delta for E2E Composer Media Attachments

> Browser automation contract for `openspec/specs/e2e/composer-media-attachments-test-plan.md`.
> Product behavior lives in `composer-media-picker`, `composer-preview`, `media-library`, and
> `media-provider-unsplash`. Test seams required by markup are tracked separately.

## ADDED Requirements

### Requirement: Lane Topology

The suite SHALL organize composer scenarios into three Playwright lane projects.

| Tag | Project | Purpose | CI |
|---|---|---|---|
| `@composer-ui-mocked` | `media-mocked-chromium` | Progress / failure / limit / deselection | PR |
| `@composer-smoke-real` | `media-real-chromium` | Real-backend happy paths | scheduled/manual |
| `@composer-provider-real` | `media-real-chromium` | Real Unsplash | deferred |

#### Scenario: Single lane tag per scenario

- GIVEN a scenario carries one tag from the topology table
- WHEN Playwright discovery runs
- THEN the scenario is selected by exactly one config project

#### Scenario: Mocked lane runs in parallel

- GIVEN `@composer-ui-mocked` is selected
- WHEN Playwright executes
- THEN workers SHALL run in parallel with isolated `MediaRouteState` per test

### Requirement: Selectors and Accessibility Contract

Locators SHALL prefer accessible roles, names, and ARIA. Stable `data-testid` MAY be introduced
ONLY for: overflow count, upload-overlay region, preview media source.

#### Scenario: Locators resolve against current markup

- GIVEN locators for inline preview cards, tile, dropzone, picker cards, overlay, `+N`, preview
- WHEN the page object exposes them
- THEN each MUST resolve via accessible role/name or approved `data-testid`

#### Scenario: No Pinia internals mutated

- GIVEN a scenario needs seeded picker state
- WHEN initial state is arranged
- THEN state SHALL be driven via API seeding, mock state, or user interactions
- AND SHALL NOT mutate `$pinia.state` directly

### Requirement: Mocked Fixture Capability Contract

`media-mocks.ts` and `media-mocked-test.ts` MUST expose: deferred upload, advanceable progress,
binary-upload failure, transition queues, external-asset seeding, channel-limit provider,
picker-source switch.

#### Scenario: Deferred upload is controllable

- GIVEN a PUT is queued via `mockNextPut`
- WHEN release has not been signalled
- THEN the inline preview SHALL remain `uploading`
- AND no `Finishing up...` segment SHALL render

#### Scenario: Channel-limit provider is deterministic

- GIVEN a mocked channel advertises `maxAttachments = 4`
- WHEN 5 library assets are staged
- THEN `Apply` SHALL be blocked and a visible warning SHALL render

### Requirement: Real Smoke Isolation Contract

`@composer-smoke-real` SHALL run with a unique run ID, seed assets through the per-run API, and
MUST NOT mutate the shared `dev-workspace-001`.

#### Scenario: Run ID isolates data

- GIVEN a real smoke scenario executes
- WHEN fixtures are created
- THEN assets and cleanup SHALL belong to the run ID namespace
- AND `afterEach` SHALL delete every asset created under that run ID

#### Scenario: No destructive cleanup of shared workspace

- GIVEN the real smoke lane executes
- WHEN teardown runs
- THEN the fixture SHALL NOT call delete endpoints against `dev-workspace-001` assets

### Requirement: Evidence and Reporting Contract

Each lane MUST emit deterministic evidence: HTML report, trace on first retry, screenshot on
failure, and per-scenario tag in the report header.

#### Scenario: Mocked lane emits coverage report

- GIVEN `@composer-ui-mocked` scenarios finish
- WHEN reporters run
- THEN `playwright-media-mocked-report/index.html` SHALL exist with `data-tag=@composer-ui-mocked`

#### Scenario: Real smoke retains failure video

- GIVEN a `@composer-smoke-real` scenario fails in CI
- WHEN the run completes
- THEN a retained-on-failure video SHALL be written under `playwright-media-real-report`

### Requirement: Plan Coverage Mapping

The implementation MUST cover 26 of 30 plan items browser-observable today. Items 18, 20, 21, 22,
23 (Unsplash provider real flows) are environment-gated and deferred.

| Plan items | Lane | Coverage |
|---|---|---|
| 1, 3, 5, 14-17, 19, 26, 27, 28 | `@composer-smoke-real` | Live-observable behavior |
| 2, 4, 6, 7-13, 24, 25, 29, 30 | `@composer-ui-mocked` | Stateful mocked scenarios |
| 18, 20, 21, 22, 23 | `@composer-provider-real` | **Deferred** - needs real Unsplash |

#### Scenario: Deferred rationale is recorded

- GIVEN plan items 18, 20, 21, 22, 23 are not in PR CI
- WHEN the spec is archived
- THEN a deferred-rationale note SHALL live in `verify-report.md`
- AND cite the missing environment/flag

### Requirement: Anti-Overclaim Guardrails

Scenarios MUST NOT assert backend-only invariants (exact state-machine transitions, provider import
internals, storage cleanup correctness) at the browser layer.

#### Scenario: Overclaim is removed or moved

- GIVEN a scenario asserts `PENDING_UPLOAD -> UPLOADING -> READY` transitions
- WHEN the spec is reviewed
- THEN that scenario SHALL be moved to `@backend-contract` or removed

### Requirement: Determinism Without Sleeps

Upload timing SHALL be driven through mock-side deferred responses or a test-controlled transport
seam. `page.waitForTimeout` is prohibited for deterministic assertions.

#### Scenario: No sleep-driven progress assertion

- GIVEN an overlay progress assertion
- WHEN the test waits for a transition
- THEN it SHALL await a deterministic mock release signal
- AND SHALL NOT use `waitForTimeout` to bridge real-time gaps

## REMOVED Requirements

None.

## Deferred Plan Items

| # | Topic | Reason |
|---|---|---|
| 18 | Unsplash tab visible when enabled | Provider-enabled env absent |
| 20 | Source switch preserves staged selection | Requires provider-enabled env |
| 21 | Unsplash search renders in picker | Requires provider-enabled env |
| 22 | Unsplash import keeps modal open | Requires provider-enabled env |
| 23 | Imported asset becomes composer attachment | Requires provider-enabled env |
