# Apply notes: 2026-05-18-rules-spring-backend-build-spike

## Outcome summary

This apply slice completed native Bazel compilation proof for the minimum local closure while preserving the stable Gradle-backed backend target unchanged.

The planned experimental `rules_spring` packaging target was **not activated** in `server/smp/BUILD.bazel` because `rules_spring@2.6.3` fails during Starlark load on this repository's Bazel `9.1.0` runtime:

- file: `external/rules_spring+/springboot/springboot.bzl`
- failure: `name 'JavaInfo' is not defined`

Activating that load would break analysis for `//server/smp:smp`, which violates the spike constraint to preserve the stable path.

## Implemented code changes

- Added `rules_spring` Bzlmod dependency in `MODULE.bazel` for controlled spike wiring.
- Added only minimal Maven artifacts needed for native Bazel library modeling and future packaging investigation.
- Regenerated `maven_install.json` with `REPIN=1 bazel run @maven//:pin`.
- Added native Bazel targets:
  - `//shared/common:common_lib`
  - `//shared/spring-boot-common:spring_boot_common_lib`
  - `//server/smp:smp_lib`
- Preserved stable behavior for:
  - `//server/smp:smp`
  - `//:smp`
  - `//:all_servers`

## Validation evidence

### Label / topology proof

- `bazel query "set(//server/smp:smp //server/smp:smp_lib //shared/common:common_lib //shared/spring-boot-common:spring_boot_common_lib //:smp //:all_servers)"`
- `bazel cquery //:smp`
- `bazel query "deps(//:all_servers, 1)"`

### Native compilation proof

Used Java 21 tool runtime explicitly to avoid helper-tool JVM mismatch:

- `bazel build --tool_java_runtime_version=remotejdk_21 //shared/common:common_lib`
- `bazel build --tool_java_runtime_version=remotejdk_21 //shared/spring-boot-common:spring_boot_common_lib`
- `bazel build --tool_java_runtime_version=remotejdk_21 //server/smp:smp_lib`

Results:

- `//shared/common:common_lib` ✅ built
- `//shared/spring-boot-common:spring_boot_common_lib` ✅ built
- `//server/smp:smp_lib` ✅ built

### Stable regression proof

- `bazel build //server/smp:smp` ✅ built unchanged stable artifact

## Important blockers discovered

1. **rules_spring / Bazel 9 compatibility blocker**
   - `rules_spring@2.6.3` load fails with `JavaInfo` undefined.
   - This happens at BUILD analysis time before any experimental target can be isolated safely.
   - Because of that, the experimental `springboot(...)` target could not be kept active without breaking the stable path.

2. **rules_kotlin resource prefix nuance**
   - `resource_strip_prefix` needed repository-relative prefixes, not package-relative prefixes.
   - Fixed for:
     - `shared/spring-boot-common`
     - `server/smp`

3. **rules_jvm_external helper runtime mismatch**
   - Initial native build hit `UnsupportedClassVersionError` in `AddJarManifestEntry`.
   - Resolved for validation by running builds with `--tool_java_runtime_version=remotejdk_21`.

4. **Repository warning only, not blocker**
   - `repo.spring.io/release` returns many `401` warnings for artifacts also available from Maven Central.
   - Resolution still completed through configured repositories, but this is noisy and may be worth cleanup later.

## Go / no-go status for this spike

- **Native compilation proof**: YES
- **Experimental Spring Boot packaging proof via rules_spring**: NO, blocked before target activation by Bazel 9 compatibility issue in upstream `rules_spring`

## Recommended next follow-up

Before continuing packaging work, validate one of these paths in a separate change:

1. patch/fork `rules_spring` for Bazel 9 compatibility (`JavaInfo` import/update), or
2. confirm a newer upstream release/fix exists and upgrade to it, or
3. if no compatible path exists, mark packaging proof as no-go for this repo/toolchain combination.
