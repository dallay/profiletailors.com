# Proposal: Back Office User Administration (#668)

## Intent

Give the platform operator operational visibility and narrowly scoped account controls. Extend the existing read-only admin surface without broad user editing, while ensuring disabled users cannot log in or refresh sessions.

## Scope

### In Scope
- Define user list/detail behavior with account, verification, registration, and workspace data.
- Add `POST` commands for `disable`, `enable`, and `sessions/revoke`, plus admin controls.
- Store state in Identity; enforce it in login/refresh; revoke active refresh sessions on disable.
- Add authorization, audit events, counters, and unit/API/integration/security/BDD/UI coverage.

### Out of Scope
- Email changes, deletion, ownership transfer, impersonation, or manual verification.
- Public/self-service controls, broad profile editing, or a separate admin data model.
- Workspace authorization changes or the previous `workspaceId` 404 decision.
- New third-party dependencies.

## Capabilities

### New Capabilities
- `user-administration`: user queries and account-control commands/UI.

### Modified Capabilities
- `iam`: administrative account state and login/refresh enforcement.
- `admin-authorization`: explicit control permission with default deny.
- `platform-admin-audit`: user-control actions and outcomes.

## Approach

Preserve `domain <- application <- infrastructure`: Identity owns state, Credentials owns revocation, and `platformadmin` orchestrates through ports/routes. Reuse existing query, authorization, refresh, and redacted-audit seams; never issue cross-context SQL from `platformadmin`. Make disable fail closed and commands retry-safe.

## Contract Decisions, Defaults, and Questions

- **Defaults:** `ACTIVE`/`DISABLED` for `USER`; one `platform.users.manage` permission for controls, granted only to `PLATFORM_OWNER`/`PLATFORM_OPERATOR`; exact-email search; idempotent commands; revoke returns a count.
- **Auth default:** disable blocks login/refresh; bearer tokens expire normally, avoiding per-request identity lookups.
- **Resolve in spec/design:** response/error bodies, bearer invalidation, failure ordering/retry semantics, and metric names/ownership. Defaults are conservative and reversible.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `server/smp/.../identity`, `credentials` | Modified | Migration, state enforcement, session revocation. |
| `server/smp/.../platformadmin` | Modified | Commands, permission, routes, audit actions. |
| `apps/web/admin` | Modified | State display and controls. |
| Backend/frontend tests and BDD | Modified | Contract and security evidence. |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Disable/revocation diverge | High | Fail-closed state, ports, retry/idempotency tests. |
| Credential path bypasses state | Medium | Cover login, refresh, bearer, and BDD security scenarios. |
| Privilege/cardinality expands | Medium | Dedicated permission and fixed labels. |

## Rollback Plan

Revert application/UI changes and migration together. If rollback is unsafe, retain the state column and disable enforcement through a reviewed forward migration.

## Dependencies

- #659 administrative authorization boundary (closed baseline).
- #657 administrative audit infrastructure (closed baseline).

## Success Criteria

- [ ] Admins query users and see accurate account, verification, registration, and workspace data.
- [ ] Authorized controls work; unauthorized requests are denied and observable.
- [ ] Disabled users cannot log in or refresh; active refresh sessions are revoked.
- [ ] Applicable tests and quality gates pass without changing the `workspaceId` 404 contract.
