# Archive Report: Fix Restrictive Component Scan

**Change**: `fix-restrictive-component-scan`
**Archived by**: `sdd-archive`
**Date**: 2026-06-11
**Archived to**: `openspec/changes/archive/2026-06-11-fix-restrictive-component-scan/`

---

## Change Summary

The change fixed a restrictive `component-scan` configuration in `SmpApplication` that prevented
Spring from discovering beans annotated with the custom `@Service` marker. The fix involved:
1. Meta-annotating the custom `@Service` with `@Component` so Spring's default filter discovers it
2. Dropping the explicit `includeFilters` workaround from `SmpApplication`
3. Normalizing all application-layer beans to use the custom `@Service` marker
4. Adding ArchUnit guard rails to prevent regression
5. Adding regression tests for bean stereotype discovery and event-consumer uniqueness

**Implementation**: 6 commits on main (7c89f097, 64074da4, 9f0aa815, 1889634d, 9e489112, 73c6e1f7)

---

## Verification Results

| Metric | Value |
|--------|-------|
| Tests | 265 passed, 0 failures, 4 skipped |
| `./gradlew :server:smp:check` | BUILD SUCCESSFUL |
| Pre-commit hooks | Passed on all commits |

---

## Spec Compliance

18 / 18 scenarios compliant (from verify-report.md). All requirements satisfied:
- Custom `@Service` meta-annotated with `@Component`
- `SmpApplication` drops `includeFilters`, keeps documented `excludeFilters`
- `RateLimitConfiguration` preventive fix
- `CredentialEncryptionService` uses custom marker
- `PublishingCredentialsProperties` registered via `@EnableConfigurationProperties`
- ArchUnit guard rails in place
- Regression tests added

---

## Specs Synced

| Domain | Action | Details |
|--------|--------|---------|
| `component-scan` | Created | New main spec created from delta spec (8 requirements, 18 scenarios) |

**Source of truth updated**: `openspec/specs/component-scan/spec.md`

---

## Archive Contents

- `proposal.md` ✅
- `specs/` ✅ (domain: `component-scan`)
- `design.md` ✅
- `tasks.md` ✅ (21/21 tasks complete)
- `verify-report.md` ✅ (PASS, 0 CRITICAL issues)
- `exploration.md` ✅
- `state.yaml` ✅

---

## SDD Cycle Complete

The change has been fully planned, implemented, verified, and archived.
Ready for the next change.