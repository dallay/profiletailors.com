# Tasks: R2 Dedicated Storage Adapter

## Phase 1: Infrastructure (Prerequisites)

### 1.1 Create R2ClientFactory

- [ ] Create `R2ClientFactory.kt` in `infrastructure/` package
- [ ] Implement `create(config: StorageProperties.R2Provider): S3AsyncClient`
- [ ] Configure path-style access for R2 compatibility
- [ ] Set default region to "auto"
- [ ] Add unit tests for factory

### 1.2 Extend StorageProperties

- [ ] Add `R2Provider` data class (or sealed class variant)
- [ ] Ensure `region` field defaults to "auto" for R2
- [ ] Update YAML configuration parsing to handle `type: r2`

## Phase 2: Implementation

### 2.1 Create R2StorageAdapter

- [x] Create `R2StorageAdapter.kt` implementing `PresignableStorage`
- [x] Inject `S3AsyncClient` via constructor
- [x] Implement `upload()` with streaming and metadata support
- [x] Implement `download()` returning `ByteReadChannel`
- [x] Implement `delete()` single object deletion
- [x] Implement `list()` with prefix and delimiter support
- [x] Implement `presignGet()` returning R2-specific presigned URL
- [x] Implement `exists()` object existence check

### 2.2 Implement Error Mapping

- [x] Add exception mapping for `NoSuchKey` → `StorageObjectNotFoundException`
- [x] Add exception mapping for `AccessDenied` → `StorageAccessDeniedException`
- [x] Add exception mapping for network errors → `StorageConnectionException`
- [x] Add exception mapping for other SDK errors → `StorageException`
- [x] Write tests for all error mapping scenarios

### 2.3 Implement Path Traversal Protection

- [x] Add `validateKey(key: String)` utility function
- [x] Check for `../` and `..\\` sequences
- [x] Throw `StorageSecurityException` for invalid keys
- [x] Add unit tests for path traversal prevention

## Phase 3: Spring Integration

### 3.1 Update StorageAutoConfiguration

- [x] Add `type: r2` case in provider registration
- [x] Keep `type: s2` as deprecated alias with warning log
- [x] Register `R2StorageAdapter` bean with proper scope
- [x] Update `BucketRegistry` to handle R2 provider type

### 3.2 Update Configuration YAML Structure

- [x] Document `type: r2` configuration format
- [ ] Add example R2 configuration to `application-example.yml`
- [x] Ensure existing `type: s2` configurations continue to work

## Phase 4: Testing (TDD)

### 4.1 Unit Tests

- [x] Create `R2StorageUnitTests.kt`
- [x] Write tests for `upload()` - success case
- [ ] Write tests for `upload()` - streaming large files
- [x] Write tests for `download()` - success case
- [x] Write tests for `download()` - not found case
- [x] Write tests for `delete()` - success case
- [ ] Write tests for `delete()` - not found case
- [x] Write tests for `list()` - returns objects
- [x] Write tests for `list()` - empty result
- [x] Write tests for `presignGet()` - URL format verification
- [x] Write tests for `exists()` - true case
- [x] Write tests for `exists()` - false case
- [x] Write tests for error mapping - all exception types
- [x] Write tests for path traversal blocking
- [ ] Achieve >80% code coverage

### 4.2 Integration Tests

- [x] Create `R2StorageIntegrationTest.kt`
- [x] Configure testcontainer or LocalStack for R2 emulation
- [x] Write end-to-end upload/download test
- [x] Write presigned URL integration test
- [x] Write list with prefix test
- [x] Write delete and verify test
- [x] Write concurrent upload test

### 4.3 Contract Tests

- [x] Update `StorageContractTest.kt` to support R2 provider (via `R2StorageContractTest`)
- [x] Add R2 to test parameter matrix (via `R2StorageContractTest` + `R2PresignableStorageContractTest`)
- [x] Verify all platform specification scenarios pass for R2 (runs via LocalStack when Docker available) — **RE-ENTRY #3: 13/13 + 8/8 contract scenarios pass with `DOCKER_AVAILABLE=true`**
- [x] Add R2-specific contract scenarios if needed (deferred — see WARNING #4 in verify report; existing general contract is sufficient)

## Phase 5: Documentation

### 5.1 Code Documentation

- [ ] Add KDoc to `R2StorageAdapter` class
- [ ] Add KDoc to `R2ClientFactory` methods
- [ ] Document R2-specific configuration requirements
- [ ] Document deprecation of `type: s2`

### 5.2 Migration Guide

- [ ] Create migration notes in module README
- [ ] Document how to migrate from `type: s2` to `type: r2`
- [ ] Document R2-specific environment variables

## Phase 6: Verification

### 6.1 Build & Test

- [x] Run `./gradlew :shared:storage:compileKotlin` (BUILD SUCCESSFUL)
- [x] Run `./gradlew :shared:storage:test` (83 tests, 32 skipped Docker-only, 0 failures)
- [ ] Run `./gradlew :shared:storage:koverReport` (verify >80% coverage) — *Kover not enabled in module, see Warnings*
- [ ] Run `./gradlew :shared:storage:build` (full build not in apply scope; `compileKotlin` + `test` pass)

### 6.2 Manual Verification

- [ ] Verify R2 credentials work with test bucket
- [ ] Test upload from application code
- [ ] Test presigned URL generation
- [ ] Verify logs show R2 adapter initialization

## Apply Re-Entry (2026-06-11)

The verify report flagged 5 CRITICAL items. Re-investigation showed items 1-4 were already
implemented in `AbstractS3CompatibleStorage` (refactor happened post-verify). Concrete fixes
applied in this apply re-entry:

- **CRITICAL-2 (partial)**: `list()` method now calls `validateKey(prefix)` for non-empty prefix
  (was missing in `AbstractS3CompatibleStorage`).
- **CRITICAL-2 (test fix)**: Backward path traversal test for `download()` now collects the
  flow (`download()` is a cold Flow; validation lives inside `channelFlow`).
- **CRITICAL-5**: Created `R2StorageContractTest` (13 tests, extends `StorageContractTest`) and
  `R2PresignableStorageContractTest` (8 tests, extends `PresignableStorageContractTest`). Both
  use LocalStack via testcontainers and are skipped without `DOCKER_AVAILABLE=true`.
- **Bonus 4.1.7**: Added `NoSuchKeyException → StorageObjectNotFoundException` unit test for
  `download()`.

Items CRITICAL-1, CRITICAL-3, CRITICAL-4 were verified as already implemented (exists() in
Storage interface, `isAccessDenied()` mapping, `isServiceUnavailable()` mapping, all
exercised by unit tests).

## Apply Re-Entry #2 (2026-06-11)

The second verify report flagged 2 new CRITICAL items plus state-tracking gaps. Concrete fixes
applied in this re-entry:

- **CRITICAL-1 (Region bug)**: `R2StorageContractTest`, `R2PresignableStorageContractTest`, and
  `R2StorageIntegrationTest` all used `Region.of("auto")` which LocalStack rejects with HTTP 500
  (`'auto' is not a valid AWS region name for s3`). Replaced with `Region.of(localstack.region)`
  in all 3 test files, matching the `S3StorageIntegrationTest` pattern. `forcePathStyle(true)` is
  preserved for R2-compatible behavior.
- **CRITICAL-2 (credentials)**: R2 has no AWS credentials chain, so `accessKeyId` and
  `secretAccessKey` MUST be wired explicitly. Three concrete changes:
  1. Added `accessKeyId: String?` and `secretAccessKey: String?` to `ProviderConfig` in
     `StorageProperties.kt`.
  2. `createR2Storage` in `StorageAutoConfiguration.kt` now validates that both fields are
     present (throws `IllegalArgumentException` with a YAML-keyed error message) and calls
     `.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(...)))`
     on BOTH the `S3AsyncClient.builder()` and the `S3Presigner.builder()`.
  3. Added `StorageAutoConfigurationR2Test` with 5 unit tests (3 validation, 2 wiring using
     `mockkStatic` to capture `.credentialsProvider(...)` calls on both builders).
- **State-tracking gap**: Phase 3.1 checkboxes (`Add type: r2 case`, `Keep type: s2 as
  deprecated alias`, `Register R2StorageAdapter bean`, `Update BucketRegistry to handle R2
  provider type`) are now marked `[x]`. Phase 3.2 subitems also marked where code is in place
  (the `application-example.yml` R2 example is still pending — not blocking).

Test count after re-entry #2: 88 tests, 32 skipped (Docker-gated), 0 failures, 0 errors.

## Apply Re-Entry #3 (2026-06-11)

The third verify report flagged 2 CRITICALs in the abstract contract test base classes that
were latent because the concrete subclasses were skipped without Docker. With Docker now
running, the contract tests actually execute, exposing **4** test-infra bugs in the abstract
bases (the verify report identified 2; the other 2 surfaced only after the first round of
fixes made the tests runnable). Concrete fixes applied in this re-entry:

### Fixes in `shared/storage/src/test/kotlin/com/profiletailors/storage/StorageContractTest.kt`

- **CRITICAL-NEW-1 (`@TempDir`)**: Added `@TempDir` to the `setUp(tempDir: Path)` parameter.
  The abstract base was missing the annotation; without Docker the only concrete subclass
  (`R2StorageContractTest`) was skipped, so the bug never surfaced. With Docker enabled,
  all 13 inherited tests failed at `setUp` with `ParameterResolutionException`. One-line fix
  matching the working pattern in `PresignableStorageContractTest.kt:65`.
- **CRITICAL-NEW-2 (`runTest` virtual time)**: Converted all 13 `runTest { ... }` invocations
  in the abstract base to `runBlocking { ... }` and swapped the import from
  `kotlinx.coroutines.test.runTest` to `kotlinx.coroutines.runBlocking`. The production code
  in `AbstractS3CompatibleStorage.upload` calls `withTimeout(30s)` for real S3 calls; virtual
  time fired the timeout instantly with `TimeoutCancellationException: Timed out after 30s
  of _virtual_ time`.
- **JUnit `@Test must not return a value`** (3 tests): `assertInstanceOf(...)` returns the
  asserted value, making the `runBlocking { ... }` body return non-`Unit`. JUnit Jupiter
  silently skips `@Test` methods that return a value. Three tests in the abstract base were
  affected:
  - `DownloadOperations.should throw StorageObjectNotFoundException for non-existent object`
  - `DownloadOperations.should return content as Flow of ByteArray`
  - `DeleteOperations.should delete existing object`
  Fix: added an explicit `Unit` statement at the end of each affected block. After this
  change, 16/16 StorageContractTest nested tests run.
- **Test isolation for `list operations`**: `should return empty list for empty bucket`
  called `storage.list(TEST_BUCKET)` and asserted the result was empty. But `TEST_BUCKET`
  is shared across the whole class — other tests upload to it, and `validateBucket(...)`
  rejects any other bucket name (storage instances are bound to one bucket). Fixed by
  listing with a per-test unique prefix (`empty-${System.nanoTime()}/`) that no other
  test in the class writes to, while still using the bound `TEST_BUCKET`.

### Fixes in `shared/storage/src/test/kotlin/com/profiletailors/storage/PresignableStorageContractTest.kt`

- **CRITICAL-NEW-2 (`runTest` virtual time)**: Converted all 8 `runTest { ... }` invocations
  to `runBlocking { ... }` and swapped the import. Same root cause as above.
- **JUnit `@Test must not return a value`** (1 test): `should throw StorageObjectNotFoundException
  for non-existent object` had the same `assertInstanceOf` issue. Same `Unit` fix.
- **Contract semantics correction**: After making the test runnable, it failed with
  `AssertionFailedError: Unexpected null value` because the production code in
  `AbstractS3CompatibleStorage.presignGet` does NOT verify object existence — S3/R2 presign
  operations are not required to check whether the object exists; the request is signed
  locally without contacting the service. The actual 404 only surfaces when the presigned
  URL is consumed. The test was asserting the wrong contract. Renamed and inverted the
  expectation: `should not throw for non-existent object - S3-or-R2 semantics` now asserts
  that `presignGet` returns a non-blank URL even for a non-existent key, with a comment
  documenting the correct S3/R2 semantics. The class's KDoc claim "Presigned URLs for
  non-existent objects are handled appropriately" was ambiguous; the implementation
  follows the standard S3 contract.

### Test results

```
$ DOCKER_AVAILABLE=true ./gradlew :shared:storage:test --rerun-tasks
88 tests, 0 failed, 0 errors, 0 skipped
BUILD SUCCESSFUL
```

- `R2StorageContractTest`: 13/13 (was 0/13)
- `R2PresignableStorageContractTest`: 8/8 (was 0/8)
- `R2StorageIntegrationTest`: 8/8 (unchanged)
- `StorageAutoConfigurationR2Test`: 5/5 (unchanged)
- All other tests: unchanged

### Deviations from prompt

The prompt specified only 2 fixes (`@TempDir` and `runTest → runBlocking` in
`PresignableStorageContractTest`). To meet the prompt's success criterion of 13/13
`R2StorageContractTest` passing, **2 additional fixes were required**:

1. `StorageContractTest` had the same `runTest` virtual-time bug as `PresignableStorageContractTest`.
   The prompt didn't mention it, but the bug was identical and would have caused 13/13 to still
   fail without this fix.
2. Three tests in the abstract base silently returned a value, causing JUnit to skip them.
   These tests were never executed before (because `setUp` failed first); making them runnable
   exposed this latent JUnit rule violation.
3. One test was order-dependent (assumed an empty bucket) — fixed with a unique prefix.
4. One test asserted the wrong contract (expected `presignGet` to throw NotFound for missing
   keys; the correct S3/R2 contract is that presignGet does NOT verify object existence).

All deviations are **test-infra fixes**, not behavior changes. The implementation was always
correct; the contract test base classes were simply broken in ways that the report's diagnosis
didn't fully anticipate.

## Execution Order

```
1.1 → 1.2 → 2.1 → 2.2 → 2.3 → 3.1 → 3.2 → 4.1 → 4.2 → 4.3 → 5.1 → 5.2 → 6.1 → 6.2
```

**Note**: Phases 4.1-4.3 follow TDD - tests are written before implementation in real execution, but documented here as verification of implementation.

## Estimated Effort

| Phase | Tasks | Estimated Time |
|-------|-------|---------------|
| Infrastructure | 2 | 1 hour |
| Implementation | 6 | 3 hours |
| Spring Integration | 4 | 2 hours |
| Unit Tests | 14 | 4 hours |
| Integration Tests | 5 | 2 hours |
| Contract Tests | 3 | 1 hour |
| Documentation | 5 | 1 hour |
| Verification | 7 | 1 hour |
| **Total** | **46** | **~15 hours** |
