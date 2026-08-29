# Tasks: Registration Policy Modes

## Phase 1: Domain and Application Contract

- [x] 1.1 RED: add mode and decision tests for `OPEN`, `INVITE_ONLY`, and `CLOSED`.
- [x] 1.2 GREEN/REFACTOR: add the pure registration mode semantics and application policy port.
- [x] 1.3 RED/GREEN: cover handler decisions and zero-work behavior for restricted modes.

## Phase 2: Infrastructure and API Contract

- [x] 2.1 RED/GREEN: bind `SMP_REGISTRATION_MODE` with a `CLOSED` default.
- [x] 2.2 RED/GREEN: map invitation-required registration to a safe Problem Details response.
- [x] 2.3 RED/GREEN: keep public capabilities allow-listed and enabled only for `OPEN` mode.

## Phase 3: Integration and Verification

- [x] 3.1 RED/GREEN: add the invite-only BDD scenario and preserve open/closed scenarios.
- [x] 3.2 Update environment examples and production Compose/Swarm wiring.
- [x] 3.3 Run focused tests, BDD fast, backend check, and inspect the final diff.
