# Verification Report

**Change**: backend-api-key-support
**Status**: FAIL
**Verified on**: 2026-05-17

---

## Summary

The previous scope-local warning is cleared: inactive API-key denial is now explicitly proven in lookup tests and in H2/PostgreSQL proving-slice integration tests for `/api/authorization/workspace-access/current`.

However, re-running required execution evidence for `server/smp` does **not** currently pass. `./gradlew test && ./gradlew build` fails during Kotlin compilation because new API-key credential replacement work in `R2dbcApiKeyCredentialReplacementGateway.kt` and the updated `R2dbcApiKeyCredentialStateLookup.kt` does not compile. Under the verify contract, that makes the overall verdict a failure even though the original inactive-state warning has been resolved.

---

## Completeness

The original `backend-api-key-support` task list remains complete in its archived artifact.

---

## Build & Tests Execution

**Working directory**: `server/smp`

**Command executed**:

```bash
./gradlew test && ./gradlew build
```

**Result**: ❌ Failed during `:compileKotlin`

Representative failures:
- `R2dbcApiKeyCredentialReplacementGateway.kt:34` — `use` receiver/type inference failure on R2DBC connection handling
- `R2dbcApiKeyCredentialReplacementGateway.kt:81` — `awaitSingleOrNull()` receiver/type mismatch
- `R2dbcApiKeyCredentialReplacementGateway.kt:168,177,203` — `const val` initializer not constant
- `R2dbcApiKeyCredentialStateLookup.kt:51` — `replacedAt` constructor parameter mismatch
- `R2dbcApiKeyCredentialStateLookup.kt:68` — unresolved `replacedAt`

Because build/test execution does not complete successfully, verify cannot return PASS.

---

## Scope-Specific Findings

### Cleared from prior verify
- ✅ Inactive API-key state is now explicitly covered in `R2dbcApiKeyCredentialStateLookupTest`.
- ✅ Inactive API-key denial is now proven end to end in:
  - `WorkspaceAccessSummaryEndpointIntegrationTest > rejects inactive api key credential before authorization executes`
  - `WorkspaceAccessSummaryEndpointPostgresIntegrationTest > rejects inactive api key credential before authorization executes on postgres`
- ✅ Runtime audit-ready proof for inactive denial is now present in both integration suites.

### Remaining blocker
- ❌ The current `server/smp` state does not compile because broader API-key credential replacement changes were introduced and are incomplete.

---

## Verdict Basis

- **Prior warning status**: cleared
- **Current execution gate**: failed
- **Final verify verdict**: FAIL
