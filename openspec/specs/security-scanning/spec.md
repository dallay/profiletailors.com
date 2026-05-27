# Security Scanning Specification

## Purpose

Define the repository-level DevSecOps scanning behavior for `profiletailors.com`. This specification
establishes the required pull-request and scheduled scanning lanes, tool coverage boundaries,
severity and gating expectations, findings publication behavior, developer documentation, local
usage expectations, and secrets/operational constraints for the repository security baseline.

## Requirements

### Requirement: Layered Scanning Lanes

The repository MUST provide two distinct security scanning lanes: a fast pull-request lane and a
scheduled deep-scan lane.

The pull-request lane MUST prioritize fast, actionable feedback for proposed changes.
The scheduled deep-scan lane MUST provide broader or slower analysis that is not appropriate to run
as a required gate on every pull request.
The repository MUST keep these lanes understandable as separate purposes rather than mixing all
scanners into one undifferentiated workflow.
The scheduled deep-scan lane MAY run on a time-based cadence and MAY analyze the full repository
even when the pull-request lane is path-scoped.

#### Scenario: Pull request receives fast security feedback

- GIVEN a contributor opens or updates a pull request
- WHEN the repository security workflows execute for that pull request
- THEN the repository MUST run the fast pull-request security lane
- AND the lane MUST report actionable findings without requiring the full deep-scan workload

#### Scenario: Deep analysis runs outside the pull-request critical path

- GIVEN the repository reaches its scheduled security scan time
- WHEN the deep-scan workflows start
- THEN the repository MUST run broader security analysis than the pull-request lane
- AND those scans MUST remain separate from routine pull-request turnaround expectations

### Requirement: Required Scanner Coverage and Role Separation

The repository MUST define security scanning coverage using Semgrep, CodeQL, SonarQube or
SonarCloud, Gitleaks, Trivy, Detekt, and ESLint security-focused rules.

Each scanner MUST have a clear role to reduce duplicated noise.
CodeQL MUST cover code-graph security analysis for the backend-oriented code it supports.
Semgrep MUST provide fast cross-language static security analysis across repository code and
security-relevant text assets that it supports.
Gitleaks MUST provide secret-detection coverage.
Trivy MUST provide dependency, filesystem, and configuration vulnerability scanning coverage where
applicable.
Detekt MUST remain the Kotlin static-analysis lane and MUST coexist with the rest of the stack
rather than being replaced by it.
ESLint security-focused rules MUST cover the active frontend application JavaScript or TypeScript
surface.
SonarQube or SonarCloud MUST provide centralized quality and security reporting for the repository
scope it supports.
The repository MUST NOT introduce overlapping scanners without a distinct documented purpose.

#### Scenario: Backend and frontend surfaces receive the intended scanner mix

- GIVEN the repository contains backend code under `server/smp` and `shared/**` and frontend code
  under the active web app path
- WHEN the security baseline is evaluated
- THEN backend-relevant scanners MUST cover `server/smp` and `shared/**`
- AND frontend lint and security rule coverage MUST include the active frontend app path

#### Scenario: A scanner is not used as a duplicate of another scanner

- GIVEN two scanners can report similar issue categories
- WHEN their repository roles are documented and configured
- THEN each scanner MUST retain a distinct purpose in the stack
- AND the repository MUST avoid duplicating the same gate without documented justification

### Requirement: Path-Aware Pull-Request Execution

The pull-request security lane SHOULD use path-aware or changed-scope execution where safe to reduce
runtime and noise.

Backend-oriented scans SHOULD trigger when backend or shared security-relevant paths change.
Frontend lint and frontend-targeted security checks SHOULD trigger when the active frontend
application or shared frontend-relevant configuration changes.
Repo-wide scanners MUST still run when changed files can affect repository-wide security outcomes,
including workflow, dependency, or scanner-configuration changes.
The repository MUST prefer conservative triggering over unsafe omission when change impact is
ambiguous.

#### Scenario: Frontend-only change avoids unnecessary backend-only work

- GIVEN a pull request changes only the active frontend application files
- WHEN the fast pull-request security lane is selected
- THEN frontend-targeted security checks SHOULD run
- AND backend-only security jobs SHOULD NOT be required unless the change also affects shared or
  repo-wide security surfaces

#### Scenario: Shared or workflow changes trigger broader checks

- GIVEN a pull request changes shared modules, workflow files, or scanner configuration
- WHEN the fast pull-request security lane is selected
- THEN the repository MUST run any security checks whose scope could be affected by those changes
- AND the repository MUST NOT rely on narrow path filters that would miss relevant security impact

### Requirement: Workflow Hardening and Non-Disruption

Security scanning workflows MUST use official or well-maintained GitHub Actions and MUST be designed
not to disrupt unrelated repository workflows.

Pull-request security workflows MUST use concurrency groups that cancel superseded in-progress runs
for the same pull request or branch update.
Security workflows MUST declare explicit minimal token permissions and MUST NOT request broader
permissions than required for their documented behavior.
Security workflows MUST remain additive to existing repository automation rather than replacing
unrelated release, contribution, or build workflows.
Security workflows SHOULD preserve understandable job naming and execution boundaries so
contributors can distinguish fast gates from deeper reporting jobs.

#### Scenario: New push cancels superseded pull-request security run

- GIVEN a pull-request security workflow is already running for an earlier commit
- WHEN a newer commit is pushed to the same pull request
- THEN the superseded in-progress pull-request security run MUST be cancelled by concurrency control
- AND the newest run MUST become the active feedback path

#### Scenario: Existing repository automation remains intact

- GIVEN the repository already contains non-security workflows
- WHEN the scanning stack is introduced
- THEN the new security workflows MUST coexist without removing unrelated existing automation
- AND contributors MUST still be able to use the existing workflows as before

### Requirement: Findings Publication and SARIF Behavior

Security scanners SHOULD publish findings using SARIF when the scanner and platform support it.

SARIF-capable scanners MUST produce repository-visible findings in the supported platform when
configured successfully.
Scanners that do not support SARIF MAY report findings through their native workflow output or
external service integration.
The repository MUST keep findings publication understandable so contributors can determine which
tool reported a result.
The repository MUST document which scanners publish SARIF and which scanners report through other
channels.

#### Scenario: SARIF-capable scan reports findings through supported security views

- GIVEN a SARIF-capable scanner runs successfully on repository content
- WHEN the scan finishes
- THEN the scanner SHOULD publish findings in SARIF to the supported repository security surface
- AND contributors MUST be able to trace those findings back to the reporting scanner

#### Scenario: Non-SARIF scanner still provides actionable output

- GIVEN a scanner in the stack does not publish findings through SARIF
- WHEN that scanner detects an issue
- THEN the repository MUST still surface the issue through its supported reporting channel
- AND the documentation MUST explain where contributors should review those findings

### Requirement: Severity Handling and Merge Gating

The repository MUST define explicit severity-handling and gating behavior for the security stack.

Fast pull-request gates MUST prioritize high-signal findings that are appropriate to block merge
decisions.
Scheduled deep scans MAY report findings without immediately becoming required merge gates.
The repository MUST document which scanners or finding classes are blocking in pull requests and
which are reporting-only.
The repository MUST keep gating behavior stable and predictable so contributors can understand why a
pull request is blocked.
Severity thresholds SHOULD be documented per scanner or reporting channel when the tool supports
severity classification.

#### Scenario: High-signal pull-request finding blocks merge

- GIVEN a pull-request scanner reports a finding class configured as blocking
- WHEN the pull-request checks conclude
- THEN the repository MUST mark the relevant required security check as failing
- AND the contributor MUST be able to identify that the failure is merge-blocking

#### Scenario: Scheduled finding does not unexpectedly block an unrelated pull request

- GIVEN a scheduled deep scan reports a finding in the repository
- WHEN no pull-request gate for that finding class is configured as required
- THEN the scheduled result MAY remain reporting-only
- AND it MUST NOT silently change routine pull-request gating behavior

### Requirement: False-Positive Control and Tuning Governance

The repository MUST support low-noise security scanning through documented, reviewable tuning and
suppression controls.

Scanner tuning, allowlists, ignores, exclusions, or suppressions MUST be repo-local, reviewable in
code review, and justified in plain English.
The repository SHOULD prefer upstream defaults with minimal local customization.
The repository MUST NOT treat false-positive reduction as a reason to disable broad security
coverage without documented rationale.
Suppressions MUST be scoped as narrowly as practical to the finding, path, or rule being controlled.

#### Scenario: False positive is suppressed in a reviewable way

- GIVEN a scanner reports a finding determined to be a false positive for this repository
- WHEN the team tunes the scanner behavior
- THEN the suppression MUST be expressed through repo-local configuration that can be reviewed in
  version control
- AND the reason for the suppression MUST be documented in English

#### Scenario: Tuning does not erase unrelated coverage

- GIVEN a specific rule or path requires suppression or exclusion
- WHEN the scanner configuration is updated
- THEN the tuning MUST remain as narrow as practical
- AND unrelated code paths or rule families MUST continue to be scanned

### Requirement: English Documentation and Local Developer Usage

The repository MUST provide English documentation for the security scanning stack and local
developer usage.

Documentation MUST describe the purpose of each scanner, the difference between pull-request and
scheduled lanes, the blocking versus reporting-only behavior, and the expected paths or surfaces
each scanner covers.
Documentation MUST explain how contributors can run the relevant scanners locally where local
execution is supported.
Documentation MUST explain where findings appear, how to interpret failures, and how to propose
legitimate suppressions.
Documentation SHOULD describe the minimum local prerequisites needed for supported local runs.

#### Scenario: Contributor needs to reproduce a CI finding locally

- GIVEN a contributor sees a security finding in repository automation
- WHEN the contributor reads the repository security documentation
- THEN the documentation MUST explain how to run the relevant supported local check
- AND the contributor MUST be able to determine where that scanner applies in the repository

#### Scenario: Contributor needs to understand blocking behavior

- GIVEN a contributor is unsure whether a security finding blocks merge
- WHEN the contributor consults repository documentation
- THEN the documentation MUST identify which checks are required versus reporting-only
- AND the explanation MUST be written in English

### Requirement: Secrets, Tokens, and Operational Boundaries

The repository MUST define the secrets and operational requirements for the scanning stack with
least-privilege expectations.

Security workflows MUST use only the secrets and tokens required for their supported integrations.
Repository documentation MUST identify which scanners require repository or external-service secrets
and which do not.
Workflows MUST avoid write-capable permissions or secrets exposure unless the scanner’s required
behavior depends on them.
If a scanner depends on an unavailable external integration secret, the repository MUST make the
operational dependency explicit rather than silently pretending the integration is active.
The repository MUST keep scanner configuration and operational instructions understandable for
maintainers.

#### Scenario: Scanner with external service dependency is configured explicitly

- GIVEN a scanner requires an external service token or project binding to publish centralized
  results
- WHEN the repository documents or runs that integration
- THEN the required secret or binding MUST be explicitly identified as an operational dependency
- AND the workflow MUST NOT request broader repository permissions than needed for that integration

#### Scenario: Scanner without secret dependency stays least-privilege

- GIVEN a scanner can run using repository contents and standard GitHub-provided context only
- WHEN the pull-request or scheduled workflow is defined
- THEN the workflow MUST avoid introducing unnecessary extra secrets for that scanner
- AND the workflow MUST keep its declared permissions limited to the minimum required behavior
