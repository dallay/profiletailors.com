# Tasks: Shared Observability Usage Standard

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 220–340 |
| 400-line budget risk | Low |
| Chained PRs recommended | No |
| Suggested split | Single PR |
| Delivery strategy | ask-on-risk |
| Chain strategy | single-pr |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: single-pr
400-line budget risk: Low

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Publish the canonical usage guide and synchronize navigation/catalog references | PR 1 | `base: main`; `branch: shared-observability-usage-standard`; `position: 1`; issue/Linear metadata not supplied; include focused documentation validation |

## Phase 1: Validation Contract / RED

- [x] 1.1 Derive a documentation checklist from every specification scenario: contract inventory, layer boundary, Kotlin-only web boundary, disclosed redaction gap, cause preservation, deferred correlation, naming/evolution, and test matrix.
- [x] 1.2 Establish the validation scope before editing: changed Markdown paths, required local reference targets, current-vs-proposed labels, and safe-example assertions; run baseline `docs-lint`, `docs-links`, and `doc-check` as available.

## Phase 2: Canonical Documentation / GREEN

- [x] 2.1 Create `docs/observability-usage.md` with separate implemented-contract and proposed-norm sections covering shared types, SMP ports/bindings, web boundary, redaction, errors/causes, correlation gap, evolution, testing, ownership, and explicit out-of-scope enforcement.
- [x] 2.2 Add a safe structured-event example in `docs/observability-usage.md` using a stable dotted name and low-cardinality IDs; demonstrate exclusion of token, password, secret, authorization, cookie, set-cookie, PII, email, and OTP values.
- [x] 2.3 Add cross-references in the canonical guide to the OpenSpec artifacts, source contracts, current sink/pipeline tests, ADR-0002, ADR-0010, C4 observability context, and `docs/observability-contracts.md` without duplicating SLA/SLO/SLI ownership.

## Phase 3: Navigation and Dependency Catalog

- [x] 3.1 Modify `docs/README.md` to make `observability-usage.md` canonical, retain the OpenSpec change link as evidence, and refresh the document date.
- [x] 3.2 Modify `docs/architecture/shared/dependencies.md` to catalog `:shared:observability`, its framework-free Kotlin contract, SMP consumption, and a link to the usage guide; refresh the date.

## Phase 4: Documentation Verification / REFACTOR

- [x] 4.1 Run targeted `pnpm exec markdownlint-cli2` on all changed Markdown, `just docs-links`, and `just doc-check`; fix every introduced lint, broken-link, or stale-date finding.
- [x] 4.2 Manually reconcile the guide against the source files and specification scenarios, confirming no code, redaction enforcement, or correlation-id implementation was added; record exact validation results for apply/verify.
- [x] 4.2 Manually reconcile the guide against the source files and specification scenarios, confirming no code, redaction enforcement, or correlation-id implementation was added; record exact validation results for apply/verify.
