# Specification: Reusable Lead-Capture Waitlist

> **Change:** reusable-lead-capture-waitlist
> **Revision:** v1
> **Date:** 2026-06-25

## Context

Profile Tailors needs a waitlist for the MVP launch (`profile-tailors-launch`). The waitlist must be
a reusable capability — not coupled to a single product or marketing campaign — so future products
can join the same infrastructure.

## Problem

The marketing site has no backend for email capture. A bespoke form-in-marketing approach would
couple lead capture to Astro, prevent reuse by other products, and create data silos. Without clear
consent separation, captured emails cannot be safely used for future outreach.

## Goals

| #  | Goal                                                                           |
|----|--------------------------------------------------------------------------------|
| G1 | Shared modules (`common` + `waitlist`) with zero server dependency             |
| G2 | Public join API returning idempotent `accepted` for new and duplicate entries  |
| G3 | Separate early-access and marketing consent; no marketing reuse without opt-in |
| G4 | Email deduplication via `UNIQUE(waitlist_id, normalized_email)`                |
| G5 | Metadata whitelist with payload limits to prevent abuse                        |

## Non-goals

- Newsletter or general `forms` capability — document as future concept only
- `ConsentSnapshot` or consent-common types in `shared/lead-capture/common`
- Waitlist management UI or admin endpoints in MVP
- Provider-specific email canonicalization (Gmail+, etc.)

## Architecture Decision

**Decision:** Two-tier shared module layout (`common` → `waitlist`) with `server/smp` adapters on
top. The `common` module holds pure value objects (VOs) with zero dependencies. The `waitlist`
module depends on `common` and contains domain logic, aggregate roots, repository ports, and service
interfaces. Adapters in `server/smp` implement repository ports, HTTP handlers, config, and rate
limiting.

## Module Boundaries

```
shared/lead-capture/
├── common/                  # Pure VOs, no dependencies
│   └── src/main/kotlin/.../
│       ├── EmailAddress.kt
│       ├── NormalizedEmail.kt
│       ├── CaptureSource.kt
│       ├── CaptureLocale.kt
│       └── LeadMetadata.kt
├── waitlist/                # Domain logic, depends on common
│   └── src/main/kotlin/.../
│       ├── Waitlist.kt
│       ├── WaitlistEntry.kt
│       ├── WaitlistStatus.kt
│       ├── WaitlistEntryStatus.kt
│       ├── Consent.kt
│       ├── WaitlistRepository.kt
│       └── WaitlistService.kt
server/smp/
└── src/main/kotlin/.../
    ├── waitlist/
    │   ├── http/WaitlistController.kt
    │   ├── r2dbc/WaitlistR2dbcRepository.kt
    │   ├── config/WaitlistConfig.kt
    │   └── rate_limit/WaitlistRateLimiter.kt
    └── resources/db/
        └── migration/..._create_waitlist_schema.sql
apps/web/marketing/
└── src/components/
    └── WaitlistForm.astro
```

**Invariant:** `shared/lead-capture` MUST NOT depend on `server/smp`.

## Domain Model

### Value Objects (`common`)

| VO                | Fields                                 | Validation                                         |
|-------------------|----------------------------------------|----------------------------------------------------|
| `EmailAddress`    | `value: String`                        | RFC-like reasonable regex, max 320 chars           |
| `NormalizedEmail` | `value: String`                        | Trim, lowercase, reasonable regex                  |
| `CaptureSource`   | `value: String`                        | Max 50 chars, alphanumeric + hyphens               |
| `CaptureLocale`   | `language: String`, `country: String?` | BCP 47 tag, max 10 chars                           |
| `LeadMetadata`    | `fields: Map<String, String>`          | Whitelist-only keys, max 5 entries, 200 bytes each |

### `WaitlistStatus`

`draft` → `active` → `paused` ←→ `active` → `closed` → `archived`

| Status     | Meaning                                            |
|------------|----------------------------------------------------|
| `draft`    | Being configured, not accepting entries            |
| `active`   | Accepting new entries                              |
| `paused`   | Temporarily stopped, returns `409 waitlist_closed` |
| `closed`   | Permanently stopped, returns `409 waitlist_closed` |
| `archived` | Soft-deleted, not visible                          |

### `WaitlistEntryStatus`

`pending` → `invited` → `converted` | `pending` → `cancelled`

| Status      | Meaning                                |
|-------------|----------------------------------------|
| `pending`   | Joined, awaiting next action           |
| `invited`   | Invitation sent                        |
| `converted` | Completed conversion (signed up, etc.) |
| `cancelled` | Opted out or removed                   |

### Aggregate: `Waitlist`

```kotlin
data class Waitlist(
    val id: WaitlistId,
    val key: WaitlistKey,        // unique slug, e.g. "profile-tailors-launch"
    val displayName: String,
    val status: WaitlistStatus,
    val metadata: LeadMetadata,
    val createdAt: Instant,
    val updatedAt: Instant
)
```

### Aggregate: `WaitlistEntry`

```kotlin
data class WaitlistEntry(
    val id: WaitlistEntryId,
    val waitlistId: WaitlistId,
    val email: EmailAddress,
    val normalizedEmail: NormalizedEmail,
    val status: WaitlistEntryStatus,
    val consent: Consent,
    val source: CaptureSource,
    val locale: CaptureLocale?,
    val metadata: LeadMetadata,
    val createdAt: Instant
)
```

## API Contract

### `POST /api/waitlists/{waitlistKey}/entries`

**Request body:**

```json
{
  "email": "user@example.com",
  "source": "marketing-site",
  "formId": "waitlist-hero",
  "locale": { "language": "en" },
  "consent": {
    "earlyAccess": true,
    "marketing": false
  },
  "metadata": {
    "utm_source": "twitter",
    "page_url": "https://profiletailors.com"
  }
}
```

**Success (202):**

```json
{
  "status": "accepted",
  "message": "You're on the waitlist"
}
```

| Status                 | Code  | Body Error                          |
|------------------------|-------|-------------------------------------|
| New entry accepted     | `202` | `{ "status": "accepted" }`          |
| Duplicate accepted     | `202` | `{ "status": "accepted" }`          |
| Waitlist not found     | `404` | `{ "error": "waitlist_not_found" }` |
| Waitlist closed/paused | `409` | `{ "error": "waitlist_closed" }`    |
| Invalid email          | `400` | `{ "error": "invalid_email" }`      |
| Consent missing        | `400` | `{ "error": "consent_required" }`   |
| Rate limited           | `429` | `{ "error": "rate_limited" }`       |

### Idempotency

The endpoint SHALL return `202 accepted` for duplicate joins (same `normalizedEmail` +
`waitlistId`). The public response MUST NOT expose a duplicate flag. External observers MUST NOT be
able to distinguish between a fresh join and a duplicate join to prevent email enumeration.

## Persistence Model

```sql
CREATE TABLE waitlists (
    id              UUID PRIMARY KEY,
    key             VARCHAR(100) NOT NULL UNIQUE,
    display_name    VARCHAR(255) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'draft',
    metadata_json   JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE waitlist_entries (
    id                UUID PRIMARY KEY,
    waitlist_id       UUID NOT NULL REFERENCES waitlists(id),
    email             VARCHAR(320) NOT NULL,
    normalized_email  VARCHAR(320) NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'pending',
    consent_early_access BOOLEAN NOT NULL DEFAULT false,
    consent_marketing    BOOLEAN NOT NULL DEFAULT false,
    source            VARCHAR(50) NOT NULL,
    locale_language   VARCHAR(10),
    locale_country    VARCHAR(10),
    metadata_json     JSONB NOT NULL DEFAULT '{}',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(waitlist_id, normalized_email)
);
```

Seed data: one waitlist row with `key = 'profile-tailors-launch'`, `status = 'active'`.

## Metadata Policy

| Rule             | Constraint                                                                   |
|------------------|------------------------------------------------------------------------------|
| Whitelist        | `utm_source`, `utm_medium`, `utm_campaign`, `page_url`, `page_title`         |
| Max entries      | 5 keys per request                                                           |
| Max key length   | 50 characters                                                                |
| Max value length | 200 bytes (UTF-8)                                                            |
| Rejection        | `400` with `invalid_metadata` if any key outside whitelist or limit exceeded |

## Consent Model

| Field         | Required | Default | Semantics                                   |
|---------------|----------|---------|---------------------------------------------|
| `earlyAccess` | YES      | —       | User explicitly wants early product access  |
| `marketing`   | NO       | `false` | User opts in to marketing/newsletter emails |

**Rules:**

- A waitlist entry with `consent.earlyAccess = false` SHALL be rejected (`400 consent_required`).
- A waitlist entry with `consent.marketing = false` SHALL NOT be used for any newsletter/marketing
  send.
- The `marketing` consent field MUST be a separate, explicit affirmative opt-in (not inferred from
  `earlyAccess`).

## Security & Abuse Prevention

| Measure                | Detail                                                                             |
|------------------------|------------------------------------------------------------------------------------|
| Rate limit             | Per-waitlist, per-IP: 10 req/min, returned as `429 rate_limited`                   |
| Email normalization    | Trim whitespace, lowercase, validate format; no provider-specific canonicalization |
| Metadata limits        | Whitelist-only keys, 5 max, 200 bytes per value                                    |
| Input validation       | Email regex on server AND client; all strings length-validated                     |
| Enumeration prevention | Duplicate joins return same `202 accepted` as new joins                            |

## Integration Plan

| Step | Description                                                                                 |
|------|---------------------------------------------------------------------------------------------|
| 1    | Create `shared/lead-capture/common` module with VOs                                         |
| 2    | Create `shared/lead-capture/waitlist` module with domain model, ports, service              |
| 3    | Add server/smp adapters: HTTP controller, R2DBC repository, config, rate limiter            |
| 4    | Create DB migration (waitlists + waitlist_entries tables) and seed `profile-tailors-launch` |
| 5    | Build `WaitlistForm.astro` component in marketing app; post to backend                      |
| 6    | Wire rate limiting per IP/waitlist                                                          |
| 7    | Write tests at every layer                                                                  |
| 8    | Roll out behind feature flag                                                                |

## Testing Strategy

| Layer       | Focus                                                                 | Tools                            |
|-------------|-----------------------------------------------------------------------|----------------------------------|
| Domain      | Value object validation, status transitions, consent rules            | JUnit 5, Kotlin test             |
| Application | `WaitlistService` use cases, dedupe, error mapping                    | JUnit 5, mock repository         |
| Persistence | R2DBC repository CRUD, UNIQUE constraint, seed data                   | `@DataR2dbcTest`, Testcontainers |
| HTTP        | Controller integration, response codes, validation errors, rate limit | `@WebFluxTest`                   |
| Frontend    | `WaitlistForm.astro` render, submit, success/error states             | Vitest, Playwright               |

## Rollout Plan

| Phase    | What                                                                                  |
|----------|---------------------------------------------------------------------------------------|
| Alpha    | Deploy backend behind feature flag; test manually with curl                           |
| Beta     | Enable form on staging; verify 202/400/404/409/429 paths                              |
| Launch   | Remove feature flag; monitor error rates and abuse patterns                           |
| Rollback | Disable form, unregister route, remove config — all non-destructive to persisted data |

## Open Questions

1. Should the rate limit be per-IP, per-email, or per-waitlist? (Proposed: per-IP per-waitlist)
2. Should we log duplicate join attempts for abuse monitoring?
3. What is the exact `Retry-After` header value for 429 responses?
4. Should the seed waitlist key be configurable via `application-{profile}.yaml`?

---

## ADDED Requirements

### Capability: `lead-capture-common`

#### Requirement: EmailAddress Value Object

The system SHALL provide an `EmailAddress` value object that validates RFC-like reasonable email
format.

##### Scenario: Valid email accepted

- GIVEN an email string `"user@example.com"`
- WHEN `EmailAddress(value)` is constructed
- THEN the object SHALL hold the original value

##### Scenario: Invalid email rejected

- GIVEN an email string `"not-an-email"`
- WHEN `EmailAddress(value)` is constructed
- THEN construction SHALL throw an `IllegalArgumentException`

#### Requirement: NormalizedEmail Dedupe Key

The system SHALL provide `NormalizedEmail` as a separate type from `EmailAddress`, performing
trim+lowercase normalization without provider-specific canonicalization.

##### Scenario: Normalization preserves original

- GIVEN an `EmailAddress("User@Example.COM")`
- WHEN `NormalizedEmail(email)` is constructed
- THEN `normalizedEmail.value` SHALL be `"user@example.com"`
- AND the original `EmailAddress.value` SHALL remain `"User@Example.COM"`

#### Requirement: LeadMetadata Whitelist

The system SHALL enforce a metadata whitelist with maximum entry limits.

##### Scenario: Whitelisted key accepted

- GIVEN a metadata payload with only approved keys and ≤5 entries, each ≤200 bytes
- WHEN `LeadMetadata(fields)` is constructed
- THEN construction SHALL succeed

##### Scenario: Unlisted key rejected

- GIVEN a metadata payload with key `"unknown_field"`
- WHEN `LeadMetadata(fields)` is constructed
- THEN construction SHALL throw an `IllegalArgumentException`

### Capability: `lead-capture-waitlist`

#### Requirement: Public Join API — Happy Path

The system SHALL expose `POST /api/waitlists/{waitlistKey}/entries` that accepts new entries with
early-access consent.

##### Scenario: New entry accepted

- GIVEN a waitlist with key `"profile-tailors-launch"` and status `active`
- WHEN a POST request sends a valid email with `earlyAccess: true`
- THEN the response SHALL be `202` with `status: "accepted"`

#### Requirement: Idempotent Duplicate Join

The system SHALL return `202 accepted` for duplicate entries (same waitlist + normalized email).

##### Scenario: Duplicate returns accepted

- GIVEN an existing entry for `"user@example.com"` on waitlist `"profile-tailors-launch"`
- WHEN a POST with the same email arrives
- THEN the response SHALL be `202` with `status: "accepted"`
- AND no new row SHALL be inserted

#### Requirement: Waitlist Not Found

The system SHALL return `404 waitlist_not_found` for unknown waitlist keys.

##### Scenario: Unknown key returns 404

- GIVEN no waitlist exists with key `"fake-waitlist"`
- WHEN a POST is sent to that key
- THEN the response SHALL be `404` with `error: "waitlist_not_found"`

#### Requirement: Closed Waitlist Rejects

The system SHALL return `409 waitlist_closed` for paused or closed waitlists.

##### Scenario: Closed waitlist returns 409

- GIVEN a waitlist with status `closed`
- WHEN a POST is sent to that waitlist
- THEN the response SHALL be `409` with `error: "waitlist_closed"`

#### Requirement: Consent Required

The system SHALL reject entries without explicit early-access consent.

##### Scenario: Missing early access consent returns 400

- GIVEN a POST with `consent.earlyAccess: false`
- WHEN the request is processed
- THEN the response SHALL be `400` with `error: "consent_required"`

#### Requirement: Marketing Consent Prohibition

The system SHALL NOT use waitlist entries for marketing unless `consent.marketing = true`.

##### Scenario: Marketing consent false blocks newsletter use

- GIVEN a waitlist entry with `consent.marketing: false`
- WHEN any marketing/newsletter query is executed
- THEN the entry SHALL NOT appear in marketing query results

#### Requirement: Email Deduplication Constraint

The system SHALL enforce `UNIQUE(waitlist_id, normalized_email)` at the database level.

##### Scenario: Duplicate email prevented by constraint

- GIVEN a row with `(waitlist_id=X, normalized_email="user@example.com")`
- WHEN an INSERT with the same pair is attempted
- THEN the database SHALL raise a unique constraint violation
- AND the application SHALL handle it gracefully, returning `202 accepted`

#### Requirement: Rate Limiting

The system SHALL rate-limit join requests per-IP per-waitlist.

##### Scenario: Rate limit exceeded

- GIVEN an IP has sent more than 10 requests per minute to the same waitlist
- WHEN another request arrives from that IP
- THEN the response SHALL be `429` with `error: "rate_limited"`

#### Requirement: Seed Waitlist

The system SHALL seed a waitlist with key `"profile-tailors-launch"` and status `active`.

##### Scenario: Seed data exists after migration

- GIVEN the migration has been applied
- WHEN querying for waitlist key `"profile-tailors-launch"`
- THEN exactly one row SHALL exist with status `active`

#### Requirement: Shared Module Independence

The `shared/lead-capture` modules SHALL NOT depend on `server/smp` or any Spring Framework types.

##### Scenario: No server dependency verified by build

- GIVEN the `shared/lead-capture` module build configuration
- WHEN the dependency graph is inspected
- THEN no dependency on `server/smp` or Spring Framework SHALL exist
