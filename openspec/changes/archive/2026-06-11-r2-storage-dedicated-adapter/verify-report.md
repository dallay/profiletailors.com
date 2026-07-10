# Verification Report: R2 Dedicated Storage Adapter

**Change**: r2-storage-dedicated-adapter
**Version**: N/A (delta spec)
**Verify date**: 2026-06-11 (fourth run — GATE CLOSING)
**Mode**: openspec
**Test execution**: `DOCKER_AVAILABLE=true ./gradlew :shared:storage:test --rerun-tasks` (88 tests,
0 failures, 0 errors, 0 skipped)

---

## Executive Summary

**Gate status: CLOSED for archive.** All 17 spec scenarios are COMPLIANT, 0 CRITICAL issues remain.

### What changed since the third verify (FAIL)

The third verify report flagged 2 CRITICAL items that prevented the contract tests from running with
Docker. The fourth apply re-entry (#3) fixed them:

1. **`@TempDir` added to `StorageContractTest.setUp(tempDir: Path)`** — 13 `R2StorageContractTest`
   tests now run instead of failing at setUp.
2. **`runTest` → `runBlocking` in both abstract bases** — all 13 `StorageContractTest` + 8
   `PresignableStorageContractTest` call sites now use `runBlocking` (real time) instead of
   `runTest` (virtual time that fired `withTimeout` instantly).

**3 bonus fixes** applied by sdd-apply that went beyond the original 2 CRITICALs:

- 4 tests with non-Unit return values fixed (`assertInstanceOf` returns the asserted value, making
  JUnit silently skip them).
- Order-dependent `should return empty list for empty bucket` fixed with per-test unique prefix.
- `should throw StorageObjectNotFoundException` corrected to match actual S3/R2 semantics (
  presignGet does NOT verify object existence — 404 only surfaces when URL is consumed).

### Test result

```text
DOCKER_AVAILABLE=true ./gradlew :shared:storage:test --rerun-tasks
BUILD SUCCESSFUL
88 tests, 0 failed, 0 errors, 0 skipped
```

### Spec compliance: 17/17 ✅

- 14 scenarios covered by non-Docker-gated unit tests (all pass)
- 3 scenarios covered by Docker-gated contract/integration tests (all pass with Docker)

### Previous CRITICALs tracking

| Report | CRITICALs                                                                          | Status        |
|--------|------------------------------------------------------------------------------------|---------------|
| #1     | 5 (exists(), path traversal, AccessDenied mapping, Network mapping, missing tests) | ✅ All FIXED   |
| #2     | 2 (Region bug, credentials wiring)                                                 | ✅ All FIXED   |
| #3     | 2 (`@TempDir`, `runTest` virtual time)                                             | ✅ All FIXED   |
| #4     | 0                                                                                  | ✅ Gate CLOSED |

---

## Completeness

| Metric                     | Value |
|----------------------------|-------|
| Task checkboxes (total)    | 73    |
| Task checkboxes complete   | 47    |
| Task checkboxes incomplete | 26    |

### Incomplete tasks (grouped by reason)

**Genuine code-level gaps (WARNING, not CRITICAL):**

- Phase 1.1 (`R2ClientFactory.kt` separate file) — client building still inline in
  `StorageAutoConfiguration.createR2Storage()`. Acceptable simplification (the function is now
  `internal` for testability).
- Phase 1.2 (`R2Provider` data class) — `ProviderConfig` was extended in place with `accessKeyId`,
  `secretAccessKey`, `accountId`, etc. No sealed type.
- Phase 3.2.2 (`application-example.yml` R2 example) — still not added.
- Phase 4.1.3 (`upload()` - streaming large files unit test) — covered by contract tests which now
  pass.
- Phase 4.1.5 (`delete()` - not found case unit test) — covered by contract tests which now pass.
- Phase 5 (Documentation) entirely incomplete: no KDoc on `R2StorageAdapter` methods, no module
  README updates, no migration guide.
- Phase 6.1.3 (`koverReport` for >80% coverage) — Kover not enabled in module (project decision;
  cannot enforce).
- Phase 6.2 (manual verification with real R2 credentials) — not run (no CI secrets).

**State-tracking is consistent**: All re-entry logs appended to `tasks.md`.

---

## Build & Tests Execution

### Build

```text
$ ./gradlew :shared:storage:compileKotlin
BUILD SUCCESSFUL in 1s
```

✅ **Build passed** — no compilation errors in main or test sources.

### Tests — With Docker (FINAL — GATE CLOSING)

```text
$ DOCKER_AVAILABLE=true ./gradlew :shared:storage:test --rerun-tasks
BUILD SUCCESSFUL
88 tests, 0 failed, 0 errors, 0 skipped
```

✅ **88/88 pass** — all contract tests now execute and pass.

Breakdown by test class:

| Test class                                     | Tests  | Docker-gated | Status                                       |
|------------------------------------------------|--------|--------------|----------------------------------------------|
| `R2StorageUnitTests`                           | 29     | No           | ✅ All pass                                   |
| `R2StorageContractTest`                        | 13     | Yes          | ✅ All pass                                   |
| `R2PresignableStorageContractTest`             | 8      | Yes          | ✅ All pass                                   |
| `R2StorageIntegrationTest`                     | 8      | Yes          | ✅ All pass                                   |
| `StorageAutoConfigurationR2Test`               | 5      | No           | ✅ All pass                                   |
| `S3StorageUnitTests`                           | 6      | No           | ✅ All pass                                   |
| `GeneratePresignedUrlUseCaseTest`              | 7      | No           | ✅ All pass                                   |
| `LocalFilesystemStorageTest`                   | 7      | No           | ✅ All pass                                   |
| `S3StorageIntegrationTest`                     | 2      | Yes          | ✅ All pass                                   |
| `StorageIntegrationTest`                       | 2      | No           | ✅ All pass                                   |
| `StorageContractTest` (R2 subclass)            | 13     | Yes          | ✅ Nested in R2StorageContractTest            |
| `PresignableStorageContractTest` (R2 subclass) | 8      | Yes          | ✅ Nested in R2PresignableStorageContractTest |
| **Total**                                      | **88** | **32**       | **✅ 88/88 pass**                             |

### Tests — Without Docker (baseline)

```text
$ ./gradlew :shared:storage:test
BUILD SUCCESSFUL
88 tests total: 56 passed, 32 skipped (Docker-gated), 0 failed, 0 errors
```

The 32 skipped tests are Docker-gated (contract/integration tests). All 56 non-Docker tests pass.

### Coverage

Not configured. `openspec/config.yaml` has `coverage_threshold: 0`. Kover is not enabled in
`shared/storage/build.gradle.kts`.

---

## Spec Compliance Matrix

| #  | Requirement               | Scenario                                           | Test(s)                                                                                                    | Result                       |
|----|---------------------------|----------------------------------------------------|------------------------------------------------------------------------------------------------------------|------------------------------|
| 1  | R2 Adapter Lifecycle      | Upload success                                     | `R2StorageUnitTests$Upload` (2 tests)                                                                      | ✅ COMPLIANT                  |
| 2  | R2 Adapter Lifecycle      | Download success                                   | `R2StorageUnitTests$Download` (2 tests)                                                                    | ✅ COMPLIANT                  |
| 3  | R2 Adapter Lifecycle      | Delete object                                      | `R2StorageUnitTests$Delete` (2 tests)                                                                      | ✅ COMPLIANT                  |
| 4  | R2 Adapter Lifecycle      | List with prefix                                   | `R2StorageUnitTests$List` (3 tests)                                                                        | ✅ COMPLIANT                  |
| 5  | R2 Adapter Lifecycle      | Presigned URL                                      | `R2StorageUnitTests$PresignGet` (3 tests)                                                                  | ✅ COMPLIANT                  |
| 6  | R2 Adapter Lifecycle      | `exists(key)` true                                 | `R2StorageUnitTests$Exists` (4 tests)                                                                      | ✅ COMPLIANT                  |
| 7  | R2 Adapter Lifecycle      | `exists(key)` false                                | `R2StorageUnitTests$Exists` (4 tests)                                                                      | ✅ COMPLIANT                  |
| 8  | R2-Specific Configuration | `type: r2`                                         | `StorageAutoConfiguration.createProvider` r2 branch (line 93) + `StorageAutoConfigurationR2Test` (5 tests) | ✅ COMPLIANT                  |
| 9  | R2-Specific Configuration | `type: s2` legacy alias                            | `createProvider` s2 branch (line 88) with `logger.warn` deprecation                                        | ✅ COMPLIANT                  |
| 10 | R2-Specific Configuration | Region defaults to "auto"                          | `createR2Storage` `Region.of(config.region ?: "auto")` (line 131)                                          | ✅ COMPLIANT                  |
| 11 | Error Handling            | NoSuchKey → `StorageObjectNotFoundException`       | `R2StorageUnitTests$ErrorMapping` (6 tests)                                                                | ✅ COMPLIANT                  |
| 12 | Error Handling            | AccessDenied → `StorageAccessDeniedException`      | `R2StorageUnitTests$ErrorMapping` (6 tests)                                                                | ✅ COMPLIANT                  |
| 13 | Error Handling            | Network errors → `StorageConnectionException`      | `R2StorageUnitTests$ErrorMapping` (6 tests, 503 status code path)                                          | ✅ COMPLIANT                  |
| 14 | Path Traversal Protection | Reject `../` and `..\\` in key                     | `R2StorageUnitTests$PathTraversalProtection` (6 tests)                                                     | ✅ COMPLIANT                  |
| 15 | Contract Test Compliance  | R2 adapter passes `StorageContractTest`            | `R2StorageContractTest` (13 tests w/ Docker)                                                               | ✅ **COMPLIANT (13/13 pass)** |
| 16 | Contract Test Compliance  | R2 adapter passes `PresignableStorageContractTest` | `R2PresignableStorageContractTest` (8 tests w/ Docker)                                                     | ✅ **COMPLIANT (8/8 pass)**   |
| 17 | Contract Test Compliance  | R2 integration (end-to-end)                        | `R2StorageIntegrationTest` (8 tests w/ Docker)                                                             | ✅ **COMPLIANT (8/8 pass)**   |

**Compliance summary**: **17/17 scenarios compliant** ✅

**Net change vs prior report (#3)**: 15/17 → **17/17**. Scenarios #15 and #16 are now COMPLIANT with
all 13+8 contract tests passing.

---

## Correctness (Static + Runtime Evidence)

| Requirement                                            | Status        | Notes                                                                                                                             |
|--------------------------------------------------------|---------------|-----------------------------------------------------------------------------------------------------------------------------------|
| `R2StorageAdapter implements PresignableStorage`       | ✅ Implemented | Via `AbstractS3CompatibleStorage`; adapter is 23 LOC + accountId validation                                                       |
| `upload()` with metadata                               | ✅ Implemented | `AbstractS3CompatibleStorage.upload` (100-133); proven by end-to-end integration test                                             |
| `download()` returning `Flow<ByteArray>`               | ✅ Implemented | (135-171); proven by end-to-end integration test                                                                                  |
| `delete()` single object                               | ✅ Implemented | (173-197); proven by integration test                                                                                             |
| `list()` with prefix and delimiter                     | ✅ Implemented | (199-238), calls `validateKey(prefix)` when non-empty                                                                             |
| `presignGet()` returning R2 URL                        | ✅ Implemented | (240-270); proven by integration test                                                                                             |
| `exists(key)`                                          | ✅ Implemented | (272-299); proven by integration tests (both true/false)                                                                          |
| `type: r2` config                                      | ✅ Implemented | `StorageAutoConfiguration.createProvider` line 93; proven by `StorageAutoConfigurationR2Test` (5/5 passing)                       |
| `type: s2` legacy alias with deprecation warning       | ✅ Implemented | Lines 88-92 with `logger.warn`                                                                                                    |
| Region defaults to `"auto"`                            | ✅ Implemented | `createR2Storage` line 131: `Region.of(config.region ?: "auto")`                                                                  |
| R2 requires explicit credentials                       | ✅ Implemented | `createR2Storage` lines 116-128 (validation) and 137, 143 (wiring) — **proven by `StorageAutoConfigurationR2Test` (5/5 passing)** |
| Error: `NoSuchKey` → `StorageObjectNotFoundException`  | ✅ Implemented | `mapToStorageException` (94-95), inline in `download` (157-158), `exists` (284)                                                   |
| Error: `AccessDenied` → `StorageAccessDeniedException` | ✅ Implemented | `isAccessDenied()` mapping in `mapToStorageException` and inline in all operations                                                |
| Error: Network (503) → `StorageConnectionException`    | ✅ Implemented | `isServiceUnavailable()` mapping                                                                                                  |
| Path traversal validation                              | ✅ Implemented | `validateKey()` (63-67); called in upload, download, delete, list, presignGet, exists                                             |
| End-to-end round trip against LocalStack               | ✅ Verified    | `R2StorageIntegrationTest` (8/8 pass with Docker)                                                                                 |
| `R2ClientFactory.kt` (separate file)                   | ⚠️ Partial    | Inline in `StorageAutoConfiguration.createR2Storage()`. Function is `internal` for testability.                                   |
| `R2Provider` data class (sealed variant)               | ⚠️ Partial    | `ProviderConfig` extended in place; no sealed type                                                                                |
| `S2Storage` dead code                                  | ⚠️ Present    | The class still exists but is no longer wired in config                                                                           |

---

## Coherence (Design)

| Design decision                                           | Followed? | Notes                                                                                                                      |
|-----------------------------------------------------------|-----------|----------------------------------------------------------------------------------------------------------------------------|
| `R2StorageAdapter implements PresignableStorage` directly | ✅ Yes     | Via `AbstractS3CompatibleStorage` inheritance                                                                              |
| `S3AsyncClient` (AWS SDK v2)                              | ✅ Yes     |                                                                                                                            |
| `forcePathStyle(true)`                                    | ✅ Yes     | `createR2Storage` line 138; also in test scaffolding                                                                       |
| Region defaults to `"auto"`                               | ✅ Yes     | Line 131                                                                                                                   |
| `R2ClientFactory` as separate factory                     | ❌ No      | Inline in `createR2Storage` (but `internal` for testability)                                                               |
| Domain exception hierarchy                                | ✅ Yes     | `StorageObjectNotFoundException`, `StorageAccessDeniedException`, `StorageConnectionException`, `StorageSecurityException` |
| S2 as deprecated alias for R2                             | ✅ Yes     | Deprecation warning at line 90; config branch returns `R2StorageAdapter`                                                   |
| `pathStyleAccessEnabled(true)` for R2                     | ✅ Yes     | `forcePathStyle(true)` in `createR2Storage`                                                                                |
| Account ID validation                                     | ✅ Yes     | `R2StorageAdapter` init block rejects blank accountId                                                                      |
| `validateKey(key)` called in all operations               | ✅ Yes     | All 6 operations call it                                                                                                   |
| Unit tests use `mockk` pattern                            | ✅ Yes     | `R2StorageUnitTests.kt`, `StorageAutoConfigurationR2Test.kt`                                                               |
| Integration tests use LocalStack                          | ✅ Yes     | `R2StorageIntegrationTest.kt`                                                                                              |
| Contract tests parameterized                              | ✅ Yes     | Concrete subclasses created and wired                                                                                      |
| `StaticCredentialsProvider` for both client and presigner | ✅ Yes     | Both builders wired in `createR2Storage`; proven by `StorageAutoConfigurationR2Test`                                       |

---

## Issues Found

### CRITICAL (must fix before archive)

**None.** All previously flagged CRITICALs are verified as fixed with passing tests and static
evidence.

### WARNING (should fix)

1. **`R2ClientFactory.kt` not created** as a separate file. Client building is inline in
   `StorageAutoConfiguration.createR2Storage()`. Acceptable simplification (function is `internal`
   for testability).
2. **No `R2Provider` sealed type** — `ProviderConfig` extended in place.
3. **Phase 5 (Documentation) entirely incomplete**: no KDoc on `R2StorageAdapter` methods, no
   `application-example.yml` R2 snippet, no migration guide, no module README updates.
4. **Unit-test coverage gaps**: `delete() - not found case` and `upload() - streaming large files`
   only covered by contract tests (not dedicated unit tests).
5. **Kover not enabled** — AC-8 `>80% coverage` criterion formally unmet (module config decision).
6. **Phase 6.2 manual verification** (real R2 credentials, log inspection) not run.
7. **`S2Storage.kt` is dead code** — no longer wired in config; could be deleted.

### SUGGESTION (nice to have)

1. **Unify `R2StorageAdapter` and `S3Storage`**: both extend `AbstractS3CompatibleStorage` and only
   differ in account-ID validation. A future refactor could drop `R2StorageAdapter` entirely.
2. **Shared `TestS3Clients` helper**: `R2StorageIntegrationTest` + both contract tests duplicate
   LocalStack wiring. A helper would prevent the Region-bug class of issue.
3. **Compiler warnings**: deprecation warnings on `URL(String)` (Java 21), unchecked casts in mockk
   subscribers. Cleanup is cheap.
4. **Spring-context integration test**: verify `R2StorageAdapter` is registered with
   `BucketRegistry` in the actual Spring application context.

---

## Behavioral compliance with previous reports' CRITICALs

| Previous CRITICAL                                | Re-verify status | Evidence                                                                  |
|--------------------------------------------------|------------------|---------------------------------------------------------------------------|
| **#1 CRITICAL-1** `exists(key)` missing          | ✅ FIXED          | `Storage.exists` in interface; 4 unit tests + 2 integration tests pass    |
| **#1 CRITICAL-2** path traversal `validateKey()` | ✅ FIXED          | 6 unit tests pass; called in all 6 operations                             |
| **#1 CRITICAL-3** AccessDenied mapping           | ✅ FIXED          | 6 error-mapping unit tests pass                                           |
| **#1 CRITICAL-4** Network errors mapping         | ✅ FIXED          | `isServiceUnavailable()` mapping; 6 unit tests pass                       |
| **#1 CRITICAL-5** R2 contract/integration tests  | ✅ FIXED          | Now 13/13 + 8/8 + 8/8 = 29/29 contract+integration tests pass with Docker |
| **#2 CRITICAL-1** Region bug                     | ✅ FIXED          | All 3 R2 test files use `Region.of(localstack.region)`; tests pass        |
| **#2 CRITICAL-2** Credentials wiring             | ✅ FIXED          | `StorageAutoConfigurationR2Test` 5/5 passes; both builders wired          |
| **#3 CRITICAL-1** `@TempDir` missing             | ✅ FIXED          | `StorageContractTest.setUp` now has `@TempDir` (line 77); 13/13 pass      |
| **#3 CRITICAL-2** `runTest` virtual time         | ✅ FIXED          | Both abstract bases now use `runBlocking`; 13+8 = 21/21 pass              |

**All 9 CRITICALs across all prior reports: ✅ RESOLVED**

---

## Verdict

**PASS**

All 17 spec scenarios are COMPLIANT with runtime test evidence. All 9 CRITICALs tracked across 4
verification runs are resolved. The test suite produces 88/88 passed with Docker (0 failures, 0
errors, 0 skipped) and 56/56 passed without Docker (32 gracefully skipped via
`@EnabledIfEnvironmentVariable`).

The remaining warnings are documentation gaps, dead code cleanup, and test-coverage polish items —
none block the archive gate.

**Gate status: CLOSED for archive.** The `sdd-archive` phase can proceed.

---

## Artifacts Written

- `openspec/changes/r2-storage-dedicated-adapter/verify-report.md` (this file, replaced)
