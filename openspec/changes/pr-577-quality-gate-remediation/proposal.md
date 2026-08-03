# Proposal: PR #577 Quality-Gate Remediation

## Intent

Keep PR #577 unified while restoring its gates through real remediation. Codecov and Sonar are below
unchanged 80% gates, and Sonar reports 14 open issues. Close findings and add behavior coverage
without weakening gates.

## Scope

### In Scope
- Fix all 14 Sonar issues: 10 accessibility input-label bugs in Analytics, Ideas, and hashtags, plus
  4 code smells in composer and Kotlin hashtag/ideas code.
- Add behavior tests for Ideas, Analytics, AI composer, and hashtags across frontend stores,
  views/components, composables, and APIs.
- Add Kover-relevant backend tests for new Ideas, Analytics, and hashtag paths; preserve the WebFlux
  request-context regression test.
- Re-run quality checks while preserving CI, BDD, and E2E contracts.

### Out of Scope
- Splitting PR #577 or changing delivery order.
- Lowering thresholds, adding exclusions, or changing workflow semantics.
- New product behavior, unrelated refactors, or using BDD/E2E as a Kover/LCOV substitute.

## Capabilities

### New Capabilities
None — remediation and tests for delivered capabilities.

### Modified Capabilities
- `quality-gates`: close all 14 findings and achieve report-backed coverage at existing 80% gates
  without changing CI, BDD, or E2E contracts.

## Approach

Write focused failing tests first for high-missing-line seams: Ideas and Analytics stores/views, AI
API/modal, hashtag composable/panel/APIs, and backend Ideas/Analytics/hashtag handlers, services,
controllers, and repositories. Apply minimum label and code-smell fixes without changing semantics.
Use fresh LCOV/Kover reports to prioritize roughly 1,190+ patch lines until both gates pass with margin.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `apps/web/app/src/modules/{ideas,dashboard,publishing}` | Modified | Accessibility fixes and Vitest behavior coverage. |
| `server/smp/src/{main,test}/kotlin/com/profiletailors/smp/{ideas,analytics,hashtags,identity,tenancy}` | Modified | Kover tests, static fixes, and regression preservation. |
| `codecov.yml`, `sonar-project.properties`, `.github/workflows/quality-gate.yml` | Preserved | Thresholds, scope, and workflow contracts remain unchanged. |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Large Vue components make tests brittle | Med | Assert behavior with focused fixtures, not snapshots. |
| R2DBC branches require PostgreSQL/Testcontainers | Med | Use existing integration infrastructure. |
| Coverage line accounting changes | Med | Use fresh LCOV/Kover reports. |

## Rollback Plan

Revert remediation commits atomically. No threshold, exclusion, schema, or delivery-order change is
required; rollback restores the prior application and test state without migrations.

## Dependencies

- Existing pnpm/Vitest, Gradle/Kover, SonarCloud, Codecov, PostgreSQL/Testcontainers, and CI credentials.

## Success Criteria

- [ ] All 14 Sonar findings close and new-code reliability reaches A.
- [ ] Fresh LCOV/Kover reports satisfy project and patch gates at 80% with margin.
- [ ] CI, BDD, E2E, and WebFlux regression contracts pass without configuration changes.
- [ ] PR #577 remains unified with no unrelated product behavior changes.
