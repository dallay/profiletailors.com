# Delta for Publishing

## ADDED Requirements

### Requirement: OAuth State Signer Placeholder-Secret Guard (SEC-002)

The system MUST fail fast at startup when the LinkedIn OAuth state-signing secret is missing or looks like a placeholder. `HmacOAuthStateSigner` construction MUST reject a blank secret and MUST reject secrets whose case-insensitive prefix matches any of `CHANGE_ME`, `change_me`, `changeme`, `placeholder`, or `test-`. Real secrets MUST be accepted. BDD/test configuration MUST provide a passing secret so the suite boots.

#### Scenario: Placeholder secret fails fast at startup

- GIVEN `SMP_LINKEDIN_STATE_SIGNING_SECRET` is `test-fake-secret`
- WHEN the `HmacOAuthStateSigner` bean is constructed
- THEN construction MUST throw (startup fails)
- AND the error MUST identify the placeholder prefix

#### Scenario: Blank secret is rejected

- GIVEN `SMP_LINKEDIN_STATE_SIGNING_SECRET` is blank or absent
- WHEN the signer is constructed
- THEN construction MUST throw with "OAuth state signing secret is required"

#### Scenario: Real secret is accepted

- GIVEN a strong, non-placeholder signing secret
- WHEN the signer is constructed
- THEN construction MUST succeed
- AND signing and verification of OAuth state MUST work

#### Scenario: BDD configuration boots with passing secret

- GIVEN `CucumberSpringConfiguration`, `BddTestProperties`, and `application.properties` provide a state-signing secret
- WHEN the BDD suite starts
- THEN the suite MUST boot without triggering the placeholder guard

## TDD Requirement

Every scenario MUST have a failing-first test. In-tree: `HmacOAuthStateSignerTest` covers rejection of `CHANGE_ME`/`change_me`/`changeme`/`placeholder` prefixes and one real-secret accept. New regressions required (design D3): a `test-`-prefix rejection test (guard lists `test-` but has no test) and an acceptance test for the `bdd-`/`smp-`-prefixed BDD/test secrets. BDD secret acceptance is additionally exercised by suite boot (`CucumberSpringConfiguration`, `BddTestProperties`, `application.properties`).
