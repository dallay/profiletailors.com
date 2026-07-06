# Proposal: Email Verification Reliability

## Intent

Fix broken verification-email dispatch and make unverified-account restrictions visible and
authoritative end to end.

## Scope

### In Scope

- Restore reliable `UserRegistered`/resend email dispatch by loading the shared event wiring used by
  verification consumers.
- Make backend email status authoritative for the SPA via `/api/auth/me` and aligned auth-store
  handling.
- Add app-shell level unverified-user guidance, including banner/warning and clear resend/verify
  entry points.
- Decide and enforce whether media-library upload/create flows are blocked for unverified users,
  aligned with publishing and social-connect gating.

### Out of Scope

- Reworking SMTP providers, deliverability infrastructure, or adding new notification channels.
- Redesigning the entire auth UX beyond verification visibility and blocked-state guidance.

## Capabilities

### New Capabilities

- None

### Modified Capabilities

- `email-verification`: guarantee dispatch wiring, expose authoritative status, and clarify
  resend/visibility behavior.
- `email-notifications`: require SMP boot wiring to activate verification email consumers reliably.
- `app-shell`: surface unverified-account banner and verification actions globally for authenticated
  users.
- `media-library`: define and enforce email-verification policy for asset creation/upload access.

## Approach

Align the registration/resend flow with the shared event configuration so verification consumers are
actually subscribed. Extend the current-user profile contract to return server-truth `emailStatus`,
then have the SPA render a persistent app-shell warning with resend/verification affordances.
Preserve existing backend 403 `EMAIL_VERIFICATION_REQUIRED` semantics for publish/connect and
explicitly decide the same rule for media uploads.

## Affected Areas

| Area                                              | Impact   | Description                                                       |
|---------------------------------------------------|----------|-------------------------------------------------------------------|
| `server/smp` auth + event bootstrapping           | Modified | Fix consumer wiring and status propagation                        |
| `apps/web/app/src/stores/auth*`                   | Modified | Use profile-sourced email status                                  |
| `apps/web/app/src/components/layout/AppShell.vue` | Modified | Add global unverified banner/actions                              |
| `openspec/specs/*`                                | Modified | Update verification, notification, app-shell, media-library specs |

## Risks

| Risk                                           | Likelihood | Mitigation                                                            |
|------------------------------------------------|------------|-----------------------------------------------------------------------|
| Event wiring fix still misses one startup path | Med        | Add startup/integration coverage for registration and resend dispatch |
| UI and JWT/profile status drift                | Med        | Make `/api/auth/me` authoritative after bootstrap and refresh         |
| Media gating surprises users or product        | Med        | Decide policy explicitly in specs before implementation               |

## Rollback Plan

Revert the event-boot wiring, `/api/auth/me` contract changes, and app-shell banner together; keep
existing publish/connect guards intact while restoring prior SPA behavior.

## Dependencies

- Shared event configuration must remain the single source for `@Subscribe` consumer registration.
- Product approval on media-library verification gating.

## Success Criteria

- [ ] Registration and resend flows reliably trigger verification email dispatch in SMP.
- [ ] SPA derives `emailStatus` from backend profile truth, not token heuristics.
- [ ] Unverified users see a global banner with resend/verify guidance.
- [ ] Media upload policy for unverified users is explicitly specified and enforced consistently.
