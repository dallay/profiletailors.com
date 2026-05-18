# Proposal: Controlled rules_spring Migration Spike for Spring Boot Backend

## Intent

The current Bazel target for the Spring Boot backend is only a wrapper around Gradle, not a native Bazel JVM build. `//server/smp:smp` uses a `genrule` to invoke `./gradlew bootJar`, which means compilation, dependency resolution, Spring Boot packaging, and test execution still happen outside Bazel's native graph. That limits build hermeticity, visibility, and confidence in future Bazel-first backend workflows.

A direct migration is too risky right now because the current Gradle path is also carrying hidden responsibilities: multi-project wiring for `shared/common` and `shared/spring-boot-common`, Spring Boot packaging behavior, annotation processing, dependency-management alignment, and wrapper-based test execution. We need a controlled spike first to prove whether Salesforce `rules_spring` can package this Kotlin + Spring Boot 4 service in this repository without disrupting the existing production build path.

## Scope

### In Scope
- Add `rules_spring` to Bazel module configuration in a non-breaking way.
- Introduce native Bazel library targets for `shared/common` and `shared/spring-boot-common` sufficient for the spike.
- Introduce a native Bazel library target for `server/smp` application sources.
- Add a parallel `rules_spring` packaging target for the backend, preserving the current `//server/smp:smp` wrapper target unchanged.
- Validate whether the backend can compile natively under Bazel and produce a runnable Spring Boot jar using `com.profiletailors.smp.SmpApplication` as the boot application class.
- Capture compatibility findings needed for a later migration decision, especially around Spring Boot 4, Kotlin target shape, and required packaging dependencies.

### Out of Scope
- Replacing the existing `//server/smp:smp` production build target.
- Full cutover of backend tests from Gradle wrappers to native Bazel test targets.
- Detekt or static-analysis migration into Bazel.
- Broad cleanup or deduplication of Gradle and Bazel dependency declarations.
- Refactoring backend application code for feature work unrelated to build migration.
- Establishing final repository-wide Spring Boot Bazel conventions or reusable macros beyond what the spike strictly needs.

## Approach

Keep the current Gradle-backed Bazel path as the stable default and add a parallel native proof target. The spike should model the two shared Gradle modules and the backend service as Bazel Kotlin/JVM libraries, then package the service through `rules_spring` with the documented launcher override required for modern Spring Boot packaging.

This change is intentionally investigative, not declarative. Its goal is to answer whether this repository can support native Bazel Spring Boot packaging with acceptable compatibility and effort before the team commits to a full migration.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `MODULE.bazel` | Modified | Add `rules_spring` and align repository-level Bazel dependencies for the spike. |
| `maven_install.json` | Modified | Regenerate if Bazel-managed Spring Boot packaging artifacts are required by `rules_spring`. |
| `server/smp/BUILD.bazel` | Modified | Add parallel native Kotlin/Bazel targets and a non-default `rules_spring` package target while preserving current wrapper targets. |
| `shared/common/` | Modified | Add native Bazel BUILD coverage for the shared Kotlin library. |
| `shared/spring-boot-common/` | Modified | Add native Bazel BUILD coverage for the Spring-oriented shared Kotlin library. |
| `BUILD.bazel` | Possibly Modified | Preserve root alias behavior and avoid changing current consumers unless needed for the spike. |
| `tools/bazel/BUILD.bazel` | Possibly Modified | Add minimal helper support only if needed to keep the spike maintainable. |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| `rules_spring` may not package Spring Boot 4 correctly | High | Keep the current Gradle wrapper path untouched and treat the spike as proof-only until output behavior is validated. |
| Kotlin targets may not fit `rules_spring`'s expected `java_library` shape directly | High | Explicitly test adapter/export patterns during the spike and record the working contract. |
| Shared Gradle subprojects may require more Bazel modeling than expected | Medium | Limit BUILD authoring to only the minimal dependency closure required by `server/smp`. |
| Gradle and Bazel dependency/toolchain drift may block parity | Medium | Treat version/toolchain mismatches as exit-criterion findings rather than forcing full alignment in the spike. |
| Temporary duplicate build declarations may create maintenance noise | Medium | Time-box the spike and avoid broad migration work before a go/no-go decision. |

## Rollback Plan

If the spike fails or introduces instability, remove the parallel `rules_spring` targets and any spike-only Bazel dependency additions, then keep `//server/smp:smp` and existing Gradle-backed test wrappers as the only supported backend build path. Because the current production path remains unchanged during the spike, rollback is limited to deleting the experimental Bazel wiring.

## Dependencies

- Existing Bazel Bzlmod setup with `rules_java`, `rules_kotlin`, and `rules_jvm_external`
- Salesforce `rules_spring`
- Current backend entrypoint at `server/smp/src/main/kotlin/com/profiletailors/smp/SmpApplication.kt`
- Existing Gradle module structure for `server/smp`, `shared/common`, and `shared/spring-boot-common`

## Success Criteria

- [ ] A parallel native Bazel target exists for packaging the backend with `rules_spring` without changing the current `//server/smp:smp` wrapper behavior.
- [ ] Bazel can natively compile the shared modules plus `server/smp` far enough to support the spike target.
- [ ] The spike produces a runnable Spring Boot jar or clearly proves why that is not yet possible in this repository.
- [ ] The team has documented evidence about Spring Boot 4 compatibility, Kotlin target-shape compatibility, and any missing packaging dependencies.
- [ ] The spike ends with a clear go/no-go recommendation for proceeding to full migration design.

## Exit Criteria

- [ ] Either the parallel `rules_spring` target builds and runs successfully, or the failure mode is isolated enough to explain the blocker precisely.
- [ ] The current Gradle-backed Bazel wrapper build remains available and unchanged as the fallback path.
- [ ] Follow-up work is clearly separated into future spec/design items instead of being folded implicitly into the spike.
