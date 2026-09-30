# ADR-0012: AGPL-3.0 Commercial Strategy

- Status: Accepted
- Date: 2026-07-17
- Last revised: 2026-09-30
- Decision owners: Project maintainer
- Scope: `profiletailors.com` repository and its published distributions
- Supersedes: None
- Superseded by: None
- Legal review status: **legal-review-required** — this ADR records the project maintainer's
  engineering and product decision, not legal advice or a legal opinion. Qualified counsel must
  review contributor rights, CLA coverage, source-offer implementation, and commercial implications
  before commercial distribution, dual licensing, or external investment.
- Related:
  - Issues/PRs: DALLAY-498
  - Contributor evidence: [`docs/compliance/contributor-copyright-map.md`](../../compliance/contributor-copyright-map.md)
  - Source-offer runbook: [`docs/compliance/agpl-source-offer.md`](../../compliance/agpl-source-offer.md)

## Context

The repository is licensed under AGPL-3.0. The maintainer wants the project to be usable by anyone,
including companies and competitors, under the rights and conditions of that licence. The project is
currently a hobby; no commercial offering, monetization model, or proprietary product boundary is
being adopted by this decision.

AGPL-3.0 does not prohibit commercial or competitive use. Its conditions, including the provisions
for modified versions interacted with remotely, apply as specified by the licence. The exact legal
application to the project's deployments and offer method remains subject to qualified counsel.

## Decision

1. Keep the code distributed from this repository under AGPL-3.0. Do not add a restriction against
   commercial or competitive use.
2. Do not establish a dual-licensing programme or proprietary code boundary at this time.
3. Treat source-offer readiness as incomplete until the exact source corresponding to each applicable
   deployment is identified and the offer is prominent, available, and operationally verified.
4. Run dependency licence checks in GitHub Actions as a required CI check. A report or local recipe
   alone is not CI enforcement.
5. Reconsider separate components or licensing only through a new documented decision, after
   technical-boundary analysis and qualified legal review. This ADR does not conclude that any
   particular architecture avoids AGPL obligations.
6. The AGPL decision concerns the software licence, not whether the operator-hosted instance is free,
   available, supported, or operated indefinitely. Hosted-service terms must not narrow rights
   granted by AGPL-3.0.

## Acceptance Criteria Status

| Criterion | Status |
| --- | --- |
| Decision recorded as ADR with legal-review status | Done — see this ADR and counsel status above |
| Current contributors and copyright holders mapped | Partial — local Git author identities and CLA Assistant records are inventoried; pull-request patch authors, identity, legal ownership, and complete historical grant coverage remain unverified |
| Source-offer obligations reflected in deployment/release process | Partial — marketing, dashboard, and admin source links are implemented with focused tests; live deployment mapping and source completeness remain unverified |
| Proprietary boundary technically enforceable and documented | Not applicable — no proprietary boundary is adopted |
| Dependency licence inventory in CI | Partial — CI Gate now captures frontend and backend licence inventories. Automated licence-blocking enforcement is not established; exact dependency terms and compatibility require review. Inventories are evidence, not legal approval |
| README, Terms, and contribution docs do not contradict the decision | Partial — README and contribution guide describe open AGPL use, and EN/ES hosted Terms now explicitly preserve commercial and competitive software use; actual hosted-service terms and OpenSpec fidelity remain separate acceptance items |

## Consequences

- Individuals, companies, and competitors may use, modify, and redistribute the code under the
  applicable AGPL-3.0 terms.
- The project does not promise that competitors will be unable to offer competing services.
- Any applicable source-offer obligation must be addressed for the deployed version and relevant
  modifications; a link to the repository's default branch alone is not evidence of completion.
- The CLA is not a copyright assignment. A contributor-provenance inventory and signatures file do
  not independently prove identity, authority, complete coverage, or legal ownership.
- The existing third-party dependency report and checker rules do not constitute a legal conclusion
  about every dependency or distribution scenario.
- Future commercial distribution, dual licensing, or investment requires counsel review of rights,
  contributor grants, third-party components, and operational compliance before proceeding.

## Alternatives Considered

### Restrictive source-available licence

Rejected because a restriction on commercial or competitive use conflicts with the maintainer's
confirmed intent that anyone may use the code under its published licence.

### Dual licensing

Not adopted. It is unnecessary for the current hobby project and would require validated rights and
qualified legal review. This is not a commitment to introduce it later.

### Proprietary boundary in the current repository

Not adopted. No separate proprietary component or technical boundary is part of the current product
strategy.

## References

- [`LICENSE`](../../../LICENSE)
- [`README.md`](../../../README.md)
- [`CONTRIBUTING.md`](../../../CONTRIBUTING.md)
- [`CLA.md`](../../../CLA.md)
- [`docs/compliance/agpl-source-offer.md`](../../compliance/agpl-source-offer.md)
- [`docs/compliance/contributor-copyright-map.md`](../../compliance/contributor-copyright-map.md)
