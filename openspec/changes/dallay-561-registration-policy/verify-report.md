# Verification Report: DALLAY-561

## Status

**PASS WITH WARNINGS**

## Scope

Verified the registration policy implementation against the DALLAY-561 proposal, design, and
registration contract. The change remains active for user review and the later invitation-aware
registration slice.

## Evidence

- `RegistrationMode` represents `OPEN`, `INVITE_ONLY`, and `CLOSED` with typed decisions.
- `RegisterUserHandler` evaluates the policy before normalization, persistence, events, or session
  issuance.
- `INVITE_ONLY` maps to HTTP 403 with the stable
  `REGISTRATION_INVITATION_REQUIRED` code and performs no registration work.
- `CLOSED` preserves the existing unavailable-registration problem.
- Public capabilities keep the existing two-field response and set `registrationEnabled` only for
  `OPEN`.
- `SMP_REGISTRATION_MODE` is wired through application configuration and production Compose/Swarm
  examples with a fail-closed `CLOSED` default.

## Commands

- Focused registration tests — **PASS**.
- `just backend-check` — **PASS**; backend tests, Postgres integration tests, Spotless, Detekt, and
  Kover verification completed successfully.
- `just backend-bdd-fast` — **PASS**.
- `just backend-bdd-postgres` — **PASS**.
- `docker compose --env-file infra/apps/smp/production/.env.example -f infra/apps/smp/production/compose.yaml config --quiet` — **PASS**.
- `DASHBOARD_IMAGE=profiletailors/dashboard:0.1.0 SMP_IMAGE=profiletailors/smp:0.1.0 PUBLIC_ORIGIN=https://app.example.com just swarm-config` — **PASS**.
- `git diff --check` — **PASS**.

## Warnings

- `just production-config` cannot run in this checkout because the deployment-only
  `infra/apps/smp/production/.env` file is absent. Compose rendering was validated with the
  checked-in example values.
- `just swarm-config` requires deployment-only image and origin variables in this checkout. The
  rendered stack was validated with explicit non-secret values above.
- No remote CI, deployment, provider, operator, or live-user acceptance evidence is claimed.
- Direct invite-only registration intentionally remains rejected until DALLAY-567 supplies a
  server-validated invitation context.

## Recommendation

Keep the change active for review. The block is locally verified and ready to continue with the
invitation-aware registration flow without requiring the pending P0 deploy.
