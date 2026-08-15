# Design: Production R2DBC Calendar Cursor Continuation

## Technical Approach

Mirror `AuditEventCursorCodec` with a 6-field cursor that carries provenance. Pure-Kotlin domain types live next to `PageCursor` in `publishing/domain/SocialContentModels.kt`:

- `SocialContentCalendarCursor(version, workspaceId, publishedAt, provider, socialAccountId, externalPostId)` — `data class` with `init` rejecting blank ids and unknown `version`.
- `CalendarCursorVersion` — `@JvmInline value class`; current `"1"`. Decoder rejects unknown versions.
- `SocialContentCalendarCursorCodec` — `object` with `encode` / `decode`. URL-safe Base64 without padding; inner delimiter is `\u001F` (`|` is in the URL-safe Base64 alphabet and would collide after Base64-encoding; `\u001F` cannot appear in any field by construction).
- `InvalidSocialContentCursorException(message, cause?) : IllegalArgumentException`.

`R2dbcSocialContentRepositories.findImportedPosts` decodes `query.cursor` BEFORE SQL is built. Decode failure raises `InvalidSocialContentCursorException`. After decode it compares `cursor.workspaceId` against `query.scope.value`; mismatch raises the same exception. The repo applies the keyset tuple predicate and binds `limitPlusOne = query.limit + 1`; Kotlin returns `fetched.take(limit)`, emits `nextCursor = encode(buildCursor(items[limit-1]))` when overflow exists, else `null`. The `+1` row's `workspaceId` is always the request's `workspaceId`. Controller, handlers, ports, and response shapes stay unchanged. 400 mapping wires into `PublishingProblemDetailsHandler`.

## Architecture Decisions

| # | Decision | Choice | Rationale |
|---|---|---|---|
| 1 | Codec location | Pure-Kotlin domain `object` next to `PageCursor` | Mirrors `AuditEventCursorCodec`; preserves `domain <- application <- infrastructure`; typed payload + typed exception. |
| 2 | Pagination model | Keyset on `(published_at, provider, social_account_id, external_post_id)` ASC, strict `>` | Total order across full post identity; deterministic under stable data. OFFSET unstable; `published_at` alone cannot break ties. |
| 3 | Index | Covering `(workspace_id, published_at, provider, social_account_id, external_post_id)` | Workspace always filtered first; remaining columns satisfy predicate + order without spill-to-sort. |
| 4 | Invalid cursor | `InvalidSocialContentCursorException` -> 400 in `PublishingProblemDetailsHandler` | Matches `GovernanceProblemDetailsHandler` pattern; explicit client error. |
| 5 | **Workspace in cursor is provenance, not authorization** | Cursor carries `workspaceId`; reader validates match against request-context `workspaceId`; SQL `WHERE workspace_id` always derives from request context | Defense in depth: SQL `WHERE workspace_id = :requestScope` is the boundary; cursor binding gives clients a typed 400 instead of a silently empty page. |
| 6 | **No cryptographic signing of cursor** | Plain Base64 payload (no HMAC) | AC "Foreign-workspace data cannot be reached through cursor tampering" is satisfied by SQL filter + workspace-binding. Modified cursor cannot leak cross-workspace data because (a) SQL filters by request workspace and (b) cross-workspace cursor is rejected with 400 before SQL. HMAC adds key-management surface without strengthening the security-relevant property. |

## Data Flow

```text
Request workspace context
        │
        ▼
decode(cursor, expectedWorkspaceId)
        │
        ├── malformed ─────────────► 400 INVALID_SOCIAL_CONTENT_CURSOR
        │
        ├── cursor.workspaceId != expectedWorkspaceId
        │                         └► 400 INVALID_SOCIAL_CONTENT_CURSOR
        │
        ▼
keyset
        │
        ▼
Repository query
WHERE workspace_id = :requestWorkspaceId
  AND (...)
```

The reader signature is `findImportedPosts(query: SocialContentCalendarQuery)` where `query.scope.value` is the request-context workspace. The reader never receives the cursor's `workspaceId` as a parameter for the SQL `WHERE` clause.

## File Changes

| File | Action |
|---|---|
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/SocialContentModels.kt` | Add cursor, version, exception, codec. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/R2dbcSocialContentRepositories.kt` | `findImportedPosts`: decode + workspace-binding + keyset tuple + `LIMIT :limit + 1` + cursor emission. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingProblemDetailsHandler.kt` | `@ExceptionHandler(InvalidSocialContentCursorException::class) -> 400`. |
| `server/smp/src/main/resources/db/changelog/publishing/019-add-social-content-calendar-keyset-index.yaml` | Covering index with `rollback: dropIndex`. |
| `server/smp/src/main/resources/db/changelog/db.changelog-master.yaml` | Include 019 after 018. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/domain/SocialContentCalendarCursorCodecTest.kt` | Codec roundtrip, version, malformed, `\u001F` splitting. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/domain/SocialContentCalendarCursorTest.kt` | Value class validation. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/domain/InvalidSocialContentCursorExceptionTest.kt` | Exception contract. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/R2dbcSocialContentRepositoriesPostgresTest.kt` | Keyset walks + workspace-binding reject (`@postgres`). |
| `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingProblemDetailsHandlerTest.kt` | 400 mapping contract. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/SocialContentLiquibaseChangelogTest.kt` | Assert 019 include, index name, rollback. |
| `server/smp/src/test/resources/features/social-content-calendar-cursor.feature` | Three BDD scenarios. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/bdd/glue/SocialContentCalendarCursorBddSteps.kt` | Glue steps wiring real `R2dbcSocialContentRepositories`. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/bdd/SocialContentBddTestConfiguration.kt` | Wire production reader for new scenario. |

No files are deleted.

## Interfaces / Contracts

```kotlin
@JvmInline value class CalendarCursorVersion(val value: String) {
    init { require(value in SUPPORTED_VERSIONS) }
    companion object { const val V1 = "1"; val SUPPORTED_VERSIONS = setOf(V1) }
}

data class SocialContentCalendarCursor(
    val version: CalendarCursorVersion,
    val workspaceId: String,
    val publishedAt: Instant,
    val provider: SocialProvider,
    val socialAccountId: String,
    val externalPostId: String,
) {
    init { require(workspaceId.isNotBlank() && socialAccountId.isNotBlank() && externalPostId.isNotBlank()) }
}

class InvalidSocialContentCursorException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

object SocialContentCalendarCursorCodec {
    private const val DELIMITER: Char = '\u001F'
    fun encode(cursor: SocialContentCalendarCursor): String
    fun decode(token: String): SocialContentCalendarCursor
}

override suspend fun findImportedPosts(query: SocialContentCalendarQuery): SocialContentPage<SocialPost>
// 1. decode + validate workspace if cursor != null
// 2. WHERE workspace_id = :scope.value [AND (published_at, provider, social_account_id, external_post_id) > tuple]
// 3. ORDER BY (published_at, provider, social_account_id, external_post_id) LIMIT :limit + 1
// 4. nextCursor = encode(buildCursor(items[limit-1])) when overflow, else null
```

The SQL binds `:workspaceId` from `query.scope.value` (request context), never from the decoded cursor.

## Testing Strategy

| Layer | What | How |
|---|---|---|
| Unit | Codec roundtrip; `\u001F` delimiter isolation; version rejection; unpadded URL-safe Base64 | Pure JUnit/Kotest. |
| Unit | Value class rejects blank fields; exception carries cause | Pure domain tests. |
| Contract | `InvalidSocialContentCursorException` -> 400 Problem Details | `PublishingProblemDetailsHandlerTest`. |
| Integration | 3+ rows / limit 2 walk, no overlap/omission, final-page null cursor, cross-workspace reject, malformed reject, lifecycle + cursor, actor + cursor | Testcontainers Postgres, `@postgres` per `rules.design`. |
| BDD | Three Gherkin scenarios (cross-workspace reject, cursor cannot override isolation, malformed cursor) | Cucumber, `@social-content-calendar @smoke @fast @postgres` — production reader wired via `BddDatabaseSupport`, `WebTestClient`, headers per `rules.design`. |

All BDD scenarios carry domain tag + `@smoke` + `@fast`; production-reader scenarios also need `@postgres` (Testcontainers).

## Migration / Rollout

Liquibase changeset `019-add-social-content-calendar-keyset-index.yaml` is additive and reversible via `<rollback>`. Reader change is in-place. Codec is additive. Problem Details handler entry is additive. Rollback via git revert (Liquibase index dropped automatically).

Invariant: the SQL always carries `WHERE workspace_id = :workspaceId` from request context; the cursor's `workspaceId` is never used as the SQL filter source. No feature flag, no data backfill.

## Open Questions

None at design time. Resolved: workspace-binding is provenance-only; no HMAC; payload is 6-field with reserved version `1`.
