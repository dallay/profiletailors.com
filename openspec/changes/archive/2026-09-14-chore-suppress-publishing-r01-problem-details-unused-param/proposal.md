# Proposal: R01 — Remove 3 UNUSED_PARAMETER Suppressions in PublishingProblemDetailsHandler

## Intent

Three `UNUSED_PARAMETER` suppressions in `PublishingProblemDetailsHandler` are live debt paydown under epic #1046: each suppressed `handle(exception: ...)` never reads its parameter (fixed detail + title only). Clearing them via rename-and-drop is a zero-behavior pilot batch establishing the pattern for R02 and the Identity (12) / Platform (4) handlers.

## Scope

### In Scope
- `:39` ProviderNotConfigured handler — rename to `handleProviderNotConfigured`, drop parameter + `@Suppress`.
- `:71` PublicationNotFound handler — rename to `handlePublicationNotFound`, drop parameter + `@Suppress`.
- `:158` RecurringScheduleNotFound handler — rename to `handleRecurringScheduleNotFound`, drop parameter + `@Suppress`.
- Lint-oracle pre-check: delete each `@Suppress` → `just backend-lint` must flag it live; any dead site becomes deletion-only with oracle evidence cited.
- New web-slice regression test asserting each renamed handler still maps exception → expected status/title (fails pre-rename, passes post-rename).
- Gates: `just backend-lint` PASS and `just backend-check` PASS; existing `infrastructure/http` web tests + `publishing-publications.feature` stay green.

### Out of Scope
- `TooManyFunctions` (`:35`) and remaining 8 `UNUSED_PARAMETER` sites — R02 follow-up.
- `detekt.yml`, `detekt-baseline.xml` (ratchet-only, hand-untouched), `shared/` modules, or production-logic change.
- Parameters that ARE read (`reason`/`denial`/`message`/`jobId`) — kept as-is.

## Capabilities

### New Capabilities
None — pure suppression removal + handler rename, no spec-level behavior changes.

### Modified Capabilities
None — no requirement changes.

## Approach

Unused parameter is load-bearing for Kotlin overload resolution (all handlers named `handle`), so rename-then-drop: Spring MVC routes `@ExceptionHandler` by annotation, never method name — zero behavior change. Rename-without-drop would collapse to identical JVM signatures (compiler-caught, not silent). Baseline verified zero IDs for these symbols; no coupling. Hexagonal rule holds: infrastructure adapter method names only, no callers.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `server/smp/.../publishing/infrastructure/http/PublishingProblemDetailsHandler.kt:39,:71,:158` | Modified | 3 renames + param/suppression drops |
| `server/smp/.../publishing/infrastructure/http/` web-slice test | New | Rename regression test (TDD) |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Oracle shows a site is dead-suppressed, not live | Low | Convert to deletion-only line, cite oracle evidence |
| Rename breaks Spring dispatch assumption | Low | Regression test + BDD feature gate; revert rename |

## Rollback Plan

Revert rename + re-add dropped lines (`git diff` shows ≤3 hunks). No migration, config, or data involved.

## Dependencies

None. Epic tracker: dallay/profiletailors.com#1046. Zero new `@Suppress`.

## Success Criteria

- [ ] 3 suppressions + unused params removed, renamed handlers dispatch correctly
- [ ] New regression test red pre-rename, green post-rename
- [ ] `just backend-lint` PASS with zero new findings (baseline only shrinks or stays equal)
- [ ] `just backend-check` PASS (arch tests green)
- [ ] Zero new `@Suppress` introduced

## Future Batch Order

R02 (remaining 8 + TooManyFunctions) → IdentityProblemDetailsHandler (12) → PlatformProblemDetailsHandler (4).
