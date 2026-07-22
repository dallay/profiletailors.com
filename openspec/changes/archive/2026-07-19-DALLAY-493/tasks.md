# Tasks: Privacy DSAR — Access, Export, Correction, Deletion

## Review Workload Forecast

| Field                   | Value                                                                                                      |
|-------------------------|------------------------------------------------------------------------------------------------------------|
| Estimated changed lines | 2800–3800                                                                                                  |
| 400-line budget risk    | High                                                                                                       |
| Chained PRs recommended | Yes                                                                                                        |
| Suggested split         | PR 1 (Domain + Migration) → PR 2 (Services + Handlers) → PR 3 (API + Audit) → PR 4 (Frontend) → PR 5 (E2E) |
| Delivery strategy       | ask-on-risk                                                                                                |
| Chain strategy          | pending                                                                                                    |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: High

### Suggested Work Units

| Unit | Goal                                                                                  | Likely PR | Notes                                           |
|------|---------------------------------------------------------------------------------------|-----------|-------------------------------------------------|
| 1    | Domain model, repository, migration                                                   | PR 1      | Foundation — no external deps                   |
| 2    | Services (aggregation, anonymization) + all 4 submit handlers + status/query handlers | PR 2      | Depends on PR 1; touches iam + waitlist domains |
| 3    | REST controller, DTOs, audit integration                                              | PR 3      | Depends on PR 2                                 |
| 4    | Frontend (PrivacySection, store, i18n, integration into SettingsView)                 | PR 4      | Depends on PR 3 API existing                    |
| 5    | E2E tests for all 4 DSAR flows                                                        | PR 5      | Depends on all prior units                      |

## Phase 1: Domain Model & Persistence

- [x] 1.1 **RED** Write `DataSubjectRequestStatus` enum test: only valid transitions
  PENDING→COMPLETED|REJECTED|FAILED, terminal states reject transitions
- [x] 1.2 **GREEN** Create `DataSubjectRequestStatus` enum with transition guards in
  `domain/DataSubjectRequestStatus.kt`
- [x] 1.3 **RED** Write `DataSubjectRequest` aggregate test: creation sets PENDING, expires_at =
  +30d, transition guards, invariants (rejection_reason required for REJECTED)
- [x] 1.4 **GREEN** Create `DataSubjectRequest` entity with typed fields, `transitionTo()` method
  enforcing state machine, factory with expiry
- [x] 1.5 **RED** Write `DataSubjectRequestRepository` interface test (contract test or mock-based)
- [x] 1.6 **GREEN** Create `DataSubjectRequestRepository` interface (`save`, `findById`,
  `findByRequester`, `findByStatus`, `findExpired`)
- [x] 1.7 **RED** Write `R2dbcDataSubjectRequestRepository` integration test against Testcontainers:
  CRUD, findBy queries, expiry filtering
- [x] 1.8 **GREEN** Create `R2dbcDataSubjectRequestRepository` with R2BC entity + row mapping
- [x] 1.9 **GREEN** Create Liquibase migration `privacy/001-create-data-subject-requests.yaml`, add
  include to `db.changelog-master.yaml`
- [x] 1.10 Create `PrivacyBoundedContext.kt` and `PrivacyBoundedContextConfiguration.kt` (Spring
  Modulith + bean wiring)
- [x] 1.11 **RED** Write `FindExpiredRequestsJob` test: invokes TTL deletion for past-expiry
  requests
- [x] 1.12 **GREEN** Create scheduled job `FindExpiredRequestsJob` (Scheduler + repository query)

## Phase 2: Application Services

- [x] 2.1 **RED** Write `AnonymizationService` test: anonymizePII replaces email/username on
  user_identities, idempotent on double-call, clears waitlist metadata
- [x] 2.2 **GREEN** Create `AnonymizationService` in `privacy/application/` (calls identity +
  waitlist repositories)
- [x] 2.3 **RED** Write `DataAggregationService` test: collects data from all 7 bounded contexts,
  structured output with `_metadata`
- [x] 2.4 **GREEN** Create `DataAggregationService` with per-context collector calls
- [x] 2.5 **RED** Write `SubmitAccessRequestHandler` test: creates PENDING request, calls
  DataAggregationService, transitions to COMPLETED
- [x] 2.6 **GREEN** Create `SubmitAccessRequestHandler`
- [x] 2.7 **RED** Write `SubmitExportRequestHandler` test: creates PENDING→COMPLETED, JSON file,
  presigned URL for >10MB, inline for ≤10MB
- [x] 2.8 **GREEN** Create `SubmitExportRequestHandler` with `StoragePort.presignUrl()` integration
- [x] 2.9 **RED** Write `SubmitCorrectionRequestHandler` test: validates email/username format,
  calls identity correction, propagates to waitlist, returns old values
- [x] 2.10 **GREEN** Create `SubmitCorrectionRequestHandler` with waitlist propagation
- [x] 2.11 **RED** Write `SubmitDeletionRequestHandler` test: sole-owner pre-validation rejects,
  phase 1 anonymizes, phase 2 removes, phase 3 marks GC
- [x] 2.12 **GREEN** Create `SubmitDeletionRequestHandler` with 3-phase orchestration in
  `AtomicTransactionRunner`
- [x] 2.13 **RED** Write `CheckRequestStatusHandler` test: returns status and result for valid
  request ID
- [x] 2.14 **GREEN** Create `CheckRequestStatusHandler` + `ListRequestsHandler`

## Phase 3: API & Audit Integration

- [ ] 3.1 **RED** Write DTO validation tests: `SubmitPrivacyRequest` rejects empty type, invalid
  email format, missing correction fields
- [ ] 3.2 **GREEN** Create `PrivacyRequestDtos.kt` with request/response DTOs and bean validation
  annotations
- [ ] 3.3 **RED** Write `PrivacyMutationAuditor` test: records `dsar.submitted`,
  `dsar.status_changed`, `dsar.completed` events with sentinel workspace_id
- [ ] 3.4 **GREEN** Create `PrivacyMutationAuditor` following `TenancyMutationAuditor` pattern
- [ ] 3.5 **RED** Write `PrivacyController` WebFlux integration test: POST creates 201, GET list
  returns 200, GET by id returns 200/404, rate limit blocks 4th request
- [ ] 3.6 **GREEN** Create `PrivacyController` with all 4 REST endpoints + rate-limit filter
- [ ] 3.7 Wire auditor into all 4 submit handlers via constructor injection

## Phase 4: Frontend

- [ ] 4.1 **RED** Write Pinia store test: `submitRequest` calls POST, `fetchRequests` populates
  state
- [ ] 4.2 **GREEN** Create `privacy.store.ts` (Pinia store under `modules/settings/infrastructure/`)
- [ ] 4.3 Create `DsarStatusBadge.vue` with status color mapping (PENDING→yellow, COMPLETED→green,
  REJECTED→red, FAILED→gray)
- [ ] 4.4 Create `DsarRequestForm.vue` with type dropdown, notes field, correction fields, submit
  button, deletion confirmation dialog
- [ ] 4.5 Create `DsarRequestList.vue` with request history table and download links
- [ ] 4.6 Create `PrivacySection.vue` composing form + list + badge
- [ ] 4.7 Add `privacy.*` i18n keys to `shared/i18n/locales/{en,es}/settings.ts`
- [ ] 4.8 Integrate `PrivacySection.vue` into `SettingsView.vue` below channels panel
- [ ] 4.9 **RED** Write component test: form submit triggers store action, list renders fetched
  requests

## Phase 5: Verification

- [x] 5.1 **E2E** ACCESS request: submit → PENDING visible in list → complete → aggregated data
  available
- [x] 5.2 **E2E** EXPORT request: submit → complete → download link works → JSON matches expected
  schema
- [x] 5.3 **E2E** CORRECTION request: submit email change → waitlist entry updated → old values
  returned
- [x] 5.4 **E2E** DELETION request: sole-owner blocked → non-owner completes → sessions revoked →
  PII redacted → legal holds preserved
- [x] 5.5 **E2E** Rate limit: submit 4 requests in same day → 4th rejected with
  `rate_limit_exceeded`
- [x] 5.6 **E2E** Audit trail: full DSAR lifecycle generates `dsar.submitted`,
  `dsar.status_changed`, `dsar.completed` events
