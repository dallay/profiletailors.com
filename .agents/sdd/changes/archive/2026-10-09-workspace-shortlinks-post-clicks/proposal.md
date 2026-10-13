# Workspace Shortlinks and Click Metrics Proposal

## Overview

Integrate the first-party shortener into post creation and let users view clicks for shortlinks belonging to their workspace. Authorization must prevent reading or inferring resources or metrics from other workspaces. The archived 2026-10-04 change considered an advanced system, not this specific scope. Core V1 backend already exists, but click recording and a metrics UI have not been demonstrated.

## Changes

### Included

- Define and integrate when/how links are shortened during post creation, without silently changing destinations or content.
- Measure shortlink clicks and provide workspace-scoped metrics.
- Enforce workspace isolation in link resolution/administrative queries and metrics, including cross-workspace negative scenarios.
- Design the minimal click recording and query mechanism after verifying available infrastructure.

### Excluded

- Reproducing the full historical design: durable outbox, Edge/Logpush reconciliation, custom domains, QR codes, abuse automation, or extreme SLO/capacity targets.
- Deciding retention, personal data, guaranteed accuracy, or measurement technology without evidence and an explicit decision.
- Changing infrastructure, deploying, or implementing before the specification, design, and tasks are approved.

### Capabilities

- New: `publishing-shortlinks` for shortlink integration in post creation.
- New: `workspace-shortlink-click-analytics` for workspace-isolated click queries.
- Modified: `publishing`, changing the contractual behavior of creating posts with links.

### Approach

Reuse Core V1 as the owner of links; connect post creation to existing link management/resolution through application contracts, not direct adapter coupling. Add measurement and a query surface scoped to the authenticated workspace. During specification, compare synchronous recording/existing persistence with minimal alternatives, define what counts as a click, and resolve retention/privacy before fixing the contract. Do not assume the current redirect captures events or that a bus, outbox, analytics, or retention configuration exists.

### Affected Areas

| Area | Impact | Description |
|---|---|---|
| `.agents/sdd/specs/publishing/spec.md` | Modified | Linked publication creation and shortlink presentation |
| New `publishing-shortlinks` spec | New | Shortener integration contract for publishing |
| New `workspace-shortlink-click-analytics` spec | New | Click recording/query and isolation |
| `server/smp/shortlinks` | Modified | Measurement/query; scope subject to design |
| `apps/web/app` publishing and analytics | Modified | Composer flow and metrics query |
| `docs/architecture/adr/0028-defer-shortlinks-beyond-core-v1.md` | Modified | Explicitly authorize this bounded product slice before triggers while preserving deferral of the rest |

### Risks

| Risk | Probability | Mitigation |
|---|---|---|
| ADR-0028 explicitly defers work beyond Core V1 | Medium | Update the ADR through an expressly authorized decision before adopting the specification; do not implicitly reinterpret its triggers |
| Incomplete/duplicate counts or redirect cost | Medium | Compare alternatives using evidence and agree on count semantics and realistic guarantees before design |
| Cross-workspace query leakage | Medium | Derive workspace from authorized context; use adversarial tests/BDD with two workspaces without revealing foreign resource existence |
| Privacy and retention unknowns | Medium | Do not persist personal attributes by default; explicitly decide retention, minimization, and deletion before closing the spec |

### Reversal

Disable the publishing integration and new query/measurement while preserving Core V1 and existing links. Design must ensure reverting UI or instrumentation does not invalidate saved destinations or publications; specify any required data cleanup or migration before implementation.

### Dependencies

- Explicit reconciliation of ADR-0028; scope selection does not authorize unilateral ADR changes.
- Contract investigation for Core V1, existing tracking, and workspace authorization controls.

### Success Criteria

- [ ] Creating a publication with links has a defined and visible shortening result under the contractual decision.
- [ ] Click metrics are queried only for shortlinks accessible in the current workspace.
- [ ] Tests cover cross-workspace access attempts without disclosing another workspace's resource or metrics.
- [ ] Accuracy, click semantics, retention, and privacy are specified without claiming unproven capabilities.
- [ ] ADR-0028 explicitly records the bounded authorization and keeps the remaining advanced capabilities deferred.

## Usage

This archived proposal is historical context; the approved specifications and current implementation own behavior.

## Troubleshooting

If archived scope conflicts with current product or architecture contracts, follow the current authoritative specification and ADR rather than inferring intent from this proposal.

## References

- `.agents/sdd/specs/publishing/spec.md`
- `.agents/sdd/specs/publishing-shortlinks/spec.md`
- `.agents/sdd/specs/workspace-shortlink-click-analytics/spec.md`
- `docs/architecture/adr/0028-defer-shortlinks-beyond-core-v1.md`
