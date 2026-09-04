# Tasks: Integrate invitation creation with notification delivery

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 450-550 |
| 400-line budget risk | Medium |
| Chained PRs recommended | Yes |
| Suggested split | PR 1: Domain types + parallel consumer → PR 2: Remove old coupling + tests |
| Delivery strategy | ask-on-risk |
| Chain strategy | single-pr |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: single-pr
Decision: Proceeded with one reviewable work unit because the existing producer and consumer must change together for a coherent event contract; Phase 3 removals remain out of scope.
400-line budget risk: Medium

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Add InvitationIssued event, InvitationEmail message, template ID, and parallel @TransactionalEventListener consumer | PR 1 | Non-breaking addition; base branch: main; includes integration tests for post-commit guarantee |
| 2 | Remove deliveryStatus from domain/persistence, delete InvitationDeliveryAttempted, update all tests | PR 2 | Breaking changes; depends on PR 1; base branch: PR 1 branch |

## Phase 1: Foundation — New Domain Types

- [x] 1.1 Create `InvitationIssued` domain event in platform-admin context
  - File: `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/domain/event/InvitationIssued.kt`
  - Properties: `invitationId: UUID`, `recipientEmail: String`, `workspaceName: String`, `locale: String?`
  - Implements `DomainEvent`
  - No rawToken, no acceptUrl
  - Done: Event class compiles, has correct properties

- [x] 1.2 Create `InvitationEmailTemplateId` enum value in notification context
  - File: `server/smp/src/main/kotlin/com/profiletailors/smp/notifications/domain/NotificationTemplateId.kt`
  - Add `INVITATION` to existing enum
  - Done: Enum compiles, INVITATION value exists

- [x] 1.3 Create `InvitationEmail` typed message in notification context
  - File: `server/smp/src/main/kotlin/com/profiletailors/smp/notifications/domain/email/InvitationEmail.kt`
  - Implements `TypedEmailMessage<InvitationEmailTemplateId>`
  - Properties: `invitationId: UUID`, `recipientEmail: String`, `workspaceName: String`, `locale: String?`, `expiresAt: Instant`, `acceptUrl: String`
  - Done: Class compiles, implements interface correctly

## Phase 2: Parallel Consumer Implementation

- [x] 2.1 Create parallel @TransactionalEventListener consumer
  - File: `server/smp/src/main/kotlin/com/profiletailors/smp/notifications/application/listener/InvitationIssuedListener.kt` (new)
  - Annotate with `@TransactionalEventListener(phase = AFTER_COMMIT)`
  - Handle `InvitationIssued` event
  - Inject `AcceptUrlTemplate` to reconstruct acceptUrl from invitationId
  - Create `InvitationEmail` message
  - Call `emailService.send()` with idempotency key `invitation:{invitationId}:initial`
  - Log on success/failure
  - Done: Listener compiles, has correct annotations, implements idempotency

- [x] 2.2 Update `InviteWaitlistEntryHandler` to publish `InvitationIssued`
  - File: `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/handler/InviteWaitlistEntryHandler.kt`
  - Keep existing `InvitationCreated` event publish
  - Add parallel publish of `InvitationIssued` event
  - Ensure rawToken not included in `InvitationIssued`
  - Done: Handler publishes both events, compiles, existing tests pass

- [x] 2.3 Write unit test for `InvitationIssuedListener`
  - File: `server/smp/src/test/kotlin/com/profiletailors/smp/notifications/application/listener/InvitationIssuedListenerTest.kt` (new)
  - Test: listener receives event and calls emailService with correct InvitationEmail
  - Test: acceptUrl is reconstructed from invitationId
  - Test: idempotency key is `invitation:{invitationId}:initial`
  - Mock emailService, verify interactions
  - Done: Tests pass, verify correct message construction

- [x] 2.4 Write integration test for post-commit guarantee
  - File: `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/InvitationPostCommitIntegrationTest.kt` (new)
  - Use `@SpringBootTest` with test transaction
  - Test: InvitationIssued is published only after transaction commit
  - Test: Listener receives event after commit completes
  - Verify event not published if transaction rolls back
  - Done: Integration test passes, confirms AFTER_COMMIT behavior

- [x] 2.5 Verify parallel consumer works in local dev environment
  - Start application locally
  - Trigger invitation creation via admin endpoint
  - Verify both old and new consumers execute
  - Check logs for InvitationIssuedListener execution
  - Verify email sent through new path
  - Done: Manual verification complete, both paths work

## Phase 3: Remove Old Coupling

- [ ] 3.1 Remove `deliveryStatus` field from `WaitlistInvitation` domain model
  - File: `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/domain/model/WaitlistInvitation.kt`
  - Delete `deliveryStatus` property
  - Remove `InvitationDeliveryStatus` import
  - Update constructor, factory methods
  - Done: Domain model compiles without deliveryStatus

- [ ] 3.2 Remove `deliveryStatus` from `WaitlistInvitationEntity` persistence model
  - File: `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/persistence/r2dbc/entity/WaitlistInvitationEntity.kt`
  - Delete `deliveryStatus` column property
  - Update mapper to/from domain model
  - Done: Entity compiles, mapper works without deliveryStatus

- [ ] 3.3 Create database migration to drop delivery_status column
  - File: `server/smp/src/main/resources/db/migration/V<next>__drop_invitation_delivery_status.sql` (new)
  - `ALTER TABLE waitlist_invitation DROP COLUMN delivery_status;`
  - Test migration up/down
  - Done: Migration compiles, runs successfully in test environment

- [ ] 3.4 Delete `InvitationDeliveryAttempted` event
  - File: `server/smp/src/main/kotlin/com/profiletailors/smp/notifications/domain/event/InvitationDeliveryAttempted.kt`
  - Delete entire file
  - Remove from event publisher calls
  - Done: File deleted, no references remain

- [ ] 3.5 Delete `SendInvitationEmailConsumer` old consumer
  - File: `server/smp/src/main/kotlin/com/profiletailors/smp/notifications/application/consumer/SendInvitationEmailConsumer.kt`
  - Delete entire file
  - Remove from Spring component scan
  - Done: File deleted, application compiles

- [ ] 3.6 Update `InviteWaitlistEntryHandler` to publish only `InvitationIssued`
  - File: `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/handler/InviteWaitlistEntryHandler.kt`
  - Remove `InvitationCreated` event publish
  - Keep only `InvitationIssued` event publish
  - Remove deliveryStatus from invitation creation
  - Done: Handler publishes only new event, compiles

## Phase 4: Test Updates

- [ ] 4.1 Update `InviteWaitlistEntryHandlerTest` unit tests
  - File: `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/application/handler/InviteWaitlistEntryHandlerTest.kt`
  - Update: verify `InvitationIssued` published (not `InvitationCreated`)
  - Update: remove deliveryStatus assertions
  - Update: verify event contains invitationId, recipientEmail, workspaceName, locale
  - Update: verify event does NOT contain rawToken
  - Done: All unit tests pass with new event structure

- [ ] 4.2 Update `WaitlistInvitationTest` domain model tests
  - File: `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/domain/model/WaitlistInvitationTest.kt`
  - Remove: deliveryStatus field tests
  - Update: factory method tests without deliveryStatus
  - Done: Domain model tests pass without deliveryStatus

- [ ] 4.3 Update `WaitlistInvitationEntityTest` persistence tests
  - File: `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/infrastructure/persistence/r2dbc/entity/WaitlistInvitationEntityTest.kt`
  - Remove: deliveryStatus mapping tests
  - Update: verify entity maps correctly without deliveryStatus
  - Done: Entity mapping tests pass

- [ ] 4.4 Update `AdminInvitationControllerTest` controller tests
  - File: `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/infrastructure/http/AdminInvitationControllerTest.kt`
  - Update: remove deliveryStatus from response assertions
  - Update: verify correct event published
  - Done: Controller tests pass

- [ ] 4.5 Update BDD scenarios for invitation lifecycle
  - File: `server/smp/src/test/resources/features/platformadmin-invitation.feature`
  - Update: remove deliveryStatus expectations from scenarios
  - Update: focus on invitation lifecycle states (ACTIVE/ACCEPTED/EXPIRED/REVOKED)
  - Update: add scenario verifying event published after commit
  - Done: BDD scenarios pass, reflect new architecture

- [ ] 4.6 Delete tests for removed components
  - Delete: `server/smp/src/test/kotlin/com/profiletailors/smp/notifications/application/consumer/SendInvitationEmailConsumerTest.kt`
  - Delete: any tests referencing `InvitationDeliveryAttempted`
  - Done: No orphaned test files remain

## Phase 5: Integration Verification

- [ ] 5.1 Run full backend test suite
  - Execute: `just backend-test`
  - Verify: all domain, application, infrastructure tests pass
  - Verify: no compilation errors
  - Done: Test suite green

- [ ] 5.2 Run backend BDD suite
  - Execute: `just backend-bdd-fast`
  - Verify: invitation BDD scenarios pass
  - Verify: no regression in notification scenarios
  - Done: BDD suite green

- [ ] 5.3 Run PostgreSQL integration tests
  - Execute: `just infra-up && just backend-test-postgres`
  - Verify: migration applies successfully
  - Verify: persistence layer works without deliveryStatus
  - Done: PostgreSQL tests pass

- [ ] 5.4 Verify architecture rules still pass
  - Execute: `HexagonalArchTest` and `ComponentScanArchTest`
  - Verify: no new violations introduced
  - Verify: event listener respects transaction boundaries
  - Done: Architecture tests pass

- [ ] 5.5 Manual end-to-end verification
  - Start local environment with `just dev-backend`
  - Create invitation via admin API
  - Verify: invitation created with ACTIVE status
  - Verify: no deliveryStatus field in database
  - Verify: InvitationIssuedListener executes after commit
  - Verify: email sent with correct acceptUrl
  - Verify: idempotency prevents duplicate emails
  - Done: E2E flow works correctly

## Phase 6: Documentation

- [ ] 6.1 Update ADR if architectural decision warrants it
  - Review: does this change establish new cross-cutting architecture rule?
  - If yes: create ADR documenting event-driven context integration pattern
  - If no: skip ADR, document in OpenSpec only
  - Done: ADR decision made and documented if needed

- [ ] 6.2 Update API documentation if needed
  - Review: does invitation response schema change?
  - If yes: update OpenAPI annotations
  - If no: skip
  - Done: API docs reflect current state

- [ ] 6.3 Update integration spec with actual implementation notes
  - File: `openspec/changes/dallay-565-invitation-notification-integration/specs/integration.md`
  - Add: implementation notes section
  - Add: actual idempotency key format used
  - Add: actual listener class name and package
  - Done: Spec updated with implementation reality
