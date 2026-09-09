# Tasks: DALLAY-567 — Accept Invitations in Registration

## Review Workload Forecast

| Field | Value |
|---|---|
| Estimated changed lines | 550–800 |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 contracts/tests → PR 2 atomic acceptance → PR 3 API/BDD/E2E/observability |
| Delivery strategy | GitHub Stacked PRs; each layer is independently reviewable and green |
| Chain strategy | `github-stacked-prs` with `main` as trunk and bottom-up integration |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: `github-stacked-prs`; each higher layer targets the immediately lower layer
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|---|---|---|---|
| 1 | Preflight contract and focused tests | PR 1 | `feat/dallay-567-invitation-contracts`, base `main`; verify token/PII redaction and safe workspace resolution. |
| 2 | Atomic registration acceptance | PR 2 | `feat/dallay-567-invitation-transaction`, base layer 1; verify rollback, locking, linkage, and target-derived tenancy. |
| 3 | API, telemetry, BDD, integration, and E2E | PR 3 | `feat/dallay-567-invitation-evidence`, base layer 2; verify success and safe failures. |

## Resolved Product Decisions

- [x] HTTP status/code taxonomy: invalid `400 INVITATION_INVALID`; expired `410 INVITATION_EXPIRED`; revoked `410 INVITATION_REVOKED`; consumed `409 INVITATION_ALREADY_CONSUMED`; replay `409 INVITATION_REPLAYED` only when the domain distinguishes it, otherwise consumed classification plus `replay_rejected` telemetry; email mismatch `403 INVITATION_EMAIL_MISMATCH`; client workspace input `400 INVITATION_WORKSPACE_OVERRIDE_NOT_ALLOWED`. Problem Details details are generic and omit sensitive data.
- [x] Existing identities may accept only when authenticated as the matching identity with exact normalized-email equality; no duplicate identity or credential is created; verification follows existing policy.
- [x] Client `workspaceId` is invalid registration input and is rejected even when it matches the invitation; workspace derives exclusively from the invitation target.

## Phase 1: TDD RED — Contracts and Acceptance Scenarios

- [x] 1.1 Add failing unit tests in `InvitationActivationCoordinatorTest.kt` and `LocalAuthHandlersTest.kt` for pre-mutation token/lifecycle/expiry/normalized-email validation and no token/PII leakage. [DALLAY-567: validity]
- [x] 1.2 Add failing tests in `InvitationRegistrationGatewayAdapterTest.kt` for immutable context, target-only workspace resolution, and rejection of invitation-ID/client-workspace fallback. [DALLAY-567: tenancy]
- [x] 1.3 Add failing matching-authentication/no-duplicate tests for existing identities; successful acceptance requires authenticated identity and exact normalized-email match. [DALLAY-567: identity]
- [x] 1.4 Add failing PostgreSQL/Testcontainers tests in `LocalAuthHandlersTransactionPostgresIntegrationTest.kt` for failure injection, rollback, two-client race, one winner, one new workspace, and existing-workspace membership. [DALLAY-567: atomicity/concurrency]
- [ ] 1.5 Add failing API/BDD tests in `LocalAuthControllerTest.kt`, `features/local-auth.feature`, and `LocalAuthCapabilitiesBddSteps.kt` for valid invite-only registration, safe rejection, override decision, response/session, and redaction.
- [x] 1.6 Add failing tests for the resolved Problem Details taxonomy and generic, non-sensitive error details. [DALLAY-567: API/security]
- [ ] 1.7 Add failing Vitest/Playwright coverage in `accept-invitation.store.spec.ts`, `AcceptInvitationView.spec.ts`, and `apps/web/app/e2e/specs/invitee-private-beta.spec.ts` for accepted, invalid, replay, and safe-error flows. [DALLAY-567: frontend]

## Phase 2: GREEN — Transactional Core and Wiring

- [x] 2.1 Create `identity/application/InvitationRegistrationContext.kt`; modify `InvitationRegistrationGateway.kt` with `prepare(rawToken, normalizedEmail)` and in-transaction `complete(...)` semantics.
- [x] 2.2 Modify `identity/application/LocalAuthHandlers.kt` to preflight read-only, hash before the transaction, revalidate inside `AtomicTransactionRunner`, reuse identity/verification/consent policy, and issue session/events post-commit.
- [x] 2.3 Modify `platformadmin/application/InvitationActivationCoordinator.kt` and `AcceptInvitation.kt` to split lock/revalidation from completion, remove nested transactions, link principal, consume once, and derive workspace from the invitation.
- [x] 2.4 Modify `InvitationRegistrationGatewayAdapter.kt`, `PlatformAdminBootstrapConfiguration.kt`, and the platformadmin Problem Details advice to wire the shared transaction and approved failure mapping without exposing raw token/email.

## Phase 3: Observability and Contract Verification

- [ ] 3.1 Create `platformadmin/domain/InvitationAccepted.kt`; modify `InvitationTelemetry.kt` and `InvitationObservability.kt` for bounded accepted/expired/replay-rejected counters and redacted post-commit audit/domain signals.
- [ ] 3.2 Make the RED integration/BDD/E2E tests pass, then run focused JUnit/Vitest, `just backend-bdd-fast`, `just backend-test-postgres`, app type-check/lint/tests, and relevant Playwright E2E.
- [ ] 3.3 Inspect the final diff and contracts for only DALLAY-567; exclude DALLAY-569, DALLAY-574, DALLAY-576, public SaaS/IAM, and unrelated policy/configuration work.
