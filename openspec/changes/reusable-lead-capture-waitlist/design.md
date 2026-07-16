# Design: Reusable Lead Capture Waitlist

## Technical Approach

Two pure-Kotlin shared modules hold domain primitives with zero Spring/server dependencies. A `leadcapture` bounded context in `server/smp` implements the HTTP + R2DBC adapters. Reuses existing `Email`, `BaseEntity`/`AggregateRoot` from `shared:common`, and the `WAITLIST` rate-limit strategy from `shared:shield:ratelimit`.

## Architecture Decisions

| Option | Tradeoffs | Decision |
|--------|-----------|----------|
| Domain in shared vs server-only | Shared enables reuse (forms, newsletter) but adds publishing cost | Shared — satisfies 3-context admission rule |
| `waitlist` separate from `common` module | Separation allows independent evolution | Separate — follows existing `shared/` convention |
| Email normalization in shared vs server | Shared prevents inconsistent dedupe across consumers | Shared — as `EmailNormalized` VO |
| Use existing `Email` vs new VO | Reuse avoids duplicating RFC 5321 validation | Reuse `Email`; `EmailNormalized` wraps normalized form + preserves original |
| R2DBC vs jOOQ vs raw SQL | R2DBC matches existing pattern | R2DBC |
| Liquibase YAML vs SQL | YAML matches existing changelog convention | YAML |
| New context vs piggyback on existing | Separate context enforces hexagonal boundaries | New `leadcapture` context |
| Duplicate returns 202 vs 409 | 409 leaks email existence | 202 — uniform, no enumeration |

## Module Dependency Rules

```
shared:common                        (pure Kotlin, zero deps)
  └── shared:lead-capture:common     (SourceType, ConsentType, EmailNormalized, MetadataWhitelist)
       └── shared:lead-capture:waitlist (Waitlist, WaitlistEntry, status enums, join VOs)
            └── server:smp:leadcapture  (application handlers, R2DBC repo, HTTP controller, config)
```

**Invariant**: `shared/lead-capture/*` MUST NOT depend on `server/smp` or any Spring/framework module. Enforce via Gradle module boundaries + ArchUnit test.

## Domain Model

### `shared/lead-capture/common`
- **SourceType** — marker enum: `WAITLIST`, `FORM`, `NEWSLETTER`
- **ConsentType** — marker enum: `EARLY_ACCESS`, `MARKETING` (separate fields, never conflated)
- **EmailNormalized** — `inline class` wrapping `Email.value.lowercase().trim()`, preserves original via `Email` reference
- **MetadataWhitelist** — defines allowed keys, max 10 entries, key ≤ 64 chars, value ≤ 1024 chars; strips HTML

### `shared/lead-capture/waitlist`
- **WaitlistStatus** — `OPEN`, `PAUSED`, `CLOSED`
- **WaitlistEntryStatus** — `JOINED`, `CONFIRMED`, `REMOVED`
- **Waitlist** (AggregateRoot) — `id`, `key` (unique), `name`, `status`, `metadataSchema`, timestamps
- **WaitlistEntry** (entity) — `id`, `waitlistId`, `email` (original), `emailNormalized`, `status`, `earlyAccessConsent`, `marketingConsent`, `metadata`, timestamps
- **WaitlistJoinRequest** — input VO: `email`, `metadata`, `earlyAccessConsent`
- **WaitlistJoinResult** — output VO: `id`, `email`, `status`, `createdAt`

## API Contract

### `POST /api/waitlists/{waitlistKey}/entries`

**Payload**:
```json
{ "email": "user@example.com", "metadata": { "referrer": "hero-section" } }
```

**202 Accepted** (new + duplicate):
```json
{ "id": "wle_abc123", "email": "user@example.com", "status": "joined", "created_at": "2026-06-25T20:00:00Z" }
```

**Errors**:

| Status | Code | When |
|--------|------|------|
| 400 | `validation_error` | Invalid email / oversized metadata |
| 404 | `waitlist_not_found` | `waitlistKey` does not exist |
| 409 | `waitlist_closed` | Waitlist status CLOSED |
| 409 | `waitlist_paused` | Waitlist status PAUSED |
| 429 | `rate_limit_exceeded` | WAITLIST rate limit exceeded |

## Persistence Model

Tables in directory `db/changelog/leadcapture/` (Liquibase YAML):

**`waitlists`**: id(varchar PK), key(varchar UNIQUE), name, description, status(default OPEN), metadata_schema(jsonb), created_at(timestamptz), updated_at(timestamptz)

**`waitlist_entries`**: id(varchar PK), waitlist_id(varchar FK→waitlists), email, email_normalized, status(default JOINED), early_access_consent(boolean), marketing_consent(boolean), metadata(jsonb), created_at, updated_at

**Constraints**: `UNIQUE(waitlist_id, email_normalized)`
**Indexes**: `(waitlist_id, status)`, `(email_normalized)`, `(created_at DESC)`
**Seed**: `profile-tailors-launch` waitlist (OPEN)

## Metadata Whitelist

`MetadataWhitelist.validate(map, schema)` returns error if:
- Schema is non-null and key not in schema
- Entry count > 10, key > 64 chars, value > 1024 chars
- Value contains HTML tags or script content

## Security / Abuse Prevention

| Control | Mechanism |
|---------|-----------|
| Rate limiting | WAITLIST strategy: 10 req/min/IP on `/api/waitlists/**` |
| No email enumeration | Duplicate returns identical 202 (same as first join) |
| Uniform duplicate | `INSERT ... ON CONFLICT DO NOTHING` — always return 202 |
| Logging privacy | Log `email_normalized` hash; never log raw email |
| Input validation | Server-side: reject invalid email, oversize metadata, script injection |

## Marketing Integration

`WaitlistForm.astro` currently client-side only. Modified to:
- POST JSON to `/api/waitlists/profile-tailors-launch/entries` on submit
- Show loading state while request in flight
- Display success message (current behavior) on 202
- Show inline error on 400/404/409/429
- Hardcode `profile-tailors-launch` key (matches seed)

## Testing Strategy

| Layer | What | Approach |
|-------|------|----------|
| Unit | `EmailNormalized`, `MetadataWhitelist`, domain invariants | TDD — write failing test first |
| Unit | `JoinWaitlistHandler` — dedupe, status guards | Mock repository |
| Integration | R2DBC repositories | Testcontainers PostgreSQL |
| WebFlux | `WaitlistController` | `WebTestClient` |
| E2E | WaitlistForm → backend round-trip | Playwright |
| Arch | `shared/lead-capture` no server imports | ArchUnit test |

TDD is mandatory: no production code without a failing test first.

## File Changes

| File | Action |
|------|--------|
| `shared/lead-capture/common/build.gradle.kts` | Create |
| `shared/lead-capture/common/src/.../common/SourceType.kt` | Create |
| `shared/lead-capture/common/src/.../common/ConsentType.kt` | Create |
| `shared/lead-capture/common/src/.../common/EmailNormalized.kt` | Create |
| `shared/lead-capture/common/src/.../common/MetadataWhitelist.kt` | Create |
| `shared/lead-capture/waitlist/build.gradle.kts` | Create |
| `shared/lead-capture/waitlist/src/.../waitlist/Waitlist.kt` | Create |
| `shared/lead-capture/waitlist/src/.../waitlist/WaitlistEntry.kt` | Create |
| `shared/lead-capture/waitlist/src/.../waitlist/WaitlistStatus.kt` | Create |
| `shared/lead-capture/waitlist/src/.../waitlist/WaitlistEntryStatus.kt` | Create |
| `shared/lead-capture/waitlist/src/.../waitlist/WaitlistJoinRequest.kt` | Create |
| `shared/lead-capture/waitlist/src/.../waitlist/WaitlistJoinResult.kt` | Create |
| `server/smp/src/.../leadcapture/application/JoinWaitlistHandler.kt` | Create |
| `server/smp/src/.../leadcapture/application/WaitlistRepository.kt` | Create |
| `server/smp/src/.../leadcapture/infrastructure/R2dbcWaitlistRepository.kt` | Create |
| `server/smp/src/.../leadcapture/infrastructure/WaitlistController.kt` | Create |
| `server/smp/src/.../leadcapture/infrastructure/WaitlistConfiguration.kt` | Create |
| `server/smp/src/main/resources/db/changelog/leadcapture/001-create-waitlists.yaml` | Create |
| `server/smp/src/main/resources/db/changelog/leadcapture/002-create-waitlist-entries.yaml` | Create |
| `server/smp/src/main/resources/db/changelog/leadcapture/003-seed-waitlists.yaml` | Create |
| `server/smp/src/main/resources/db/changelog/db.changelog-master.yaml` | Modify — include leadcapture |
| `server/smp/build.gradle.kts` | Modify — add waitlist dep |
| `shared/shield/ratelimit/.../RateLimitProperties.kt` | Modify — endpoint `/api/waitlists/**` |
| `apps/web/marketing/.../WaitlistForm.astro` | Modify — POST to backend |
| `docs/architecture/shared/dependencies.md` | Modify — add lead-capture |

## Rollout & Rollback

**Rollout**: ① shared modules + domain tests → ② migrations + seed → ③ server adapters + rate limit endpoint update → ④ WaitlistForm.astro → deploy.

**Rollback**: Remove leadcapture includes from master changelog → drop tables → remove server/smp leadcapture package → revert WaitlistForm.astro → delete shared modules.

## Risks and Mitigations

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Shared module imports server code | Low | Gradle isolation + ArchUnit |
| Consent blur across EARLY_ACCESS / MARKETING | Med | Separate boolean fields; handler rejects marketing consent |
| WaitlistKey enumeration via 404 | Low | Acceptable risk for public waitlist; rate limiting |
| Metadata injection | Low | Whitelist + sanitization at domain boundary |
| Spam via automated submissions | Med | Rate limit 10/min/IP + ON CONFLICT dedupe |

## Open Questions

None.
