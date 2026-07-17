# Subprocessor Register

> **Classification:** Internal and future public transparency record
> **Status:** No production subprocessors approved
> **Register date:** 2026-07-17

## Overview

Record organisations that process customer-controlled personal data on behalf of the Profile Tailors operating legal entity. A candidate provider, configurable adapter, environment variable, documentation example, local development dependency, or independent platform controller is not an approved subprocessor.

The current repository does not establish a selected production host, database host, object-storage provider, managed monitoring provider, or managed metrics provider. LinkedIn requires role analysis per processing operation and must not automatically be labelled a subprocessor. Resend and Ahrefs Web Analytics are conditional integrations and are not approved production subprocessors.

### Status Vocabulary

| Status | Meaning |
|---|---|
| Candidate | Being evaluated; must not receive production personal data. |
| Contracting | Due diligence and contract work incomplete; activation blocked. |
| Approved inactive | Approved for a defined configuration but not currently receiving data. |
| Active | Verified production processing matches the approved record. |
| Suspended | New processing stopped pending incident, legal, security, or contract resolution. |
| Exiting | Replacement and data-return/deletion controls are in progress. |
| Removed | Processing ended and approved deletion/expiry evidence is complete. |

## Changes

| Version | Date | Description |
|---|---|---|
| 1.0 | 2026-07-17 | Replaced inferred provider lists with an empty-by-default activation register |

## Usage

### Current Approved Register

| Legal entity | Service | Status | Data and people | Processing and access countries | Agreement and transfer evidence | Last verified |
|---|---|---|---|---|---|---|
| None | No production subprocessor set is approved | — | — | — | — | 2026-07-17 |

### Known Candidates and Non-Approved Integrations

| Name or category | Repository evidence | Current classification | Required decision |
|---|---|---|---|
| Production hosting and CDN | Static Astro and Vite builds; no deployment adapter or provider selection | Not selected | Select legal entity, product, regions, logs, security, DPA, subprocessors, transfers, retention, deletion, and exit |
| Production PostgreSQL host | R2DBC configuration accepts a production connection | Not selected | Select service and regions; validate encryption, backup, access, DPA, transfers, retention, deletion, continuity, and exit |
| Production object storage | Local filesystem is default; S3 and R2-compatible adapters exist | Not selected | Select only one approved production configuration and verify every storage, support, telemetry, and backup location |
| Resend | Email adapter activates only with a non-empty API key | Conditional candidate | Determine production need, legal entity, role, message data, events, locations, DPA, transfers, retention, suppression, incident terms, and deletion |
| Ahrefs Web Analytics | Marketing component loads conditionally with a key and is cookieless by default | Conditional recipient candidate | Determine controller/processor role, request data, locations, contract, retention, transfer, opt-out/consent, and runtime evidence |
| Managed monitoring or metrics | No selected provider found | Not selected | Approve provider, fields, redaction, sampling, access, regions, DPA, transfers, retention, incident and deletion controls |
| LinkedIn | Real OAuth connection and publishing adapters exist | Role-dependent external platform; not approved as subprocessor | Map independent-controller, customer-instructed recipient, and any processor operations; approve platform terms, scopes, locations, transfers, retention, deletion, and incidents |
| Other social platforms | Names appear in frontend types, examples, or drafts; no production backend adapter located | Not available | Do not list publicly or contractually until product, platform, data, security, and legal approval exists |

Provider brands previously named in legal drafts are not carried forward as candidates merely because they were mentioned.

### Activation Record

Before status may become `Active`, record:

- Exact provider legal entity, registration, address, affiliate involvement, and signing authority
- Exact service, plan, tenant, account, configuration, and customer-facing feature
- Processor, subprocessor, independent controller, joint controller, recipient, or other role for each purpose
- Data categories, data-subject categories, purpose, frequency, and volume
- Primary, replica, backup, support, telemetry, incident, and remote-access countries
- Subprocessor list, change mechanism, locations, and flow-down obligations
- Executed agreement version, parties, effective date, term, precedence, and archive
- Transfer mechanism per route, local clauses, impact assessment, and supplementary measures
- Security controls, certifications and exact scope, audit evidence, incident terms, and vulnerability process
- Retention, deletion, return, backup expiry, restore re-deletion, legal holds, and certificate capability
- Availability, continuity, portability, export, exit, insolvency, and concentration-risk plan
- Privacy-rights support, government-demand handling, regulator cooperation, and customer assistance
- Product, security, privacy, business, procurement, and qualified legal approvals
- Production deployment evidence, first-data date, owner, next review, and monitoring alerts

### Role Decision

Answer per processing purpose:

1. Who determines why the data is processed?
2. Who determines essential means rather than implementation details?
3. Is the provider prohibited from using data for its own advertising, product development, or unrelated purposes?
4. Does the provider combine customer data with other sources?
5. Can the provider retain or disclose data outside instructions?
6. Does a platform receive data because the customer intentionally directs publication to it?
7. Which party responds to rights, breach, government, and deletion events?
8. How does the applicable country define the roles and contract duties?

Marketing language such as “service provider,” a provider's self-classification, or an EU DPA label does not resolve every regional role.

### Change Notice

For customers entitled to notice, the approved process must:

- Publish and directly deliver the exact proposed legal entity, service, countries, data, purpose, role, and planned activation date.
- Link the governing DPA and current immutable register version.
- Give the contractually and legally approved notice period.
- Provide a secure, bounded objection route and criteria.
- Record delivery, customer response, assessment, mitigation, decision, and any termination or alternative.
- Issue emergency notice as soon as permitted when replacement is necessary for security, law, or continuity.
- Never activate before approval and any applicable notice period complete.

### Ongoing Verification

At the approved cadence and after material change:

- Reconcile invoices, accounts, DNS, deployment, environment, network, logs, and data-flow evidence against the register.
- Review provider terms, DPA, subprocessors, countries, security reports, incidents, government-demand policy, retention, and deletion.
- Confirm only approved data fields and purposes are sent.
- Test export, rights assistance, deletion, credential rotation, and exit.
- Review financial, operational, concentration, geopolitical, sanctions, and regulatory risks.
- Suspend or remediate unexplained drift before updating public disclosures.

### Exit Record

Record stop-processing time, replacement, exports, credential revocation, account closure, primary deletion, backup expiry, subprocessor propagation, legal holds, certificates, residual risks, customer notices, contract termination, and final verification. A provider remains `Exiting` until evidence satisfies the approved completion criteria.

## Troubleshooting

- **The provider appears in source code:** Code capability is not production activation; retain `Candidate` or `Not selected`.
- **The provider has an online DPA:** Verify execution, exact legal parties, service, plan, version, locations, and transfer annexes.
- **The provider calls itself an independent controller:** Map actual purposes and essential means under every enabled market; do not accept labels without analysis.
- **One provider uses different legal entities by country:** Create separate records and transfer paths for each contracting or processing entity.
- **A subprocessor changes silently:** Suspend new affected processing where feasible, investigate contract breach, notify customers as required, and update only after approval.
- **A provider cannot delete backups immediately:** Record the approved expiry, isolation, restore re-deletion, and contract terms; do not promise immediate destruction.
- **A customer objects:** Apply the signed objection criteria, assess risk, offer an approved alternative where available, and preserve all evidence.
- **A provider is removed from runtime but still has data:** Keep it `Exiting` until deletion or approved expiry is verified.

## References

- [`customer-dpa-template.md`](customer-dpa-template.md): Customer and flow-down obligations
- [`controller-processor-matrix.md`](controller-processor-matrix.md): Current role evidence
- [`data-inventory.yaml`](data-inventory.yaml): Recipient activation and agreement status
- [`retention-and-erasure-control-plan.md`](retention-and-erasure-control-plan.md): Provider deletion and exit
- [`legal-publication-gate.md`](legal-publication-gate.md): Public provider disclosure gate
- [`global-legal-readiness.md`](global-legal-readiness.md): Country role and transfer overlays
