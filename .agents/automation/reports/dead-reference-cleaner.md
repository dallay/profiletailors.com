# Dead Documentation and Reference Cleaner Report

## Overview

### Purpose

The Dead Documentation and Reference Cleaner has audited the codebase for nonexistent files, incorrect relative markdown paths, outdated anchors, and incorrect command reference links in accordance with the repository framework.

### Execution Result

The audit concluded with **CHANGES_APPLIED**. The cleaner detected stale references to password-recovery change artifacts that have been archived, incorrect casing in relative command hub file references, and incorrect relative paths to standardized AI agent guidelines, and corrected them safely.

### Scope Inspected

- **Documentation Surfaces (`docs/`)**: Inspected runbooks, compliance documents, and getting-started guides.
- **Specification Surfaces (`openspec/`)**: Checked root READMEs and specifications for any broken internal relative paths.
- **Frontend Codebase (`apps/web/app/`)**: Inspected package README files for correct relative documentation link structures.

## Changes

### Changes Applied

- Updated `docs/runbooks/password-recovery.md` to point to correct archived password-recovery change files (spec, design) and corrected casing of relative Justfile path (`../../Justfile`).
- Updated `openspec/README.md` to point to correct archived password-recovery state, spec, progress, and verify-report artifacts under `changes/archive/2026-07-29-password-recovery/`.
- Updated `apps/web/app/README.md` to reference the correct relative path to standardized AI agent guidelines (`../../../.agents/AGENTS.md`).

### Evidence Table

| Target File | Finding ID | Description | Original Link | Corrected Link / Status | Outcome |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `docs/runbooks/password-recovery.md` | PASSWORD-RECOVERY-DOC-DRIFT | Points to active `changes/password-recovery/` spec and design files, which have been archived. | `../../openspec/changes/password-recovery/spec.md` | `../../openspec/changes/archive/2026-07-29-password-recovery/spec.md` | **Resolved** |
| `docs/runbooks/password-recovery.md` | JUSTFILE-PATH-CASING | Reference to `justfile` uses lowercase path which mismatches the actual file `Justfile`. | `../../justfile` | `../../Justfile` | **Resolved** |
| `openspec/README.md` | OPENSPEC-README-PASSWORD-RECOVERY | Password recovery files are referenced under active `changes/password-recovery/` instead of `changes/archive/2026-07-29-password-recovery/`. | `changes/password-recovery/spec.md` | `changes/archive/2026-07-29-password-recovery/spec.md` | **Resolved** |
| `apps/web/app/README.md` | APP-AGENTS-DOC-LINK | Reference points to `../../../AGENTS.md` which is not present/tracked at root in git. | `../../../AGENTS.md` | `../../../.agents/AGENTS.md` | **Resolved** |
| `docs/compliance/README.md` | MARKETING-PAGE-INTENTIONAL-ROUTES | Links to deployed web routes (`/privacy`, `/terms`, `/acceptable-use`, `/cookies`) are reported as missing filesystem files. | `/privacy` etc. | Ignored (intentional marketing routes per README definition) | **Ignored** |

## Usage

### Validation Table

| Check Name | Target/Command | Outcome | Details |
| :--- | :--- | :--- | :--- |
| File Casing Check | `Justfile` vs `justfile` | **Passed** | Upper-case `Justfile` matches filesystem exactly. |
| Relative Path Check | Archive Password-Recovery Paths | **Passed** | Paths correctly resolve to existing archived files in `openspec/changes/archive/2026-07-29-password-recovery/`. |
| AI Agent Guide Check | Standard AGENTS.md Path | **Passed** | Path correctly resolves to `.agents/AGENTS.md` from dashboard. |
| Test Suite Validation | `just ci-local` | **Passed** | Verification that documentation corrections do not impact execution or linting. |

## Troubleshooting

### Unresolved Findings

None.

### Blockers

None.

## References

### Automation State

- **Task**: `dead-reference-cleaner`
- **Result Status**: `CHANGES_APPLIED`

### Risk Assessment

- **Overall Risk**: **LOW** (Changes are strictly limited to documentation, relative links, and markdown reference files).

### Human Review Notes

All corrections conform exactly to the existing state of the repository. All resolved links have been manually verified to exist on the filesystem. No unguessable references have been modified, in adherence to the safety rules of the maintenance framework.
