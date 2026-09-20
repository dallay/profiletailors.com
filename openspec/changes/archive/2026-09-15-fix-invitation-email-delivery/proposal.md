# Proposal: Reliable Direct Invitation Email Delivery

## Intent

v0.5.1 persisted direct invitations, but neither created a notification nor reached Resend. Restore
delivery without coupling persistence to provider failure.

## Scope

### In Scope

- Create/resend use reactive transactions; dispatch only after commit, never before or after
  rollback.
- Existing uses resolved `workspaces.name`, never ID. New uses: “You’ve been invited to create a new
  Profile Tailors workspace.”
- Create uses `invitation:{id}:initial`; resend uses `invitation:{id}:resend:{deliveryId}`. Replay
  is
  deduped; intentional resends differ.
- Keep the raw-token handler→event→consumer handoff temporarily; document its conflict with
  canonical
  `invitations` security requirements and assign token-safe follow-up to DALLAY-566.

### Out of Scope

- UI listing/navigation, global UUID migration, crash-durable outbox, DALLAY-567 QA files, and
  provider
  guarantees beyond `PENDING`/`SENT`/`FAILED`.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `invitations`: direct create/resend post-commit and target-aware delivery contract.
- `email-notifications`: reactive wiring, workspace copy, and resend idempotency.

## Approach

Choose reactive-aware publication over explicit after-commit publication: use Spring’s
`TransactionalEventPublisher`, `AFTER_COMMIT`, and `TransactionalOperator`. This preserves the seam
and
avoids a commit-to-publish gap; explicit publication is easier to test but moves transaction
ownership
and adds a failure window. Resolve names through a narrow tenancy read port.

## Test Strategy

Add a real R2DBC wiring regression: real publisher and transactions, create/resend × targets; no
listener/row/provider call before commit, one after commit, none after rollback; replay, distinct
resend,
provider failure.

## ADR/Docs Impact

Update the transaction policy/ADR and change specs/design; record copy choice and token
contradiction /
follow-up. Do not edit DALLAY-567.

## Affected Areas

| Area                                      | Impact   | Description                 |
|-------------------------------------------|----------|-----------------------------|
| `platformadmin` handlers/publisher        | Modified | Reactive events and labels. |
| `notifications` consumer/email/repository | Modified | Dispatch and resend keys.   |
| `tenancy`, tests, transaction docs        | Modified | Name port and wiring proof. |

## Risks

| Risk                         | Likelihood | Mitigation                              |
|------------------------------|------------|-----------------------------------------|
| Reactive context absent      | Med        | Commit/rollback test; no fallback.      |
| Tenancy lookup coupling      | Med        | Narrow read port; no aggregate/UI.      |
| Token contradiction persists | High       | No new path; name DALLAY-566 follow-up. |

## Rollback Plan

Revert code-only wiring, label, and key changes; retain existing rows. No migration or outbox
rollback.

## Dependencies

- `TransactionalOperator`, Spring reactive events, Resend, DALLAY-568, and DALLAY-566 token-safe
  follow-up.

## Acceptance Criteria

- Both targets: committed create/resend produces one notification/provider attempt; pre-commit and
  rollback produce neither.
- Event replay sends once; separate resend sends once with a distinct key.
- Email has a human label; provider failure leaves the invitation persisted and notification
  `FAILED`.

## Success Criteria

- [ ] Regression and docs prove the publication trade-off and token follow-up without DALLAY-567
  changes.

## Next Phase

Run `sdd-spec` and `sdd-design` in parallel.
