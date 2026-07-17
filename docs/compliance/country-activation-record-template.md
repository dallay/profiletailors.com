# Country Activation Record Template

> **Classification:** Internal — Market Release Control
> **Status:** Template — no country approved
> **Decision unit:** One country and relevant subdivisions, customer class, product configuration, legal entity, and document set

## Overview

Provide the evidence required to target, offer, monitor, contract, collect data from, employ people in, or otherwise enable Profile Tailors in a country. Regional analysis is a routing aid only: approval for the EU/EEA, “America,” or “Asia” does not activate every member country or subdivision.

An activation decision binds an immutable configuration. A different legal entity, language, customer class, price, provider, data flow, social platform, AI feature, transfer route, or material legal-document version requires change review and may require a new record.

### Activation States

| State | Meaning |
|---|---|
| Research | Primary sources and applicability are incomplete; no targeting or production processing. |
| Remediation | Applicable requirements are known but controls or documents are incomplete. |
| Counsel review | Product and technical evidence is complete enough for qualified local review. |
| Approved inactive | Exact scope is approved but not yet exposed to production users. |
| Active | Production configuration matches the approval and is monitored. |
| Suspended | New access or processing is disabled because approval assumptions changed or failed. |
| Exiting | Customer, data, contract, provider, and regulatory exit obligations are in progress. |

## Changes

| Version | Date | Description |
|---|---|---|
| 1.0 | 2026-07-17 | Added immutable country scope, evidence, approvals, activation, monitoring, and suspension controls |

## Usage

### 1. Record Control

- **Country / subdivision / code:** [COUNTRY / REGION / ISO]
- **Record ID and version:** [IDENTIFIER]
- **Current state:** [ACTIVATION STATE]
- **Target activation and legal effective date:** [DATES]
- **Product / business / security / privacy owners:** [NAMES]
- **Qualified local counsel and scope:** [COUNSEL / ENGAGEMENT]
- **Primary legal sources and access dates:** [OFFICIAL SOURCES]
- **Approval expiry / next review / change watcher:** [DATES / OWNER]
- **Suspension and rollback owner:** [OWNER]

### 2. Legal and Business Presence

- Operating and contracting legal entity, form, registration, tax identifiers, address, and authorised signatory
- Establishment, branch, employee, contractor, representative, DPO, local agent, fiscal representative, or data representative
- Domain, language, currency, local marketing, sales, support, telephone, address, and social targeting
- Customer, affected-person, employee, provider, and data-location nexus
- Registration, licence, filing, notification, certification, insurance, capital, books-and-records, or local-presence duties
- Sanctions, export controls, anti-bribery, beneficial ownership, payment, banking, invoicing, tax, permanent-establishment, and currency restrictions
- Sector restrictions for public bodies, education, children, health, finance, telecom, employment, political activity, or other regulated use

### 3. Approved Product Scope

Record exact enabled and disabled capabilities:

- Marketing site and early-access registration
- Account registration and authentication methods
- Workspace, membership, roles, and collaboration
- API keys and API access
- Social platforms, OAuth scopes, publishing, scheduling, media, and analytics
- Email, notification, support, and customer-success channels
- Paid plans, trials, checkout, renewal, cancellation, refunds, credits, and taxes
- AI models, providers, input, output, training, recommendation, automation, and human review
- Cookie, browser storage, analytics, monitoring, security, and advertising technologies
- Children, consumers, professionals, organisations, employees, agencies, and restricted audiences

Anything not listed as enabled remains unavailable through UI, API, sales, contract, support, and documentation.

### 4. Data and Privacy Approval

- Territorial and personal scope of applicable privacy, communications, cyber-security, consumer, and sector laws
- Controller, processor, business, service provider, entrusted party, intermediary, or equivalent role per purpose
- Data, sources, purposes, legal bases, notices, consequences, legitimate interests, and consent conditions
- Sensitive, child, biometric, financial, authentication, precise-location, communications, employee, and public-source data rules
- Data inventory, ROPA or local record, DPO/representative, registration, DPIA, audit, and regulator consultation
- Rights, identity verification, authorised agents, appeals, complaints, deadlines, extensions, and non-discrimination
- Retention, deletion, anonymisation, backup, legal hold, suppression, and proof
- Security, risk, incident, breach, authority, affected-person, customer, and sector notification
- Direct marketing, electronic communications, telemarketing, suppression lists, sender identity, and unsubscribe
- Cookies, local storage, SDKs, pixels, consent, reject, withdrawal, opt-out, and proof
- Automated decision, profiling, AI transparency, explanation, human intervention, and prohibited use

Every item links to implemented tests and an accountable operator, not policy text alone.

### 5. Providers, Platforms, and Transfers

- Selected provider legal entities, services, roles, plans, countries, and production accounts
- Social-platform developer approval, terms, scopes, data use, deletion, branding, audit, and suspension risk
- DPA, security annex, subprocessors, audit evidence, incidents, rights, retention, deletion, and exit
- Origin, destination, support, backup, telemetry, and remote-access routes
- Adequacy, contract, clauses, consent, certification, filing, security assessment, localisation, or other approved mechanism
- Transfer and DPIA decisions, supplementary measures, government-demand handling, onward transfers, and monitoring
- Provider failure, portability, concentration, sanctions, geopolitical, insolvency, and replacement plan

### 6. Public and Contractual Documents

List immutable approved versions and languages for:

- Legal notice or service-provider disclosure
- Privacy policy and country/state/province addendum
- Cookie and browser-storage policy and preference UI
- Terms of Service, order form, consumer information, cancellation and withdrawal notice
- Acceptable Use Policy and content/platform procedures
- Subprocessor list and customer DPA
- Open-source and corresponding-source notice
- Accessibility statement and contact
- AI transparency, child/parent, direct-marketing, and sector notices where applicable
- Security, support, SLA, refund, complaint, appeal, and authority information

Record professional legal translation, reconciliation to the controlling version, accessibility, effective date, change notice, archive, acceptance or presentation evidence, and public route.

### 7. Consumer and Commercial Approval

- B2B, B2C, mixed, marketplace, agency, creator, and free-service classification
- Pre-contract information, price, currency, taxes, fees, order steps, confirmation, durable copy, and invoice
- Trial, renewal, notice, price changes, cancellation, withdrawal, refunds, service start, digital-content rules, and minimum term
- Warranty, conformity, availability, support, remedies, liability, indemnity, insurance, unfair terms, and non-waivable rights
- Governing law, courts, arbitration, collective redress, online dispute resolution where current and applicable, complaint authority, and language
- Advertising claims, testimonials, comparative claims, environmental claims, scarcity, counters, waitlists, dark patterns, and influencer disclosures

### 8. Content, Platform, and Safety Approval

- Customer authority and rights in content
- Intellectual-property, trademark, publicity, privacy, confidentiality, defamation, election, advertising, and regulated-content rules
- Child sexual abuse material, non-consensual intimate material, trafficking, threats, self-harm, fraud, impersonation, malware, spam, and abuse handling
- Notice-and-action, trusted flagger, counter-notice, appeal, transparency, preservation, emergency, and law-enforcement workflows
- Platform-specific publication, deletion, rate, automation, labelling, and prohibited-content terms
- Human moderation, escalation, language coverage, safety contacts, and operator capacity

### 9. Accessibility and Language

- Required public, contract, support, complaint, consent, and safety languages
- Professional legal translation and local terminology review
- Web, mobile, document, email, authentication, consent, support, and payment accessibility requirements
- Audit scope, conformance decision, assistive-technology tests, remediation, contact, and response ownership
- Plain-language, literacy, child, vulnerable-person, disability, and alternative-format needs

### 10. Operational Readiness Evidence

- Production deployment and configuration evidence
- Monitoring, alerts, on-call, support, privacy, legal, abuse, security, and authority contacts
- Rights, consent, deletion, export, incident, cancellation, refund, appeal, and complaint exercises
- Provider outage, transfer suspension, security incident, content emergency, and market rollback drills
- Staffing, language, time-zone, service-level, authority, and escalation capacity
- Data reconciliation, audit logs, immutable legal versions, acceptance records, and evidence exports

### 11. Approval Record

All approvals reference the exact record version and evidence:

| Approval | Decision and evidence |
|---|---|
| Product truth | [OWNER / DATE / REFERENCE] |
| Technical truth | [ENGINEERING / DATE / TESTS] |
| Security | [OWNER / DATE / ASSESSMENTS] |
| Privacy operations | [OWNER / DATE / EXERCISES] |
| Business and entity authority | [SIGNATORY / DATE / SCOPE] |
| Tax and finance | [REVIEWER / DATE / SCOPE] |
| Qualified local legal | [COUNSEL / DATE / EFFECTIVE LAW / QUALIFICATIONS] |
| Release | [OWNER / DATE / DEPLOYMENT / ROLLBACK] |

No self-approval by the document author substitutes for the required owners.

### 12. Activation and Monitoring

At activation:

- Verify country routing, availability, language, product flags, providers, storage, prices, tax, documents, acceptance, contacts, and monitoring.
- Preserve deployment, configuration, tests, screenshots, network/storage observations, approvals, and first-processing time.
- Confirm disabled features cannot be reached through API, stale client, support, or sales process.
- Start law, regulator, provider, platform, incident, complaint, metric, and document-expiry monitoring.

Suspend on law change, expired approval, missing owner, provider or country drift, invalid transfer, serious incident, unhandled rights request, contact failure, misleading claim, contract mismatch, regulator direction, or evidence that a key assumption is false.

## Troubleshooting

- **The region is approved:** Create a country record anyway; regional approval does not resolve national consumer, language, tax, employment, cookie, breach, registration, or sector rules.
- **Users can access the site from an unapproved country:** Distinguish passive accessibility from targeting and service activation with qualified counsel; keep registration, contracting, and processing controls aligned to the approved scope.
- **A customer wants global employee access:** Assess every employee and access country, role, transfer, support, and customer contract; do not use the customer's request as automatic legal approval.
- **The provider supports the country:** Provider availability does not approve Profile Tailors' entity, product, contract, privacy, consumer, tax, content, or operational duties.
- **The law changes during launch:** Suspend affected capability or market, preserve evidence, reassess, update documents and controls, and obtain new approval.
- **Only English copy is ready:** Keep markets requiring another language inactive until professional legal translation and operational language support are approved.
- **A feature is disabled in UI only:** Verify API, background jobs, imports, stale clients, support tools, and direct links cannot activate it.
- **One subdivision has stricter law:** Create the subdivision overlay and apply correct routing or the stricter compatible rule to the approved scope.

## References

- [`global-legal-readiness.md`](global-legal-readiness.md): Regional and country applicability register
- [`legal-document-register.md`](legal-document-register.md): Required artifact status
- [`legal-publication-gate.md`](legal-publication-gate.md): Policy approval gate
- [`data-inventory.yaml`](data-inventory.yaml): Processing evidence
- [`customer-dpa-template.md`](customer-dpa-template.md): Customer data contract
- [`subprocessor-register.md`](subprocessor-register.md): Provider activation
- [`international-transfer-assessment-template.md`](international-transfer-assessment-template.md): Transfer approval
- [`dpia-screening-and-assessment.md`](dpia-screening-and-assessment.md): High-risk assessment
- [`vendor-due-diligence-checklist.md`](vendor-due-diligence-checklist.md): Provider evidence
