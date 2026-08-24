# Tasks: LinkedIn Company Pages Community Inbox — PR2 Foundation

Current implementation is **partial**: existing Kotlin handlers, controller, adapter, fakes, and
focused tests are not completion evidence. Keep every task open until its RED→GREEN verification is
rerun.

**Baseline note:** the existing dirty changes in this worktree are the intentional PR2 baseline.
Preserve them; do not reset, discard, or relocate them while completing these tasks.

## Review Workload Forecast

| Field                   | Value                                           |
|-------------------------|-------------------------------------------------|
| Estimated changed lines | 650–950                                         |
| 400-line budget risk    | High                                            |
| Chained PRs recommended | No                                              |
| Suggested split         | One coherent PR on the existing branch/worktree |
| Delivery strategy       | exception-ok                                    |
| Chain strategy          | size-exception                                  |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: size-exception
400-line budget risk: High

### Suggested Work Units

| Unit | Goal                                                                      | Likely PR | Notes                                                                                                                                           |
|------|---------------------------------------------------------------------------|-----------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| 1    | Complete all open PR2 contracts, persistence, integration, BDD, and gates | Single PR | Base is the existing `feature/linkedin-pages-02-sync-calendar` branch/worktree; authorized size exception; no auto-chain or PR2-A/B/C branches. |

## Phase 1: Contracts and access policy

- [x] 1.1 **RED→GREEN:** Add Spring/Mediator registration tests for sync, detail, and calendar; then
  make `SocialContentApplicationHandlers.kt` implement the correct handler interfaces and `@Service`
  wiring.
- [x] 1.2 **RED→GREEN:** Test approval, matching workspace/account, `ADMIN`, both organization
  scopes, supported API version, retention version, account kind, and disabled flags; add
  `SocialContentAccessGate` and typed denial while preserving false defaults in
  `SocialContentProperties.kt`.

## Phase 2: Adapter and persistence foundation

- [x] 2.1 **RED→GREEN:** Prove a disabled/mismatched gate causes zero token-resolution and HTTP
  calls; inject the gate into `LinkedInCommunityManagementAdapter.kt` before every provider request
  and keep `RealLinkedInPublisher` separate.
- [ ] 2.2 **PARTIAL — RED→GREEN evidence:** Schema-supported atomic post/tombstone/checkpoint
  ordering, rollback, workspace isolation, deterministic fake seam, and production R2DBC batch
  wiring are implemented and focused-verified. Complete actor persistence, approval-evidence
  persistence, and provider-portable checkpoint mapping remain blocked by the existing schema; no
  migrations or simulated productive adapters were added.
- [x] 2.3 **RED→GREEN:** Cover cursor resume, overlap deduplication, bounded retries,
  checkpoint-after-persistence, and full-sync-only tombstones; refactor
  `SocialContentSyncHandler.kt`/`SocialContentFoundationHandlers.kt` to the safe ports. Cases 1–4
  are evidenced below, and case 5 is covered by the current focused handler, fake-writer, and
  persistence test runs.
    - [x] Caso 1 — sync incremental con cursor y high-water mark: RED captured when an empty
      provider page did not advance the checkpoint high-water mark; GREEN persists the latest
      observed mark, resumes with the stored cursor, and applies the configured overlap.
    - [x] Caso 2 — deduplicación por overlap entre páginas: focused test verifies that page 1 and
      page 2 may return the same external post and that the batch writer receives only the newest
      `lastModifiedAt` version; existing production collection logic already satisfies this
      behavior, so no production change was needed.
    - [x] Caso 3 — retries acotados por rate limit: focused test verifies at most `maxRetries`
      retries beyond the initial attempt, honors `retryAfter`, uses the fallback backoff when
      absent, wraps the terminal provider error, and leaves posts/checkpoint unapplied; existing
      production retry logic already satisfies this behavior, so no production change was needed.
    - [x] Caso 4 — checkpoint después de persistencia: focused R2DBC batch-writer tests verify
      upserts/tombstones complete and commit before `checkpointRepository.save`; tombstone or commit
      failures propagate without saving, preserving the previous checkpoint. The minimal production
      change moves checkpoint save outside the transaction block while retaining error propagation.
    - [x] Foundation correction A —
      `SocialContentFoundationHandlersTest.incremental sync does not tombstone posts absent from the modified page`
      proves incremental foundation reads never infer deletion from an incomplete page.
    - [x] Foundation correction B —
      `SocialContentFoundationHandlersTest.overlap posts are upserted once with the newest version`
      proves foundation reads deduplicate by `ExternalPostId` and persist the newest
      `lastModifiedAt` version once.

## Phase 3: HTTP and Spring integration

- [ ] 3.1 **RED→GREEN:** Test v1 headers, invalid range/cursor/limit, isolation, detail
  immutability, and problem details; complete explicit version mappings and validation in
  `PublishingControllers.kt`.
- [ ] 3.2 **RED→GREEN:** Add application-context tests for `SocialContentConfiguration.kt`; bind
  production/fake beans conditionally so disabled operations resolve neither credentials nor
  external transport.

## Phase 4: Cucumber and focused verification

- [x] 4.1 **RED→GREEN:** Create tagged `social-content-sync.feature` and `community-inbox.feature`
  first with failing scenarios, then implement `SocialContentBddSteps.kt` (and
  `PublishingBddSteps.kt` changes) using `BddDatabaseSupport`, deterministic fakes, headers,
  isolation, gates, retry, tombstones, and personal OAuth/publisher regressions. RED was captured
  before the social-content test configuration/glue existed; GREEN evidence covers all tagged
  social-content scenarios in the fast suite. The full suite still reports four unrelated legacy
  publication failures in `PublishingBddSteps.kt:430`.
- [x] 4.2 **RED→GREEN:** Rerun focused domain/application/adapter/controller/config tests; do not
  mark existing historical tests complete without current evidence. Postgres Cucumber configuration
  now imports `SocialContentBddTestConfiguration`, and the full Postgres suite no longer reports the
  12 social-content context failures.

## Phase 5: Full gates and honest state

- [ ] 5.1 Run `SMP_DB_TEST_PASSWORD=test just backend-test-fast`, `just backend-bdd-fast`, Postgres
  BDD with `just infra-up`, `just backend-check`, `just backend-build`, and `just ci-local`; record
  failures and blockers.
- [ ] 5.2 Update `openspec/changes/linkedin-company-pages-community-inbox/state.yaml` only from
  current evidence: keep implementation `partial` until all gates pass, then advance to `verify`;
  never claim completion from historical results.
