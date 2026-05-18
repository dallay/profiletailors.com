# Tasks: Controlled rules_spring Migration Spike for Spring Boot Backend

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Review budget | 400 changed lines unless project config says otherwise |
| Estimated workload | High |
| Chained PRs recommended | Yes |
| Proposed delivery strategy | stacked-prs |
| Work-unit balance | Slice 1 module/deps, Slice 2 shared libs, Slice 3 server+packaging, Slice 4 validation+decision |

## Phase 1: Module and dependency wiring

- [x] 1.1 Update `MODULE.bazel` to add `rules_spring` Bzlmod wiring and only the Bazel/JVM repos needed for an experimental Spring Boot packaging path.
- [x] 1.2 Regenerate `maven_install.json` only if native compile or `rules_spring` packaging requires missing Spring Boot loader/tooling or coroutine/runtime artifacts; keep changes limited to spike needs.
- [x] 1.3 Verify root `BUILD.bazel` remains unchanged in behavior: `//:smp` still points to `//server/smp:smp` and `//:all_servers` still contains only the stable target.

## Phase 2: Shared native Bazel targets

- [x] 2.1 Create `shared/common/BUILD.bazel` with `kt_jvm_library(name = "common_lib")`, `srcs` from `src/main/kotlin/**/*.kt`, Java 21/Kotlin compiler flags mirroring Gradle, and the minimal Maven deps from `shared/common/build.gradle.kts`.
- [x] 2.2 Build `//shared/common:common_lib`; if it fails, tighten deps/import mapping before touching downstream targets and record the first blocker for evidence.
- [x] 2.3 Create `shared/spring-boot-common/BUILD.bazel` with `kt_jvm_library(name = "spring_boot_common_lib")`, dependency on `//shared/common:common_lib`, and `resources` covering `src/main/resources/**`.
- [x] 2.4 Ensure `shared/spring-boot-common/BUILD.bazel` preserves `META-INF/spring.factories` and `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`; then build `//shared/spring-boot-common:spring_boot_common_lib`.

## Phase 3: Server native target and experimental packaging

- [x] 3.1 Extend `server/smp/BUILD.bazel` without changing existing `genrule(name = "smp")` or `sh_test` wrappers: add `kt_jvm_library(name = "smp_lib")` over `src/main/kotlin/**/*.kt` and runtime resources, depending on both shared native libs.
- [x] 3.2 Build `//server/smp:smp_lib` natively; resolve only the minimum Bazel dependency closure needed for compilation and keep Gradle-only concerns deferred.
- [ ] 3.3 Add experimental `springboot(name = "smp_rules_spring", boot_app_class = "com.profiletailors.smp.SmpApplication", boot_launcher_class = "org.springframework.boot.loader.launch.JarLauncher", ...)` targeting `:smp_lib` first. _(Blocked: `rules_spring@2.6.3` breaks Bazel 9.1.0 analysis with `JavaInfo` undefined; keeping it active would violate stable-path preservation.)_
- [ ] 3.4 If `rules_spring` rejects `kt_jvm_library`, add `java_library(name = "smp_lib_java_adapter", exports = [":smp_lib"], runtime_deps = [":smp_lib"])` and rewire `smp_rules_spring` to the adapter; keep the adapter absent if direct wiring works. _(Blocked behind 3.3 compatibility failure before target activation.)_

## Phase 4: Validation and evidence capture

- [x] 4.1 Run `bazel query`/`cquery` to prove both stable and experimental labels exist and that `//:smp` still resolves only to `//server/smp:smp`; save command/output snippets for the change evidence. _(Experimental packaging label intentionally not activated because upstream compatibility issue would break the stable path during analysis.)_
- [x] 4.2 Run `bazel build //shared/common:common_lib //shared/spring-boot-common:spring_boot_common_lib //server/smp:smp_lib` and capture pass/fail output as native compilation proof for every required local module.
- [ ] 4.3 Run `bazel build //server/smp:smp_rules_spring`; if an artifact is produced, inspect jar path, manifest/launcher, and `BOOT-INF` layout; if not, capture the exact packaging blocker. _(Blocked by upstream `rules_spring` Bazel 9 analysis failure before target activation.)_
- [ ] 4.4 Run `bazel run //server/smp:smp_rules_spring` or `java -jar <artifact>` and capture startup logs showing runnable state or the precise runtime failure tied to `SmpApplication`. _(Blocked by 4.3.)_
- [x] 4.5 Re-run `bazel build //server/smp:smp` and capture that the stable Gradle-backed path still builds unchanged.

## Phase 5: Rollback / cleanup decision

- [x] 5.1 Summarize evidence in the change artifacts for go/no-go: direct `kt_jvm_library` vs adapter, Boot 4 launcher behavior, extra packaging deps, and whether proof is success or failed proof.
- [x] 5.2 If the spike is a no-go or too noisy, list the exact rollback set (`MODULE.bazel`, `maven_install.json`, `shared/*/BUILD.bazel`, `server/smp/BUILD.bazel` experimental labels only) while preserving stable targets and aliases.
