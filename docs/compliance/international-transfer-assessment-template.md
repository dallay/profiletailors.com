# International Transfer Assessment Template

> **Classification:** Internal — Privacy, Security, and Legal
> **Status:** Template — no production transfer approved
> **Decision granularity:** One actual exporter/importer/role/service/data/country route per assessment

## Overview

Assess whether personal data may lawfully and safely move, be remotely accessed, supported, replicated, backed up, disclosed, or made available across borders. A contractual clause alone is not evidence that a transfer is permitted, and an EU mechanism is not a universal solution for the Americas or Asia.

Complete this assessment after selecting exact production providers and before sending production personal data. Reassess when a party, role, service, country, subprocessor, law, government-access practice, data category, encryption design, or transfer mechanism changes.

### Scope Rules

- Assess each actual transfer route, including remote support and onward transfers.
- Identify legal parties, not brands or cloud regions alone.
- Distinguish controller-to-controller, controller-to-processor, processor-to-processor, processor-to-controller, and internal-group paths.
- Include origin, transit where legally relevant, destination, support, telemetry, backup, disaster-recovery, and administrator access countries.
- Evaluate localisation, registration, filing, certification, consent, notice, contract, adequacy, assessment, and regulator-approval duties per country.
- Keep the transfer disabled when any required mechanism or operational safeguard is missing.

## Changes

| Version | Date | Description |
|---|---|---|
| 1.0 | 2026-07-17 | Added route-level global transfer mapping, legal-environment analysis, safeguards, and decision evidence |

## Usage

### 1. Assessment Control

- **Assessment ID and version:** [IDENTIFIER]
- **Business owner / privacy owner / security owner:** [NAMES]
- **Qualified legal reviewers and jurisdictions:** [COUNSEL / COUNTRY]
- **Related service, feature, customer, and contract:** [REFERENCES]
- **Assessment date / effective-law date / next review:** [DATES]
- **Change trigger and suspension owner:** [TRIGGER / OWNER]
- **Decision:** [APPROVED / APPROVED WITH CONDITIONS / REJECTED / SUSPENDED]

### 2. Transfer Route

| Field | Evidence |
|---|---|
| Exporter | Exact legal entity, address, registration, establishment, role, and signing authority |
| Importer | Exact legal entity, address, registration, establishments, role, and signing authority |
| Other parties | Customer, affiliate, provider, subprocessor, platform, support, and onward recipient entities |
| Origin | Collection, storage, establishment, affected-person, and customer countries |
| Destination | Primary, replica, backup, support, telemetry, incident, and remote-access countries |
| Service | Exact product, plan, account, feature, and configuration |
| Data | Bounded categories, sensitivity, free text, media, credentials, metadata, logs, volume, and frequency |
| People | Customer users, workspace members, social-account owners, content subjects, waitlist applicants, children, or other classes |
| Purpose | Customer instruction and every separate controller purpose |
| Duration | Transfer start, frequency, storage, access window, termination, deletion, and backup expiry |
| Onward paths | Each subprocessor or recipient, country, role, mechanism, and purpose |
| Data flow evidence | Architecture, network, provider, DNS, deployment, support, and contract references |

### 3. Applicable Law and Mechanism

For every origin and destination, record:

- Law and territorial nexus
- Export restriction and localisation rule
- Available mechanism and eligibility conditions
- Required parties, modules, clauses, annexes, signatures, or certifications
- Adequacy, whitelist, exemption, contract, consent, necessity, or regulator process where applicable
- Registration, filing, impact assessment, standard contract, certification, security assessment, or prior approval
- Notice and consent content, language, timing, and withdrawal consequence
- Special rules for sensitive data, children, biometrics, financial data, authentication data, health data, public bodies, employment, or regulated sectors
- Onward-transfer and change-notification requirements
- Rights, complaint, remedy, and copy-of-safeguards requirements
- Effective date, transition, official source, counsel conclusion, and review expiry

Never record `SCC` without the full legal instrument, decision/version, modules, parties, annexes, docking, governing law, supervisory authority, signatures, and route-specific assessment.

### 4. Destination Legal Environment

Qualified counsel evaluates, to the extent relevant and supportable:

- Government, intelligence, law-enforcement, regulatory, and civil discovery access powers
- Thresholds, necessity, proportionality, targeting, authorisation, independent oversight, secrecy, and challenge rights
- Importer obligations, technical ability, and history of demands
- Notice to exporter, customer, and affected people, including lawful delay or prohibition
- Data localisation, encryption-control, cyber-security, critical-infrastructure, and sector rules
- Judicial and administrative remedies available in practice
- Regulator independence, enforcement, discrimination, and foreign-person protections
- Conflicting-law and compelled-disclosure scenarios
- Public transparency reports and reliable route-specific provider evidence

Separate verified law and practice, provider representations, public reports, customer facts, assumptions, and unresolved questions. Avoid unsupported conclusions that a country is simply “safe” or “unsafe”.

### 5. Technical Measures

Record implemented measures and who controls the keys or access:

- Data minimisation and field-level exclusion before transfer
- Strong transport protection and endpoint authentication
- Encryption or tokenisation before transfer with keys inaccessible to the importer where feasible
- Pseudonymisation with separated re-identification data
- Tenant, role, attribute, network, and privileged-access controls
- Customer-managed keys or split knowledge where operationally supported
- Logging, anomaly detection, access review, immutable evidence, and alerting
- Local processing, edge filtering, aggregation, or anonymisation
- Short retention, automatic deletion, backup expiry, and restore re-deletion
- Query-only, transient, sealed, or enclave processing where verified
- Credential, OAuth, API key, secret, and support-access restrictions

State limitations: encryption at rest controlled by the importer does not prevent that importer from accessing plaintext during service processing.

### 6. Contractual Measures

Record executed, enforceable commitments for:

- Purpose and instruction limitation
- Confidentiality, access, security, incident, and evidence duties
- Government-demand validation, challenge, minimisation, notice, and transparency
- Onward-transfer approval and flow-down
- Rights, audit, regulator, assessment, and consultation assistance
- Data return, deletion, backup expiry, hold, and certification
- Localisation, remote access, support countries, and change control
- Suspension, termination, portability, business continuity, and remedies
- Conflict priority between the transfer instrument, DPA, service terms, and mandatory law

Contractual promises do not cure technical impossibility or an importer legally compelled to disregard them.

### 7. Organisational Measures

- Transfer owner and approved administrators
- Request-handling and government-demand team
- Workforce confidentiality, training, location, and access reviews
- Incident and breach escalation across time zones
- Provider and subprocessor monitoring
- Legal change monitoring and assessment review
- Customer configuration and data-minimisation guidance
- Audit, penetration, vulnerability, continuity, exit, and deletion exercises
- Emergency suspension and migration plan
- Documentation, privilege, access control, and retention of assessment evidence

### 8. Residual Risk and Decision

For each plausible access, misuse, loss, unavailability, onward-transfer, rights, or enforcement scenario, record:

- Threat actor and legal or technical path
- Affected data and people
- Likelihood and severity before safeguards
- Existing and required safeguards
- Evidence of effectiveness and limitations
- Residual likelihood and severity
- Uncertainty and missing evidence
- Decision owner and acceptance authority

Permitted decisions:

- **Approved:** Every legal condition and operational safeguard is satisfied for the exact route.
- **Approved with conditions:** Processing remains disabled until named conditions are evidenced; approval expires if a condition fails.
- **Rejected:** No valid or proportionate route is available.
- **Suspended:** A legal, provider, security, incident, or factual change invalidates prior approval.

The decision includes activation date, monitoring, next review, customer notice, affected-person disclosure, and rollback.

### Regional Routing Questions

| Region | Questions requiring local approval |
|---|---|
| EU/EEA | Restricted transfer test, adequacy, SCC or other mechanism, transfer impact, supplementary measures, onward transfers, representative/DPO, and data-subject information |
| United States | State and sector restrictions, service-provider/contractor use limits, sale/share or targeted-advertising implications, government and litigation access, localisation commitments, and customer contract |
| Canada | Federal/provincial accountability, comparable protection, public-sector or health localisation, notice, access, and foreign demand implications |
| Brazil | ANPD international-transfer regulation, adequacy or approved mechanism, Brazilian clauses, sensitive data, notice, data-subject rights, and filing requirements |
| Other Americas | National registration, consent, contract, adequacy, localisation, regulator, language, and sector requirements |
| Japan | Foreign-third-party provision, information obligations, consent or exception, APPI equivalence, recipient information, records, and entrusted-processing distinction |
| South Korea | Overseas transfer type, required items, consent or statutory basis, entrustment disclosure, country/recipient/period, safeguards, and destruction |
| India | DPDP commencement and government restrictions, contract and security, consent/notice, children, Significant Data Fiduciary, CERT-In, and sector localisation |
| Singapore | Comparable protection, contractual or other legally recognised basis, data intermediary role, access/correction, retention, and breach |
| Mainland China | PIPIA, separate consent or other approved basis, localisation thresholds, security assessment, standard contract or certification, filing, sensitive data, and onward transfers |
| Hong Kong | Due diligence and contractual controls for overseas processors, data protection principles, access/correction, retention, security, and transparency |
| Other Asia | Country-specific localisation, contract, consent, certification, filing, security assessment, regulator approval, representative, and breach rules |

### Change Monitoring

Suspend or reassess on:

- New destination, support, backup, or subprocessor country
- New legal entity, affiliate, merger, acquisition, insolvency, or contract
- New data, sensitive category, child audience, scale, purpose, or automated processing
- Government-access, cyber-security, localisation, or transfer-law change
- Mechanism invalidation, adequacy review, clause update, regulator decision, or enforcement
- Provider demand, incident, transparency report, audit, security, encryption, key-control, or retention change
- Customer or affected-person complaint showing the assessment assumptions are wrong

## Troubleshooting

- **The provider says all data stays in one region:** Verify support, telemetry, control plane, incident, backup, affiliate, and subprocessor access paths.
- **Data is encrypted:** Identify who can access plaintext and keys during normal operation; encryption alone may not resolve government or provider access.
- **The importer is an independent controller:** Assess the disclosure and its own transfer basis; a processor DPA is not the correct instrument.
- **Consent is proposed as the fallback:** Confirm validity, specificity, information, voluntariness, withdrawal, service consequence, frequency, and whether the law permits it for systematic transfers.
- **The transfer is only remote access:** Treat access or availability across borders according to the applicable law; physical database location is not the only route.
- **One route is approved:** Do not extend approval to another customer, data class, purpose, legal entity, provider plan, or country without change review.
- **The mechanism becomes unavailable:** Suspend new transfer, preserve security and customer continuity lawfully, notify required parties, and execute the approved exit or migration plan.

## References

- [`customer-dpa-template.md`](customer-dpa-template.md): Contract and transfer annex structure
- [`subprocessor-register.md`](subprocessor-register.md): Importer and onward-recipient evidence
- [`data-inventory.yaml`](data-inventory.yaml): Data, purpose, recipient, and location evidence
- [`global-legal-readiness.md`](global-legal-readiness.md): Market transfer overlays and primary sources
- [`retention-and-erasure-control-plan.md`](retention-and-erasure-control-plan.md): Deletion, backup, and exit controls
- [European Commission international data transfer guidance](https://commission.europa.eu/law/law-topic/data-protection/international-dimension-data-protection_en)
- [European Commission Standard Contractual Clauses](https://commission.europa.eu/publications/standard-contractual-clauses-international-transfers_en)
