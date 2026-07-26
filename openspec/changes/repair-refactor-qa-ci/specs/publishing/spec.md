# Delta for Publishing

## ADDED Requirements

### Requirement: Cross-Context Port Migration Compatibility

Cross-context port references and audit metadata MUST conform to their current public contracts
without changing publishing, media, or governance domain semantics.

#### Scenario: Backend compilation resolves migrated ports

- GIVEN a production component depends on a migrated cross-context port
- WHEN the backend Kotlin compilation gate runs
- THEN all references MUST resolve through the current contract
- AND no duplicate, stale, or context-internal port type MAY remain

#### Scenario: Existing audit semantics remain intact

- GIVEN a migrated port operation records audit metadata
- WHEN the operation completes
- THEN metadata MUST satisfy the current port contract
- AND the observable operation outcome MUST remain unchanged

### Requirement: CI Recovery Sequence

The repair MUST validate failures in dependency order: frontend build, type-check, lint, affected
unit tests, backend compilation, then affected backend test lanes. A later gate MUST NOT mask an
earlier failing gate.

#### Scenario: Earlier gate failure stops recovery

- GIVEN a gate in the recovery sequence fails
- WHEN repair validation is performed
- THEN the failing gate MUST be reported before later gates are treated as passing
- AND validation MUST resume from that gate after correction

#### Scenario: Recovered CI preserves behavior

- GIVEN every affected gate passes
- WHEN the repair diff is reviewed
- THEN it MUST contain only compliance repairs
- AND it MUST NOT introduce product, API, route, state, or visual behavior changes
