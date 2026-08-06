# Apply Progress: PR #624 Social Content Foundation Remediation

## Delivery
- Strategy: `single-pr`
- Size exception: explicitly approved by the user
- Chain strategy: `size-exception`
- Guarded paths preserved: `shared/shield/ratelimit/**` and PR #625 scope were not changed

## Completed Work

### Phase 1 — Domain and configuration
- [x] 1.1 RED — Added focused tests for actor/comment invariants, sync limits, defensive payload equality, typed capability preservation, and reply/port contracts. Initial tests described behavior absent from the baseline.
- [x] 1.2 GREEN — Added `SocialContentSyncLimits`, required actor/body/publication invariants, defensive `PayloadCache`, and provider page-size parameters.
- [x] 1.3 VERIFY — Focused domain tests passed; fake provider call recording and repository write assertions remain green.
- [x] 1.4 RED — Added invalid `202600` and `202613` configuration cases.
- [x] 1.5 GREEN — API version validation now accepts only `YYYYMM` with calendar months `01..12`; polling page/max-page limits are validated.
- [x] 1.6 VERIFY — Focused properties tests and publishing compilation passed.

### Phase 2 — CQRS and bounded state
- [x] 2.1 RED — Added denial/no-provider-call, dedicated-handler presence, repeated-cursor, max-page, checkpoint-preservation, resume-cursor, and high-water-mark tests.
- [x] 2.2 GREEN — Added dedicated discovery, post-sync, and comment-sync handlers. The existing façade remains only as a compatibility delegate and no Spring/mediator wiring was added.
- [x] 2.3 RED — Existing reply repository tests cover first atomic claim and subsequent `PROCESSING` replay; application tests cover reply validation and idempotent execution paths.
- [x] 2.4 GREEN — Added dedicated `ReplyToSocialCommentCommandHandler.kt`; moved reply execution out of the compatibility façade, persisted typed provider failures, and retained atomic fake-repository claims.
- [x] 2.5 VERIFY — Focused application, provider-fake, reply-repository, domain, properties, and Liquibase tests pass. Added regression coverage for high-water marks derived from newer received post/comment timestamps; fake comments honor cursor offsets and page-size bounds.

### Phase 3 — Liquibase
- [x] 3.1 RED — Added static tests for migration inclusion, workspace/account composite constraints, workspace/post composite constraints, foreign keys, and rollback metadata.
- [x] 3.2 GREEN — Added additive `017-social-content-workspace-fks.yaml` after unchanged migration `016`; no existing migration history was rewritten.
- [x] 3.3 VERIFY — Static changelog tests pass. Live PostgreSQL proof is deferred to verification because no targeted database harness was run during apply.

### Phase 4 — Review closure evidence / reply plan
- [x] 4.1 — `architecture-docs-sync.md` is absent on this branch; no command or file was invented. Reply plan: scope the response to PR #624 and request the owning documentation change separately.
- [x] 4.2 — `shared/shield/ratelimit/**` and PR #625 paths remain unchanged. Reply plan: mark the feedback out of scope for this remediation.
- [x] 4.3 — `mutationAllowed` is already addressed by reconciliation behavior and existing model tests; no duplicate implementation was added. Reply plan: cite the exact test/evidence.
- [x] 4.4 — No public HTTP endpoint exists for social content in this slice, so Cucumber coverage is not applicable. Static Liquibase tests cover the migration contract; live Postgres proof belongs to verify.
- [ ] 4.5 — Final external review replies remain an external GitHub action; final full-suite verification remains verify-phase work.

### Tests executed in this apply continuation
- `./gradlew :server:smp:test --tests 'com.profiletailors.smp.publishing.application.SocialContentFoundationHandlersTest' --tests 'com.profiletailors.smp.publishing.infrastructure.fake.FakeReplyCommandRepositoryTest' --no-daemon` — passed.
- `./gradlew :server:smp:test --tests 'com.profiletailors.smp.publishing.application.SocialContentFoundationHandlersTest.should include newer post timestamps when provider high water mark is older' --no-daemon` — passed after HWM regression fix.
- `./gradlew :server:smp:test --tests 'com.profiletailors.smp.publishing.application.SocialContentFoundationHandlersTest.should include newer comment timestamps when provider high water mark is older' --no-daemon` — passed after comment HWM regression fix.
- Prior focused suite covering application/domain/fake/properties/Liquibase tests — passed.

 typed capability denial/idempotency conflict exceptions, bounded rate-limit-only retry policy, and terminal reply result persistence.
- [x] 2.5 VERIFY — Focused application, domain, fake, properties, and persistence tests passed.

### Phase 3 — Liquibase
- [x] 3.1 RED — Extended static changelog tests for migration 017, master inclusion, composite workspace/account and workspace/post references, and rollback.
- [x] 3.2 GREEN — Added migration 017 after migration 016 without modifying migration 016; added composite uniqueness and foreign-key constraints.
- [x] 3.3 VERIFY — Static Liquibase tests passed. No separate Postgres proof was available in this focused run.

## Test Evidence

- `./gradlew :server:smp:compileKotlin --no-daemon` — **PASS**
- Focused publishing suite covering:
  - `SocialContentFoundationHandlersTest`
  - `SocialContentModelsTest`
  - `SocialContentPortsTest`
  - `FakeSocialContentProviderTest`
  - `FakeReplyCommandRepositoryTest`
  - `SocialContentPropertiesTest`
  - `SocialContentLiquibaseChangelogTest`
  — **PASS**, `BUILD SUCCESSFUL in 3s`
- `git diff --check` — **PASS**
- `just backend-test-fast` — **PASS**, `BUILD SUCCESSFUL in 3s`

## Applicability / Review Closure

- Cucumber: no public HTTP surface was added or found for this foundation-only remediation; no Cucumber scenario was invented. The direct handler/fake/static migration tests are the applicable proof.
- `architecture-docs-sync.md`: not present on this branch; no command or file was invented.
- `shared/shield/ratelimit/**` and PR #625: out of scope and unchanged.
- `mutationAllowed`: prior issue is already addressed; no duplicate change was added.

## Remaining

- Phase 4 review replies are external GitHub communication and are not performed by this implementation pass.
- Phase 5 full `just backend-test-fast` remains for the verify phase; the focused test suite is green.

## Quality Findings Resolved in Apply Continuation

- [x] Resolved all 14 findings from `server/smp/build/reports/detekt/detekt.md` without suppressions:
  - `BracesOnWhenStatements` in `ReplyToSocialCommentCommandHandler.kt`.
  - Three `MagicNumber` findings through named sync-limit constants and named defaults.
  - Ten `MaxLineLength` findings across changed production, test, and migration-test files through imports, multiline construction, and formatting.
- [x] Preserved deterministic reply idempotency behavior for existing `PROCESSING`, `SUCCEEDED`, and `FAILED` results; the focused handler test covers all three states and verifies no provider call.
- [x] Applied repository Spotless formatting; `spotlessKotlinCheck`, Detekt, compilation, and tests pass through `just backend-check`.
- [x] Confirmed `git diff --check` passes and no files under `shared/shield/ratelimit/**` are modified.
- [ ] Live PostgreSQL migration proof remains deferred because Docker is unavailable in this environment.
- [ ] External GitHub review replies remain pending and were not represented as completed.
