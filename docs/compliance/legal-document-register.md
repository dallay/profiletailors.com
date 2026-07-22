# Legal Document Register

> **Classification:** Internal — Legal and Compliance
> **Status:** Active register — production legal publication blocked
> **Register date:** 2026-07-17

## Overview

Track every public notice, customer contract, internal compliance record, and jurisdiction overlay
required to operate Profile Tailors. A file existing in the repository means only that a draft or
control record exists; it does not mean the document is legally approved, operationally implemented,
accepted, or published.

The register covers the European Union and EEA, the Americas, and Asia. A market may be enabled only
after the global document set and the applicable country overlays have immutable approval evidence.

### Status Vocabulary

| Status               | Meaning                                                                                                              |
|----------------------|----------------------------------------------------------------------------------------------------------------------|
| Implemented evidence | The record describes current repository behaviour and has technical validation; legal approval may still be pending. |
| Draft blocked        | A draft exists but must not be relied on or published.                                                               |
| Missing              | No adequate artifact exists.                                                                                         |
| Conditional          | Required only if a feature, provider, customer model, data category, or jurisdiction is enabled.                     |
| Approved             | Product, technical, business, and qualified legal approvals identify the immutable version and enabled markets.      |

## Changes

| Version | Date       | Description                                                                     |
|---------|------------|---------------------------------------------------------------------------------|
| 1.0     | 2026-07-17 | Added the global public, contractual, operational, and country-overlay register |

## Usage

### Public Documents

| Document                                     | Current artifact                                                         | Status                       | Publication blocker                                                                                                                  |
|----------------------------------------------|--------------------------------------------------------------------------|------------------------------|--------------------------------------------------------------------------------------------------------------------------------------|
| Legal notice / service-provider disclosure   | None                                                                     | Missing                      | Operating legal person, address, registration, tax, contact, and hosting-domain disclosures unresolved                               |
| Privacy policy — English                     | `apps/web/marketing/src/i18n/en.ts`                                      | Draft blocked                | Entity, markets, providers, legal bases, transfers, retention, rights, contacts, and counsel approval                                |
| Privacy policy — Spanish                     | `apps/web/marketing/src/i18n/es.ts`                                      | Draft blocked                | Same blockers plus professional legal translation review                                                                             |
| Cookie and browser-storage policy — English  | `apps/web/marketing/src/i18n/en.ts`                                      | Draft blocked                | Production observation, provider selection, security review, consent classification, and entity                                      |
| Cookie and browser-storage policy — Spanish  | `apps/web/marketing/src/i18n/es.ts`                                      | Draft blocked                | Same blockers plus professional legal translation review                                                                             |
| Terms of Service — English                   | `apps/web/marketing/src/i18n/en.ts`                                      | Draft blocked                | Contracting party, B2B/B2C, countries, service, pricing, liability, law, disputes, acceptance, and counsel                           |
| Terms of Service — Spanish                   | `apps/web/marketing/src/i18n/es.ts`                                      | Draft blocked                | Same blockers plus professional legal translation review                                                                             |
| Acceptable Use Policy — English and Spanish  | `apps/web/marketing/src/i18n/en.ts`, `apps/web/marketing/src/i18n/es.ts` | Draft blocked                | Contract incorporation, enforcement, reporting, appeal, market, and platform-rule approval                                           |
| Subprocessor list                            | [`subprocessor-register.md`](subprocessor-register.md)                   | Draft blocked                | Register is empty by design; no production processor set or executed agreement evidence                                              |
| Open-source / corresponding-source notice    | Repository `LICENSE` only                                                | Missing for deployed service | Deployed version, modification status, source route, offer mechanics, marks, and non-code scope unresolved                           |
| Privacy-rights request page                  | None                                                                     | Missing                      | Identity verification, intake, deadlines, exceptions, appeals, operators, and tested channels absent                                 |
| Accessibility statement                      | None                                                                     | Missing                      | Accessibility audit, scope, conformance decision, contact, remediation ownership, and market requirements absent                     |
| AI transparency notice                       | None                                                                     | Conditional                  | Required only after enabled AI features, models, providers, inputs, outputs, training settings, and risk classification are approved |
| Children and age notice                      | None                                                                     | Conditional                  | Minimum age, child-directed status, parental process, and country age thresholds unresolved                                          |
| Consumer cancellation and withdrawal notice  | None                                                                     | Conditional                  | B2C scope, paid flow, country, fulfilment timing, refund, and cancellation controls unresolved                                       |
| Regional privacy addenda and opt-out notices | None                                                                     | Conditional                  | Applicability thresholds and enabled markets unresolved                                                                              |

### Customer and Provider Contracts

| Document                                       | Status                                                                                           | Activation condition or blocker                                                                                       |
|------------------------------------------------|--------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------|
| Customer service agreement or order form       | Missing                                                                                          | Contracting entity, product, pricing, service level, support, countries, and customer type                            |
| Customer data processing addendum              | [`customer-dpa-template.md`](customer-dpa-template.md)                                           | Non-executable template                                                                                               | Parties, controller/processor allocation, instructions, security annex, subprocessors, transfers, deletion, audit, incident terms, and regional schedules |
| Controller-to-processor agreements             | Missing or unverified                                                                            | Required for every selected production processor before personal data is sent                                         |
| International-transfer instrument              | Conditional and unverified                                                                       | Select only after exporter, importer, role, country, data, transfer path, and applicable regional mechanism are known |
| Transfer impact assessment                     | [`international-transfer-assessment-template.md`](international-transfer-assessment-template.md) | Template                                                                                                              | Complete per actual exporter/importer/role/service/data/country route |
| Social-platform developer and API terms record | Unverified                                                                                       | LinkedIn product approval, scopes, permitted uses, retention, deletion, branding, and audit duties                    |
| Security schedule                              | Missing                                                                                          | Approved architecture, safeguards, incident ownership, testing, vulnerability handling, and customer commitments      |
| Service-level agreement                        | Conditional                                                                                      | Paid or negotiated service commitment and measurable operational capacity                                             |
| Confidentiality and workforce data terms       | Missing                                                                                          | Legal entity, workforce model, access roles, confidentiality, training, and offboarding controls                      |

### Internal Operational Records

| Record                                           | Artifact                                                                         | Status                         | Remaining work                                                                                               |
|--------------------------------------------------|----------------------------------------------------------------------------------|--------------------------------|--------------------------------------------------------------------------------------------------------------|
| Global legal readiness                           | [`global-legal-readiness.md`](global-legal-readiness.md)                         | Draft blocked                  | Country activation approvals and local counsel evidence                                                      |
| Country activation record                        | [`country-activation-record-template.md`](country-activation-record-template.md) | Template                       | Complete per country, subdivision, customer class, entity, product configuration, and immutable document set |
| Publication gate                                 | [`legal-publication-gate.md`](legal-publication-gate.md)                         | Implemented evidence           | All unchecked approval items remain blockers                                                                 |
| Data inventory                                   | [`data-inventory.yaml`](data-inventory.yaml)                                     | Implemented evidence with gaps | Resolve every pending basis, provider, transfer, retention, and contact field                                |
| Human-readable inventory                         | [`data-inventory.md`](data-inventory.md)                                         | Implemented evidence with gaps | Keep generated facts aligned with the machine inventory                                                      |
| ROPA                                             | [`ropa.md`](ropa.md)                                                             | Draft blocked                  | Entity, lawful bases, contacts, approvals, and production facts                                              |
| Controller/processor matrix                      | [`controller-processor-matrix.md`](controller-processor-matrix.md)               | Implemented evidence with gaps | Select providers and execute agreements                                                                      |
| Incident runbook                                 | [`incident-response-runbook.md`](incident-response-runbook.md)                   | Draft blocked                  | Assign roles, contacts, systems, providers, drills, and counsel                                              |
| Incident deadline matrix                         | [`incident-sla-table.yaml`](incident-sla-table.yaml)                             | Draft blocked                  | Local confirmation for every enabled market and sector                                                       |
| Authority and affected-person templates          | `breach-notification-*.md`                                                       | Draft blocked                  | Adapt per incident, authority, legal person, language, and jurisdiction                                      |
| Versioned legal acceptance                       | [`legal-acceptance-record.md`](legal-acceptance-record.md)                       | Design contract                | Implement persistence, API, UI, evidence export, and tests                                                   |
| Data-subject rights runbook                      | [`rights-request-runbook.md`](rights-request-runbook.md)                         | Design contract                | Implement intake, country clocks, identity, search, fulfilment, appeals, operators, and tests                |
| Retention and erasure control plan               | [`retention-and-erasure-control-plan.md`](retention-and-erasure-control-plan.md) | Remediation plan               | Implement control owners, jobs, backup handling, holds, proof, and tests                                     |
| Consent and preference register                  | [`consent-and-preference-register.md`](consent-and-preference-register.md)       | Design register                | Implement waitlist, marketing, cookies, optional purposes, withdrawal, proof, and locale/version evidence    |
| DPIA and high-risk screening                     | [`dpia-screening-and-assessment.md`](dpia-screening-and-assessment.md)           | Screening framework            | Complete per feature, provider, market, and material change; add country and sector overlays                 |
| Law-enforcement request runbook                  | None                                                                             | Missing                        | Authority validation, scope, preservation, disclosure, conflict-of-law, notice, and transparency record      |
| Vendor due diligence                             | [`vendor-due-diligence-checklist.md`](vendor-due-diligence-checklist.md)         | Control template               | Complete before any candidate receives production data or becomes customer-facing                            |
| Marketing and electronic-communications register | None                                                                             | Missing                        | Country channel rules, consent or exception, suppression, sender identity, and unsubscribe proof             |
| Records hold and litigation preservation         | None                                                                             | Missing                        | Authorised hold, scoped suspension of deletion, review, release, and audit evidence                          |

### Country Overlay Record

For every enabled country, create an approval record containing:

- Country and subdivisions where relevant
- Targeting, monitoring, establishment, customer, employee, and data-location nexus
- B2B, consumer, child, creator, regulated-sector, and public-sector scope
- Required language and professional legal translation evidence
- Entity disclosure, registration, tax, representative, DPO, local-agent, or filing duties
- Privacy notice, rights, consent, cookies, direct marketing, breach, transfer, localisation, and
  retention overlays
- Consumer terms, renewal, cancellation, withdrawal, refunds, dispute, platform, and content duties
- Accessibility, AI, cyber-security, recordkeeping, sanctions, export-control, and law-enforcement
  overlays
- Primary legal sources, qualified local counsel, approval date, effective-law date, next review,
  and rollback owner

The country record approves an immutable set of documents and product capabilities. It must not
approve a brand, branch, or future configuration in the abstract.

## Troubleshooting

- **A document exists but has unresolved facts:** Keep it `Draft blocked`; do not describe it as
  complete.
- **A document is not required in the first market:** Mark it `Conditional` with the exact feature
  or market trigger; do not delete it from the register.
- **A provider supplies its own DPA or transfer terms:** Record the executed version, parties,
  roles, services, locations, and effective date; a public URL alone is not execution evidence.
- **One policy is approved for one country:** Approval does not extend automatically to another
  country, language, entity, provider set, or product version.
- **A technical test passes:** Update technical evidence only; legal and business approvals remain
  independent.
- **Law or product behaviour changes:** Return affected documents to `Draft blocked`, disable
  impacted market or feature if necessary, and run the change-impact review.

## References

- [`legal-publication-gate.md`](legal-publication-gate.md): Mandatory publication approvals
- [`global-legal-readiness.md`](global-legal-readiness.md): EU/EEA, Americas, and Asia market
  register
- [`data-inventory.yaml`](data-inventory.yaml): Processing evidence source
- [`controller-processor-matrix.md`](controller-processor-matrix.md): Role and provider evidence
- [`openspec/specs/legal-pages/spec.yaml`](../../openspec/specs/legal-pages/spec.yaml): Canonical
  legal-page contract
- [`LICENSE`](../../LICENSE): Repository licence
