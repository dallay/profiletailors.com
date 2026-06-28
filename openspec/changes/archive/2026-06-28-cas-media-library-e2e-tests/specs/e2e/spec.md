# Delta for E2E

## ADDED Requirements

### Requirement: Media suite organization

The test implementation MUST split CAS Media Library automation into explicit `media-ui-mocked`, `media-smoke-real`, `media-real-extended`, `media-backend-contract`, and `media-large-boundary` groups, with tags/projects matching their execution mode.

#### Scenario: Correct lane selection
- GIVEN a media E2E command runs
- WHEN tests are discovered
- THEN only scenarios matching that lane's tags/projects SHALL execute
- AND real-CAS tests SHALL NOT use mocked CAS route handlers.

#### Scenario: PR-safe coverage
- GIVEN ordinary PR CI runs
- WHEN media tests execute
- THEN mocked UI coverage SHALL be parallel-safe
- AND expensive large-boundary cases SHALL be excluded.

### Requirement: Deterministic media fixtures

The implementation MUST generate or provide deterministic media fixtures with a manifest recording filename, type, size, SHA-256, and expected relationship.

#### Scenario: Duplicate and mutation validation
- GIVEN `base.png`, `base-copy.png`, and `base-mutated.png`
- WHEN setup validates fixtures
- THEN duplicate files SHALL have equal bytes/hash
- AND mutated files SHALL decode successfully with a different hash.

#### Scenario: Large fixture exclusion
- GIVEN browser CI runs
- WHEN fixture setup starts
- THEN 500 MB boundary fixtures SHALL NOT be loaded by browser suites.

### Requirement: Auth/session and isolation setup

The implementation MUST provide authenticated media sessions, unique run/workspace namespaces, and idempotent cleanup for posts, assets, routes, and storage prefixes.

#### Scenario: Protected media access
- GIVEN no authenticated session
- WHEN `/media` opens
- THEN protected media data SHALL NOT be exposed.

#### Scenario: Cleanup after failure
- GIVEN a test creates run-marked assets/posts
- WHEN the test fails
- THEN teardown SHALL delete posts before assets
- AND fail if active run-owned records remain.

### Requirement: Real CAS request ledger assertions

Real-CAS tests MUST capture an ordered per-file request ledger correlated by workspace, assetId, and fixture hash, and MUST assert CAS protocol sequences without global request counts.

#### Scenario: New content sequence
- GIVEN fresh content uploads
- WHEN CAS completes
- THEN the ledger SHALL show PUT `201 PENDING_UPLOAD`, one binary POST, and READY evidence.

#### Scenario: Dedup sequence
- GIVEN equivalent bytes are already READY
- WHEN duplicate upload starts
- THEN the ledger SHALL show READY/dedup initiation and zero binary POSTs
- AND record `200` versus documented `201` as contract drift if observed.

### Requirement: Stateful route mocks

Mocked UI tests MUST use per-context stateful route mocks for list, upload, polling, preview, auth, rate-limit, error, pagination, deletion, and workspace-switch behavior.

#### Scenario: Mock reset
- GIVEN two mocked tests run in parallel
- WHEN each browser context starts
- THEN each SHALL receive isolated mock state
- AND handlers SHALL be removed after the test.

#### Scenario: Failure modeling
- GIVEN mocked PUT returns `429` or `5xx`
- WHEN upload runs
- THEN POST SHALL NOT follow failed initiation.

### Requirement: Known-defect handling

The suite MUST expose known product drift and defects without suppressing, normalizing, or treating them as implemented behavior.

#### Scenario: Accessibility defects
- GIVEN unnamed icon actions or fields without stable `id`/`name`
- WHEN accessibility checks run
- THEN failures SHALL be reported as known defects with trace evidence.

#### Scenario: Product limitation
- GIVEN composer media controls are inspected
- WHEN no library selector exists
- THEN the suite SHALL record the limitation, not fail unrelated flows.

### Requirement: Backend-only invariant exclusion

Browser E2E reports MUST NOT claim proof for physical blob uniqueness, DB locks, GC/reference counting, streaming memory, or 500 MB enforcement; those SHALL belong to backend-contract suites.

#### Scenario: Browser report boundary
- GIVEN a browser dedup test passes
- WHEN results are reported
- THEN the report SHALL state only observable CAS sequence and UI state were proven.

#### Scenario: Backend ownership
- GIVEN a backend-only invariant is required
- WHEN coverage is assigned
- THEN it SHALL map to WebFlux/PostgreSQL/storage tests, not Playwright.
