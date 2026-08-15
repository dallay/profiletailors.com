# Tasks: Production R2DBC Calendar Cursor Continuation

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 500–700 |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | Single PR, internally reviewable as Slice A → B → C → D |
| Delivery strategy | single-pr with approved size exception |
| Chain strategy | size-exception |

Decision needed before apply: No — user approved the size exception.
Chained PRs recommended: Yes
Chain strategy: size-exception
400-line budget risk: High

The user explicitly approved a single-PR size exception despite the estimated 500–700 changed lines. Slice boundaries remain dependency-ordered and independently reviewable; do not create branches or commits during apply.

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| Slice A | Cursor contract and domain tests | PR 1, Slice A | Complete pure-Kotlin contract; no SQL; depends on no slice. |
| Slice B | Production keyset reader, index, Postgres tests | PR 1, Slice B | Depends on A; complete persistence deliverable with migration rollback. |
| Slice C | HTTP 400 mapping and production-reader BDD | PR 1, Slice C | Depends on B; complete API acceptance behavior. |
| Slice D | Focused verification and quality gates | PR 1, Slice D | Depends on A–C; evidence and final gate only. |

## Phase 1: Slice A — Cursor Contract (complete deliverable)

- [x] 1.1 RED: Add `SocialContentCalendarCursorCodecTest.kt`, `SocialContentCalendarCursorTest.kt`, and `InvalidSocialContentCursorExceptionTest.kt` covering six fields, version `1`, URL-safe unpadded Base64, delimiter isolation, roundtrip, blank/malformed/unsupported inputs, invalid timestamps/providers/IDs, and cause preservation.
- [x] 1.2 GREEN: Add `CalendarCursorVersion`, `SocialContentCalendarCursor`, `InvalidSocialContentCursorException`, and `SocialContentCalendarCursorCodec` to `publishing/domain/SocialContentModels.kt`; keep the domain pure Kotlin and validate all decoded fields.
- [x] 1.3 REFACTOR: Align codec structure and naming with `AuditEventCursorCodec`; preserve the cursor workspace as provenance/binding metadata only, with no HMAC/signing or authorization behavior.

## Phase 2: Slice B — Production Reader and Persistence (complete deliverable)

- [x] 2.1 RED: Extend `R2dbcSocialContentRepositoriesPostgresTest.kt` with limit+1 page walks, tie timestamps, final-page null cursor, actor/lifecycle filters, strict tuple boundaries, no overlap/omission, and cross-workspace cursor rejection before SQL.
- [x] 2.2 RED: Extend `SocialContentLiquibaseChangelogTest.kt` to require the 019 master include, index name/columns, and rollback definition.
- [x] 2.3 GREEN: Add `server/smp/src/main/resources/db/changelog/publishing/019-add-social-content-calendar-keyset-index.yaml` and include it after 018 in `db.changelog-master.yaml`.
- [x] 2.4 GREEN: Update `R2dbcSocialContentRepositories.kt` to decode and bind-check before SQL, derive `workspace_id` only from `query.scope.value`, apply strict four-field keyset `WHERE`/`ORDER BY`, fetch `limit + 1`, and emit the boundary cursor.
- [x] 2.5 REFACTOR: Consolidate SQL bindings and Postgres fixtures; verify existing range, actor, lifecycle, and non-cursor repository behavior remains unchanged.

## Phase 3: Slice C — HTTP and BDD (complete deliverable)

- [x] 3.1 RED: Add `PublishingProblemDetailsHandlerTest.kt` coverage for HTTP 400, title, and `INVALID_SOCIAL_CONTENT_CURSOR` for malformed, unsupported-version, and workspace-mismatch exceptions.
- [x] 3.2 GREEN: Add the `InvalidSocialContentCursorException` handler to `PublishingProblemDetailsHandler.kt` with the existing Problem Details contract.
- [x] 3.3 RED: Add `social-content-calendar-cursor.feature` tagged `@social-content-calendar @smoke @fast @postgres` for continuation, final-page behavior, foreign-workspace rejection, malformed cursor rejection, and request-scope isolation.
- [x] 3.4 GREEN: Add `SocialContentCalendarCursorBddSteps.kt` and wire `SocialContentBddTestConfiguration.kt` to the production R2DBC reader; use `BddDatabaseSupport`, `WebTestClient`, `latestResponse`, `USER_BEARER`, and required headers.
- [x] 3.5 REFACTOR: Reset database state per scenario, keep sync-checkpoint cursors out of calendar decoding, and preserve existing `community-inbox.feature` opaque-cursor propagation coverage.

## Phase 4: Slice D — Verification and Quality Gates

- [x] 4.1 Run focused checks through Just: `just backend-test-fast`, `just backend-test-postgres`, `just backend-bdd-fast`; run `just infra-up` then `just backend-bdd-postgres`, and clean up with `just infra-down`.
- [ ] 4.2 Run `just ci`; confirm AC1–AC6, 80% coverage targets, migration rollback evidence, request-context tenant SQL, and no unresolved stability/concurrency caveat is misrepresented.
