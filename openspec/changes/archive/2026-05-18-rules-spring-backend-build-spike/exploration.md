## Exploration: Migrate Spring Boot backend Bazel wrapper build to rules_spring

### Current State
The backend deployable target `//server/smp:smp` is not a native Bazel JVM target today. `server/smp/BUILD.bazel` defines a `genrule` that shells into `./gradlew bootJar`, then copies the generated fat jar into Bazel output space. That wrapper tracks source files and Gradle build files as inputs, but the actual compilation, Spring Boot packaging, dependency management, and test execution still happen in Gradle.

The backend module is a Kotlin + Spring Boot 4.0.6 WebFlux service with the application entrypoint in `server/smp/src/main/kotlin/com/profiletailors/smp/SmpApplication.kt`. It depends on two local Gradle subprojects, `shared/common` and `shared/spring-boot-common`, wired through `server/smp/settings.gradle.kts` as `:shared-common` and `:shared-spring-boot-common`.

Bazel already has Bzlmod, `rules_java`, `rules_kotlin`, and `rules_jvm_external` configured in `MODULE.bazel`, with a large Maven graph pinned in `maven_install.json`. However, there are no native Bazel BUILD files under `shared/common` or `shared/spring-boot-common`, so Bazel cannot yet model those modules as first-class Kotlin/Java libraries. Tests are also wrapper-based today: `//server/smp:unit_tests` and `//server/smp:integration_tests` call Gradle from `sh_test`.

`rules_spring` can package a Spring Boot executable jar from a Bazel `java_library`, but its documented contract is Java-centric: it expects a `java_library` dependency graph and explicit Spring Boot packaging deps. It also documents a Boot 3 launcher override and standard Bazel Java-style testing against the underlying library, not against Gradle.

### Affected Areas
- `MODULE.bazel` — would need `bazel_dep(name = "rules_spring", ...)` plus any repo-level dependency/tooling alignment needed for packaging.
- `maven_install.json` — would need regeneration if Spring Boot packaging artifacts required by `rules_spring` are added to Bazel-managed Maven deps.
- `BUILD.bazel` — root alias `//:smp` points to `//server/smp:smp`; preserving that label during migration minimizes blast radius.
- `server/smp/BUILD.bazel` — current genrule/sh_test wrapper would be the main migration point to native Bazel Kotlin/Java + `springboot(...)` targets.
- `server/smp/build.gradle.kts` — remains the source of truth today for dependency scopes, plugins, toolchain, detekt, and Boot packaging behavior; would need to be mapped or partially retained during a spike.
- `server/smp/settings.gradle.kts` — currently wires shared Gradle projects; equivalent Bazel package boundaries would be required for native Bazel compilation.
- `shared/common/build.gradle.kts` — defines a shared Kotlin library with dependencies and detekt config; would need a Bazel library target before `server/smp` can depend on it natively.
- `shared/spring-boot-common/build.gradle.kts` — defines a Spring-oriented shared Kotlin library with API exposure to `shared-common`; also requires a native Bazel library target.
- `server/smp/src/main/kotlin/com/profiletailors/smp/SmpApplication.kt` — provides the `boot_app_class` needed by `rules_spring`.
- `server/smp/detekt.yml` — current static-analysis config is Gradle-driven; native Bazel packaging would not automatically preserve detekt behavior.
- `tools/bazel/BUILD.bazel` — may need helper macros or conventions if the repo wants a reusable Spring Boot packaging pattern rather than a one-off BUILD target.

### Approaches
1. **Full native Bazel migration now** — Replace the Gradle wrapper build with native Bazel Kotlin libraries for shared modules and service code, then package the app with `rules_spring`.
   - Pros: Real Bazel graph, hermetic packaging path, clearer future for remote caching and test/build parity.
   - Cons: Highest risk; requires solving shared-module BUILD files, Kotlin/Spring integration, dependency mapping, test strategy, and likely JVM toolchain/version alignment all at once.
   - Effort: High

2. **Controlled spike with parallel native target** — Keep existing `//server/smp:smp` wrapper intact, add new native Bazel library/package targets and a non-default `rules_spring` proof target for the backend jar.
   - Pros: Lowest operational risk; lets the team validate rules compatibility, packaging shape, dependency closure, and launcher behavior without breaking current build/test flows.
   - Cons: Temporary duplication of dependency declarations and build logic; spike may not yet cover all Gradle-only features.
   - Effort: Medium

3. **Stay on wrapper but improve Bazel visibility only** — Keep Gradle `bootJar` as the production path and only add more Bazel metadata/filegroups around sources and shared modules.
   - Pros: Minimal disruption.
   - Cons: Does not answer the migration question; no proof that `rules_spring` works with this codebase.
   - Effort: Low

### Recommendation
Recommend **Approach 2: controlled spike with a parallel native target**.

That is the safest first move because the current backend build is carrying several hidden Gradle responsibilities that a direct cutover would abruptly drop: multi-project dependency wiring, Spring Boot plugin packaging behavior, dependency-management/BOM behavior, detekt integration, annotation processor wiring, and test invocation patterns. A spike should prove that Bazel can compile the local shared modules plus `server/smp`, then package a runnable jar with `rules_spring`, while leaving the current `//server/smp:smp` path untouched.

A sensible spike scope is:
- Add `rules_spring` via Bzlmod.
- Add native Bazel BUILD files for `shared/common` and `shared/spring-boot-common` with Kotlin/JVM library targets.
- Add a native Bazel library target for backend sources in `server/smp`.
- Add a parallel `springboot(...)` target such as `//server/smp:smp_rules_spring` using `boot_app_class = "com.profiletailors.smp.SmpApplication"` and the Boot 3+ launcher override `org.springframework.boot.loader.launch.JarLauncher`.
- Keep the existing `//server/smp:smp` alias/genrule as the production path until output parity and test strategy are validated.

### Risks
- **Spring Boot version compatibility risk**: `rules_spring` documentation explicitly describes Boot 3 launcher handling. This repo is on Boot 4.0.6, so launcher and packaging compatibility must be proven, not assumed.
- **Kotlin target-shape risk**: `rules_spring` expects a `java_library` input. The backend is Kotlin-first, so the spike must verify whether `kt_jvm_library` output is directly acceptable or whether an adapter `java_library`/export pattern is required.
- **Shared module gap**: `shared/common` and `shared/spring-boot-common` currently have no BUILD files, so native Bazel compilation cannot start with `server/smp` alone.
- **Dependency drift risk**: Gradle and Bazel dependency graphs are already not identical. Example: Gradle uses Kotlin 2.3.21 and Java toolchain 24, while Bazel currently pins `rules_kotlin` 2.2.0 and registers remote JDK 21 repos.
- **Potential artifact mismatch**: Gradle `bootJar` may include behavior not automatically mirrored by `rules_spring` packaging, including manifest/loader details and optional development/runtime-scoped handling.
- **Missing Bazel-managed packaging deps**: repo grep did not show `spring-boot-loader`, `spring-boot-loader-tools`, or `spring-boot-jarmode-tools` in `maven_install.json`; the spike may need those explicitly in Bazel Maven resolution depending on `rules_spring` expectations.
- **Annotation/config metadata risk**: Gradle declares `annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")`; native Bazel wiring for generated config metadata may need explicit setup or may be deferred for the spike.
- **Testing strategy regression risk**: current Bazel-visible tests are Gradle wrappers. Native Bazel migration would eventually require `java_test`/Kotlin test targets and an explicit policy for Docker/Testcontainers integration.
- **Static analysis parity risk**: detekt is enforced today through Gradle `check`; native Bazel packaging alone will not preserve that quality gate.
- **Operational duplication during transition**: maintaining Gradle and Bazel declarations in parallel is extra work, but it is the price of a low-risk spike.

### Ready for Proposal
Yes — propose a **time-boxed build spike** that introduces `rules_spring` in parallel, proves native compilation + runnable jar packaging for `server/smp`, defines exit criteria, and explicitly excludes production cutover, detekt migration, and full test migration from the first change.
