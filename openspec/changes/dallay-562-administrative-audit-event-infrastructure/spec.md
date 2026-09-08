# Delta for Administrative Audit Event Infrastructure

## Purpose

Define the `AdministrativeAuditEvent` model, `SensitiveFieldRedactor` redaction policy,
`AdministrativeAuditEventRepository` port, `AuditEventPublisher` service, and
Liquibase schema for persisting Back Office administrative audit events without
leaking tokens, passwords, or secrets.

## ADDED Requirements

### Requirement: Administrative audit event model

`AdministrativeAuditEvent` MUST be constructed with the following fields:

| Field | Type | Constraints |
|-------|------|-------------|
| `id` | `UUID` | Not null, primary key |
| `actorId` | `UUID` | Not null |
| `actorType` | `String` | Not blank, max 64 chars |
| `action` | `String` | Not blank, max 128 chars |
| `targetId` | `String` | Not blank, max 255 chars |
| `targetType` | `String` | Not blank, max 64 chars |
| `correlationId` | `String?` | Nullable, max 128 chars |
| `metadata` | `Map<String, String>` | Pre-sanitized by caller via `SensitiveFieldRedactor` |
| `occurredAt` | `Instant` | Not null |

Construction MUST reject any blank string field or null required field.

#### Scenario: Complete audit event construction

- GIVEN valid required fields and null optional correlationId
- WHEN `AdministrativeAuditEvent` is constructed
- THEN construction MUST succeed and all fields are queryable

#### Scenario: Missing required field rejects construction

- GIVEN a blank `actorType`
- WHEN `AdministrativeAuditEvent` is constructed
- THEN construction MUST throw `IllegalArgumentException`

---

### Requirement: Sensitive field redaction policy

`SensitiveFieldRedactor` MUST accept a `Map<String, String>` and return a new map
with all sensitive keys removed. A key is sensitive when its lowercase name
contains any of the following substrings: `password`, `token`, `secret`,
`credential`, `key`, `invitationToken`, `resetToken`, `refreshToken`,
`accessToken`.

The function MUST be pure (no side effects) and case-insensitive on key names.
Null maps MUST return an empty map.

#### Scenario: Password key is redacted

- GIVEN a map with `{"password": "secret123", "action": "LOGIN"}`
- WHEN `SensitiveFieldRedactor.redact(input)` is called
- THEN the result MUST contain only `{"action": "LOGIN"}`

#### Scenario: Token substring keys are redacted

- GIVEN a map with `{"accessToken": "abc", "userToken": "xyz", "name": "Alice"}`
- WHEN `SensitiveFieldRedactor.redact(input)` is called
- THEN the result MUST contain only `{"name": "Alice"}`

#### Scenario: Case-insensitive matching

- GIVEN a map with `{"PASSWORD": "secret", "MyToken": "value"}`
- WHEN `SensitiveFieldRedactor.redact(input)` is called
- THEN both keys MUST be absent from the result

#### Scenario: Null input returns empty map

- GIVEN null input
- WHEN `SensitiveFieldRedactor.redact(null)` is called
- THEN the result MUST be an empty map

#### Scenario: No sensitive keys returns identical map

- GIVEN a map with `{"action": "UPDATE", "targetId": "123"}`
- WHEN `SensitiveFieldRedactor.redact(input)` is called
- THEN the result MUST contain exactly the same entries

---

### Requirement: Administrative audit event repository port

`AdministrativeAuditEventRepository` MUST declare the following suspend functions
in `com.profiletailors.smp.administrative.domain`:

- `save(event: AdministrativeAuditEvent): AdministrativeAuditEvent` — persists the event and returns it
- `findById(id: UUID): AdministrativeAuditEvent?` — returns the event or null
- `findByActor(actorId: UUID): List<AdministrativeAuditEvent>` — returns all events for an actor
- `findByTarget(targetType: String, targetId: String): List<AdministrativeAuditEvent>` — returns all events for a target
- `findByCorrelationId(correlationId: String): List<AdministrativeAuditEvent>` — returns all events sharing a correlation ID

The port interface MUST have no Spring annotations.

#### Scenario: Save and retrieve by id

- GIVEN a valid `AdministrativeAuditEvent`
- WHEN `repository.save(event)` is called followed by `repository.findById(event.id)`
- THEN the returned event MUST equal the saved event

#### Scenario: Find by non-existent id returns null

- GIVEN a random UUID with no persisted event
- WHEN `repository.findById(randomId)` is called
- THEN the result MUST be null

---

### Requirement: Audit event publisher service

`AuditEventPublisher` in `com.profiletailors.smp.administrative.application`
MUST accept an `AdministrativeAuditEvent` and delegate persistence to
`AdministrativeAuditEventRepository`. Capability handlers MUST call this service
after completing administrative actions; they are responsible for building
pre-sanitized metadata before calling `publish`.

The publisher MUST NOT perform redaction itself — callers MUST pre-redact using
`SensitiveFieldRedactor`.

#### Scenario: Publisher delegates to repository

- GIVEN a valid `AdministrativeAuditEvent` with pre-sanitized metadata
- WHEN `AuditEventPublisher.publish(event)` is called
- THEN the event MUST be persisted via `AdministrativeAuditEventRepository.save`

#### Scenario: Metadata must be pre-sanitized by caller

- GIVEN an event with unsanitized metadata containing `{"password": "secret"}`
- WHEN `AuditEventPublisher.publish(event)` is called
- THEN the persisted event metadata MUST NOT contain the password field
- AND the caller is responsible for pre-sanitizing

---

### Requirement: Liquibase migration for administrative_audit_events

The Liquibase migration MUST create `administrative_audit_events` with:

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | `uuid` | PK, NOT NULL |
| `actor_id` | `uuid` | NOT NULL |
| `actor_type` | `varchar(64)` | NOT NULL |
| `action` | `varchar(128)` | NOT NULL |
| `target_id` | `varchar(255)` | NOT NULL |
| `target_type` | `varchar(64)` | NOT NULL |
| `correlation_id` | `varchar(128)` | NULL |
| `metadata` | `text` | NULL (JSON serialized map) |
| `occurred_at` | `timestamp with time zone` | NOT NULL |

Indexes MUST exist on `actor_id`, `target_id`, `action`, `occurred_at`, and
`correlation_id`.

#### Scenario: Migration creates table with indexes

- GIVEN the Liquibase changelog entry for `administrative_audit_events`
- WHEN the migration runs against a blank database
- THEN the table MUST exist with all columns and indexes defined

---

### Requirement: Unit tests for redaction

`SensitiveFieldRedactorTest` MUST cover:

- Exact sensitive key removal (password, token, secret, credential, key)
- Compound and camelCase variants (invitationToken, resetToken, refreshToken, accessToken)
- Case-insensitive matching
- Null and empty map handling
- Map with no sensitive keys
- Map with mixed sensitive and non-sensitive keys

`AdministrativeAuditEventTest` MUST cover construction with valid/invalid inputs.

#### Scenario: All denylist substrings are tested

- GIVEN the denylist: password, token, secret, credential, key, invitationToken, resetToken, refreshToken, accessToken
- WHEN a map with each as a key substring is redacted
- THEN all such entries MUST be absent from the result

#### Scenario: Construction rejects blank fields

- GIVEN an event with blank `action`
- WHEN the event is constructed
- THEN an exception MUST be thrown

---

## MODIFIED Requirements

None — this is a new capability with no existing behavior.

## REMOVED Requirements

None.

---

## Notes

- The `administrative` bounded context is new; no existing `administrative/` package exists yet
- Redaction is caller responsibility — `AuditEventPublisher` does not call `SensitiveFieldRedactor`
- The `metadata` column stores JSON-serialized `Map<String, String>` after redaction
- Existing `AdminAuditEvent` in `platformadmin` is a separate model for platform-role assignment audit; `AdministrativeAuditEvent` is the generic Back Office administrative action audit model
