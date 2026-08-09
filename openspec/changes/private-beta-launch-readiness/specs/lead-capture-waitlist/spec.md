# Delta for Lead Capture Waitlist

## ADDED Requirements

### Requirement: Production Waitlist Activation Is Evidence-Bounded (DALLAY-520)

The waitlist activation exercise MUST use the existing waitlist lifecycle and MUST record a dated,
UTC evidence entry for activation, entry creation, invite eligibility, and conversion. Code and test
results MAY prove implemented behavior; only operator evidence from the managed beta environment MAY
prove that the deployed activation path was exercised. A passing local or CI check MUST NOT be treated
as production activation evidence.

#### Scenario: Active waitlist accepts a beta entry

- GIVEN the managed beta waitlist is intentionally `active`
- WHEN an operator submits a consented test entry
- THEN the entry MUST be accepted and recorded as `pending`
- AND the evidence MUST include environment, UTC timestamp, scope, source, and outcome

#### Scenario: Closed or paused waitlist blocks activation

- GIVEN the managed beta waitlist is `paused`, `closed`, or `archived`
- WHEN an operator submits a test entry
- THEN the system MUST reject it without creating a new entry
- AND the launch record MUST classify the activation check as blocked, not successful

#### Scenario: Duplicate activation does not disclose membership state

- GIVEN a test email already exists on the same waitlist
- WHEN the operator submits the entry again
- THEN the public result MUST remain the existing idempotent acceptance contract
- AND evidence MUST redact the email or use a non-production test identifier

### Requirement: Waitlist Evidence Protects Personal Data

Evidence MUST contain only the minimum data needed to reproduce the check. It MUST NOT include access
tokens, invitation tokens, full email addresses, provider payloads, or unredacted logs. Evidence with
missing provenance, exposed secrets, or unverifiable timestamps MUST be a launch blocker.

#### Scenario: Redacted evidence is retained

- GIVEN a waitlist activation check has completed
- WHEN its evidence record is stored
- THEN identifiers MUST be redacted or hashed
- AND an operator MUST be able to identify the test run without recovering the person’s email
