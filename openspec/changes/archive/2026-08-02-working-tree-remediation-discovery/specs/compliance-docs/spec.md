# Delta for Compliance Docs

## ADDED Requirements

### Requirement: Retention Governance Claims Validate Against Implementation

Retention documentation (`docs/retention-framework-acceptance-criteria.md`) MUST only claim a governance retention-rules API that actually exists. Before the docs land, the system MUST expose `POST /api/governance/retention/rules` and the `retention_periods` table (committed or in-tree), or the doc claims MUST be softened to match reality. Docs MUST land only after the referenced implementation.

#### Scenario: Governance API exists before the docs claim it

- GIVEN `docs/retention-framework-acceptance-criteria.md` claims `POST /api/governance/retention/rules` and the `retention_periods` table
- WHEN the implementation is verified (route and schema exist, committed or in-tree)
- THEN the API MUST exist
- OR the doc claim MUST be softened to not overstate reality

### Requirement: Audit Report Matches Landed Fixes

`docs/security/audit-report.md` MUST reference only security fixes that actually landed in this change. Its "Fixed in this audit" claims MUST match SEC-001 (`/api/media/proxy` removed from `permitAll()`) and SEC-002 (placeholder-secret guard), and MUST NOT claim fixes absent from the tree.

#### Scenario: Audit claims correspond to real fixes

- GIVEN the audit report lists SEC-001 and SEC-002 as fixed
- WHEN the referenced code is inspected
- THEN `/api/media/proxy` MUST be absent from the public allowlist
- AND `HmacOAuthStateSigner` MUST reject placeholder prefixes

### Requirement: ADR-0012 Acceptance Criteria Met

ADR-0012 (`docs/architecture/adr/0012-agpl-commercial-strategy.md`) MUST be marked Accepted only when its acceptance criteria are met: `just licence-check` MUST exist and run inside `ci-local`, and the AGPL source-offer runbook (`docs/compliance/agpl-source-offer.md`) MUST exist.

#### Scenario: Acceptance criteria verified before status change

- GIVEN ADR-0012 is marked Accepted
- WHEN the acceptance criteria are checked
- THEN `just licence-check` MUST be present in the justfile and included in `ci-local`
- AND the AGPL source-offer runbook MUST exist

## TDD Requirement

Docs-claim validation is a verification gate, not a unit-test surface. Failing-first signals: `just ci-local` (includes `just licence-check`), `just backend-bdd-fast` (SEC-001 authorization feature), and `just backend-test-fast` (SEC-002 guard tests). The retention-governance-API existence check is a manual/grep verification (design open question) that MUST resolve before Slice H lands.
