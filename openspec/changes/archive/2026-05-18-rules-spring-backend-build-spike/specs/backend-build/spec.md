# Backend Build Specification

## Purpose

Define the controlled backend build spike behavior for evaluating a parallel native Bazel + `rules_spring` packaging path for `server/smp` without changing the current stable Gradle-backed build path.

## Requirements

### Requirement: Stable Backend Build Path Preservation

The system MUST preserve the current stable backend build path while introducing the spike target.

The existing stable backend packaging path MUST remain available as the default supported path for the backend during the spike.
The experimental native Bazel packaging path MUST be introduced as a separate non-default target.
The spike MUST NOT require current consumers of the stable backend target to change labels, commands, or expected fallback behavior.
The spike MUST NOT redefine successful completion as replacing the current stable path.

#### Scenario: Stable path remains available when spike target is added

- GIVEN the repository adds an experimental native backend packaging target for the spike
- WHEN a maintainer invokes the current stable backend packaging path
- THEN the stable path MUST remain available
- AND the stable path MUST continue to be treated as the supported backend build path for the duration of the spike

#### Scenario: Experimental path is isolated from stable consumers

- GIVEN the repository contains both the stable backend packaging path and the experimental native spike target
- WHEN a maintainer reviews the supported backend build entrypoints for this change
- THEN the experimental target MUST be documented as a separate non-default path
- AND the stable path MUST remain the default supported backend packaging path

### Requirement: Minimum Native Bazel Compilation Coverage for the Spike

The system MUST prove native Bazel compilation coverage for the minimum local code required by the spike target.

At minimum, native Bazel compilation coverage MUST include the backend application sources required to package `server/smp` and the local shared modules required by that packaging path.
The spike MUST define compilation proof in terms of Bazel-native analysis and compilation of those local sources, not merely wrapper execution of Gradle.
The spike MAY leave unrelated backend modules, wrapper-based test targets, detekt integration, and broader repository migration outside the proof boundary.
If native Bazel compilation cannot cover the required local dependency closure for the spike target, the spike MUST be treated as failed proof rather than partial migration success.

#### Scenario: Minimum required local modules compile natively for the spike

- GIVEN the spike target depends on backend application sources and local shared modules
- WHEN the experimental native Bazel path is validated
- THEN the validation MUST prove native Bazel compilation coverage for the backend sources required by `server/smp`
- AND the validation MUST prove native Bazel compilation coverage for each local shared module required by that packaging path

#### Scenario: Wrapper-only execution does not satisfy native compilation proof

- GIVEN a validation attempt reaches packaging only by shelling out to Gradle or another non-native wrapper step for local source compilation
- WHEN the spike evidence is evaluated
- THEN the spike MUST NOT count that result as native Bazel compilation coverage
- AND the result MUST be classified as insufficient proof for the spike objective

#### Scenario: Missing local dependency coverage fails the spike proof

- GIVEN the experimental native Bazel path compiles some but not all required local backend or shared-module sources
- WHEN the spike evidence is evaluated against the proof boundary
- THEN the spike MUST be treated as failed proof for native compilation coverage
- AND the missing local dependency closure MUST be identified as a blocker to successful validation

### Requirement: Packaging Validation Evidence Classification

The system MUST classify packaging validation with explicit success and failure evidence for the spike target.

Successful packaging validation MUST require evidence that the experimental native Bazel target both packages the backend artifact and produces a runnable Spring Boot artifact for the spike scope.
Successful packaging validation MUST include evidence that the produced artifact starts through the configured backend application entrypoint and reaches a runnable state consistent with the spike's packaging objective.
Failed packaging validation MUST include evidence that the experimental target cannot package the artifact, cannot launch the artifact, or cannot reach a runnable state because of a packaging, launcher, dependency-closure, or compatibility blocker.
The spike MUST record validation outcomes as either successful proof or failed proof with a concrete blocker; ambiguous results MUST be treated as failed proof until clarified.
The spike MAY limit packaging validation to the minimum runnable artifact evidence needed for migration decision-making and MUST NOT require production cutover parity.

#### Scenario: Packaging validation succeeds with runnable artifact evidence

- GIVEN the experimental native Bazel target completes packaging for the backend spike
- WHEN packaging validation is reviewed
- THEN the evidence MUST show that a backend artifact was produced by the experimental native path
- AND the evidence MUST show that the produced artifact can be launched into a runnable Spring Boot state for the spike scope

#### Scenario: Packaging validation fails because artifact is not runnable

- GIVEN the experimental native Bazel target produces an artifact
- AND the produced artifact fails to launch or fails to reach a runnable Spring Boot state
- WHEN packaging validation is reviewed
- THEN the validation MUST be classified as failed proof
- AND the recorded evidence MUST identify the launch or runtime blocker as the reason

#### Scenario: Ambiguous packaging outcome is treated as failure

- GIVEN the spike team cannot determine whether the experimental native artifact is valid because the evidence is incomplete or contradictory
- WHEN the packaging result is classified
- THEN the outcome MUST be recorded as failed proof
- AND the missing or contradictory evidence MUST be called out explicitly

### Requirement: Fallback Behavior for Spike Failure

The system MUST define deterministic fallback behavior when the experimental spike target fails.

If the experimental native Bazel target fails compilation proof or packaging validation, the stable backend build path MUST remain the supported path without requiring emergency migration work.
Failure of the experimental target MUST NOT block use of the stable backend packaging path.
The spike MUST record failure as a go/no-go input for later migration planning rather than as an implicit requirement to repair or cut over immediately.
Any rollback of spike-only wiring MAY remove the experimental target and related spike-only support, but MUST NOT require removing the stable backend path.

#### Scenario: Experimental target failure falls back to stable path

- GIVEN the experimental native Bazel spike target fails validation
- WHEN the backend build path for ongoing work is determined
- THEN the stable backend packaging path MUST remain the supported fallback
- AND the repository MUST NOT require the experimental target to succeed in order to build the backend through the existing supported path

#### Scenario: Spike rollback removes only experimental behavior

- GIVEN the spike is concluded with a no-go decision
- WHEN maintainers roll back spike-only build behavior
- THEN they MAY remove the experimental native target and related spike-only wiring
- AND they MUST preserve the stable backend packaging path as the continuing supported path
