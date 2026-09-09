# Proposal: DALLAY-567 — Accept Invitations in Registration

## Intent

Enable invite-only recipients to register with a server-validated invitation. Identity, credentials, workspace/membership, verification/consent, and invitation consumption must commit or roll back together, leaving no reusable invitation or partial account.

This is strictly DALLAY-567. It builds on, without repeating, merged foundations: DALLAY-561 registration modes; DALLAY-562 redacted audit persistence; DALLAY-563 admin authorization; DALLAY-564 invitation lifecycle/CAS/security; DALLAY-568 issuance/revoke/resend; DALLAY-570 targets/shared activation; and DALLAY-571 waitlist observability.

## Scope

### In Scope
- Validate token and lifecycle before mutation; safely reject invalid, expired, revoked, consumed, replayed, and email-mismatched attempts.
- Replace token-presence authorization, split identity paths, and unsafe workspace fallback with one transaction honoring `EXISTING_WORKSPACE` and `NEW_WORKSPACE`.
- Link the created principal to the invitation; emit acceptance audit/domain signals without raw tokens, URLs, or full email data.
- Prove rollback and concurrency: account plus consumption commit together or neither does; exactly one contender succeeds.
- Add aggregate acceptance, expiry, revoke, and replay-rejection metrics and focused registration evidence.

### Out of Scope
- DALLAY-556 work beyond this acceptance slice; keep it the only active Linear issue and do not mutate Linear.
- DALLAY-569, DALLAY-574, DALLAY-576, Back Office UI, broader waitlist conversion, issuance/delivery, public SaaS/IAM, billing, enterprise, compliance, SRE, or unrelated registration policy.

## Capabilities

### New Capabilities
- None. Use existing registration and invitation contracts.

### Modified Capabilities
- `invitations`: validated registration acceptance, principal linkage, atomic provisioning/consumption, safe failures, and observability.
- `e2e`: invite-only registration and safe invalid/replay outcomes.

## Approach

Keep identity as the registration entry point and `platformadmin` as invitation owner. Resolve a validated invitation context before mutation, then use the existing atomic runner/shared activation seam for identity, credentials, tenancy, consent/verification, membership, and consumption. Make workspace targets explicit; never use invitation ID or caller fallback. Reuse redaction and low-cardinality boundaries. Raw tokens remain in-process only for verification and never enter persistence, responses, audit, logs, or metrics.

## Affected Areas

| Area | Impact | Description |
|---|---|---|
| `server/smp/.../identity` | Modified | Invitation-aware registration and safe errors. |
| `server/smp/.../platformadmin` | Modified | Acceptance linkage, audit, telemetry, transaction seam. |
| `server/smp/src/test`, `apps/web/app/...` | Modified | Backend/frontend registration, rollback, race, and security evidence. |
| `openspec/specs/invitations`, `openspec/specs/e2e` | Modified | Contract deltas only. |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Transaction boundaries diverge | High | Failure-injection integration tests. |
| Concurrent duplicates | Med | Lock/CAS, uniqueness, two-client race. |
| Token/PII leakage | Med | Redaction, low-cardinality signals, security tests. |

## Rollback Plan

Disable invite-only registration with the existing fail-closed mode; leave invitations intact. Revert only this integration, telemetry, and contract delta—no destructive migration or foundation rollback.

## Dependencies

- Merged DALLAY-561/562/563/564/568/570/571 foundations and identity/tenancy transaction ports.
- Product decisions are resolved in this proposal: stable Problem Details taxonomy, authenticated matching existing-identity acceptance, and rejection of any client `workspaceId` input. Problem Details details remain generic and omit tokens, full emails, workspace values, and other sensitive data.

## Success Criteria

- [ ] Valid invite-only registration creates one linked principal, workspace/membership, verification/consent set, and consumed invitation.
- [ ] Invalid, expired, revoked, mismatched, and replayed attempts leave no account, membership, workspace, or reusable invitation.
- [ ] Failure-injection and two-client tests prove atomicity/exactly-once outcomes.
- [ ] Audit and aggregate metrics contain no raw token, URL, or full email.
