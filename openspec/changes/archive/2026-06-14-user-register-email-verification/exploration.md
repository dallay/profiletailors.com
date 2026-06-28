## Exploration: User Registration + Email Verification Flow

### Current State

The backend is a Spring Boot 4 Kotlin application following hexagonal architecture (domain →
application → infrastructure). The identity bounded context owns user registration, login, refresh,
and logout via CQRS commands dispatched through a shared `Mediator` bus.

**Registration today:**

- `RegisterUserCommand` → `RegisterUserHandler` creates principal + user identity + password
  credential + workspace, then issues JWT + refresh session immediately.
- No email verification step exists. Users are fully authenticated upon registration.
- The `user_identities` table has `email` and `username` columns but no `email_verified` or
  `email_status` column.
- The `principals` table has no status concept (no `UNVERIFIED`/`VERIFIED`/`SUSPENDED`).

**Event bus:**

- The `shared/bus` module provides a full mediator with `Command`, `CommandWithResult`, `Query`,
  `Notification`, `DomainEvent`, `EventPublisher`, `EventConsumer`, and `EventMultiplexer`.
- `DomainEvent` interface with `BaseDomainEvent` base class exists but is NOT currently used by any
  bounded context — the identity context emits no domain events.
- The publishing context uses a separate in-memory `ReactorChannelEventPublisher` (Reactor `Sinks`)
  for SSE channel events — a different pattern from the shared bus.

**Email infrastructure:**

- **None.** No Spring Boot Starter Mail dependency, no SMTP config, no email sending code anywhere
  in the codebase.

**Database:**

- Liquibase-managed PostgreSQL migrations with R2DBC access.
- Identity tables: `principals`, `user_identities`, `local_password_credentials`.
- Identity changelogs are numbered 001-003; next would be 004.

**Testing:**

- Unit tests with hand-written fakes (no mocking framework).
- Integration tests using `IntegrationTestBase` with H2 in-memory DB + Liquibase baseline.
- BDD tests using Cucumber with `.feature` files.
- ArchUnit tests enforcing hexagonal layer boundaries.

### Affected Areas

**Backend — Identity Bounded Context (primary):**

- `server/smp/.../identity/application/LocalAuthHandlers.kt` — `RegisterUserHandler` must emit a
  domain event instead of issuing auth session; `LoginUserHandler` must check email verification
  status before issuing session.
- `server/smp/.../identity/application/LocalAuthApi.kt` — New command types for email verification,
  new result types.
- `server/smp/.../identity/application/IdentityRegistrationGateway.kt` — May need to store email
  verification token reference.
- `server/smp/.../identity/application/LocalPasswordCredentialGateway.kt` —
  `LocalPasswordCredentialRecord` needs email status.
- `server/smp/.../identity/application/PrincipalIdentityLookup.kt` — `PrincipalIdentityFacts` needs
  email status field.
- `server/smp/.../identity/application/LocalAuthExceptions.kt` — New exception for unverified email
  login attempt.
- `server/smp/.../identity/infrastructure/http/LocalAuthController.kt` — New endpoint for email
  verification.
- `server/smp/.../identity/infrastructure/http/IdentityProblemDetailsHandler.kt` — Handle new
  exceptions.
- `server/smp/.../identity/infrastructure/R2dbcIdentityRegistrationGateway.kt` — Persist email
  status during registration.
- `server/smp/.../identity/infrastructure/R2dbcPrincipalIdentityLookup.kt` — Include email status in
  queries.
- `server/smp/.../identity/infrastructure/IdentityBootstrapConfiguration.kt` — Wire new beans.
- `server/smp/.../identity/infrastructure/security/IdentitySecurityConfiguration.kt` — Add
  `/api/auth/verify-email` to permitAll.

**Database — New Migration:**

- New `identity/004-add-email-verification.yaml` — Add `email_status` column to `user_identities`,
  new `email_verification_tokens` table.
- Update `db.changelog-master.yaml` to include new migration.

**New Bounded Context (Notification):**

- New `notification/` bounded context or email dispatch adapter within identity infrastructure.
- Domain: `EmailVerificationRequested` event.
- Application: `SendVerificationEmailUseCase`.
- Infrastructure: `SmtpEmailSender` adapter.

**Shared Modules:**

- `shared/bus` — Already has the event/notification infrastructure needed. No changes required.
- `shared/common` — `Email` value object already exists.

### Approaches

1. **Domain Event + Notification Handler** — Uses existing `DomainEvent`/`EventConsumer`/
   `Mediator.publish`. Fully async, decoupled. Follows CQRS patterns. Extensible. (Effort: Medium)
2. **Direct Notification Dispatch** — Simpler but loses `DomainEvent` semantic. (Effort: Low-Medium)
3. **Inline Synchronous Flow** — Too coupled; blocks on email send. (Effort: Low)

### Recommendation

Use **Domain Event + Notification Handler**. The codebase already provides `DomainEvent` and event
publishing primitives. Registration should emit `UserRegistered` domain event; a
NotificationConsumer sends the verification email.

### Risks

- Email delivery reliability: add retry + resend endpoint.
- Token security: single-use, TTL, hashed (follow existing token hasher patterns).
- Backward compatibility: migration must mark existing users as VERIFIED.
- Module boundary: keep adapters within identity.infrastructure.
- Login guard: ensure login and refresh flows respect email status.

### Ready for Proposal

Yes — exploration complete. The next step is `sdd-propose` to create a formal proposal (ADR, scope,
acceptance criteria, tasks).