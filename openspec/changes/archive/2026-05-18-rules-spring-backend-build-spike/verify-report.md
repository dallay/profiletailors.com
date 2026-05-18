# Verification Report

**Change**: `2026-05-18-rules-spring-backend-build-spike`
**Date**: 2026-05-18
**Verifier**: sdd-verify

---

## Completeness

| Metric | Value |
|--------|-------|
| Tasks total | 14 |
| Tasks complete | 10 |
| Tasks incomplete | 4 |

### Incomplete tasks

- **3.3** Add experimental `springboot(...)` target `//server/smp:smp_rules_spring`.
- **3.4** Add optional `smp_lib_java_adapter` if `rules_spring` rejects `kt_jvm_library`.
- **4.3** Build experimental packaging target and inspect artifact layout.
- **4.4** Run packaged artifact and capture runnable Spring Boot startup evidence.

### Completeness assessment

These tasks are incomplete **because of a documented upstream compatibility blocker**, not because the implementation stopped without classification. The blocker is consistent across `tasks.md`, `apply-notes.md`, and source comments in `server/smp/BUILD.bazel`: `rules_spring@2.6.3` fails to load on Bazel `9.1.0` with `JavaInfo` undefined, and activating that rule would endanger the stable path.

Result: **acceptable for a spike outcome, but not equivalent to successful packaging proof**.

---

## Build & Execution Evidence

### Verification commands executed

```bash
bazel query "set(//server/smp:smp //server/smp:smp_lib //shared/common:common_lib //shared/spring-boot-common:spring_boot_common_lib //:smp //:all_servers)"
bazel cquery //:smp
bazel query "deps(//:all_servers, 1)"
bazel build --tool_java_runtime_version=remotejdk_21 //shared/common:common_lib //shared/spring-boot-common:spring_boot_common_lib //server/smp:smp_lib
bazel build //server/smp:smp
./gradlew test --rerun-tasks
./gradlew build
```

### Query / topology evidence

- `bazel query` returned:
  - `//:all_servers`
  - `//:smp`
  - `//server/smp:smp`
  - `//server/smp:smp_lib`
  - `//shared/common:common_lib`
  - `//shared/spring-boot-common:spring_boot_common_lib`
- `bazel cquery //:smp` completed successfully and resolved the root alias target.
- `bazel query "deps(//:all_servers, 1)"` returned only:
  - `//:all_servers`
  - `//server/smp:smp`

Interpretation: the stable root/aggregate entrypoints still resolve only to the stable Gradle-backed target.

### Native Bazel compilation proof

**Command**: `bazel build --tool_java_runtime_version=remotejdk_21 //shared/common:common_lib //shared/spring-boot-common:spring_boot_common_lib //server/smp:smp_lib`

**Result**: ✅ Passed

Validated local proof targets:
- `//shared/common:common_lib`
- `//shared/spring-boot-common:spring_boot_common_lib`
- `//server/smp:smp_lib`

Interpretation: the minimum local dependency closure required by the spike compiles natively under Bazel.

### Stable path regression proof

**Command**: `bazel build //server/smp:smp`

**Result**: ✅ Passed

Artifact reported by Bazel:
- `bazel-bin/server/smp/smp.jar`

Interpretation: the stable Gradle-backed packaging path remains available and unchanged.

### Gradle verification

**Tests**: `./gradlew test --rerun-tasks` → ✅ Passed
- Parsed JUnit XML totals: **98 passed / 0 failed / 0 errors / 0 skipped**

**Build**: `./gradlew build` → ✅ Passed
- Included successful `detekt`, `test`, and `build`
- Non-blocking compiler warnings were emitted in Kotlin sources/tests, but no failures occurred

### Coverage

`openspec/config.yaml` sets `coverage_threshold: 0`.

Interpretation: coverage is effectively non-gating for this change. No separate coverage run was required to make a go/no-go decision for this spike.

---

## Spec Compliance Matrix

> Note: this spike's behavior is build-system behavior, not feature-path application behavior. There are no dedicated automated test files for the spike scenarios themselves. Compliance below is therefore proven by **executed Bazel/Gradle verification commands** and repository state, not by new unit tests specific to the spike.

| Requirement | Scenario | Runtime evidence | Result |
|-------------|----------|------------------|--------|
| Stable Backend Build Path Preservation | Stable path remains available when spike target is added | `bazel build //server/smp:smp` passed; root alias and aggregate queries still point only to stable target | ✅ COMPLIANT |
| Stable Backend Build Path Preservation | Experimental path is isolated from stable consumers | `bazel query "deps(//:all_servers, 1)"` showed only `//server/smp:smp`; `BUILD.bazel` alias unchanged | ✅ COMPLIANT |
| Minimum Native Bazel Compilation Coverage for the Spike | Minimum required local modules compile natively for the spike | Native Bazel build passed for `common_lib`, `spring_boot_common_lib`, and `smp_lib` | ✅ COMPLIANT |
| Minimum Native Bazel Compilation Coverage for the Spike | Wrapper-only execution does not satisfy native compilation proof | Proof used native Bazel builds for local modules, not Gradle wrappers | ✅ COMPLIANT |
| Minimum Native Bazel Compilation Coverage for the Spike | Missing local dependency coverage fails the spike proof | Required local closure was covered by successful native builds | ✅ COMPLIANT |
| Packaging Validation Evidence Classification | Packaging validation succeeds with runnable artifact evidence | No runnable `rules_spring` artifact was produced | ⚠️ FAILED PROOF RECORDED |
| Packaging Validation Evidence Classification | Packaging validation fails because artifact is not runnable | Failure classified before activation because `rules_spring@2.6.3` cannot load on Bazel 9.1.0 (`JavaInfo` undefined) | ✅ COMPLIANT |
| Packaging Validation Evidence Classification | Ambiguous packaging outcome is treated as failure | Outcome was explicitly classified as failed proof, not success | ✅ COMPLIANT |
| Fallback Behavior for Spike Failure | Experimental target failure falls back to stable path | Stable path continued to build successfully after blocker classification | ✅ COMPLIANT |
| Fallback Behavior for Spike Failure | Spike rollback removes only experimental behavior | Proposal/tasks/apply notes define rollback limited to experimental wiring while preserving stable path | ✅ COMPLIANT |

**Compliance summary**: 9 of 10 scenarios compliant, with the only non-compliant success scenario being the one that requires successful runnable packaging proof. That miss is expected and correctly classified as the spike's upstream blocker outcome rather than hidden or misreported success.

---

## Correctness: Requirement-by-Requirement Evaluation

| Requirement | Status | Notes |
|------------|--------|-------|
| Stable Backend Build Path Preservation | ✅ Implemented | `BUILD.bazel` still maps `//:smp -> //server/smp:smp`; `//:all_servers` still contains only `//server/smp:smp`; stable build passed. |
| Minimum Native Bazel Compilation Coverage for the Spike | ✅ Implemented | Native Bazel libraries exist for `shared/common`, `shared/spring-boot-common`, and `server/smp`; all built successfully with Bazel using Java 21 tool runtime. |
| Packaging Validation Evidence Classification | ⚠️ Partial but correctly classified | No experimental packaging target was activated, so there is no packaged runnable artifact. However, the failure is precisely classified with concrete blocker evidence: upstream `rules_spring` Bazel 9 incompatibility (`JavaInfo` undefined). |
| Fallback Behavior for Spike Failure | ✅ Implemented | Stable fallback remained available and buildable; rollback scope is documented and limited to spike-only wiring. |

---

## Coherence: Design Match

| Decision | Followed? | Notes |
|----------|-----------|-------|
| Preserve the current stable target and root alias unchanged | ✅ Yes | Verified in root `BUILD.bazel` and with Bazel query/cquery output. |
| Add experimental targets as explicit non-default labels | ⚠️ Deviated | `smp_lib` was added, but `smp_rules_spring` was intentionally **not activated** because loading `rules_spring` would break the stable path. Deviation is justified by the spike safety constraint. |
| Model shared modules as Bazel Kotlin/JVM libraries mirroring Gradle boundaries | ✅ Yes | `shared/common/BUILD.bazel` and `shared/spring-boot-common/BUILD.bazel` were created as planned. |
| Use `kt_jvm_library` as the primary modeling rule | ✅ Yes | Implemented across the three local modules. |
| Keep a Java adapter fallback only if needed | ✅ Yes | Adapter was not added prematurely because target activation never progressed past upstream compatibility failure. |
| Preserve Gradle as source of truth outside minimum spike proof | ✅ Yes | Gradle wrapper packaging/tests remained intact; spike work stayed limited to native compilation proof. |
| File changes match design table | ⚠️ Mostly | `MODULE.bazel`, `server/smp/BUILD.bazel`, `shared/common/BUILD.bazel`, `shared/spring-boot-common/BUILD.bazel`, and OpenSpec artifacts changed as expected. Experimental `springboot(...)` target itself was not activated due blocker. |

---

## Explicit Evaluation Requested

### 1) Stable path preservation

**Result**: ✅ Verified

Evidence:
- Root alias in `BUILD.bazel` still points `//:smp` to `//server/smp:smp`.
- `//:all_servers` still references only `//server/smp:smp`.
- `bazel build //server/smp:smp` succeeded.
- `server/smp/BUILD.bazel` explicitly avoids loading `rules_spring` to prevent breaking stable analysis.

Conclusion: stable path preservation was achieved exactly as required.

### 2) Native compilation proof coverage

**Result**: ✅ Verified

Evidence:
- Native Bazel build succeeded for all minimum local proof targets:
  - `//shared/common:common_lib`
  - `//shared/spring-boot-common:spring_boot_common_lib`
  - `//server/smp:smp_lib`
- These are the minimum local modules described by proposal/design/spec as necessary for the spike proof boundary.

Conclusion: native compilation proof coverage is sufficient for the spike's minimum closure.

### 3) Packaging validation status and failure classification

**Result**: ⚠️ Failed proof, correctly classified

Evidence:
- No `smp_rules_spring` target is active in `server/smp/BUILD.bazel`.
- `apply-notes.md` records the concrete upstream blocker:
  - file: `external/rules_spring+/springboot/springboot.bzl`
  - error: `name 'JavaInfo' is not defined`
- Source comment in `server/smp/BUILD.bazel` matches that classification.
- Tasks 3.3, 3.4, 4.3, and 4.4 remain blocked and document the same reason.

Conclusion: packaging validation did **not** succeed, but the failure classification is correct, concrete, and non-ambiguous. This satisfies the spike requirement to produce explicit failure evidence when runnable packaging proof is not possible.

### 4) Rollback / fallback readiness

**Result**: ✅ Ready

Evidence:
- Proposal rollback plan is explicit and limited to spike-only Bazel wiring.
- Tasks 5.1 and 5.2 are completed and document no-go evidence plus rollback scope.
- Stable path stayed intact throughout verification.

Conclusion: rollback/fallback readiness is adequate and operationally safe.

### 5) Are remaining blocked tasks acceptable for this spike outcome?

**Result**: ✅ Yes, with warnings

Reasoning:
- This is a **spike**, not a production migration.
- The spec explicitly allows failed proof if the blocker is concrete and the stable path remains supported.
- The blocked tasks are all downstream of the same upstream incompatibility and do not reflect uninvestigated work.
- What is **not** acceptable would be presenting packaging as partially successful. The implementation does not do that; it records a no-go/blocked packaging outcome clearly.

Conclusion: the blocked tasks are acceptable for archive **only because** the change intent includes investigation and classification, and because the blocker handling is explicit and preserves fallback behavior.

---

## Issues Found

### CRITICAL

None.

### WARNING

1. **No active experimental `smp_rules_spring` target exists in the repository state.** The spike proved native compilation but did not land an invocable packaging proof label because upstream `rules_spring` loading would break stable analysis.
2. **No runnable Spring Boot artifact proof exists for the experimental path.** Packaging success remains unproven for this repo/toolchain combination.
3. **Spec/design success path remains unresolved.** The migration question is answered only up to "native compile yes / packaging blocked upstream," not to "packaging works."
4. **Gradle build emitted Kotlin warnings** that are non-blocking now but may become stricter with future Kotlin versions.

### SUGGESTION

1. Validate a Bazel-9-compatible `rules_spring` release or patch in a follow-up spike before attempting to reactivate `springboot(...)`.
2. If no compatible upstream path exists, archive this spike as a no-go for current toolchain combination and open a separate migration-alternatives change.

---

## Verdict

# PASS WITH WARNINGS

The spike **successfully verified** stable path preservation, native Bazel compilation for the minimum local dependency closure, and fallback safety. It **did not** verify `rules_spring` packaging or runnable artifact proof, but that failure was captured precisely and classified correctly as an upstream Bazel 9 compatibility blocker rather than being misrepresented as partial packaging success.
