# Proposal: Initial Platform Admin Bootstrap

## Intent

Establish the first `PLATFORM_OWNER` without customer/workspace access. Require current Terms acceptance. This is a product choice, not a legal-compliance assessment.

## Scope

### In Scope
- Use an explicitly invoked interactive in-container CLI. No password args/env. Print link once to terminal; no bootstrap email; retain verification email.
- Issue a hashed, one-use invitation with 30-minute expiry. Require explicit, transactional, concurrency-safe replacement. Fail closed if an owner exists or status is uncertain.
- Require current Terms acceptance and email verification before owner grant. Store the version as `PLATFORM` consent, without workspace/membership. Require no bootstrap age check; preserve customer-registration behavior.
- Keep status private; add recipient-only `/bootstrap`. Set per-installation admin origin/CORS; require no fixed domain.

### Out of Scope
- Public issuance/status APIs, readiness changes, workspace invitation/customer-registration changes, and live deployment or CORS verification. The public recipient completion and Terms-metadata endpoints are in scope.

## Capabilities

### New Capabilities
- `platform-admin-bootstrap`: Initial invitation, Terms acceptance, verified grant, and security.

### Modified Capabilities
- `invitations`: Add bootstrap semantics; preserve workspace invitations.
- `consent-scopes`: Add `PLATFORM` scope, requiring schema, persistence, and architecture work.

## Approach

Add a distinct workspace-free completion path and record current Terms acceptance as platform consent. Extend scope invariants, schema, persistence, and architecture checks while preserving workspace isolation. Reuse existing token and verification primitives. Serialize owner checks and replacement; keep issuance CLI-only and origin installation-configured.

## Affected Areas

| Area | Impact | Description |
|---|---|---|
| Consent specifications | Modified | `PLATFORM` scope; bootstrap terms acceptance without workspace |
| Consent schema/persistence/architecture tests | Modified | Platform consent without tenant records |
| `server/smp/.../platformadmin/`, `identity/` | Modified | CLI, bootstrap, verification, grant |
| `apps/web/admin/` | Modified | Recipient-only `/bootstrap` |
| Installation configuration | Operator action | Admin origin and matching CORS |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Scope extension risks isolation or migration | Medium | Constrain, migrate, and test; retain workspace restrictions |
| Verification and grant cross contexts | Medium | Define and test atomic or recoverable behavior |

## Rollback Plan

Disable bootstrap issuance and completion; preserve consent evidence. Revert schema only if no `PLATFORM` records depend on it; otherwise retain it pending a data-preserving migration.

## Dependencies

- Current Terms version and existing verification flow.
- Per-installation admin origin and matching CORS.

## Success Criteria

- [ ] Current Terms consent is `PLATFORM`-scoped; no workspace or membership is created before owner grant.
- [ ] No bootstrap age check; customer-registration behavior is unchanged.
- [ ] Link is terminal-only; no invitation email; verification email unchanged.
- [ ] Tests cover owner checks, replacement, schema, isolation, audit secrecy, and failures.
