# Proposal: Integrate invitation creation with notification delivery

## Intent

Establish a clean architectural boundary between the Invitation lifecycle (ACTIVE/ACCEPTED/EXPIRED/REVOKED) and the Notification delivery lifecycle (PENDING/SENT/FAILED) by refactoring the existing partial integration that currently violates separation of concerns.

The current implementation mixes Invitation and Notification concerns:
- `WaitlistInvitation` has a `deliveryStatus` field that tracks notification state (architectural violation)
- `InvitationCreated` event contains the raw bearer token in its payload (security risk)
- Event publishing happens synchronously within the `@Transactional` boundary without post-commit guarantee (reliability risk)
- `SendInvitationEmailConsumer` updates invitation's `deliveryStatus` (cross-context coupling)

This change must establish the correct seam: Invitation issues a domain event when created; Notification infrastructure consumes that event to create and dispatch the email; each context maintains its own lifecycle state independently.

## Scope

### In Scope

1. **Replace `InvitationCreated` event with `InvitationIssued` event**
   - Remove `rawToken` from event payload entirely
   - Event contains invitation ID, recipient email, workspace name, locale only
   - Accept URL is reconstructed from invitation ID, not passed through event
   - Event name reflects domain action (issued) not persistence detail (created)

2. **Remove `deliveryStatus` from `WaitlistInvitation` entity**
   - Invitation lifecycle is independent: ACTIVE → ACCEPTED/EXPIRED/REVOKED
   - Delivery state belongs exclusively to Notification context
   - Platform-admin context cannot query notification delivery status (intentional separation)

3. **Ensure post-commit event publishing**
   - Evaluate current `SpringDomainEventPublisher` implementation
   - Add `@TransactionalEventListener(phase = AFTER_COMMIT)` if not already guaranteed
   - Document transaction boundary decision in design phase

4. **Update `SendInvitationEmailConsumer` to consume `InvitationIssued`**
   - Remove invitation `deliveryStatus` update logic (no longer exists)
   - Maintain notification lifecycle (PENDING → SENT/FAILED) within Notification context
   - Idempotency key remains `invitation:{invitationId}:initial`

5. **Remove `InvitationDeliveryAttempted` event and its consumer**
   - Event exists only to update invitation `deliveryStatus` (which no longer exists)
   - Cross-context notification → invitation coupling is eliminated
   - Notification context owns delivery state; invitation context does not observe it

6. **Update `InviteWaitlistEntryHandler`**
   - Remove `deliveryStatus` from invitation creation
   - Publish `InvitationIssued` instead of `InvitationCreated`
   - Token handling remains: generate plaintext, hash for persistence, pass plaintext only to accept URL builder (not event payload)

7. **Update admin invitation retrieval/listing**
   - Remove `deliveryStatus` from DTOs and OpenAPI schemas
   - Admin context sees invitation lifecycle only (ACTIVE/ACCEPTED/EXPIRED/REVOKED)
   - No delivery status exposed (future: DALLAY-574 may add delivery observability if needed)

8. **Update tests**
   - Unit tests: verify `InvitationIssued` event payload excludes token
   - Integration tests: verify post-commit event delivery
   - BDD scenarios: verify invitation creation triggers notification without coupling

### Out of Scope

- **Admin resend operations** (DALLAY-574, blocked by this change)
  - Resend semantics (new delivery capability vs. retry existing one)
  - Admin UI for resend/retry
  - Resend generates new invitation or reuses existing invitation decision
  
- **Delivery status observability for admins** (DALLAY-574)
  - Whether admins should see notification delivery status at all
  - If yes, how to query across context boundary without coupling
  
- **Retry mechanisms** (future work)
  - Automatic retry for transient failures
  - Exponential backoff
  - Dead letter queue
  
- **Token rotation or revocation on delivery failure** (future)
  - Current: token remains valid even if email fails
  - Future: may want to regenerate token on resend
  
- **Notification delivery audit trail** (future)
  - Full history of delivery attempts
  - Failure reasons and diagnostics
  
- **Multi-channel delivery** (future)
  - SMS, in-app notification, etc.
  - Current scope is email only

## Capabilities

> This section is the CONTRACT between proposal and specs phases.

### New Capabilities
None — this is a refactor that establishes correct boundaries without introducing new product behavior.

### Modified Capabilities
- `invitation-lifecycle`: Requirements change to remove delivery status tracking from Invitation aggregate. The invitation lifecycle (ACTIVE → ACCEPTED/EXPIRED/REVOKED) remains unchanged, but delivery status is no longer part of Invitation state.
- `notification-delivery`: Requirements change to clarify that Notification context is the sole owner of delivery lifecycle (PENDING → SENT/FAILED), with no cross-context coupling back to Invitation.

## Approach

### High-Level Steps

1. **Analyze transaction boundary** (design phase decision)
   - Inspect `SpringDomainEventPublisher` implementation
   - Verify whether `@EventListener` consumers run within transaction or post-commit
   - Document decision: keep current synchronous model or add `@TransactionalEventListener`

2. **Refactor domain event**
   - Create `InvitationIssued` event without `rawToken` field
   - Update event serialization tests to verify token exclusion
   - Preserve `InvitationCreated` temporarily for backward compatibility (remove after migration)

3. **Remove delivery status from Invitation**
   - Delete `deliveryStatus` field from `WaitlistInvitation` entity
   - Remove `InvitationDeliveryStatus` enum if no other usages
   - Update repository, tests, and DTOs

4. **Update event consumer**
   - Modify `SendInvitationEmailConsumer` to listen for `InvitationIssued`
   - Remove logic that updates invitation `deliveryStatus`
   - Verify idempotency and notification lifecycle remain intact

5. **Remove reverse coupling**
   - Delete `UpdateInvitationDeliveryOnNotificationAttempted` consumer
   - Delete `InvitationDeliveryAttempted` event
   - Verify no other consumers depend on this event

6. **Update invitation creation handler**
   - Modify `InviteWaitlistEntryHandler` to publish `InvitationIssued`
   - Remove `deliveryStatus = PENDING` from invitation creation

7. **Update admin API**
   - Remove `deliveryStatus` from DTOs
   - Update OpenAPI schemas
   - Update BDD scenarios

8. **Cleanup**
   - Remove `InvitationCreated` event (after migration complete)
   - Update architecture tests to verify separation

### Token Handling (Security Critical)

**Current flow (correct, preserve as-is):**
1. Generate plaintext token
2. Hash token with secure algorithm
3. Persist hash in `WaitlistInvitation.tokenHash`
4. Build accept URL with plaintext token
5. **Never** put plaintext token in event payload, notification payload, logs, or audit

**Refactored flow:**
- Step 5 becomes: **Never** put plaintext token in `InvitationIssued` event payload
- Accept URL reconstruction: notification consumer rebuilds URL from invitation ID (fetches via repository) or receives pre-built URL without token exposure in event

**Decision needed in design phase:**
- Option A: Pass accept URL in event (plaintext token embedded in URL string, but not as separate field)
- Option B: Pass only invitation ID; consumer fetches invitation and rebuilds URL
- User guidance: "Raw token must NEVER be in event payload" suggests Option B is safer

### Idempotency

Idempotency key for notification creation: `invitation:{invitationId}:initial`

**Why not `invitation:{email}`?**
- One user can have multiple invitations (e.g., waitlist + direct invite)
- Email-based key would block second invitation incorrectly
- Invitation ID is the unique identity for this specific issuance

**Resend semantics (out of scope but documented for context):**
- Resend is NOT retry: resend generates a new delivery capability
- Current idempotency key prevents duplicate initial sends only
- Future resend (DALLAY-574) would use a different key: `invitation:{invitationId}:resend:{timestamp}`

### Post-Commit Guarantee

**Critical decision for design phase:**

Current `SpringDomainEventPublisher` implementation must be analyzed:
- Does `ApplicationEventPublisher.publishEvent()` run synchronously within transaction?
- If yes, consumer failure rolls back invitation save (unacceptable)
- If no, post-commit is already guaranteed (acceptable)

**Options:**
1. **Keep synchronous model** — if failure rollback is acceptable (it's not for this use case)
2. **Add `@TransactionalEventListener(phase = AFTER_COMMIT)`** — Spring-native, simple, no new infrastructure
3. **Introduce transactional outbox pattern** — durable, reliable, but adds complexity (overkill for current needs)

**Recommendation:** Option 2 unless analysis shows post-commit is already guaranteed.

**Why post-commit matters:**
- Invitation save must commit before notification dispatches
- Email delivery is external I/O; failure should not prevent invitation creation
- Invitation is usable immediately after creation; notification is best-effort delivery

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/domain/model/WaitlistInvitation.kt` | Modified | Remove `deliveryStatus` field |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/handler/InviteWaitlistEntryHandler.kt` | Modified | Publish `InvitationIssued` instead of `InvitationCreated`; remove `deliveryStatus` from creation |
| `shared/notifications/src/main/kotlin/com/profiletailors/notifications/domain/event/InvitationIssued.kt` | New | Domain event without token in payload |
| `shared/notifications/src/main/kotlin/com/profiletailors/notifications/domain/event/InvitationCreated.kt` | Removed | Replaced by `InvitationIssued` |
| `server/smp/src/main/kotlin/com/profiletailors/smp/notifications/infrastructure/email/SendInvitationEmailConsumer.kt` | Modified | Consume `InvitationIssued`; remove invitation update logic |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/events/UpdateInvitationDeliveryOnNotificationAttempted.kt` | Removed | Cross-context coupling eliminated |
| `shared/notifications/src/main/kotlin/com/profiletailors/notifications/domain/event/InvitationDeliveryAttempted.kt` | Removed | No longer needed |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/http/dto/*` | Modified | Remove `deliveryStatus` from DTOs |
| `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/**/*Test.kt` | Modified | Update tests for removed field and new event |
| `server/smp/src/test/resources/features/platform-admin-invitation.feature` | Modified | Update BDD scenarios |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Token exposure if URL passed in event | Medium | Design phase must decide: pass URL or pass invitation ID only; document security rationale |
| Event consumer failure still within transaction | Medium | Verify transaction boundary in design phase; add `@TransactionalEventListener` if needed |
| Breaking change for admin API consumers | Low | `deliveryStatus` field removal is breaking; version API or document migration |
| Future resend requirements not anticipated | Medium | Document resend semantics clearly as out of scope; DALLAY-574 will address |
| Notification delivery failure invisible to admins | High | Accepted trade-off: clean architecture > immediate observability; DALLAY-574 can add observability later if needed |

## Rollback Plan

This change modifies database schema (removes `deliveryStatus` column) and event contracts (replaces `InvitationCreated` with `InvitationIssued`). Rollback is non-trivial.

**Backward-compatible migration approach:**

1. **Phase 1 (this change):**
   - Add `InvitationIssued` event alongside `InvitationCreated` (both published)
   - `SendInvitationEmailConsumer` listens to both events (whichever arrives first wins via idempotency)
   - `deliveryStatus` field deprecated but not removed (nullable, not written, not read)
   - Deploy to production, verify notification delivery continues

2. **Phase 2 (follow-up PR):**
   - Stop publishing `InvitationCreated`
   - Remove `InvitationCreated` event listener from consumer
   - Remove `deliveryStatus` field from schema (migration drops column)
   - Remove `InvitationDeliveryAttempted` event and consumer
   - Deploy to production

**Rollback:**
- If Phase 1 breaks: revert code deploy; database schema unchanged; rollback is clean
- If Phase 2 breaks: revert code deploy; database schema change requires migration rollback (re-add column as nullable)

**Alternative (aggressive, not recommended):**
- Single-phase deploy with all changes
- Rollback requires database migration rollback (complex)
- Higher risk

## Dependencies

- **DALLAY-564** (defines Invitation model) — already complete; this change refactors that model
- **DALLAY-574** (admin operations: resend, delivery observability) — blocked by this change; cannot implement until seam is clean

## Success Criteria

### Definition of Done

- [ ] `InvitationIssued` domain event created without `rawToken` field
- [ ] `deliveryStatus` field removed from `WaitlistInvitation` entity
- [ ] `InvitationCreated` event replaced or deprecated
- [ ] `SendInvitationEmailConsumer` updated to consume `InvitationIssued` and no longer updates invitation state
- [ ] `InvitationDeliveryAttempted` event and its consumer removed
- [ ] Post-commit event publishing verified (transaction boundary analysis complete)
- [ ] Admin API DTOs updated to remove `deliveryStatus`
- [ ] OpenAPI schemas updated
- [ ] Unit tests verify `InvitationIssued` event serialization excludes token
- [ ] Integration tests verify invitation creation triggers notification without cross-context coupling
- [ ] BDD scenarios updated and passing
- [ ] Architecture tests verify Invitation and Notification contexts are decoupled
- [ ] Documentation updated: ADR or design doc explains separation of concerns decision

### Acceptance Criteria

**Given** a platform admin invites a waitlist entry  
**When** the invitation is created  
**Then** the invitation entity has no `deliveryStatus` field  
**And** an `InvitationIssued` event is published without the raw token  
**And** the event is published after the transaction commits  
**And** a notification is created with `PENDING` status  
**And** the email is dispatched  
**And** the notification status is updated to `SENT` or `FAILED`  
**And** the invitation status remains `ACTIVE` regardless of delivery outcome  

**Given** a notification delivery fails  
**When** the failure is recorded  
**Then** the invitation is not updated  
**And** the invitation remains usable (token still valid)  

**Given** an admin retrieves an invitation  
**When** the API response is returned  
**Then** no `deliveryStatus` field is present  
**And** only invitation lifecycle status is visible (ACTIVE/ACCEPTED/EXPIRED/REVOKED)  
