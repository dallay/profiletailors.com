# Governance Consent HTTP API Specification

## Purpose

Define the HTTP API adapter for workspace-scoped GDPR consent management. The API exposes existing consent domain capabilities (record, withdraw, list, history) over authenticated REST endpoints with workspace isolation, idempotent semantics, append-only withdrawal evidence, and proper authorization.

---

## Requirements

### Requirement: Authentication Context Binding

The system SHALL derive `principalId` and `workspaceId` from the Spring Security `ResourceContext` for all HTTP requests. Controllers MUST NOT accept these as explicit request parameters.

#### Scenario: Authenticated user gets consent records

- GIVEN a valid JWT bearer token with `principalId: alice` and `workspaceId: ws-123`
- WHEN `GET /api/governance/consent` is invoked
- THEN the handler SHALL use `ResourceContextProvider.require().principalId` → `alice`
- AND `ResourceContextProvider.require().workspaceId` → `ws-123`
- AND no explicit path/query parameters for `principalId` or `workspaceId`

#### Scenario: Missing security context throws structured error

- GIVEN a request without valid authentication
- WHEN any consent endpoint is invoked
- THEN the system SHALL return `401 Unauthorized` with RFC 7807 Problem Details
- AND `type` SHALL be `about:blank`
- AND `title` SHALL be `Unauthorized`

---

### Requirement: Authorization Permission Enforcement

The system SHALL enforce `workspace:consent:read` permission for all query endpoints. The permission MUST be registered in `PermissionRegistry` and assigned to `OWNER` and `ADMIN` roles. `MEMBER` role MUST NOT have this permission.

#### Scenario: Owner can list workspace consent records

- GIVEN a principal with role `OWNER` in workspace `ws-123`
- WHEN `GET /api/governance/consent` is invoked
- THEN `WorkspaceAuthorizationDecider.isAuthorized(ws-123, principal, "workspace:consent:read")` SHALL return `true`
- AND records SHALL be returned

#### Scenario: Member is denied access to consent records

- GIVEN a principal with role `MEMBER` in workspace `ws-123`
- WHEN `GET /api/governance/consent` is invoked
- THEN the system SHALL return `403 Forbidden` with Problem Details
- AND `detail` SHALL include `workspace:consent:read`

#### Scenario: History endpoint requires same permission

- GIVEN an `ADMIN` principal with valid context for workspace `ws-123`
- WHEN `GET /api/governance/consent/history?subjectReference=alice@example.com` is invoked
- THEN authorization SHALL pass (ADMIN has `workspace:consent:read`)
- AND history SHALL be returned

---

### Requirement: Workspace Isolation

The system SHALL ensure all repository queries are scoped by `workspaceId` from security context. No endpoint SHALL accept `workspaceId` as a request parameter.

#### Scenario: Cannot access records outside own workspace

- GIVEN a principal in workspace `ws-123`
- WHEN any consent query is executed
- THEN repository SHALL filter by `workspaceId = ws-123`
- AND records from `ws-456` SHALL NOT be returned

---

### Requirement: Record Consent — Idempotent Status Semantics

The `POST /api/governance/consent` endpoint SHALL record consent with explicit application outcome semantics:

| Condition | HTTP Status | Meaning |
|-----------|-------------|---------|
| New consent record created | `201 Created` | Consent applied for the first time |
| Existing record is `ACTIVE` | `200 OK` | No action taken; idempotent replay |
| Existing record is `WITHDRAWN` | `201 Created` | Re-recorded; new evidence created |

The response body SHALL include `applicationOutcomes` listing what was applied.

#### Scenario: First-time consent recording returns 201

- GIVEN no existing consent for `alice@example.com`, purpose `MARKETING`, workspace `ws-123`
- WHEN `POST /api/governance/consent` with valid payload is invoked
- THEN status `201 Created` SHALL be returned
- AND `applicationOutcomes` SHALL list the newly recorded consent

#### Scenario: Idempotent replay of active consent returns 200

- GIVEN an existing `ACTIVE` consent for the same `subjectReference`, `subjectKind`, `purpose`, `workspaceId`
- WHEN `POST /api/governance/consent` with identical payload is invoked
- THEN status `200 OK` SHALL be returned
- AND `applicationOutcomes` SHALL be empty (nothing applied)

#### Scenario: Re-consent after withdrawal returns 201 with new record

- GIVEN a `WITHDRAWN` consent record for `alice@example.com`, purpose `MARKETING`, workspace `ws-123`
- WHEN `POST /api/governance/consent` is invoked
- THEN status `201 Created` SHALL be returned
- AND a new `ACTIVE` record SHALL be created
- AND `applicationOutcomes` SHALL include the new consent

---

### Requirement: Withdraw Consent — Append-Only Evidence

The `POST /api/governance/consent/withdraw` endpoint SHALL append a withdrawal record without modifying existing evidence. The original consent record SHALL be preserved with status `WITHDRAWN`.

#### Scenario: Successful withdrawal returns 200

- GIVEN an `ACTIVE` consent record for `alice@example.com`, purpose `MARKETING`, workspace `ws-123`
- WHEN `POST /api/governance/consent/withdraw` with matching criteria is invoked
- THEN status `200 OK` SHALL be returned
- AND the existing record status SHALL be set to `WITHDRAWN`
- AND a withdrawal event SHALL be appended to history

#### Scenario: Withdrawal of non-existent consent returns 404

- GIVEN no consent record exists for the given criteria
- WHEN `POST /api/governance/consent/withdraw` is invoked
- THEN status `404 Not Found` SHALL be returned
- AND `detail` SHALL describe the missing record

---

### Requirement: List Workspace Consent Records

The `GET /api/governance/consent` endpoint SHALL return all active consent records for the authenticated workspace, with optional filters for `subjectKind` and `purpose`.

#### Scenario: List all records without filters

- GIVEN workspace `ws-123` has 5 active consent records
- WHEN `GET /api/governance/consent` is invoked
- THEN all 5 records SHALL be returned in the response
- AND each record SHALL include `id`, `subjectReference`, `subjectKind`, `purpose`, `consentedAt`

#### Scenario: Filter by subjectKind and purpose

- GIVEN workspace `ws-123` has records for `USER` and `CONTACT`, purposes `MARKETING` and `ANALYTICS`
- WHEN `GET /api/governance/consent?subjectKind=USER&purpose=MARKETING` is invoked
- THEN only records matching BOTH criteria SHALL be returned
- AND record count SHALL reflect the filtered set

---

### Requirement: Consent History

The `GET /api/governance/consent/history` endpoint SHALL return the full consent lifecycle for a subject, including recording, withdrawals, and re-consents.

#### Scenario: Full history shows lifecycle events

- GIVEN a subject `alice@example.com` with multiple consent state changes
- WHEN `GET /api/governance/consent/history?subjectReference=alice@example.com` is invoked
- THEN all records SHALL be returned ordered by `consentedAt` ascending
- AND each record SHALL include `status` (`ACTIVE` or `WITHDRAWN`)
- AND `withdrawnAt` SHALL be populated for WITHDRAWN records

---

### Requirement: Waitlist Adapter Integration

The system SHALL invoke the waitlist adapter after consent state changes to propagate consent decisions.

#### Scenario: Consent recorded updates waitlist

- GIVEN a `WAITLIST` entry exists for `alice@example.com` in workspace `ws-123`
- WHEN `POST /api/governance/consent` records consent for purpose `MARKETING`
- THEN the waitlist adapter SHALL be notified of the consent decision
- AND the entry SHALL reflect the consent status

---

### Requirement: Flow Materialization

Query handlers SHALL materialize reactive Flows before returning results. The system MUST call `.toList()` on all `Flow<T>` returned by repository queries.

#### Scenario: List records with Flow properly materialized

- GIVEN repository returns `Flow<ConsentRecord>`
- WHEN `GetWorkspaceConsentRecordsHandler` processes the query
- THEN `.toList()` SHALL be invoked on the Flow
- AND the result SHALL be `List<ConsentRecordResult>`

---

### Requirement: Validation and Error Handling

The system SHALL validate all request parameters and return RFC 7807 Problem Details for errors.

#### Scenario: Invalid enum value returns 400

- GIVEN a request with `subjectKind: INVALID_VALUE`
- WHEN validation rejects the enum
- THEN status `400 Bad Request` SHALL be returned
- AND `detail` SHALL identify the invalid field
- AND `type` SHALL be `about:blank`

#### Scenario: Locale format validated

- GIVEN a request with `locale: xx-XX` not matching ISO 639-1 + optional ISO 3166-1
- WHEN validation runs
- THEN status `400 Bad Request` SHALL be returned
- AND `detail` SHALL describe valid format

#### Scenario: Problem Details structure

- GIVEN any error response
- THEN the body SHALL be JSON with `type`, `title`, `status`, `detail`, `instance`
- AND `status` SHALL match the HTTP status code

---

## Integration Test Requirements

### Requirement: Authorization Handler Tests

The system SHALL have unit tests verifying `WorkspaceAuthorizationDecider` is called with correct parameters for both query handlers.

#### Scenario: GetWorkspaceConsentRecordsHandler checks authorization

- GIVEN `GetWorkspaceConsentRecordsHandler` is invoked
- THEN `WorkspaceAuthorizationDecider.isAuthorized(workspaceId, principal, "workspace:consent:read")` SHALL be called exactly once

#### Scenario: Authorization denied returns structured error

- GIVEN authorization returns `false`
- WHEN the handler is invoked
- THEN `AuthorizationDeniedException` SHALL be thrown
- AND the HTTP layer SHALL map it to `403 Forbidden`

---

### Requirement: Postgres Integration Tests

The system SHALL have integration tests using Testcontainers or real Postgres for reactive repository query methods.

#### Scenario: findActiveByWorkspace returns correct records

- GIVEN two consent records exist in Postgres for workspace `ws-123`
- AND one record exists for workspace `ws-456`
- WHEN `findActiveByWorkspace(ws-123)` is called
- THEN exactly 2 records SHALL be returned
- AND no record from `ws-456` SHALL be included

#### Scenario: findHistoricalByIdentity returns ordered history

- GIVEN multiple state changes exist for `alice@example.com` in workspace `ws-123`
- WHEN `findHistoricalByIdentity(ws-123, alice@example.com, null)` is called
- THEN records SHALL be returned ordered by `consentedAt` ascending

---

### Requirement: BDD End-to-End Scenarios

The system SHALL have Cucumber scenarios covering complete HTTP flows.

#### Scenario: Full consent lifecycle via HTTP

- GIVEN an authenticated principal with `OWNER` role
- WHEN the following HTTP flow executes:
  1. `POST /api/governance/consent` records consent → `201`
  2. `GET /api/governance/consent` lists records → contains new record
  3. `POST /api/governance/consent/withdraw` withdraws → `200`
  4. `GET /api/governance/consent/history` shows `WITHDRAWN`
  5. `POST /api/governance/consent` re-records → `201`
- THEN all steps SHALL succeed with expected status codes
- AND final history SHALL show two `ACTIVE` records

---

## OpenAPI Documentation Requirements

### Requirement: Schema Definitions

The system SHALL include complete `@Schema` annotations on all request/response DTOs with descriptions, examples, and format constraints.

### Requirement: Security Requirements

All endpoints SHALL declare `@SecurityRequirement(name = "bearerAuth")` to document JWT authentication.

### Requirement: Error Response Documentation

All 4xx error responses SHALL be documented with Problem Details schema including `type`, `title`, `status`, `detail`, `instance`.

---

## Acceptance Criteria

| # | Criterion |
|---|-----------|
| 1 | `principalId` and `workspaceId` derived from `ResourceContext`, not request params |
| 2 | `workspace:consent:read` permission registered and assigned to OWNER, ADMIN |
| 3 | MEMBER role receives `403` on query endpoints |
| 4 | POST consent: `201` for new, `200` for idempotent ACTIVE, `201` for re-consent after WITHDRAWN |
| 5 | POST withdraw: `200` success, `404` if no active record found |
| 6 | GET consent: returns active records scoped to workspaceId |
| 7 | GET history: returns full lifecycle ordered by consentedAt |
| 8 | Waitlist adapter notified after consent state changes |
| 9 | Query handlers materialize Flows with `.toList()` |
| 10 | Invalid enum: `400 Bad Request` with clear detail |
| 11 | Locale format validated against ISO 639-1 + optional ISO 3166-1 |
| 12 | All errors return RFC 7807 Problem Details |
| 13 | Authorization handler unit tests cover both query handlers |
| 14 | Postgres integration tests verify workspace isolation |
| 15 | BDD scenario covers full consent HTTP lifecycle |
| 16 | OpenAPI specs include `@SecurityRequirement`, schemas, examples |
