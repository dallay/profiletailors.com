# Specification: Liquibase Seed Security

## Requirement: Production changelog isolation
The default Liquibase changelog MUST contain schema and required reference data only and MUST NOT include development seed changelogs.

### Scenario: Default deployment
- GIVEN the default Spring configuration
- WHEN Liquibase resolves its changelog
- THEN it uses `db.changelog-master.yaml`
- AND no development user seed is reachable.

## Requirement: Explicit development seeds
Development seed data MUST be reachable only through a changelog selected by the `dev` Spring profile.

### Scenario: Development deployment
- GIVEN the `dev` profile is active
- WHEN Liquibase resolves its changelog
- THEN it uses `db.changelog-dev.yaml`
- AND that changelog includes the production baseline and non-credential development data.

## Requirement: Credential seed prohibition
Committed Liquibase resources MUST NOT contain BCrypt hashes or seed rows for `password_hash`.

### Scenario: CI scan
- GIVEN a change introduces forbidden credential seed material
- WHEN the quality gate runs
- THEN the Liquibase seed security check fails.
