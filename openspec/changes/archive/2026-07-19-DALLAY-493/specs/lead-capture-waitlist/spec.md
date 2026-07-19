# Delta for Lead Capture Waitlist — DSAR Integration

## ADDED Requirements

### Requirement: Waitlist Entry DSAR Lookup and Anonymization

`WaitlistEntryRepository` MUST support lookup by normalized email (case-insensitive, trimmed). An `anonymizeEmail(entryId)` operation MUST replace the `email` field with `[REDACTED on {timestamp}]` and clear PII in `metadata` (→ `{}`).

#### Scenario: Lookup matches normalized email

- GIVEN entries with emails `"User@Example.com"` and `"other@x.com"`
- WHEN `findByNormalizedEmail("user@example.com")` is called
- THEN the entry with email `"User@Example.com"` MUST be returned

#### Scenario: Anonymization clears PII

- GIVEN an entry with `email = "user@x.com"` and `metadata = {"name":"John"}`
- WHEN `anonymizeEmail(entryId)` is called
- THEN `email` MUST be `[REDACTED on 2026-07-19T12:00:00Z]`
- AND `metadata` MUST be `{}`

### Requirement: Correction Propagation

When `email` is corrected on a `user_identities` row, the correction MUST propagate to waitlist entries matching BOTH old and new email (entries with the old email are updated to the new email). Entries matching only the old or only the new email MUST NOT be affected.

#### Scenario: Propagation updates matching entries

- GIVEN an entry with `email = "old@x.com"`
- AND a correction from `old@x.com` to `new@x.com`
- WHEN propagation runs
- THEN the entry's email MUST become `"new@x.com"`
