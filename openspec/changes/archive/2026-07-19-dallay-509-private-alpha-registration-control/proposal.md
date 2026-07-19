# Proposal: Private Alpha Registration Control

## Intent

Prevent accidental public account creation during Private Alpha while preserving login and refresh for existing users. The backend remains authoritative; the SPA only reflects availability.

## Scope

### In Scope
- Add a typed, non-secret, environment-backed registration switch with secure default `false`.
- Reject disabled registration before command dispatch or mutations.
- Expose one allow-listed public capability and adapt registration UI/routes.
- Add TDD coverage and document environment configuration.

### Out of Scope
- Login, refresh, logout, verification, or existing-user behavior changes.
- Invitations, waitlists, allowlists, admin toggles, or generic configuration APIs.

## Capabilities

### New Capabilities
- `public-application-capabilities`: Minimal unauthenticated runtime capability contract exposing only registration availability.

### Modified Capabilities
- `registration`: Registration becomes environment-gated while preserving enabled-mode behavior and atomicity.
- `iam`: The registration proving-slice endpoint may deny account creation without affecting existing authentication.

## Approach

Bind a typed backend boolean from environment configuration; default it to `false`, requiring explicit opt-in in environments that permit registration. This fail-safe default may add local/test setup, but avoids silently opening production when configuration is missing.

Before mediator dispatch, disabled `POST /api/auth/register` returns `403 Forbidden` with `application/problem+json`: `type: "/problems/registration-disabled"`, `title: "Registration disabled"`, `status: 403`, a non-sensitive `detail`, and extension `code: "registration_disabled"`. `403` expresses an intentional policy denial; `503` was rejected because registration is not transiently unhealthy and retry guidance would be misleading.

Expose `GET /api/capabilities/public` returning only `{ "registrationEnabled": boolean }`. The SPA hides registration entry points and fails closed for registration when capability loading fails, while login remains usable. Implementation follows test-first TDD.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `server/smp/.../identity` | Modified | Typed switch, pre-dispatch gate, Problem Details, tests |
| `server/smp/src/main/resources/application.yaml` | Modified | Safe default and environment binding |
| `apps/web/app/src/modules/auth`, `router` | Modified | Capability client and closed-registration UX |
| `.env.example` | Modified | Non-secret operator documentation |
| `openspec/specs/registration`, `iam` | Modified | Availability requirements |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Missing override closes intended registration | Med | Document explicit per-environment opt-in |
| Capability failure blocks login | Low | Isolate loading; fail closed only for registration |
| Future path bypasses controller gate | Med | Design policy placement and test zero dispatch/mutations |

## Rollback Plan

Revert backend/frontend changes and remove the capability endpoint/property. During operational rollback, explicitly enable registration only if restoring public signup is intended.

## Dependencies

- Environment configuration support already present; no new library dependency.

## Success Criteria

- [ ] Disabled requests return the specified Problem Details and cause zero dispatch/mutations.
- [ ] Enabled registration still returns `201`; existing login and refresh remain functional.
- [ ] Public capability exposes only registration availability; SPA mirrors it and preserves login.
- [ ] Tests are written failing-first and cover both states plus capability-fetch failure.
