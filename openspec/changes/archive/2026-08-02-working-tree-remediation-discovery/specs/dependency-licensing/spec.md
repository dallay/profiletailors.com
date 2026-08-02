# Delta for Dependency Licensing

## ADDED Requirements

### Requirement: AGPL-Incompatible License Rejection

The build MUST fail when a dependency carries an AGPL-incompatible license. `just licence-check` (via the registered `com.profiletailors.legal.licence-report` plugin) MUST reject dependencies under GPL-2.0 or other AGPL-incompatible licenses. The check MUST run as part of the local CI gate (`ci-local` includes `just licence-check`, per ADR-0012).

#### Scenario: GPL-2.0 dependency fails the gate

- GIVEN a dependency resolved with license GPL-2.0
- WHEN `just licence-check` runs
- THEN the gate MUST fail
- AND the report MUST name the offending dependency and license

#### Scenario: AGPL-compatible dependencies pass

- GIVEN all resolved dependencies carry AGPL-compatible licenses
- WHEN `just licence-check` runs
- THEN the gate MUST pass

### Requirement: Dependency Bump Compilation

The dependency version bumps in `gradle/libs.versions.toml` (`springdoc 3.0.2→3.0.3`, `awsS3 2.20.15→2.50.1`) and `server/smp/build.gradle.kts` (`jackson-module-kotlin 2.21.2→2.22.1`), plus the `kotlin-bom` platform added to library modules, MUST compile without breaking the backend build.

#### Scenario: Bumped dependencies compile

- GIVEN the bumped dependency versions are in place
- WHEN `just backend-build` runs
- THEN compilation MUST succeed
- AND all modules (including library modules with the new `kotlin-bom`) MUST build

## TDD Requirement

Compilation is the regression check for the bumps (`just backend-build`). The license gate is verified by `just licence-check`. No unit-test change is required; the failing-first signal is the gate/build failing before the change lands.
