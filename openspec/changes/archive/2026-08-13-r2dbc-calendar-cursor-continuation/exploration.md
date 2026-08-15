## Exploration: r2dbc-calendar-cursor-continuation

**Change name**: `r2dbc-calendar-cursor-continuation`
**Linear issue**: DALLAY-550 — "[Backend] Implement production R2DBC calendar cursor continuation"
**Parent epic**: DALLAY-526 — Unified social content sync and community engagement
**Status**: explore
**Predecessor PR2 (archived)**: `linkedin-company-pages-community-inbox` — verify-report.md WARNING #3 documented the exact gap this change closes.

### Current State

The reader interface, application layer, and HTTP controller already accept an opaque `cursor: PageCursor` end-to-end, but the production SQL never applies it. The verified facts:

1. **Reader ignores the cursor and returns null.** `R2dbcSocialContentRepositories.findImportedPosts` (`server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/R2dbcSocialContentRepositories.kt:201-224`) builds a SELECT that filters by `workspace_id`, `published_at` range, optional `actorId` and `lifecycle`, and orders by `published_at, external_post_id`. It binds `query.scope`, `from`, `to`, optional `actorId` and `lifecycle`, and `limit` — but never references `query.cursor`. It then returns `SocialContentPage(items, null, items.maxOfOrNull { it.publishedAt })`, so `nextCursor` is always `null`. The `PostgreSQL` integration test `R2dbcSocialContentRepositoriesPostgresTest.\`findImportedPosts returns posts within range for actor\`` (line 272) and `\`findImportedPosts filters by lifecycle\`` (line 300) only assert item count and ordering, never cursor continuation.
2. **Application layer forwards the cursor unchanged.** `SocialContentCalendarQueryHandler.handle` (`server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/SocialContentCalendarQueryHandler.kt:32-43`) calls `reader.findImportedPosts(query)`, validates that no item crosses `query.scope`, and maps items to `SocialContentCalendarItem`. The page's `nextCursor` is propagated verbatim through `SocialContentCalendarResponse` (line 27). `WorkspaceSocialContentCalendarQueryHandler` (`SocialContentApplicationHandlers.kt:117-134`) just builds a `SocialContentCalendarQuery` from the workspace context and delegates.
3. **Controller already accepts opaque cursor.** `SocialContentController.calendar` (`PublishingControllers.kt:94-111`) takes `cursor: String?` and wraps with `PageCursor`, returns `limit = 50` by default. No response shape change is required.
4. **Domain `PageCursor` is opaque by design.** `PageCursor` (`SocialContentModels.kt:144-149`) is a `@JvmInline value class` with non-blank validation only — no codec. The `SocialContentCalendarQuery` (lines 57-72) already constrains `limit` to 1..100 and `from < to`. `SocialContentPage<T>` (in `SocialContentPorts.kt`) carries items, `nextCursor: PageCursor?`, and `highWaterMark: Instant?`.
5. **Ordering is partial and unsafe.** Current `ORDER BY published_at, external_post_id` does not include `workspace_id`, `provider`, or `social_account_id`. Those columns are always equal to the query's `workspace_id` and optional `actorId`, so within a single result they are redundant, but they MUST be present in the keyset to guarantee a total order when the cursor carries them — otherwise a tuple comparison `WHERE (published_at, external_post_id) > (?, ?)` becomes ambiguous about how to compare rows in different workspaces or providers.
6. **No keyset index.** The schema (`db/changelog/publishing/016-create-social-content-foundation.yaml`) defines only `idx_social_content_posts_workspace_actor_published` on `(workspace_id, social_account_id, published_at)` (line 174). That index covers the filter and `ORDER BY` start, but it is NOT a covering index for the cursor tie-breakers `(provider, social_account_id, external_post_id)`. A keyset query that compares the full 4-tuple `(published_at, provider, social_account_id, external_post_id)` will need a wider covering index, or PostgreSQL will fall back to in-memory sort once the workspace result set grows.
7. **BDD "Calendar preserves an opaque continuation cursor"** (`server/smp/src/test/resources/features/community-inbox.feature:40-45`) currently asserts that the opaque string reaches `state.content.lastCursor()` (i.e. `BddContentStore.findImportedPosts` line 302-318). The BDD store records the cursor but `return SocialContentPage(items, null, ...)` like the production reader, so the test passes only because propagation is checked, not continuation. The existing `\`rejects a reader result from another workspace\`` (line 103) and `\`passes workspace filters and opaque cursor to the reader\`` (line 71) use the in-memory `RecordingReader` in `SocialContentCalendarQueryHandlerTest.kt`.
8. **Existing cursor codec pattern to mirror.** `AuditEventCursorCodec` (`server/smp/src/main/kotlin/com/profiletailors/smp/governance/domain/AuditEventModels.kt:19-50`) is a pure-Kotlin domain `object` (no infrastructure imports) that encodes a typed `AuditEventCursor(createdAt, id)` as `Base64.getUrlEncoder().withoutPadding().encodeToString("${createdAt}|${id}".toByteArray())`, decoding with strict validation that throws `InvalidAuditEventCursorException` on blank, malformed, missing-separator, bad-date, or empty-id input. Tests cover 11 cases including whitespace trimming, separation boundaries, and cause preservation (`AuditEventCursorCodecTest.kt`). Used end-to-end through `GetWorkspaceAuditEventsHandler` (`governance/application/GetWorkspaceAuditEventsHandler.kt:36,56-64`) with a `+1` trick to detect the next page, and surfaced as 400 via `GovernanceProblemDetailsHandler.handle(InvalidAuditEventCursorException)` (line 43-44). Domain layer; `shared/presentation` only adds `CursorEncoder`/`Base64CursorEncoder` (`shared/presentation/src/main/kotlin/com/profiletailors/common/domain/presentation/pagination/CursorEncoder.kt`) but is a generic Base64 wrapper without the typed payload validation.
9. **Concurrent-import semantics are independent of the calendar cursor.** `ImportSocialPostsHandler` (`server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/SyncSocialPostsCommandHandler.kt:23-87`) and `SocialContentSyncHandler.importPosts` (`SocialContentSyncHandler.kt:47-130`) drive `ImportSocialPostsHandler` for provider-side pagination; they read posts from a `SocialContentProvider`, fold them in-memory, upsert, and call `tombstoneMissing` only on full sync (when `checkpoint?.cursor == null`, line 52). Sync checkpoints are `SyncCheckpoint.cursor` in `social_content_sync_checkpoints` — a separate opaque string from the calendar's `PageCursor` on the reader. Adding keyset cursor logic to `findImportedPosts` does NOT change sync semantics; the import flow does not consume `nextCursor` from the reader.
10. **Duplicate/omission risk under concurrent imports.** The same `social_content_posts` row may be upserted by an in-flight `ImportSocialPostsHandler` while a calendar reader returns it. Because the calendar keyset order is determined by `(published_at, provider, social_account_id, external_post_id)` — all immutable or only forward-progressing (`published_at` is a provider timestamp and may be updated by `upsert`) — a single row can move within the same page or between pages. Imported posts are append-only from the provider's perspective, so the calendar should treat `published_at` as a high-water keyset and accept the inherent "row may shift within page" risk that keyset pagination always carries. The proposal must call this out explicitly: stability is best-effort under concurrent writes, not transactional. The spec's "Prevent duplicates and omissions across overlapping pages and concurrent imports" needs a precise definition — the fix can guarantee no DUPLICATIONS within a single client session (last-seen-keyset excludes the previous boundary) and no OMISSIONS in the page-1..N union (next page starts strictly after last returned tuple), but cannot prevent an in-flight `published_at` update from making a row reappear in a later page.

### Affected Areas

**Will change (production code)**

- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/R2dbcSocialContentRepositories.kt` — add keyset decode + WHERE tuple predicate to `findImportedPosts`; add ORDER BY tuple; emit `nextCursor` when another page exists (use the `+1` trick: fetch `limit+1`, return `limit` items, build cursor from the boundary tuple when `items.size > limit`). No change to `tombstoneMissing`, `upsert`, `findByWorkspaceAndExternalId`, `find`, or `save`.
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/SocialContentModels.kt` — add `SocialContentCalendarCursor(publishedAt, provider, socialAccountId, externalPostId)` value class; add `InvalidSocialContentCursorException`; add `SocialContentCalendarCursorCodec` `object` next to `PageCursor` (mirror `AuditEventCursorCodec`). Pure-Kotlin, no infrastructure imports.
- `server/smp/src/main/resources/db/changelog/publishing/019-add-social-content-calendar-keyset-index.yaml` — new Liquibase changeset: `CREATE INDEX idx_social_content_posts_calendar_keyset ON social_content_posts (workspace_id, published_at, provider, social_account_id, external_post_id)`; add `<include>` in `db.changelog-master.yaml` after line 99; add rollback `dropIndex` entry.
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingProblemDetailsHandler.kt` — add `@ExceptionHandler(InvalidSocialContentCursorException::class)` returning 400 Bad Request with a dedicated `detail` and `title = "Invalid social content cursor"`. Mirrors `GovernanceProblemDetailsHandler.handle(InvalidAuditEventCursorException)` (`governance/infrastructure/http/GovernanceProblemDetailsHandler.kt:43-44`).

**Will change (tests)**

- `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/R2dbcSocialContentRepositoriesPostgresTest.kt` — add focused keyset coverage using Testcontainers Postgres: (a) seed N+limit posts, walk all pages, assert no duplicates and no omissions; (b) tampered cross-workspace cursor is rejected at the repository boundary by the `workspace_id` predicate (asserts isolation); (c) final page returns `nextCursor = null`; (d) lifecycle filter + cursor; (e) actor filter + cursor; (f) `published_at` boundary equality (the cursor's `published_at` is included; only `external_post_id` is strictly greater for the first row at the same instant, by design).
- `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/domain/SocialContentCursorCodecTest.kt` (NEW) — mirror `AuditEventCursorCodecTest` with codec unit tests: encode produces non-padded base64url; roundtrip; reject blank, missing separator, separator at end, separator at start, bad timestamp, blank id, whitespace trim, exception-cause preservation.
- `server/smp/src/test/resources/features/community-inbox.feature` — update the "Calendar preserves an opaque continuation cursor" scenario to assert the response carries a `nextCursor` (with `state.content.readerCalls()` not yet exercising real continuation, because the BDD fakes still return `nextCursor = null`); add NEW scenario "Calendar pages imported posts through production keyset cursor" that uses a real `R2dbcSocialContentRepositories` (likely requires a new wiring override or a new BDD glue class — see BDD coverage gap below).
- `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingProblemDetailsHandlerTest.kt` — add `handle maps InvalidSocialContentCursorException to a 400 problem detail with an invalid cursor title`.

**May change (optional / responsive)**

- `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/http/SocialContentControllersTest.kt` — current test only mocks the mediator. No signature change to `SocialContentCalendarResponse`, so no controller-test change is required. May add an assertion that an explicit cursor query parameter is parsed into `PageCursor(value)`, but the existing `GET calendar returns filtered social content page` test already proves this.
- `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/application/SocialContentCalendarQueryHandlerTest.kt` — the existing `RecordingReader` returns a `PageCursor("opaque.next")` directly. The handler is unchanged, so these tests stay green. May add a new test asserting the reader's `nextCursor` flows through, but that's already covered.
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/SyncSocialPostsCommandHandler.kt` — `ImportSocialPostsHandler` operates on `SocialContentProvider.fetchPosts`, not on `R2dbcSocialContentRepositories.findImportedPosts`. No change.

**Could be affected transitively (verify only)**

- `server/smp/src/test/kotlin/com/profiletailors/smp/bdd/SocialContentBddTestConfiguration.kt` `BddContentStore.findImportedPosts` (line 302-318) — returns `nextCursor = null`. The BDD feature file only asserts cursor propagation today. After adding a real-reader BDD scenario, this fake must either gain cursor logic or be replaced by the production repository.
- `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/SocialContentLiquibaseChangelogTest.kt` — must add an assertion that the new changelog file is included in `db.changelog-master.yaml` (line 99 area) and that the new index name appears in the changelog. Existing `\`foundation changelog includes expiry checkpoint and hierarchy indexes\`` (line 73) only checks the foundation changelog's indexes.

### Approaches

1. **Approach A — Mirror `AuditEventCursorCodec` in the publishing domain** (RECOMMENDED)
   - New `SocialContentCalendarCursorCodec` in `publishing/domain/SocialContentModels.kt` next to `PageCursor`; typed payload `SocialContentCalendarCursor(publishedAt, provider, socialAccountId, externalPostId)`; encode = `Base64.getUrlEncoder().withoutPadding().encodeToString("${published.toEpochMilli()}|${provider}|${socialAccountId}|${externalPostId}".toByteArray())`; decode validates each part and throws `InvalidSocialContentCursorException` on any failure.
   - Reader SQL: `WHERE (published_at, provider, social_account_id, external_post_id) > (:cursorPublishedAt, :cursorProvider, :cursorSocialAccountId, :cursorExternalPostId)` (row-tuple comparison PostgreSQL supports). Cursor `null` omits the predicate. `ORDER BY published_at, provider, social_account_id, external_post_id LIMIT :limitPlusOne`. Last returned tuple at the `limit`-th row becomes the next cursor; `nextCursor = null` when `items.size <= limit`.
   - Domain layer stays pure Kotlin (no Spring, no infrastructure imports — preserves `domain <- application <- infrastructure` rule from `openspec/config.yaml` rules.design). Decoder fails closed at the application layer boundary; reader SQL never sees an invalid cursor.
   - Pros:
     - Matches the existing governance pattern 1:1, including the typed payload and `InvalidXxxException` discipline.
     - Domain remains Spring-free; codec stays a testable `object` with no DI.
     - Cursor format is well-documented: it can only be produced by `encode`, and `decode` is the single source of truth for what an attacker can forge.
     - Workspace isolation is enforced twice: the SQL `WHERE workspace_id = :workspaceId` is the primary guard, and the row-tuple predicate keeps the cursor strictly within the same `(workspace_id, published_at, ...)` range.
     - 400 mapping via `ProblemDetails` follows the existing `GovernanceProblemDetailsHandler` pattern.
   - Cons:
     - Cursor string is longer than a simple offset/int. Acceptable because cursors are opaque to clients and already opaque in the rest of the API.
     - Requires a Liquibase changelog and master `include` line.
   - Effort: Low (one new domain class, one new exception, one new codec object, one new Liquibase changeset, one new `@ExceptionHandler`, focused repository and BDD tests).

2. **Approach B — Reuse `Base64CursorEncoder` from `shared/presentation`**
   - Use the generic `Base64CursorEncoder` to encode `${publishedAt.toEpochMilli()}|${provider}|${actorId}|${externalPostId}`. No typed payload class; readers/writers agree on the delimiter string only by convention.
   - Pros: Slightly less code; one less domain value class.
   - Cons: No type-safe payload; no per-field validation in the decoder; a future change to the delimiter silently breaks existing cursors. The `AuditEventCursorCodec` precedent exists precisely to avoid this drift. `Base64CursorEncoder.decode` returns the raw string and the consumer must re-parse and re-validate, scattering the validation logic.
   - Effort: Low.

3. **Approach C — Pure SQL `OFFSET`/`LIMIT` pagination**
   - Use `OFFSET = pageIndex * limit` and return `nextCursor = (pageIndex + 1).toString()`. Smallest SQL delta.
   - Pros: Trivial to implement; no cursor schema concerns.
   - Cons: Violates the issue's "keyset continuation" requirement and risks duplicates/omissions on concurrent imports (offset shifts when new rows are inserted at the page boundary). Linear DALLAY-550 explicitly says keyset. Reject.

### Recommendation

**Approach A**. It mirrors the verified `AuditEventCursorCodec` pattern that has 11 codec tests, is exercised by the `GetWorkspaceAuditEventsHandler`, and is mapped to a 400 by `GovernanceProblemDetailsHandler`. Reusing the same shape makes the change self-documenting and gives the diff a precedent. The new `SocialContentCalendarCursor` is the right granularity because calendar ordering depends on the full 4-tuple identity `(workspace_id, provider, social_account_id, external_post_id)` plus `published_at`, and a typed value class captures the contract.

The tuple comparison `(published_at, provider, social_account_id, external_post_id) > (?, ?, ?, ?)` is the keyset predicate. PostgreSQL evaluates it lexicographically, which matches the `ORDER BY` ordering. The decoder runs at the repository boundary (or one layer up in the application handler — see below) so invalid cursors are rejected before any SQL is built. Both `AuditEventCursorCodec` and the proposed `SocialContentCalendarCursorCodec` are pure-Kotlin domain `object`s; no infrastructure imports are added to the domain layer.

**One design choice that the proposal must lock in**: whether the cursor decoder lives in the repository (call it from the reader) or in the application handler (decode first, then pass a typed `SocialContentCalendarCursor` to the reader via a new `SocialContentCalendarQuery` field). The cleaner separation is the application handler (it already validates input), but the reader signature would need to change. The lower-risk path is to keep the signature, decode inside the reader, throw `InvalidSocialContentCursorException` from the reader, and let the existing `@RestControllerAdvice` map it to 400. The recommendation is the lower-risk path to minimize blast radius and keep the reader's contract identical.

**Index**: a new covering index `(workspace_id, published_at, provider, social_account_id, external_post_id)` is required for stable keyset performance. The existing `idx_social_content_posts_workspace_actor_published` covers the leading columns but not the tie-breakers, and tuple comparison falls back to in-memory sort without it. The new index doubles the index footprint on the table but is the only way to keep the keyset query bound by index scan; alternatives like replacing the existing index are not viable because other queries (range scans without cursor) still benefit from the existing index.

**Concurrency caveat to call out in the proposal**: imported posts can be upserted between paginated calls; the spec's "no duplicates and no omissions" guarantee holds for a single client session that uses the returned `nextCursor` (because the tuple comparison is strict-greater-than, so the last row of page N is excluded from page N+1) but does not hold for "the same row appears twice" if a concurrent `upsert` changes its `published_at` to a value in the already-returned range. This is inherent to keyset pagination on a mutable set; the proposal should phrase the guarantee as "no duplicates and no omissions within a single client's paginated sequence against a stable snapshot, modulo concurrent `published_at` rewrites". This matches how every other keyset-paginated system in the industry documents it.

### Risks

- **Concurrent `published_at` rewrite under import** can cause a row to reappear or skip in a client's paginated session. Mitigated by documenting the guarantee precisely; not preventable without a snapshot isolation level that PostgreSQL `READ COMMITTED` (the default) does not offer, and adding a `REPEATABLE READ` transaction is out of scope.
- **Cursor tampering for cross-workspace read** is the highest-impact risk. Mitigation: the SQL always includes `WHERE workspace_id = :workspaceId`; the row-tuple comparison does NOT include `workspace_id` but every row in the result set is already constrained by the leading `workspace_id = :workspaceId`, so a forged cursor can only walk the rows the caller is already entitled to read. The proposal should add a focused repository test that proves this: forge a cursor from another workspace's post id, assert no rows leak.
- **No covering index** for the keyset tuple comparison: a workspace with thousands of imported posts would degrade. Mitigation: add the new index in the same change.
- **Existing handler test in `SocialContentCalendarQueryHandlerTest`** passes because the reader stub returns a hard-coded `nextCursor`. The handler is unchanged, so it stays green. No regression.
- **Existing BDD scenario "Calendar preserves an opaque continuation cursor"** currently passes because `BddContentStore.findImportedPosts` records the input cursor and the BDD glue asserts that recorded value. After the change, the BDD `BddContentStore` still records the input cursor but now MUST also return a real `nextCursor` so the new end-to-end BDD scenario can drive page 2 through the real reader — that requires wiring the production `R2dbcSocialContentRepositories` into the BDD context (currently the BDD `SocialContentBddTestConfiguration` provides `BddContentStore` as the `SocialContentReader` bean, see lines 120 and 232). Two options: (1) extend the BDD store to also return a non-null `nextCursor` when seeded posts exceed the page size, OR (2) add a new BDD context profile that injects the production repository. Option 1 keeps the BDD test cheap; option 2 actually proves the production reader. The proposal MUST pick one — recommend option 2 for the "Calendar pages imported posts through production keyset cursor" scenario and option 1 for the existing "preserves opaque cursor" scenario.
- **No new Liquibase changelog without updating `db.changelog-master.yaml`**. Liquibase will silently skip a changeset file that is not `include`d. Existing test `\`master includes the social content foundation changelog\`` (`SocialContentLiquibaseChangelogTest.kt:10`) and `\`foundation changelog defines isolated content tables and rollback\`` (line 20) only assert the 016/017/018 changelogs. The new test must assert that the master references the new 019 file.
- **BDD test isolation**: a new BDD scenario that drives page 2 must not depend on shared mutable state across scenarios. The existing `@Before("@linkedin-social-content")` resets the BDD store; if option 2 is chosen, the Postgres BDD lane must seed posts into the real table and reset between scenarios via the existing `BddDatabaseSupport.resetDatabase()`.
- **Test-only R2DBC primary alias** (per PR2 verify-report WARNING 1 and `SmpApplication.kt:41` `RestControllerAdvice` filter): the production `R2dbcSocialContentRepositories` is wired through Spring Boot's component scan, so no alias concerns. The BDD context (option 2) must avoid the alias trap.
- **Coverage threshold 80%** in `openspec/config.yaml` testing.coverage: the new codec + reader keyset + ProblemDetails mapping code must be covered by focused tests; the `R2dbcSocialContentRepositoriesPostgresTest` already provides the integration lane.

### Ready for Proposal

**Yes.** The technical approach is clear (Approach A), the precedent is established (`AuditEventCursorCodec`), the schema change is small (one index), the BDD coverage strategy has two viable options that the proposal can pick from, and the concurrency caveats are now explicit. The orchestrator should hand off to `sdd-propose` next, with the proposal calling out:

- A new Liquibase changeset `019-add-social-content-calendar-keyset-index.yaml` covering `(workspace_id, published_at, provider, social_account_id, external_post_id)`.
- A new domain codec `SocialContentCalendarCursorCodec` and typed cursor class in `SocialContentModels.kt`.
- The reader's `findImportedPosts` is the only method to change; all other repository methods and the application/handler/controller layers stay byte-for-byte identical.
- The `+1` trick to detect the next page (fetch `limit+1`, return the first `limit`, build cursor from the limit-th tuple when overflow).
- Cursor tampering tested explicitly: forge a cursor that points to a foreign workspace's row, assert no leak.
- BDD coverage: option 2 (real production reader) for "Calendar pages imported posts through production keyset cursor"; keep the existing `BddContentStore` for the existing propagation scenario.
- `InvalidSocialContentCursorException` mapped to 400 by `PublishingProblemDetailsHandler`.

Open question for the proposal stage (do not block on it now, but the orchestrator should surface it): should the row-tuple cursor compare STRICT `>` or `>=`? Strict is correct for the "no duplicates" guarantee; non-strict would be needed only if the SQL needed to return a row whose `published_at` exactly equals the cursor and whose remaining tuple is `>`. AuditEventCursorCodec uses strict `>` (via the `+1` trick + comparison in the reader). The proposal should lock in strict `>`.

### Files Verified

- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/R2dbcSocialContentRepositories.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/SocialContentModels.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/SocialContentPorts.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/SocialContentCalendarQueryHandler.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/SocialContentApplicationHandlers.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/SocialContentSyncHandler.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/SyncSocialPostsCommandHandler.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingControllers.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingProblemDetailsHandler.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/governance/domain/AuditEventModels.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/governance/application/GetWorkspaceAuditEventsHandler.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/governance/infrastructure/http/GovernanceProblemDetailsHandler.kt`
- `shared/presentation/src/main/kotlin/com/profiletailors/common/domain/presentation/pagination/CursorEncoder.kt`
- `shared/presentation/src/main/kotlin/com/profiletailors/common/domain/presentation/pagination/InvalidCursor.kt`
- `shared/common/src/main/kotlin/com/profiletailors/common/domain/model/pagination/CursorPage.kt`
- `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/R2dbcSocialContentRepositoriesPostgresTest.kt`
- `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/SocialContentLiquibaseChangelogTest.kt`
- `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/application/SocialContentCalendarQueryHandlerTest.kt`
- `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/application/SocialContentSyncHandlerTest.kt`
- `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/http/SocialContentControllersTest.kt`
- `server/smp/src/test/kotlin/com/profiletailors/smp/governance/domain/AuditEventCursorCodecTest.kt`
- `server/smp/src/test/kotlin/com/profiletailors/smp/bdd/glue/SocialContentBddSteps.kt`
- `server/smp/src/test/kotlin/com/profiletailors/smp/bdd/SocialContentBddTestConfiguration.kt`
- `server/smp/src/test/resources/features/social-content-sync.feature`
- `server/smp/src/test/resources/features/community-inbox.feature`
- `server/smp/src/main/resources/db/changelog/publishing/016-create-social-content-foundation.yaml`
- `server/smp/src/main/resources/db/changelog/publishing/017-social-content-workspace-fks.yaml`
- `server/smp/src/main/resources/db/changelog/publishing/018-social-content-comment-checkpoints.yaml`
- `server/smp/src/main/resources/db/changelog/db.changelog-master.yaml`
- `openspec/changes/archive/2026-08-05-linkedin-company-pages-community-inbox/verify-report.md`
- `openspec/config.yaml`
- Linear issue DALLAY-550 (read via Linear MCP, In Progress, parent DALLAY-526)
