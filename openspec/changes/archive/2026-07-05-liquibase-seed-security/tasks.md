# Tasks: Liquibase Seed Security

- [x] Add failing regression tests for production/dev changelog isolation and credential seed prohibition.
- [x] Remove the dev seed include from the production master.
- [x] Add an explicit dev changelog and select it from the dev profile.
- [x] Remove the committed BCrypt credential CSV and its loader.
- [x] Add a CI security scan for Liquibase credential seeds.
- [x] Run focused tests and the scan.
- [x] Run final backend verification.
- [x] Implement `LocalFixturesRunner` with `@Profile("local-fixtures")` to restore dev login.
- [x] Add unit tests for `LocalFixturesRunner` covering creation and idempotence.
- [x] Create `application-local-fixtures.yaml` and include profile in `application-dev.yaml`.
