# Tasks: Reliable Direct Invitation Email Delivery

## Review Workload Forecast

| Field                   | Value                                                                                       |
|-------------------------|---------------------------------------------------------------------------------------------|
| Estimated changed lines | 650–900                                                                                     |
| 400-line budget risk    | High                                                                                        |
| Chained PRs recommended | Yes                                                                                         |
| Suggested split         | PR 1 contracts/tests → PR 2 transactional handlers/wiring → PR 3 consumer/docs/verification |
| Delivery strategy       | ask-on-risk                                                                                 |
| Chain strategy          | github-stacked-prs                                                                          |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: github-stacked-prs
400-line budget risk: High

### Suggested Work Units

| Unit | Goal                                                 | Likely PR | Notes                                                                     |
|------|------------------------------------------------------|-----------|---------------------------------------------------------------------------|
| 1    | Establish failing seams and contracts                | PR 1      | Tests first; transaction, target resolution, event delivery identity      |
| 2    | Implement atomic mutation and reactive publication   | PR 2      | Depends on PR 1; invitation, audit, and event registration share rollback |
| 3    | Implement post-commit delivery and document boundary | PR 3      | Depends on PR 2; provider failure leaves ACTIVE/FAILED                    |

## Phase 1: RED Tests and Contract Seams

- [x] 1.1 Extend `CreateInvitationHandlerTest` and `ResendInvitationHandlerTest` with failing cases
  for `TransactionalOperator`/`AtomicTransactionRunner`, audit plus event registration rollback,
  existing-workspace name lookup, missing workspace → HTTP 404, `NEW_WORKSPACE` copy, same
  invitation ID, rotated material, expiry, and version.
- [x] 1.2 Extend `SendInvitationEmailConsumerTest` and
  `shared/notifications/src/test/kotlin/com/profiletailors/notifications/domain/InvitationEmailTest.kt`
  with failing tests for target-aware copy, raw-token temporary in-memory handoff, `:initial` versus
  `:resend:{deliveryId}` keys, replay dedupe including `FAILED`, and distinct intentional resends.
- [x] 1.3 Add failing real-wiring cases to
  `PlatformAdminInvitationTransactionPostgresIntegrationTest` and
  `R2dbcNotificationRepositoryPostgresTest`: commit dispatches once; pre-commit and rollback create
  no notification/provider call; audit/event registration rolls back; provider failure persists
  `FAILED` while invitation remains `ACTIVE`.
- [x] 1.4 Extend `invitations-direct.feature` and `DirectInvitationBddSteps.kt` for existing/new
  target copy, resend identity, provider-failure HTTP success, and preserved
  201/200/401/403/404/409/token-redaction responses.

## Phase 2: Minimum Production Implementation

- [x] 2.1 Add `InvitationEventPublisher` under `platformadmin/application/contracts`, delivery
  identity fields to `InvitationIssued`/`DirectInvitationResent`, and the narrow tenancy
  workspace-name lookup with a not-found exception mapped to 404. Deviation: implemented as
  `WorkspaceNameReader.kt`/`R2dbcWorkspaceNameReader.kt` instead of extending
  `WorkspaceReadRepository.kt`/`R2dbcWorkspaceReadRepository.kt` to keep the existing repository
  unchanged per interface segregation; documented in ADR-0020 addendum.
- [x] 2.2 Refactor `CreateInvitationHandler.kt` and `ResendInvitationHandler.kt` to own
  `AtomicTransactionRunner`: validate/resolve target, mutate invitation, write audit, and register
  the event in one reactive transaction; preserve raw token only across the temporary
  handler→event→consumer boundary.
- [x] 2.3 Implement `SpringTransactionalInvitationEventPublisher` in `platformadmin/infrastructure`,
  wire it in `PlatformAdminBootstrapConfiguration.kt`, and remove reliance on controller
  `@Transactional` for these operations while preserving `AdminInvitationController.kt`
  response/error compatibility.
- [x] 2.4 Update `SendInvitationEmailConsumer.kt` to use `AFTER_COMMIT` with fallback disabled,
  target copy, per-create/resend idempotency, one provider attempt, and non-throwing `PENDING`→
  `SENT`/`FAILED` handling; keep committed invitations `ACTIVE` on provider failure.

## Phase 3: Refactor, Documentation, and Verification

- [x] 3.1 Refactor only after RED/GREEN tests pass; keep domain/application/infrastructure
  dependency direction and redact tokens from durable payloads, logs, audit, and HTTP responses.
  Evidence: all four lanes GREEN before docs; HexagonalArchTest passes; no code changes needed;
  token audit confirms no rawToken in payload keys, audit, logs, metrics, or HTTP; `acceptUrl` in
  notification payload is the existing temporary DALLAY-566 surface only.
- [x] 3.2 Update `docs/architecture/adr/0020-first-class-invitation-aggregate.md` and the relevant
  notification/operations documentation with reactive event-context wiring, atomicity, provider
  semantics, and the explicit DALLAY-566 token-safe follow-up; do not edit DALLAY-567 artifacts or
  canonical specs. Evidence: ADR-0020 addendum covers wiring, atomicity boundary, 404 semantics,
  resend identity, and DALLAY-566 follow-up; no DALLAY-567 or `openspec/specs/` edits; operational
  monitoring guidance embedded in ADR.
- [x] 3.3 Run focused evidence: `just backend-test-fast`, `just backend-test-postgres`,
  `just backend-bdd-fast`, then `just backend-check`; inspect the final diff for unchanged
  `state.yaml`, canonical specs, and DALLAY-567 QA files. Evidence: all four lanes Passed; diff
  shows no suppressions, baseline, secrets, canonical-spec, or DALLAY-567 edits; `state.yaml` phase
  untouched.
