# Semantic PR Validation Specification

## Purpose

Define reliable pull-request title validation for overlapping events while preserving PR-scoped serialization and an auditable workflow revision.

## Requirements

### Requirement: Validate Semantic PR Events

The Semantic PR validation MUST run for pull requests opened, edited, or synchronized by the repository caller. It MUST apply the configured title rules and publish a passing or failing validation result for the pull request.

#### Scenario: Valid title passes

- GIVEN a pull request is opened, edited, or synchronized
- AND its title satisfies the configured semantic title rules
- WHEN validation completes
- THEN the Semantic PR check passes

#### Scenario: Invalid title fails

- GIVEN a pull request is opened, edited, or synchronized
- AND its title does not satisfy the configured semantic title rules
- WHEN validation completes
- THEN the Semantic PR check fails with the validation outcome

### Requirement: Serialize Validation Without Cancelling In-Flight Work

The shared Semantic PR workflow MUST serialize validations by pull request using its PR-scoped concurrency group. When a newer event enters the same group, it MUST NOT cancel the validation already running. A newer event MAY remain pending or replace an older pending event during a burst, but it MUST NOT cancel the active validation.

#### Scenario: New event preserves active validation

- GIVEN a Semantic PR validation is running for pull request 787
- WHEN a newer event for pull request 787 enters the same concurrency group
- THEN the running validation completes without concurrency cancellation
- AND the newer validation waits for the group or remains pending

#### Scenario: Different pull requests are isolated

- GIVEN validations are running for pull requests 787 and 788
- WHEN pull request 787 receives another event
- THEN only pull request 787's group is affected
- AND pull request 788's running validation is not cancelled

#### Scenario: Burst replaces pending work only

- GIVEN one validation is running and multiple newer events enter one pull-request group
- WHEN the concurrency scheduler manages pending work
- THEN an older pending validation MAY be replaced
- AND the already-running validation remains uncancelled

### Requirement: Pin the Authoritative Workflow Revision

The repository caller MUST invoke the released Semantic PR reusable workflow at an immutable commit SHA containing this behavior. It MUST NOT duplicate the validation job or add caller-side concurrency that changes the source workflow's PR-scoped behavior.

#### Scenario: Caller uses the released immutable revision

- GIVEN the source workflow fix has been released
- WHEN the repository caller is inspected
- THEN its reusable-workflow reference uses the released commit SHA
- AND the release tag is retained only as human-readable reference metadata

#### Scenario: Mutable or unreleased revision is rejected

- GIVEN the caller references a mutable tag or a SHA that does not contain the source fix
- WHEN the workflow pin is validated
- THEN the change is rejected until an immutable released revision is used
