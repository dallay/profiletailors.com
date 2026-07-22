# Delta for Governance Consent HTTP API

> **Status**: Active change — parallel design in progress
> **Corrected against**: proposal.md, exploration.md, ConsentRecordModels.kt, ConsentController.kt,
> R2dbcConsentRepository.kt, ContextProviders.kt, GovernanceWaitlistConsentRecorder.kt

---

## Context: Corrections Applied

This spec supersedes the draft version with corrections grounded in actual repository types and
runtime behavior:

1. `ResourceContext` has `workspaceId` only — `principalId` comes from `PrincipalContextProvider`
2. `SubjectKind` enum is `WORKSPACE | USER | ANONYMOUS` — CONTACT does not exist
3. Waitlist adapter (`GovernanceWaitlistConsentRecorder`) calls governance; HTTP changes must NOT
   notify or mutate waitlist
4. `RecordConsentOutcome` defined as `data class(created: Boolean, record: ConsentRecord)`
5. Current UPSERT implementation replaces one row — append-only claim is **not implemented**
6. Status logic `if (record.status == WITHDRAWN) OK else CREATED` is inverted
7. Testing scope scoped to unit, WebFlux contract, and R2DBC integration tests

---

## ADDED Requirements

### Requirement: Workspace-scoped consent recording with idempotent semantics

The system **MUST** accept a consent record via `POST /api/governance/consent` for the authenticated
workspace context.

The system **MUST** derive `workspaceId` from `ResourceContext` populated by the JWT filter —
controllers **MUST NOT** accept `workspaceId` as a request parameter or method parameter.

The system **MUST** check for an existing active record (same workspace, subject, purpose, policy
version) before persisting. If one exists, the system **MUST** return HTTP 200 with the existing
record. If none exists, the system **MUST** persist a new record and return HTTP 201.

The response **MUST** include
`RecordConsentOutcome(created: Boolean, record: ConsentRecordResponse)` to distinguish new creation
from idempotent return.

```kotlin
data class RecordConsentOutcome(
    val created: Boolean,                    // true = 201, false = 200
    val record: ConsentRecordResponse
)
```

The controller **MUST** implement the idempotency check by calling `existsActive` before
`recordConsentHandler.handle`.

The system **MUST NOT** bind `principalId` from the request — `principalId` is available via
`PrincipalContextProvider` for logging or audit but is not required for workspace-scoped consent
recording.

#### Scenario: New consent record — first submission for subject + purpose

- GIVEN an authenticated request with valid `RecordConsentRequest`
- AND no active consent exists for the same workspace + subject + purpose + policy version
- WHEN `POST /api/governance/consent` is called
- THEN HTTP 201 is returned
- AND `RecordConsentOutcome.created == true`
- AND the response body contains the new `ConsentRecordResponse`

#### Scenario: Idempotent consent record — submission already active

- GIVEN an authenticated request with valid `RecordConsentRequest`
- AND an active consent exists for the same workspace + subject + purpose + policy version
- WHEN `POST /api/governance/consent` is called
- THEN HTTP 200 is returned
- AND `RecordConsentOutcome.created == false`
- AND the response body contains the existing `ConsentRecordResponse`

#### Scenario: Idempotent consent record — subject previously withdrawn

- GIVEN an authenticated request for subject S, purpose P, policy version V
- AND the latest record for S+P+V has `status == WITHDRAWN`
- WHEN `POST /api/governance/consent` is called
- THEN the handler **MUST** persist a new record with `status == ACTIVE`
- AND HTTP 201 is returned (new row created)
- AND `RecordConsentOutcome.created == true`
- AND both the withdrawn and new records remain queryable via `GET /history`

> **Note**: This behavior requires `RecordConsentHandler` to generate a new `ConsentRecordId` on
> each call rather than reusing the withdrawn record's ID. The current `R2dbcConsentRepository` uses
`ON CONFLICT (id) DO UPDATE` which replaces the row — this must be changed to INSERT with a new ID
> to satisfy DALLAY-491 historical evidence preservation.

---

### Requirement: Enum validation returns 400 with clear error detail

The system **MUST** handle invalid enum values for `subjectKind` and `consentType` by catching
`IllegalArgumentException` from `valueOf()` and returning HTTP 400 with a RFC 9457 Problem Details
body.

The error response **MUST** include `type: "about:blank"`, `title: "Bad Request"`, `status: 400`,
and a `detail` field describing the invalid value and valid options.

```kotlin
// Controller-level handling
try {
    val subjectKind = SubjectKind.valueOf(request.subjectKind.uppercase())
} catch (e: IllegalArgumentException) {
    throw EnumValidationException(
        field = "subjectKind",
        value = request.subjectKind,
        validValues = SubjectKind.entries.map { it.name }
    )
}
```

#### Scenario: Invalid subjectKind returns 400

- GIVEN `subjectKind = "CONTACT"` (invalid — actual values are WORKSPACE, USER, ANONYMOUS)
- WHEN `POST /api/governance/consent` is called
- THEN HTTP 400 is returned
- AND problem detail `detail` contains "subjectKind" and lists valid values

---

### Requirement: GET /history requires subjectValue, subjectKind, purpose

The system **MUST** require all three query parameters on `GET /api/governance/consent/history`:
`subjectValue`, `subjectKind`, and `purpose`. These are **not** optional.

The system **MUST** use `PrincipalContextProvider` to obtain `principalId` for audit logging, but *
*MUST NOT** expose it in the API contract.

#### Scenario: History returns all records for subject including withdrawn

- GIVEN an authenticated request with `subjectValue="user-123"`, `subjectKind="USER"`,
  `purpose="marketing.emails"`
- WHEN `GET /api/governance/consent/history` is called
- THEN all records for that subject + purpose are returned ordered by `givenAt ASC`
- AND each record includes its `status` (ACTIVE or WITHDRAWN)
- AND `withdrawnAt` and `withdrawalReason` are populated for withdrawn records

---

### Requirement: GET /list supports optional subjectKind and purpose filters

The system **MUST** accept optional `subjectKind` and `purpose` query parameters on
`GET /api/governance/consent`.

When filters are provided, the system **MUST** return only active records matching all supplied
criteria.

When no filters are provided, the system **MUST** return all active records for the workspace.

#### Scenario: List all active records for workspace

- GIVEN an authenticated request with no query parameters
- WHEN `GET /api/governance/consent` is called
- THEN all ACTIVE records for the workspace are returned
- AND records are ordered by `givenAt DESC`

#### Scenario: Filter by subjectKind and purpose

- GIVEN an authenticated request with `subjectKind=USER` and `purpose=marketing.emails`
- WHEN `GET /api/governance/consent` is called
- THEN only ACTIVE records matching both filters are returned

---

## MODIFIED Requirements

### Requirement: Consent withdrawal preserves historical evidence

The system **MUST** record a withdrawal without destroying the evidence of prior consent. Withdrawal
**MUST NOT** delete any existing row.

The system **MUST** update the existing record's `status` to `WITHDRAWN`, set `withdrawnAt` to the
current timestamp, and optionally record a `withdrawalReason`.

> **(Previously: domain model comment claimed append-only but implementation uses UPSERT that
replaces row)**  
> The actual behavior depends on `RecordConsentHandler.handle` creating a new `ConsentRecord` with a
> new ID on each call. The `R2dbcConsentRepository.UPSERT_CONSENT` SQL uses
`ON CONFLICT (id) DO UPDATE` — if the handler reuses the same ID, the row is replaced, not
> preserved. For DALLAY-491 compliance, `RecordConsentHandler` **MUST** generate a fresh
`ConsentRecordId` on each invocation.

#### Scenario: Withdraw active consent — original record preserved

- GIVEN an active consent record with ID `R1`, `status=ACTIVE`
- WHEN `POST /api/governance/consent/withdraw` is called for that subject + purpose
- THEN the record's status is updated to `WITHDRAWN`
- AND `withdrawnAt` and `withdrawalReason` are set
- AND `GET /api/governance/consent/history` returns both the original and withdrawn records

---

### Requirement: Withdraw endpoint returns 200 on success, 404 when not found

The system **MUST** return HTTP 200 when an active consent is successfully withdrawn.

The system **MUST** return HTTP 404 with a problem detail body when no active consent exists for the
requested subject + purpose + policy version.

#### Scenario: Withdraw returns 200 on success

- GIVEN an active consent record for subject S, purpose P, policy version V
- WHEN `POST /api/governance/consent/withdraw` is called with matching fields
- THEN HTTP 200 is returned
- AND the response contains the updated record with `status=WITHDRAWN`

#### Scenario: Withdraw returns 404 when no active consent

- GIVEN no active consent record exists for the requested subject + purpose + policy version
- WHEN `POST /api/governance/consent/withdraw` is called
- THEN HTTP 404 is returned
- AND problem detail `title` is "Not Found" with `detail` describing the missing resource

---

## REMOVED Requirements

### Requirement: (Removed) principalId as controller method parameter

The previous spec implied `principalId` should be passed as a controller method parameter. This is *
*incorrect**.

`principalId` is available via `PrincipalContextProvider.require().principalId` for internal audit
logging, but controllers **MUST NOT** accept it as a method parameter from the HTTP layer.

---

### Requirement: (Clarified — not implemented) Append-only persistence claim

The `ConsentRepository` interface comment stated "Records are append-only: save never updates an
existing row." This is **not reflected in the actual implementation**.

The `R2dbcConsentRepository.save()` uses `UPSERT_CONSENT` with `ON CONFLICT (id) DO UPDATE` which *
*replaces** the existing row. The domain model (`ConsentRecord.withdraw()`) creates a new instance,
but the repository implementation does not persist both rows.

For DALLAY-491 compliance (historical evidence preservation), the implementation **MUST** be
corrected to:

1. Generate a new `ConsentRecordId` on each call to `RecordConsentHandler.handle()`
2. Remove or change the `ON CONFLICT (id) DO UPDATE` clause to INSERT only

This is a **required fix** before the HTTP API can claim GDPR accountability compliance.

---

## Out of Scope

- Waitlist consent UI or API changes — waitlist owns its `WaitlistConsentRecorder` port
- Bulk withdraw or withdraw-all operations (DALLAY-496)
- Real-time consent event streaming
- Multi-workspace aggregation

---

## Test Scope

Verification **MUST** include:

| Layer         | Tests                                               | Rationale                                |
|---------------|-----------------------------------------------------|------------------------------------------|
| Unit          | `ConsentControllerWebTest` (9 tests)                | HTTP contract, status codes, validation  |
| Unit          | `RecordConsentHandlerTest`                          | Idempotency logic, new ID generation     |
| Unit          | `WithdrawConsentHandlerTest`                        | Withdrawal preserves evidence            |
| Integration   | `R2dbcConsentRepositoryTest`                        | Repository query methods against real DB |
| Authorization | `GetWorkspaceConsentRecordsHandler` permission test | `workspace:consent:read` enforcement     |
| Authorization | `GetConsentHistoryHandler` permission test          | `workspace:consent:read` enforcement     |

> **Note**: Full BDD/E2E suite is deferred unless meaningful gaps remain after the above tests pass.
> Governance data is legally sensitive but the proposed test layers provide substantive coverage
> without mandating a broad Cucumber suite.

---

## Key Corrections Summary

| # | Issue                                        | Correction                                                                                                    |
|---|----------------------------------------------|---------------------------------------------------------------------------------------------------------------|
| 1 | `ResourceContext` has no `principalId`       | `workspaceId` from `ResourceContext`; `principalId` from `PrincipalContextProvider` if needed for audit       |
| 2 | `SubjectKind` enum wrong                     | Actual: `WORKSPACE \| USER \| ANONYMOUS` — no `CONTACT`                                                       |
| 3 | Waitlist integration direction wrong         | Waitlist calls governance via `GovernanceWaitlistConsentRecorder`; HTTP changes do NOT affect waitlist        |
| 4 | `RecordConsentOutcome` undefined             | Defined as `data class(created: Boolean, record: ConsentRecordResponse)`                                      |
| 5 | Append-only claim contradicts implementation | Current UPSERT **replaces** row; DALLAY-491 requires `RecordConsentHandler` to generate new `ConsentRecordId` |
| 6 | Status logic inverted                        | `if (WITHDRAWN) OK else CREATED` is wrong — should be `if (existsActive) OK else CREATED`                     |
| 7 | Scope creep in tests                         | Targeted unit + integration + authorization tests sufficient; BDD/E2E deferred                                |
