# ADR-0012: AGPL-3.0 Commercial Strategy

- Status: Proposed
- Date: 2026-07-17
- Decision owners: Principal Architect
- Scope: profiletailors.com monorepo
- Supersedes: None
- Superseded by: None
- Related:
    - OpenSpec: `legal-compliance-foundation`
    - Issues/PRs: DALLAY-498

## Overview

Profile Tailors is distributed under the GNU Affero General Public License v3.0 (AGPL-3.0).
The AGPL-3.0 network-interaction clause (Section 13) extends copyleft obligations to users who
interact with the software over a network, which applies to SaaS deployments.

The repository also contains:

- **`CONTRIBUTING.md`** — Contributor guidelines and process.
- **`CLA.md`** — Contributor License Agreement requiring contributors to grant a perpetual,
  worldwide, non-exclusive licence.
- Third-party dependencies with varying licence terms.

This ADR documents how the AGPL-3.0 obligations apply to Profile Tailors operations, the boundary
between open-source and proprietary capabilities, and the decision on dual-licensing.

### Decision drivers

- Ensure compliance with AGPL-3.0 source distribution obligations for network-interactive use.
- Define clear boundaries between open-source and proprietary capabilities for commercial offerings.
- Evaluate whether dual-licensing is necessary or feasible at this stage.
- Document third-party licence obligations and CLA mechanics without asserting legal correctness.

### Decision

1. **AGPL-3.0 Section 13 compliance.** Profile Tailors MUST offer source code access to all users
   who interact with the software over a network.
    - **Implementation status: PENDING — requires legal/compliance review.**
    - **Current state:** The canonical source at `github.com/dallay/profiletailors.com` is publicly
      accessible, which partially satisfies Section 13, but the following are not yet established:
        - A prominent user-facing notice or link in the application UI identifying where source can
          be
          obtained.
        - A mechanism to identify the deployed commit or release SHA corresponding to each
          deployment environment (production, staging, preview).
        - A process for serving the exact source artifact (tagged release or commit snapshot) that
          corresponds to the running deployment.
    - **Recommended approach:** Add a `Source` link to the application footer (marketing site and
      dashboard) pointing to the GitHub repository with the current deployed tag. Automate
      deployment tagging in CI so each deploy produces a reachable git tag. This MUST be reviewed
      by legal counsel before going live.

2. **No proprietary fork exists.** All development occurs in the public monorepo. There is no
   separate enterprise edition or closed-source fork at this time.

3. **Dual-licensing is deferred.** The operational complexity and legal cost of maintaining a
   dual-licensing programme (CLA administration, commercial licence drafting, compliance
   enforcement) is not justified at the current stage. This decision MUST be revisited if:
    - A customer explicitly requests a non-AGPL commercial licence, OR
    - Profile Tailors seeks institutional/enterprise sales where AGPL-3.0 is a blocker.

4. All new source files SHOULD include a SPDX licence header:
   `SPDX-License-Identifier: AGPL-3.0-only`

5. The existing CLA (`CLA.md`) and contribution process (`CONTRIBUTING.md`) remain unchanged.
   The CLA grants Profile Tailors the ability to relicense contributions if dual-licensing is
   later adopted.

### Scope and boundaries

- This ADR applies to the `profiletailors.com` monorepo and all derived works.
- Third-party dependencies retain their own licences; this ADR does not modify their terms.
- `LICENSE`, `CONTRIBUTING.md`, and `CLA.md` are NOT modified by this ADR.

## Changes

| Version | Date       | Description                                                            |
|---------|------------|------------------------------------------------------------------------|
| 1.0     | 2026-07-17 | Initial ADR — AGPL-3.0 strategy, no dual-licensing, compliance posture |

## Usage

This ADR governs licensing decisions for the repository. Use it as:

- A reference for answering licensing questions from contributors and users.
- A baseline for evaluating dual-licensing when market demand materialises.
- A compliance checklist for AGPL-3.0 Section 13 obligations.

### When to revisit this decision

The dual-licensing deferral MUST be revisited if:

- A customer explicitly requests a non-AGPL commercial licence, OR
- Profile Tailors seeks institutional/enterprise sales where AGPL-3.0 is a blocker.

### Alternatives considered

#### Dual-licensing (AGPL-3.0 + Commercial)

- Description: Offer the software under AGPL-3.0 for open-source use and a separate commercial
  licence for proprietary integration.
- Advantages: Removes AGPL friction for enterprise customers; creates revenue stream.
- Disadvantages: Requires commercial licence drafting, CLA administration, enforcement
  infrastructure, and legal counsel. Premature at current stage.
- Reason rejected: Deferred until market demand materialises.

#### MIT or Apache 2.0

- Description: Re-license under a permissive open-source licence.
- Advantages: Maximum adoption, no copyleft concerns.
- Disadvantages: Incompatible with existing AGPL-3.0 codebase without all contributor consent.
  Reduces ability to monetise.
- Reason rejected: Not feasible without full contributor re-licensing agreement.

## Troubleshooting

- **SPDX header missing in new file:** Add `SPDX-License-Identifier: AGPL-3.0-only` to the file
  header. CI should eventually flag this (see follow-up action).
- **Customer asks for non-AGPL licence:** Escalate to Principal Architect. This is the trigger
  condition for revisiting the dual-licensing decision.
- **CLA signing question from contributor:** Point to `CLA.md`. The current CLA is designed to
  support future re-licensing if dual-licensing is later adopted.

## References

- AGPL-3.0 Section 13: Remote network interaction; source code distribution
- [`LICENSE`](../../../LICENSE): Repository licence file
- [`CONTRIBUTING.md`](../../../CONTRIBUTING.md): Contribution guidelines
- [`CLA.md`](../../../CLA.md): Contributor License Agreement
- [OpenSpec: legal-compliance-foundation](../../../openspec/changes/archive/2026-07-17-dallay-488-legal-policies/proposal.md)

### Follow-up actions

- [ ] Add SPDX header check to CI/linting (separate issue).
- [ ] Schedule AGPL-3.0 Section 13 compliance review with legal counsel.
- [ ] Add source notice/link to application UI (footer).
- [ ] Automate deployment tagging in CI.
- [ ] Monitor enterprise adoption requests as signal for dual-licensing evaluation.
