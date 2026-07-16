# Design: Reusable Lead Capture Waitlist Capability

## Architecture

Source: [ADR-0011](../../docs/architecture/adr/0011-reusable-lead-capture-waitlist.md)

### Module Layout

```text
shared/
  lead-capture/
    common/                 # framework-free VOs only
      build.gradle.kts
      src/main/kotlin/com/profiletailors/leadcapture/common/
        EmailAddress.kt
        NormalizedEmail.kt
        CaptureSource.kt
        CaptureLocale.kt
        LeadMetadata.kt
    waitlist/               # domain + application (CQRS, pure Kotlin)
      build.gradle.kts
      src/main/kotlin/com/profiletailors/leadcapture/waitlist/
        domain/
          Waitlist.kt
          WaitlistStatus.kt
          WaitlistEntry.kt
          WaitlistEntryStatus.kt
          WaitlistConsent.kt
        application/
          JoinWaitlistCommand.kt
          JoinWaitlistHandler.kt
          ports/
            WaitlistRepository.kt
            WaitlistEntryRepository.kt
```

### Dependency Rule

```text
shared:lead-capture:common  ←── shared:lead-capture:waitlist
                                      ↑
                          server:smp (implements ports)
```

`shared:lead-capture:waitlist` depends on `shared:lead-capture:common`. Neither depends on `server:smp` or any framework. `server:smp` depends on both shared modules and provides infrastructure adapters.

### Gradle Configuration

Both shared modules use explicit dependency constraints:

```kotlin
dependencies {
    constraints {
        implementation("org.springframework:*") { exclude() }
    }
}
```

Or more simply: do not add Spring/R2DBC to the dependencies at all. Use `kotlin-test` only for testing.

## Key Design Decisions

### 1. Consent lives in waitlist, not common

`WaitlistConsent` is inside the waitlist domain because consent is bounded-context-specific. Different bounded contexts (newsletter, forms) will have different consent models. Putting a `ConsentSnapshot` in `common` would couple all contexts to one consent schema.

### 2. Idempotent join via uniform response

The `JoinWaitlistHandler` internally distinguishes `joined_new`, `already_joined`, and `reactivated` for metrics/events. The public response is always `{ "message": "You're on the list.", "status": "accepted" }`. This prevents email enumeration.

### 3. Conservative email normalization

`NormalizedEmail` does trim + lowercase only. No Gmail dot/plus canonicalization. This avoids edge-case bugs and preserves the original email. The original and normalized values are both stored.

### 4. Per-waitlist deduplication

`UNIQUE(waitlist_id, email_normalized)` — the same email can join different waitlists. Global deduplication would prevent cross-product reuse.

### 5. Metadata whitelist

`LeadMetadata` enforces a fixed key set: `utm_source`, `utm_medium`, `utm_campaign`, `utm_content`, `utm_term`, `referrer`, `page_path`, `user_agent_family`, `consent_version`. Unlisted keys are rejected. This prevents PII leakage through metadata.

## Sequence: Join Waitlist

```text
Client → POST /api/waitlists/{waitlistKey}/entries
  → WaitlistController
    → JoinWaitlistCommand(email, source, formId, locale, metadata, consent)
    → JoinWaitlistHandler.handle(command)
      → WaitlistRepository.findByKey(waitlistKey)
        → if not found: throw WaitlistNotFoundException → 404
        → if not active: throw WaitlistClosedException → 409
      → NormalizedEmail.from(email)
      → WaitlistEntryRepository.findByWaitlistIdAndEmail(waitlistId, normalizedEmail)
        → if exists: return JoinResult.alreadyJoined
        → if not: create WaitlistEntry, save, return JoinResult.joinedNew
    → return AcceptedResponse (uniform)
```

## Enforcement

- Gradle: `shared:lead-capture:*` modules do not include Spring or R2DBC in dependencies.
- ArchUnit: tests forbid `shared/lead-capture/**` from importing `org.springframework.*`, `io.r2dbc.*`, or `com.profiletailors.smp.*`.
- Domain tests: assert `earlyAccess` required, `marketing` default false, idempotent dedupe, valid lifecycle transitions.
- HTTP tests: assert 202 (new + duplicate), 400, 404, 409, 429.
