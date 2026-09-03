# ADR-0012: AGPL-3.0 Commercial Strategy

- Status: Accepted
- Date: 2026-07-17
- Last revised: 2026-07-31
- Decision owners: Principal Architect
- Scope: profiletailors.com monorepo
- Supersedes: None
- Superseded by: None
- Legal review status: **Pending external counsel** — this ADR records engineering intent and
  operational processes. It does not constitute legal advice. The items marked
  `[LEGAL-REVIEW REQUIRED]` must be reviewed by a qualified lawyer before commercial distribution
  or external investment.
- Related:
    - OpenSpec: legal-compliance-foundation
    - Issues/PRs: DALLAY-498
    - Contributor map: [`docs/compliance/contributor-copyright-map.md`](../../compliance/contributor-copyright-map.md)
    - Source-offer runbook: [`docs/compliance/agpl-source-offer.md`](../../compliance/agpl-source-offer.md)

## Acceptance Criteria Status

| Criterion                                                        | Status                                                           |
| ---------------------------------------------------------------- | ---------------------------------------------------------------- |
| Decision recorded as ADR with legal-review status                | Done — see header above                                          |
| Current contributors and copyright holders mapped                | Done — `docs/compliance/contributor-copyright-map.md`            |
| Source-offer obligations reflected in deployment/release process | Done — `docs/compliance/agpl-source-offer.md`                    |
| Proprietary boundary technically enforceable and documented      | Done — see "Proprietary boundary" section below                  |
| Dependency licence scanning in CI                                | Done — `just licence-check` is defined in `Justfile` and called by `ci-local` |
| README, Terms, contribution docs do not contradict this decision | Done — README updated; `CLA.md` and `CONTRIBUTING.md` consistent |

**Verification evidence (2026-08-02):** The current `Justfile` defines `licence-check` at lines
339–344 and invokes it from `ci-local` at line 360. Commit `2babe18f` added the backend
`LicenceReportPlugin` and its SMP wiring; it did not modify `Justfile` (the recipe was introduced
earlier by `d395d8c3`). The acceptance claim is supported by the current recipe plus the landed
backend enforcement.

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

### Proprietary boundary

AGPL-3.0 copyleft covers the combined work. The following rules define where proprietary code can
legally exist alongside the open-source monorepo:

| Layer                                                                       | Copyleft applies?                                     | Rule                                                                                                                       |
| --------------------------------------------------------------------------- | ----------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| Code merged into this monorepo                                              | **Yes**                                               | Must be AGPL-3.0-only                                                                                                      |
| Services communicating over a **network API** (HTTP/gRPC) with the monorepo | **No**                                                | Separate network processes are not a "combined work" under AGPL-3.0 §13 unless they embed or statically link monorepo code |
| Plugins or modules loaded at runtime via a defined plugin API               | **Depends** [LEGAL-REVIEW REQUIRED]                   | If the plugin boundary is a network call → no copyleft; if it is in-process dynamic linking → counsel required             |
| Infrastructure-only configuration (Dockerfile, Helm, Terraform)             | **No** — unless they include modified monorepo source | Configuration alone does not trigger copyleft                                                                              |

**Technical enforcement:** proprietary services must live in a separate private repository and
communicate with the monorepo exclusively via its published HTTP/WebSocket API. No monorepo source
file may be copied into a proprietary repository. This boundary is reinforced by architecture
tests (ArchUnit) that prevent cross-module compilation dependencies beyond defined public API
packages.

**Consequence for investors/acquirers:** the monorepo is AGPL-3.0. A commercial licence can be
issued only by Dallay (as copyright holder via CLA), conditional on legal review and execution of
a commercial licence agreement. See the dual-licensing deferral above for the current position.

### Dependency licence policy

All runtime dependencies MUST use licences compatible with AGPL-3.0. The following categories
are assessed by the `licence-check` recipe in `Justfile`, which is called by `ci-local`:

| Category                       | Examples                                                    | Status                                         |
| ------------------------------ | ----------------------------------------------------------- | ---------------------------------------------- |
| Permissive                     | MIT, Apache-2.0, BSD-2-Clause, BSD-3-Clause, ISC, Unlicense | Allowed                                        |
| Weak copyleft                  | LGPL-2.1, LGPL-3.0, MPL-2.0, CDDL-1.0                       | Allowed with review                            |
| Strong copyleft (compatible)   | GPL-3.0, AGPL-3.0                                           | Allowed (compatible with AGPL-3.0)             |
| Strong copyleft (incompatible) | GPL-2.0-only                                                | **Blocked** — incompatible with AGPL-3.0       |
| Proprietary / Commercial       | BSL-1.1, SSPL-1.0, Elastic-2.0                              | **Blocked** — requires legal review before use |

The JVM report is written to `server/smp/build/reports/dependency-licence/`; the frontend scan
reads the JSON output from `pnpm licenses list --json`. Both checks run through `licence-check`,
which `ci-local` invokes before the remaining local CI checks.

### Scope and boundaries

- This ADR applies to the `profiletailors.com` monorepo and all derived works.
- Third-party dependencies retain their own licences; this ADR does not modify their terms.
- `LICENSE`, `CONTRIBUTING.md`, and `CLA.md` are NOT modified by this ADR.
- The contributor and copyright map is maintained in
  [`docs/compliance/contributor-copyright-map.md`](../../compliance/contributor-copyright-map.md).
- The AGPL source-offer process is documented in
  [`docs/compliance/agpl-source-offer.md`](../../compliance/agpl-source-offer.md).

## Changes

| Version | Date       | Description                                                                                            |
| ------- | ---------- | ------------------------------------------------------------------------------------------------------ |
| 1.0     | 2026-07-17 | Initial ADR — AGPL-3.0 strategy, no dual-licensing, compliance posture                                 |
| 2.0     | 2026-07-31 | Accepted — proprietary boundary, dependency policy, CI scanning, contributor map, source-offer runbook |

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
- OpenSpec: legal-compliance-foundation

### Follow-up actions

- [x] Add dependency licence scanning to CI (`just licence-check` → `ci-local`).
- [x] Create contributor and copyright map (`docs/compliance/contributor-copyright-map.md`).
- [x] Create AGPL source-offer runbook (`docs/compliance/agpl-source-offer.md`).
- [x] Document proprietary boundary in this ADR.
- [x] Update README licence section with source-offer notice.
- [ ] [LEGAL-REVIEW REQUIRED] Validate AGPL-3.0 Section 13 compliance posture with external counsel.
- [ ] [LEGAL-REVIEW REQUIRED] Draft commercial licence template for when dual-licensing is triggered.
- [ ] Add SPDX header check to Biome/Detekt/CI lint (separate issue — DALLAY-499).
- [ ] Add source notice link to application UI footer (marketing site and dashboard).
- [ ] Automate deployment tagging in CI so each deploy produces a reachable git tag.
- [ ] Monitor enterprise adoption requests as signal for dual-licensing evaluation.
