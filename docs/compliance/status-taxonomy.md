# Compliance Status Taxonomy

## Overview

This document defines the allowed `Status` labels for compliance artifacts so readers can quickly
separate operative public legal pages from internal controls, templates, and staged market packs.

It standardises status wording across `docs/compliance/`.

## Changes

| Version | Date       | Description                                                                  |
|---------|------------|------------------------------------------------------------------------------|
| 1.0     | 2026-07-31 | Added canonical status taxonomy and naming pattern for compliance artifacts. |

## Usage

### Canonical status pattern

Use this format in document headers:

`> **Status:** <class> — <scope or qualifier>`

### Allowed classes

| Class                     | Meaning                                                                                  |
|---------------------------|------------------------------------------------------------------------------------------|
| Active policy baseline    | Public legal policy publication is currently active for the operator-hosted instance.    |
| Active register           | Internal register is currently maintained and in use.                                    |
| Implemented evidence      | Technical/operational evidence exists, but legal/business approval may still be pending. |
| Internal control artifact | Internal evidence/control document; not a public policy document.                        |
| Internal remediation plan | Internal plan for gaps and fixes; not a public policy document.                          |
| Draft blocked             | Draft exists but cannot be relied on as active policy until approvals complete.          |
| Screening framework       | Internal evaluation framework; not itself an approval record.                            |
| Design contract           | Target operating contract/design, pending implementation.                                |
| Design register           | Target register model, pending implementation or completeness.                           |
| Template                  | Reusable template; non-executable until instantiated and approved.                       |
| Conditional               | Required only when a feature, provider, customer model, or jurisdiction is enabled.      |
| Missing                   | Required artifact does not yet exist.                                                    |
| Staged                    | Prepared for controlled future enablement; not active by default.                        |

### Examples

- `> **Status:** Active policy baseline — operator-hosted legal pages approved`
- `> **Status:** Internal control artifact — not a public policy document`
- `> **Status:** Template — no production transfer approved`

### Migration guidance

- Keep the first term in `Status` exactly one taxonomy class from this file.
- Put contextual details after `—`.
- Avoid bare statuses without class context (for example, `No production subprocessors approved`).
- When publication state changes, update both the controlling source
  (`apps/web/marketing/src/legal/legal-publication.ts`) and impacted compliance statuses.

## Troubleshooting

- If a status appears to conflict with runtime legal publication, treat runtime publication state
  and
  approved policy pages as operative, then reconcile internal docs in the same change set.
- If a new document does not fit existing classes, use the closest class and open a follow-up to
  extend this taxonomy.

## References

- `docs/compliance/README.md`
- `docs/compliance/legal-document-register.md`
- `docs/compliance/legal-publication-gate.md`
- `docs/compliance/marketing-legal-baseline.md`
