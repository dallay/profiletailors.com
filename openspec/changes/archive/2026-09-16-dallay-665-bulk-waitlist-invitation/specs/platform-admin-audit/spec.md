# Delta for platform-admin-audit

## ADDED Requirements

### Requirement: Per-entry bulk audit

The system MUST publish one audit event per requested entry: `WAITLIST_ENTRY_INVITED/SUCCEEDED` per `invited` entry, `REJECTED` plus stable code per `skipped` entry, `FAILED` plus stable code per `failed` entry (including unexpected errors). Each audit MUST commit independently of its entry's state change. Metadata MUST carry IDs and codes only — never raw tokens or emails.

#### Scenario: Success audited per entry

- GIVEN 2 entries bulk-invited successfully
- WHEN the batch completes
- THEN 2 `WAITLIST_ENTRY_INVITED/SUCCEEDED` events exist, one per entry

#### Scenario: Failed entry still audited with code

- GIVEN a batch containing 1 CONVERTED entry
- WHEN the bulk invite runs
- THEN that entry yields a `FAILED` audit event carrying `ENTRY_ALREADY_CONVERTED` despite no state change
