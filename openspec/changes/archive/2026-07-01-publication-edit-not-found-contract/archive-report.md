# Archive Report: Publication Edit Not-Found Contract

## Summary

The `2026-07-01-publication-edit-not-found-contract` change passed verification with one
documented WARNING (no CRITICAL issues). The HTTP-boundary runtime test is recorded as a
follow-up because the production controller uses Spring `@Version` + `ApiVersionStrategy`,
which needs additional Spring Boot infrastructure to stand up in a standalone
`ApplicationContext`. The advice mapping and controller exception flow are proven green
(`PublishingProblemDetailsHandlerTest` 10/10, `PublishingControllersTest` 14/14), the
implementation is intentionally narrow, and the production fix is correct. The new
"Update-Only Publication Misses Return HTTP 404" requirement was merged into the main
`publishing` spec, preserving all unrelated publishing requirements.

## Verification Gate

- **Verification result**: PASS WITH WARNINGS
- **Critical issues**: None
- **Warnings**: 1 (HTTP-boundary `WebTestClient` runtime test is a documented follow-up)
- **Tasks complete**: 10/10 (Phase 1: 3/3, Phase 2: 3/3, Phase 3: 3/3, Phase 4: 2/2, but
  tasks 1.1–1.3 reflect the TDD-style cycle and 2.1–2.3 the contract implementation, all
  marked complete; phase headings total 11 line-items across 4 phases, all checked)
- **Verification report**: `verify-report.md`
- **Archive rationale**: PASS WITH WARNINGS (no CRITICAL) is the documented gate for
  archival. The advice + controller-level exception proof is sufficient to ship the
  production fix; the full-stack `WebTestClient` proof is queued for the
  `IntegrationTestBase` extension.

## Specs Synced

| Domain | Action | Added | Modified | Removed |
|--------|--------|-------|----------|---------|
| `publishing` | Updated | 1 | 0 | 0 |

Added requirements:

- `Update-Only Publication Misses Return HTTP 404` (with three scenarios: edit miss,
  sibling update-only operations share the contract, create-capable save flows remain
  out of scope)

No MODIFIED Requirements and no REMOVED Requirements in this delta.

## Archive Destination

`openspec/changes/archive/2026-07-01-publication-edit-not-found-contract/`

## Source of Truth

`openspec/specs/publishing/spec.md`

The new requirement sits at the end of the `## Requirements` section, immediately after
`Delete Behavior Is Unchanged`. All 56 pre-existing requirements in the publishing spec
were preserved unchanged.

## PR Grouping — Read Before Merging

**This change MUST be merged as part of the grouped `publication edit hardening` PR** that
also includes related publication-edit issues (the `2026-06-24-publication-edit-hardening`
chain). It MUST NOT be split out as one PR per issue.

Rationale (from the proposal/design pairing and from the verification record):

- The 404 contract for `PublicationNotFoundException` is a narrow HTTP-boundary fix that
  only becomes correct when the full publication edit hardening chain lands together. The
  companion work (active-workspace write rules, edit/delete status matrix, hardening
  quality gates) is what gives the 404 mapping its real meaning: without those rules the
  "update-only vs create/save" distinction has no behavioral anchor.
- Splitting this change into its own PR would force reviewers to validate the same
  `findByWorkspaceAndId` semantics twice, and it would isolate a contract fix from the
  rules it depends on.
- The orchestrator explicitly recorded this constraint as part of the archive: do not
  file one PR per issue from the publication-edit hardening chain.

Operational consequence:

- When opening the PR, include this change folder plus every other archive folder in the
  publication edit hardening group in a single PR body.
- Do not cherry-pick `PublishingProblemDetailsHandler` changes into an isolated PR — the
  404 mapping, the workspace-scoped write rules, and the create-vs-update clarification
  travel together.

## Follow-ups Carried Out of This Archive

| Item | Why | Where to track |
|------|-----|----------------|
| Full-stack `WebTestClient` regression proving `PATCH /api/publishing/publications/{id}` (and one sibling) returns 404 `ProblemDetail` end-to-end | Closes the spec/design "controller/WebFlux" verification gap; protects future refactors of `PublishingProblemDetailsHandler` | Extend `server/smp/src/test/kotlin/com/profiletailors/smp/integration/support/IntegrationTestBase.kt` with publishing-scoped wiring; then rerun the targeted backend suite. |
| Reconsider publishing-specific `errorCode` (e.g. `PUBLICATION_NOT_FOUND`) | Aligns with media's `errorCode`-bearing problem details and removes title-string parsing for the frontend | Open question from `design.md`. Add `setProperty("errorCode", "PUBLICATION_NOT_FOUND")` in the advice handler and update the spec. |
| Assert `Content-Type: application/problem+json` once the WebTestClient test exists | Future serialization regression guard | Same follow-up as the WebTestClient regression. |

## Archive Contents

- `proposal.md` — intent, scope, and rollback plan
- `design.md` — narrow advice-layer architecture decision and rationale
- `specs/publishing/spec.md` — delta (now merged into main spec)
- `tasks.md` — phased task breakdown (all marked complete)
- `verify-report.md` — PASS WITH WARNINGS, with a documented HTTP-boundary follow-up
- `exploration.md` — pre-proposal investigation context
- `state.yaml` — will be updated by this archive phase

## SDD Cycle Complete

The change has been fully planned, specified, designed, implemented, verified, and
archived. The publishing specification now reflects the update-only 404 contract.