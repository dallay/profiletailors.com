# Design: Password Recovery

## Technical Approach

Password recovery remains an independent capability in the `identity` hexagon (`domain ← application ← infrastructure`). PR 1 delivers the complete core backend: dedicated hash-only tokens, atomic reset/session revocation, post-commit email dispatch, feature flag, rate limits, HTTP contracts, and core tests. PR 3 adds audit, retention/cleanup, notification resilience, telemetry, metrics, and operational hardening without changing PR 1’s public API.

The scenario catalogs in `spec.md` and `tasks.md` remain unchanged, but audit, cleanup, retry/terminal-failure, telemetry, metrics, and runbook scenarios are **PR 3 acceptance work**, not PR 1 completion gates.

## Architecture Decisions

| Decision | Options / tradeoff | Choice and rationale |
|---|---|---|
| Token lifecycle | Reuse verification tokens vs independent storage | Use `password_reset_tokens` and `PasswordResetToken`. Recovery has separate expiry, consumption, retention, and future audit needs. |
| Secret storage | Store raw token vs hash-only | Generate 256-bit URL-safe token, persist SHA-256 only, and carry raw token only in the post-commit email event. A database leak must not yield usable links. |
| Reset transaction | Separate writes vs one atomic boundary | Token consume, credential update, and `revokeAllForPrincipal` run in one R2DBC transaction. This prevents replay and partial password/session state. |
| Notification baseline | Synchronous send vs post-commit event | PR 1 publishes `PasswordResetRequested` only after token commit and dispatches through `SendPasswordResetEmailConsumer`; HTTP never waits for delivery. PR 3 owns retries and terminal failure persistence. |
| Abuse controls | No controls, distributed store, or existing adapter | PR 1 keeps IP and normalized-email buckets plus reset-attempt IP limits. Distributed coordination and related metrics are PR 3 hardening. |
| Timing enumeration | No-op seam, constant work, or bounded minimum duration | PR 1 uses a configurable 250 ms minimum request duration measured with monotonic time. Every accepted existing, OAuth-only, and unknown path enters the equalizer after account-dependent lookup/token/transaction/event work. The suspending delay is cancellation-aware and injected in deterministic tests; production never binds a no-op. |
| Operational concerns | Include in core vs additive hardening | PR 3 owns audit emission/storage, cleanup retention, retry policy, terminal failure records, telemetry/spans, metrics, and runbook. They are valuable but do not alter core correctness. |

## Data Flow

```text
POST /forgot-password
  → mark monotonic start → IP/email limits → RequestPasswordResetHandler
  → existing local: [invalidate active + insert hash] TX commits → publish transient PasswordResetRequested
  → unknown/OAuth-only: no account-dependent writes or event
  → every accepted path: wait only for remaining configured minimum duration
  → 202; SendPasswordResetEmailConsumer → EmailSender asynchronously

POST /reset-password
  → IP limit → hash token → ResetPasswordHandler
  → [conditional consume + password update + revoke refresh sessions] one TX
  → 204, no access/refresh token
```

PR 3 attaches after stable boundaries:

```text
successful reset ─→ post-commit audit/telemetry adapters
email consumer ───→ retry policy ─→ terminal failure store/metrics
password_reset_tokens ─→ scheduled retention cleanup
```

## File Changes

| File | PR | Action / responsibility |
|---|---:|---|
| `server/smp/src/main/resources/db/changelog/identity/005-create-password-reset-tokens.yaml` | 1 | Dedicated additive table, unique hash, FK cascade, active-token index. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/domain/PasswordResetToken.kt` | 1 | Pure lifecycle model. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/domain/PasswordResetRequested.kt` | 1 | Transient post-commit email contract. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/{RequestPasswordResetHandler,ResetPasswordHandler,PasswordResetTokenRepository}.kt` | 1 | Core orchestration and persistence port. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/R2dbcPasswordResetTokenRepository.kt` | 1 | Atomic PostgreSQL adapter. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/email/{SendPasswordResetEmailConsumer,EmailTemplates}.kt` | 1 | Baseline dispatch and safe template. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/http/LocalAuthController.kt` | 1 | 202/204 endpoints. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/security/AuthRateLimitWebFilter.kt` | 1 | Core IP limits; email limit remains application-side. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/**` and `docs/runbooks/password-recovery.md` | 3 | Additive audit, cleanup, retry/failure, observability, metrics, and runbook components. Exact files are selected in PR 3 design/apply. |

## Interfaces / Cross-PR Contracts

PR 1 must retain these seams for PR 3:

- Keep token timestamps and `principal_id`; never repurpose or remove rows before PR 3 defines retention.
- Keep `PasswordResetRequested` and `EmailSender` behind interfaces. Retry wrappers may reuse the event, but persisted retry/failure records must exclude raw token, email URL query, and passwords.
- Keep reset orchestration behind `ResetPasswordHandler` and `AtomicTransactionRunner`. PR 3 audit emission must occur only after successful commit and must not weaken reset atomicity.
- Keep rate-limit keys/results internal; PR 3 metrics use bounded, PII-free result labels.
- Cleanup should use a PR 3-specific port/adapter rather than expanding the core token port unless application code needs that capability.
- Public endpoints, status codes, Problem Details, feature flag, and token schema remain backward compatible across PRs.

## Testing Strategy

| Layer | PR 1 gate | PR 3 gate |
|---|---|---|
| Unit | hashing, normalization, token errors, feature flag, rate limits, dispatch ordering | retry policy, audit payload redaction, telemetry/metric labels |
| Integration | PostgreSQL atomicity, rollback, concurrency, FK, session revocation | cleanup retention/idempotency, audit/failure storage |
| HTTP/BDD | 202/204, validation, enumeration resistance, core notification, limits | audit, cleanup, retry, terminal failure, operational observability scenarios |
| E2E | PR 2 UI flows | None required unless hardening changes user-visible behavior |

## Migration / Rollout

PR 1 is additive and guarded by `app.identity.password-recovery.enabled`; rollback disables endpoints and may later drop the table. PR 3 ships independently after PR 1 and must tolerate existing token rows. No PR 3 component is required to merge or declare PR 1 complete.

## Open Questions

None. The PR boundary is explicitly decided.
