# publishing.infrastructure.persistence — test coverage design

## Goal

Raise instruction coverage from **60%** → **≥ 80%** on
`com.profiletailors.smp.publishing.infrastructure.persistence`.

## Current coverage per class

| Class | Instruction Cov. | Public methods |
|---|---|---|
| `R2dbcDeliveryAttemptRepository` | **6%** | `record` |
| `R2dbcPublicationAssetRepository` | **33%** | `findByWorkspaceAndIds`, `create`, `updateStatus`, `updateProviderAssetRef` |
| `R2dbcPublicationRepository` | **71%** | `createDraft`, `updateEditableDraft`, `findByWorkspaceAndId`, `markPublished`, `markFailed`, `markCancelled` |
| `R2dbcPublicationJobRepository` | **70%** | `enqueue`, `replaceForPublication`, `claimNextDue`, `rescheduleRetry`, `complete`, `fail`, `cancel` |
| `R2dbcSocialConnectionRepository` | **76%** | `upsert`, `findByWorkspaceAndId` |
| `R2dbcSocialAccountRepository` | **69%** | `upsert`, `findByWorkspaceAndId` |
| `R2dbcPublishingRepositoriesKt` | **66%** | — (extension functions, covered via repo calls) |
| `R2dbcPublishingConnectionRepositoriesKt` | **82%** | — (extension functions, covered via repo calls) |

**Total missed: 920 / 2,335 = 60%.** To reach 80% we need ~468 more covered instructions.

## Strategy

Use **in-memory DatabaseClient stub** — no real DB required.
Mock `DatabaseClient.sql()` returning `ExecutableQuery` / `ExecutableUpdate` that
resolve to predefined rows or row counts.

This mirrors the integration test style already in
`R2dbcPublishingRepositoriesTest.kt` (which uses `DatabaseUnitTestBase` for real DB).
For unit tests here we want isolation + speed.

### Repository: R2dbcDeliveryAttemptRepository

**Methods:**
- `record(attempt: DeliveryAttempt): DeliveryAttempt` — INSERT then return attempt

**Tests (3 scenarios):**
1. `record stores attempt and returns it` — verify rowsUpdated = 1, returned attempt is same reference
2. `record serializes all fields correctly` — verify SQL binds correct values for every field
3. `record with null optional fields succeeds` — providerMessage, providerErrorCode, externalPublicationId all null

### Repository: R2dbcPublicationAssetRepository

**Methods:**
- `findByWorkspaceAndIds(workspaceId, assetIds): List<PublicationAsset>` — SELECT with filter
- `create(asset: PublicationAsset): PublicationAsset` — INSERT then return asset
- `updateStatus(assetId, status)` — UPDATE status
- `updateProviderAssetRef(assetId, providerAssetRef)` — UPDATE status + providerAssetRef JSON

**Tests (6 scenarios):**
1. `findByWorkspaceAndIds returns empty list for empty assetIds` — early return
2. `findByWorkspaceAndIds maps all columns correctly` — verify row mapping including providerAssetRef JSON parsing
3. `findByWorkspaceAndIds filters by assetIds` — test the in-memory filter
4. `create inserts asset and returns it` — rowsUpdated = 1, returned same reference
5. `updateStatus updates status field` — rowsUpdated = 1
6. `updateProviderAssetRef serializes providerAssetRef as JSON` — verify objectMapper.writeValueAsString called

### Repository: R2dbcPublicationRepository

**Methods:** `createDraft`, `updateEditableDraft`, `findByWorkspaceAndId`, `markPublished`, `markFailed`, `markCancelled`

Already partially covered by `R2dbcPublishingRepositoriesTest.kt` (upsert + findByWorkspaceAndId).
Focus on **state transition methods** that are untested:
- `markPublished` — UPDATE + verify correct SQL bindings
- `markFailed` — UPDATE with error code + message
- `markCancelled` — UPDATE with cancelledAt
- `replaceAssetLinks` private method via `createDraft` with multiple assets

**Tests (5 scenarios):**
1. `markPublished sets status PUBLISHED and clears error fields` — rowsUpdated = 1
2. `markFailed sets status FAILED and stores error details` — rowsUpdated = 1, correct bindings
3. `markCancelled sets status CANCELLED and failed_at = cancelledAt` — rowsUpdated = 1
4. `createDraft replaces asset links (delete-then-insert)` — verify two SQL calls for replaceAssetLinks
5. `findByWorkspaceAndId returns null when not found` — awaitSingleOrNull returns null

### Repository: R2dbcPublicationJobRepository

**Methods:** `enqueue`, `replaceForPublication`, `claimNextDue`, `rescheduleRetry`, `complete`, `fail`, `cancel`

**Tests (7 scenarios):**
1. `enqueue calls insertJob` — rowsUpdated = 1
2. `replaceForPublication deletes then inserts` — two SQL calls, rowsUpdated = 1 each
3. `claimNextDue returns null when no jobs due` — empty row, awaitSingleOrNull returns null
4. `claimNextDue claims and returns PublicationJobClaim` — verified via rowsUpdated + returned object
5. `rescheduleRetry updates status RETRY_WAITING and clears claim` — rowsUpdated = 1
6. `complete sets COMPLETED` — rowsUpdated = 1
7. `fail sets FAILED` — rowsUpdated = 1
8. `cancel sets CANANCELLED by publication_id` — rowsUpdated = 1

## Test infrastructure

Create `MockDatabaseClient` that intercepts `sql()` calls and returns predefined results.
Use a simple `Mock executable` pattern — no external mocking library needed.

```kotlin
class MockDatabaseClient {
    private val queryResults = mutableMapOf<String, Any>()
    private val updateResults = mutableMapOf<String, Int>()

    fun whenSql(pattern: String, rows: List<Map<String, Any?>>) = ...
    fun whenSql(pattern: String, rowsUpdated: Int) = ...
    fun whenSql(pattern: String, singleRow: Map<String, Any?>) = ...
}
```

Each repository test gets its own `MockDatabaseClient` instance with preloaded results.
ObjectMapper in `R2dbcPublicationAssetRepository` is a real `ObjectMapper` (no mock needed for basic serialization).

## File layout

```
src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/
  R2dbcPublishingRepositoriesUnitTest.kt   ← all 4 repository test classes
```

## Coverage target

- `R2dbcDeliveryAttemptRepository`: 6% → **≥ 90%**
- `R2dbcPublicationAssetRepository`: 33% → **≥ 80%**
- `R2dbcPublicationRepository`: 71% → **≥ 85%**
- `R2dbcPublicationJobRepository`: 70% → **≥ 85%**
- Overall package: 60% → **≥ 80%**