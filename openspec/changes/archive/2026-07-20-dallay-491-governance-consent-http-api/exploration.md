# Exploration: DALLAY-491 Governance Consent HTTP API

## Current State

The governance consent system has been partially implemented with HTTP endpoints, query handlers,
and authorization checks. The implementation follows the approved HTTP design from conversation:

### What Exists (Uncommitted Draft)

**HTTP Controller** (`ConsentController.kt`):

- `POST /api/governance/consent` — Record consent (idempotent, returns 201/200)
- `POST /api/governance/consent/withdraw` — Withdraw consent (returns 200/404)
- `GET /api/governance/consent` — List workspace consent records (with filters)
- `GET /api/governance/consent/history` — Get full consent history for a subject

**Query Handlers**:

- `GetWorkspaceConsentRecordsHandler` — Lists active records for workspace with authorization
- `GetConsentHistoryHandler` — Retrieves historical records for a subject with authorization

**Authorization**:

- Permission: `workspace:consent:read` enforced via `WorkspaceAuthorizationDecider`
- Both query handlers check authorization and throw `AuthorizationDeniedException` on denial
- Workspace ID extracted from `ResourceContext` (security context)
- No cross-workspace data leakage — repository methods are workspace-scoped

**Repository Support**:

- `findActiveByWorkspace(workspaceId, subjectKind?, purpose?)` — EXISTS, tested
- `findHistoricalByIdentity(workspaceId, subjectReference, purpose)` — EXISTS, tested
- Both return `Flow<ConsentRecord>` for reactive streaming

**Tests**:

- `ConsentControllerWebTest.kt` — 9 tests covering:
    - POST record consent (201 new, 200 idempotent)
    - POST withdraw consent (200 success, 404 not found)
    - GET list workspace records
    - GET history by subject
    - Validation errors (400)

**Problem Details**:

- `ConsentRecordNotFoundException` mapped to 404 with proper title/detail

### What's Missing (Before Production)

1. **Authentication Integration**:
    - Controller methods accept `principalId: String` and `workspaceId: String` parameters but these
      are NOT bound from security context
    - Missing `@AuthenticationPrincipal` or similar Spring Security binding
    - Missing integration with existing auth infrastructure (JWT, session, etc.)
    - No tests verify that auth context is correctly extracted

2. **Authorization Permission Registration**:
    - Permission `workspace:consent:read` is checked but may not be registered in role definitions
    - Need to verify which roles (OWNER, ADMIN, MEMBER) should have this permission
    - No authorization tests exist for the query handlers

3. **HTTP Status Semantics Clarification**:
    - `POST /api/governance/consent` currently returns 200 for idempotent case when status is ACTIVE
    - Line 103:
      `val status = if (record.status == ConsentStatus.WITHDRAWN) HttpStatus.OK else HttpStatus.CREATED`
    - This logic seems inverted — should return 200 when already active, 201 when newly created
    - Need to clarify intended behavior and add test coverage for edge cases

4. **Flow Materialization**:
    - `GetWorkspaceConsentRecordsHandler` and `GetConsentHistoryHandler` call `records.map { ... }`
      on a `Flow<ConsentRecord>`
    - This creates a new `Flow` but does NOT materialize it into a list
    - The returned `GetWorkspaceConsentRecordsResult(records = ...)` expects a
      `List<ConsentRecordResult>` but receives a `Flow`
    - **CRITICAL BUG**: Code will not compile or will fail at runtime — need to call `.toList()` on
      the Flow

5. **Integration Tests**:
    - No end-to-end integration tests with real auth context
    - No Postgres-backed repository tests for the new query methods
    - No BDD scenarios covering the HTTP API flows

6. **OpenAPI Documentation**:
    - Annotations exist but incomplete:
        - Missing request/response body schemas
        - Missing security requirements (`@SecurityRequirement`)
        - Missing parameter descriptions
        - No examples for typical use cases

7. **Validation Completeness**:
    - `locale` field validated with `@Size(min = 2, max = 16)` — should verify against ISO locale
      format
    - `policyVersion` validated with `@Size(max = 64)` — no format validation
    - Enum values (subjectKind, consentType) use `valueOf()` which throws generic
      `IllegalArgumentException` — should return 400 with clear message

8. **Error Handling**:
    - No handling for malformed enum values (returns 500 instead of 400)
    - No handling for Flow materialization errors
    - No handling for database connection failures

## Affected Areas

### Core Implementation (Draft)

-

`server/smp/src/main/kotlin/com/profiletailors/smp/governance/infrastructure/http/ConsentController.kt` —
NEW, uncommitted

-

`server/smp/src/main/kotlin/com/profiletailors/smp/governance/application/GetWorkspaceConsentRecordsHandler.kt` —
NEW, uncommitted

-

`server/smp/src/main/kotlin/com/profiletailors/smp/governance/application/GetConsentHistoryHandler.kt` —
NEW, uncommitted

- `server/smp/src/main/kotlin/com/profiletailors/smp/governance/application/ConsentQueries.kt` —
  NEW, uncommitted
- `server/smp/src/main/kotlin/com/profiletailors/smp/governance/application/ConsentResult.kt` — NEW,
  uncommitted

### Modified Files

-

`server/smp/src/main/kotlin/com/profiletailors/smp/governance/application/RecordConsentHandler.kt` —
Changed visibility to `internal`

-

`server/smp/src/main/kotlin/com/profiletailors/smp/governance/infrastructure/http/GovernanceProblemDetailsHandler.kt` —
Added 404 handler

### Tests (Draft)

-

`server/smp/src/test/kotlin/com/profiletailors/smp/governance/infrastructure/http/ConsentControllerWebTest.kt` —
NEW, uncommitted

### Existing Infrastructure (No Changes Needed)

- `server/smp/src/main/kotlin/com/profiletailors/smp/governance/domain/ConsentRepository.kt` —
  Interface already supports required queries
-

`server/smp/src/main/kotlin/com/profiletailors/smp/governance/infrastructure/R2dbcConsentRepository.kt` —
Implementation already complete

-

`server/smp/src/main/kotlin/com/profiletailors/smp/authorization/domain/WorkspaceAuthorizationDecider.kt` —
Authorization interface ready

-

`server/smp/src/main/kotlin/com/profiletailors/smp/authorization/application/WorkspaceAuthorizationService.kt` —
Authorization service ready

## Approaches

### Approach 1: Minimal Completion (Complete Draft + Fix Bugs)

**Description**: Fix the critical Flow materialization bug, add auth context binding, complete the
implementation as-is with minimal additional work.

**Pros**:

- Fastest path to working HTTP API
- Preserves approved HTTP design decisions
- Smallest changeset for review
- Low risk — no architectural changes

**Cons**:

- Leaves production-readiness gaps (integration tests, full validation)
- Authorization permission may not be properly registered
- OpenAPI documentation incomplete
- No BDD coverage

**Effort**: Low (1-2 days)

**Steps**:

1. Fix Flow materialization: Add `.toList()` in query handlers
2. Add auth context binding to controller (match existing patterns)
3. Fix HTTP status logic in POST /consent (line 103)
4. Add basic enum validation error handling
5. Register `workspace:consent:read` permission in role definitions
6. Run existing tests to verify no regressions

---

### Approach 2: Production-Ready Completion (Full Test Coverage + Documentation)

**Description**: Complete the implementation with full integration tests, authorization tests, BDD
scenarios, and complete OpenAPI documentation.

**Pros**:

- Production-ready from day one
- Full test coverage gives confidence
- Complete documentation for API consumers
- Proper authorization testing
- Follows existing testing patterns in the codebase

**Cons**:

- More time investment
- Larger changeset to review
- May discover additional edge cases requiring design decisions

**Effort**: Medium (3-5 days)

**Steps**:

1. All steps from Approach 1
2. Add Postgres integration tests for query handlers
3. Add BDD scenarios for HTTP API flows
4. Add authorization tests verifying role permissions
5. Complete OpenAPI documentation with examples
6. Add end-to-end integration test with real auth context
7. Add validation for locale format and enum parsing

---

### Approach 3: Phased Rollout (Minimal + Incremental Improvements)

**Description**: Ship Approach 1 first to unblock dependent work, then iterate with improvements in
follow-up changes.

**Pros**:

- Unblocks dependent work faster
- Allows real-world usage to inform improvements
- Spreads effort across multiple PRs
- Easier to review in smaller chunks

**Cons**:

- Ships with known limitations
- Risk of "phase 2 never happens"
- Technical debt accumulates if not disciplined
- May need to support incomplete API surface temporarily

**Effort**: Low initial (1-2 days), Medium follow-up (2-3 days)

**Phase 1**:

- Steps from Approach 1 (minimal completion)

**Phase 2** (separate change):

- Integration tests
- BDD scenarios
- Complete OpenAPI docs
- Full validation

## Recommendation

**Approach 2: Production-Ready Completion**

**Rationale**:

1. **Governance data is legally sensitive** — consent records are GDPR evidence; we cannot afford
   bugs or incomplete authorization
2. **Test coverage is not optional** — existing codebase has high test discipline (BDD, integration
   tests); shipping without tests breaks the standard
3. **Authorization gap is a security risk** — if permission is not properly registered, we may
   inadvertently grant access to wrong roles
4. **Flow materialization bug is a blocker** — code will not work as-is; if we're fixing bugs
   anyway, complete the job
5. **Small additional effort for large confidence gain** — moving from Approach 1 to Approach 2 is ~
   2-3 extra days but eliminates all known risks

**Why not Approach 1**:

- Leaves known bugs (Flow materialization, status logic)
- No integration tests means we're flying blind on auth context binding
- No verification that authorization permissions are correctly wired

**Why not Approach 3**:

- Risk of phase 2 never happening
- Governance API is not a high-velocity feature — better to do it right once
- Small changeset advantage is lost if we have to fix bugs post-merge

## Risks

### Critical Risks (Must Address Before Merge)

1. **Flow Materialization Bug (BLOCKER)**:
    - **Impact**: Code will not compile or runtime failure
    - **Likelihood**: 100% — code is incorrect
    - **Mitigation**: Add `.toList()` in both query handlers
    - **Verification**: Run unit tests, verify compilation

2. **Authorization Permission Not Registered (SECURITY)**:
    - **Impact**: Permission check always fails OR wrong roles have access
    - **Likelihood**: High — no evidence permission exists in role definitions
    - **Mitigation**: Verify permission in authorization module, add if missing
    - **Verification**: Authorization handler tests with real permission resolver

3. **Auth Context Not Bound (FUNCTIONAL)**:
    - **Impact**: API returns 500 or null pointer on every request
    - **Likelihood**: High — parameters not annotated
    - **Mitigation**: Add Spring Security binding (match existing patterns)
    - **Verification**: Integration test with real auth context

### Medium Risks (Should Address)

4. **HTTP Status Logic Inverted**:
    - **Impact**: Clients see 200 when expecting 201 (confusing but not breaking)
    - **Likelihood**: Medium — logic seems backwards
    - **Mitigation**: Clarify intended behavior, add test coverage
    - **Verification**: Controller tests for both cases

5. **Incomplete Validation**:
    - **Impact**: Bad data accepted, unclear error messages
    - **Likelihood**: Medium — happens in production usage
    - **Mitigation**: Add format validation, improve error messages
    - **Verification**: Validation tests for edge cases

### Low Risks (Nice to Have)

6. **Missing Integration Tests**:
    - **Impact**: Bugs not caught until production
    - **Likelihood**: Low — unit tests cover most logic
    - **Mitigation**: Add Postgres integration tests
    - **Verification**: Run full test suite

7. **Incomplete OpenAPI Docs**:
    - **Impact**: API consumers have unclear contract
    - **Likelihood**: Low — internal API, team can ask questions
    - **Mitigation**: Complete annotations with examples
    - **Verification**: Review generated OpenAPI spec

## Ready for Proposal

**No** — Implementation is in draft state with critical bugs.

### What the Orchestrator Should Tell the User

**Status**: The HTTP API implementation is ~70% complete but has **3 critical blockers** that
prevent merge:

1. **Flow materialization bug** — Query handlers return `Flow` but controller expects `List`. Code
   will not work. Fix: add `.toList()`.

2. **Missing auth context binding** — Controller parameters `principalId` and `workspaceId` are not
   bound from security context. API will fail on every request. Fix: add Spring Security
   annotations.

3. **Authorization permission not verified** — Permission `workspace:consent:read` is checked but
   may not be registered in role definitions. Fix: verify and register permission.

**Next Steps**:

1. **Decide approach**: Choose between:
    - **Minimal** (fix bugs, basic tests) — 1-2 days, ships with limitations
    - **Production-ready** (full test coverage, complete docs) — 3-5 days, ready for real usage
    - **Phased** (ship minimal now, iterate later) — risky for sensitive governance data

2. **If production-ready** (recommended):
    - Fix critical bugs (Flow, auth binding, status logic)
    - Add authorization tests verifying permission registration
    - Add Postgres integration tests for query handlers
    - Add BDD scenarios for HTTP flows
    - Complete OpenAPI documentation
    - Run full CI suite

3. **Create formal proposal** with:
    - Scope: complete HTTP API with full test coverage
    - Acceptance criteria: all tests pass, OpenAPI docs complete, auth verified
    - Rollback plan: remove HTTP endpoints, keep domain/repository unchanged

**Recommendation**: Choose **production-ready** approach. Governance data is legally sensitive;
incomplete testing is not acceptable. The additional 2-3 days investment eliminates all known risks.
