# Archive Report: Working Tree Remediation Discovery

**Change**: `working-tree-remediation-discovery`  
**Mode**: OpenSpec  
**Archived**: 2026-08-02  
**Archived to**: `openspec/changes/archive/2026-08-02-working-tree-remediation-discovery/`  
**Verification verdict**: PASS WITH WARNINGS  
**Critical issues**: None

## Eligibility

The final `verification.md` explicitly approves archive. The current MVP contract is implemented;
the known backend/app baseline failures, registration Playwright harness warning, and direct
scenario-coverage gaps remain documented warnings. Distributed waitlist rate limiting is explicitly
deferred outside MVP: no Redis or other distributed store was added, the bounded per-JVM Caffeine
limiter remains the accepted behavior, SMP waitlist limiting defaults OFF, and DALLAY-512/DALLAY-513
remain follow-up blockers.

## Specs Synced

| Domain | Action | Details |
|---|---|---|
| `privacy-compliance` | Updated | Modified `Consent Banner UI Structure` with the always-dark fixed palette and contrast scenarios; 0 added, 1 modified, 0 removed requirements. |
| `e2e` | Created + updated | Promoted the delta to `openspec/specs/e2e/spec.md`; the existing login plan already matched the 12-character contract, and the register plan's Spanish placeholder was corrected. |
| `marketing-a11y-seo` | Created | Promoted the full delta as the new main spec with 3 added requirements. |
| `dependency-licensing` | Created | Promoted the full delta as the new main spec with 2 added requirements. |
| `iam` | Updated | Added password minimum enforcement and SEC-001 allowlist scenarios while preserving existing IAM requirements; 1 added, 1 modified, 0 removed requirements. |
| `publishing` | Updated | Added the SEC-002 OAuth state signer placeholder-secret guard; 1 added, 0 modified, 0 removed requirements. |
| `lead-capture-waitlist` | Updated | Synced the accepted per-JVM Caffeine/default-OFF MVP decision only. The distributed follow-up requirement was intentionally not promoted to the main spec; 0 added, 0 modified, 0 removed requirements. |
| `quality-gates` | Created | Promoted the full delta as the new main spec with 2 added requirements. |
| `compliance-docs` | Created | Promoted the full delta as the new main spec with 3 added requirements. |

The explicit MVP deferral is preserved in the main waitlist spec as a decision and follow-up note;
the main specs contain no current distributed-rate-limit requirement.

## Source of Truth Updated

- `openspec/specs/privacy-compliance/spec.md`
- `openspec/specs/e2e/login-flow.md`
- `openspec/specs/e2e/register-flow.md`
- `openspec/specs/e2e/spec.md`
- `openspec/specs/marketing-a11y-seo/spec.md`
- `openspec/specs/dependency-licensing/spec.md`
- `openspec/specs/iam/spec.md`
- `openspec/specs/publishing/spec.md`
- `openspec/specs/lead-capture-waitlist/spec.md`
- `openspec/specs/quality-gates/spec.md`
- `openspec/specs/compliance-docs/spec.md`

## Archive Contents Verified

- `exploration.md` ✅
- `proposal.md` ✅
- `specs/` — 9 delta specs ✅
- `design.md` ✅
- `tasks.md` ✅ (28/28 checklist items complete; F7.1 explicitly cancelled/deferred)
- `apply-progress.md` ✅
- `verification.md` ✅
- `state.yaml` ✅ (`current_phase: archive`, `next: none`)
- `archive-report.md` ✅

## SDD Cycle Complete

The change was planned, implemented, verified, and archived. No application code was modified, and
no commit or push was performed by the archive phase.
