# Delta for Cloudflare Tunnel Ingress

## ADDED Requirements

### Requirement: VPS Route and Operations Evidence Are Independently Recorded (DALLAY-557)

Private-beta acceptance MUST identify the managed production route, active deployment namespace,
release identity, and operator responsible for the check. Repository rendering, local smoke tests, and
`cloudflared tunnel ingress validate` MAY establish configuration validity only; they MUST NOT establish
that the managed VPS route, readiness, backup, restore, or rollback worked.

#### Scenario: Managed route and readiness are evidenced

- GIVEN an approved beta deployment exists in the managed environment
- WHEN an operator checks the public API route and private readiness endpoint
- THEN the record MUST include hostname, active namespace, release identity, UTC timestamp, and observed
  status
- AND the public route MUST preserve the existing authorization boundary while management readiness
  remains private

#### Scenario: Backup and restore are proven without exposing secrets

- GIVEN a database and media backup exists for the active beta instance
- WHEN an operator performs or verifies a controlled restore rehearsal
- THEN the record MUST identify backup scope, restore target, timestamp, result, and data-integrity check
- AND it MUST NOT contain database credentials, tokens, or customer content beyond minimum test data

#### Scenario: Rollback is available and bounded

- GIVEN a deployment or worker change fails acceptance
- WHEN the operator executes the documented rollback or safe-off procedure
- THEN the active service MUST return to the last known-good release/configuration
- AND the record MUST capture the action, observed result, and remaining limitation

### Requirement: Public Exposure Remains Restricted

The production route MUST NOT expose PostgreSQL, backend management port `9091`, or direct origin
listeners. A failed privacy/security boundary check MUST be a launch blocker even when application
health is green.

#### Scenario: Direct management access is blocked

- GIVEN an external client bypasses the managed hostname
- WHEN it probes origin or management ports
- THEN no public listener may accept the connection
- AND the evidence MUST classify the route as blocked

### Requirement: Health, Backup, and Rollback Evidence Has Operator Scope

Automated deployment validation MAY establish rendered configuration and repository behavior only. The
DALLAY-557 prerequisite MUST require managed-VPS evidence for active-service convergence, private
readiness, backup/restore, worker safe-off, and rollback. A local or CI pass MUST NOT satisfy a missing
VPS observation.

#### Scenario: Local validation is not accepted as VPS proof

- GIVEN local configuration and smoke checks pass
- WHEN DALLAY-557 evidence is reviewed without a managed-VPS record
- THEN the prerequisite MUST remain unverified
- AND DALLAY-559 MUST remain `NO-GO`
