# Dead Documentation and Reference Cleaner Report

## Purpose

Detect and clean dead repository references, stale paths, and obsolete links across repository documentation, OpenSpec artifacts, ADRs, and configurations.

## Execution Result

`CHANGES_APPLIED`: Identified and resolved dead reference findings across documentation, task plans, and OpenSpec files. All relative markdown links have been audited and verified across 1123 markdown files.

## Scope Inspected

- `docs/runbooks/`
- `docs/architecture/`
- `docs/architecture/adr/`
- `openspec/`
- `.agents/skills/`
- `.agents/rpi/plan/tasks/`

## Changes Applied

- `.agents/rpi/plan/tasks/back-office-qa.md`: Corrected relative depth for Cucumber feature and Playwright spec links (from `../../` to `../../../../`).
- `docs/runbooks/password-recovery.md`: Updated dead links pointing to deleted archived change files to point to the active `openspec/specs/password-recovery-ui/spec.md`.
- `openspec/README.md`: Updated password-recovery references to point to `specs/password-recovery-ui/spec.md`.
- `docs/architecture/README.md`: Corrected directory reference from `shared/lead-capture:common` to `shared/lead-capture/common`.
- `docs/architecture/adr/0012-agpl-commercial-strategy.md`: Removed dead markdown link to deleted proposal file.
- `.agents/skills/spring-boot/references/swagger-standard.md`: Escaped bracketed method name to prevent link parsing ambiguity.

## Evidence Table

| Finding ID | Location | Target / Issue | Severity | Status | Remediation |
| :--- | :--- | :--- | :--- | :--- | :--- |
| FINDING-001 | `docs/runbooks/password-recovery.md` | Deleted archive path | LOW | Resolved | Updated link to active contract `openspec/specs/password-recovery-ui/spec.md` |
| FINDING-002 | `openspec/README.md` | Deleted archive path | LOW | Resolved | Updated link to active contract `specs/password-recovery-ui/spec.md` |
| FINDING-003 | `docs/architecture/README.md` | Path syntax error (`shared/lead-capture:common`) | LOW | Resolved | Corrected to `shared/lead-capture/common` |
| FINDING-004 | `docs/architecture/adr/0012-agpl-commercial-strategy.md` | Deleted proposal file | LOW | Resolved | Unlinked dead proposal reference |
| FINDING-005 | `.agents/rpi/plan/tasks/back-office-qa.md` | Incorrect relative path depth (`../../`) | LOW | Resolved | Fixed relative paths to `../../../../` |

## Validation Table

| Check Name | Target | Status | Notes |
| :--- | :--- | :--- | :--- |
| `markdown-link-audit` | repository-markdown-files | Passed | Audited all relative markdown link targets across 1123 markdown files. |
| `just ci-local` | repository | Passed | Executed full local CI suite. |

## Unresolved Findings

None.

## Blockers

None.

## Automation State

- **Last Execution:** `2026-09-28T12:00:00Z`
- **Schema Version:** `1`
- **Task Identity:** `dead-reference-cleaner`
- **Execution Result:** `CHANGES_APPLIED`

## Risk Assessment

- **Overall Risk:** LOW. All changes are minimal, evidence-backed documentation and reference corrections without code or contract side effects.

## Human Review Notes

All corrected references have been verified against current on-disk paths and specifications.
