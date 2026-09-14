# Delta for E2E (Invitation Acceptance Evidence)

## ADDED Requirements

### Requirement: Browser preserves invitation error classification

The invitation browser flow MUST classify failures from the HTTP status and application code, not
status alone. It MUST distinguish expired, revoked, and email-mismatch outcomes, show safe recovery
copy, and avoid success navigation or session state after rejection.

#### Scenario: Expired invitation is classified

- GIVEN the acceptance request returns `410` with `INVITATION_EXPIRED`
- WHEN the browser handles the response
- THEN the expired-invitation state is shown with no dashboard redirect or session

#### Scenario: Revoked invitation is classified

- GIVEN the acceptance request returns `410` with `INVITATION_REVOKED`
- WHEN the browser handles the response
- THEN the revoked-invitation state is shown rather than the expired state
- AND no token, full email, or internal response detail is shown

#### Scenario: Email mismatch is classified

- GIVEN the acceptance request returns `403` with `INVITATION_EMAIL_MISMATCH`
- WHEN the browser handles the response
- THEN the email-mismatch state is shown with no dashboard redirect or session

### Requirement: Acceptance evidence is environment-qualified

Deterministic local Cucumber, PostgreSQL, and Playwright results MAY establish local evidence for
QA-06 through QA-10 when each status, application code, browser state, and required persistence
assertion is recorded. Local evidence MUST NOT be presented as deployed or manual acceptance.

#### Scenario: Local matrix produces bounded evidence

- GIVEN local fixtures for expired, revoked, mismatched, and matching-existing-identity journeys
- WHEN the focused backend and browser suites pass
- THEN QA-06 through QA-10 are reported with their exact observed outcomes and runner provenance

#### Scenario: Deployed or manual acceptance is unavailable

- GIVEN no deployed target, credentials, or controlled invitation fixtures are supplied
- WHEN acceptance evidence is reviewed
- THEN deployed/manual acceptance remains `BLOCKED`
- AND passing local evidence is not promoted to deployed acceptance

### Requirement: Follow-up preserves production behavior

The acceptance-evidence follow-up MUST change tests and test fixtures only. It MUST NOT change
production behavior, API classifier or schema, UI copy, runtime configuration, or the existing
acceptance contract.

#### Scenario: Existing contract remains the observed contract

- GIVEN the current invitation acceptance implementation and its local fixtures
- WHEN QA-06 through QA-10 are executed
- THEN evidence verifies the existing statuses, codes, mutation rules, and browser states without
  requiring a production change
