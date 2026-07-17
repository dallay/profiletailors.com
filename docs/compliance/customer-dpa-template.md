# Customer Data Processing Addendum Template

> **Classification:** Internal — Contract Drafting
> **Status:** Non-executable template — parties, services, markets, annexes, and qualified legal approval unresolved
> **Restriction:** Do not sign, publish, or incorporate by reference in its current form

## Overview

Define the evidence and clause architecture for a customer Data Processing Addendum when Profile Tailors processes personal data on documented customer instructions. This template is not a contract and intentionally avoids identifying “Profile Tailors” as a legal party until the operating legal person is established.

The final DPA must match the approved service agreement, data inventory, controller/processor roles, production providers, security controls, incident process, rights workflow, retention controls, and enabled countries. Regional terminology such as controller/processor, business/service provider, organisation/service provider, personal information processor/entrusted party, or equivalent must be introduced only through an approved country schedule.

### Drafting Rules

- Identify exact legal parties, addresses, registration details, and signing authority.
- Describe only services and processing available in the approved production version.
- Treat customer instructions separately from Profile Tailors' independent-controller purposes.
- Do not name a subprocessor, location, certification, DPA, SCC, adequacy decision, or security control without evidence.
- Do not use EU Standard Contractual Clauses as a universal transfer mechanism.
- Do not promise deletion, audit, incident, availability, or assistance periods that operations cannot meet.
- Preserve mandatory local rights and restrictions through country schedules.
- Resolve conflict order among the service agreement, order form, DPA, security schedule, transfer instrument, and mandatory law.

## Changes

| Version | Date | Description |
|---|---|---|
| 1.0 | 2026-07-17 | Added the evidence-driven global DPA structure and regional schedules |

## Usage

### Cover Record

Complete and approve before drafting operative clauses:

| Field | Required evidence |
|---|---|
| Customer | Exact legal name, form, address, registration, country, signatory, and authority |
| Service provider | Exact Profile Tailors operating legal person, address, registration, and signatory |
| Service agreement | Immutable signed version, order form, term, and precedence |
| Customer role | Controller, processor for another controller, business, public body, employer, or approved local equivalent |
| Profile Tailors role | Processor/service provider/contractor or approved equivalent for each purpose |
| Independent purposes | Security, account, billing, legal, or other controller purposes described separately and approved |
| Services | Exact enabled modules, APIs, platforms, support, storage, and optional features |
| Markets | Customer locations, affected people, offering/monitoring countries, data locations, and prohibited countries |
| Processing annex | Approved activity, data, person, purpose, duration, source, frequency, and special-category record |
| Security annex | Implemented controls, shared responsibility, testing, exceptions, evidence date, and owner |
| Subprocessor annex | Selected vendors only, with role, service, legal entity, country, processing, agreement, and transfer mechanism |
| Transfer annex | Approved transfer path and instrument per exporter/importer/role/country combination |
| Country schedules | Every applicable national or state/provincial addendum and legal approval |
| Commercial approval | Assistance fees, audit model, insurance, caps, service levels, support, and exit costs |

### 1. Scope and Roles

The final clause set must:

- Identify processing where the customer determines purposes and Profile Tailors acts only on documented instructions.
- Identify any customer-processor scenario and require the customer's authority from the relevant controller.
- List Profile Tailors' separately approved independent-controller processing without hiding it inside processor terms.
- State that mandatory law may require processing outside instructions, with notice where legally permitted.
- Define what constitutes an instruction, who may issue it, how conflicts are escalated, and how unlawful instructions are handled.
- Exclude features, providers, data, and countries not listed in the signed annexes.

### 2. Processing Details Annex

Generate the annex from the approved inventory rather than generic language:

- Subject matter and exact service functions
- Nature, operations, frequency, and duration
- Business and processing purposes determined by the customer
- Data-subject categories
- Personal-data categories, including free text, media, credentials, identifiers, logs, and inferred data
- Sensitive, child, financial, authentication, regulated, or criminal-offence data restrictions
- Data sources and collection channels
- Customer administrators and authorised workspace roles
- Approved social platforms and provider accounts
- Storage, access, support, and transfer countries
- End-of-service return, export, deletion, backup expiry, and hold rules

The annex must not say “all data necessary to provide the service” without a bounded inventory.

### 3. Confidentiality and Personnel

Require role-based access, confidentiality obligations, approved training, least privilege, joiner/mover/leaver controls, access review, privileged-access protection, and disciplinary or contractual consequences. Identify whether employees, contractors, support personnel, and affiliates can access customer data and from which countries.

Do not promise background checks, dedicated personnel, localisation, or security clearances unless implemented and lawful for the relevant workforce location.

### 4. Security Annex

Describe implemented controls and shared responsibilities for:

- Tenant isolation and authorisation
- Authentication, refresh sessions, API keys, and credential revocation
- Encryption in transit, secrets at rest, OAuth credential encryption, and key management
- Secure development, code review, dependencies, vulnerability handling, and release controls
- Database, object, media, queue, cache, and backup protection
- Logging, audit integrity, monitoring, alerting, and time synchronisation
- Incident detection, evidence preservation, containment, recovery, and notification
- Availability, continuity, disaster recovery, restoration testing, and capacity
- Deletion, anonymisation, legal holds, restore re-deletion, and processor confirmation
- Physical, workforce, provider, and endpoint safeguards
- Customer configuration and administrator responsibilities

Each control states scope, owner, evidence date, limitation, review cadence, and test reference. Planned controls belong in a remediation plan, not the executed annex.

### 5. Subprocessors

The final terms define:

- Specific or general written authorisation model accepted for each market
- Initial approved subprocessor annex
- Minimum notice content and period before a material addition or replacement
- Customer objection grounds, review, mitigation, alternative service, and termination consequence
- Flow-down obligations appropriate to role and jurisdiction
- Profile Tailors' responsibility for subprocessor performance to the extent required by law and contract
- Emergency replacement process for security, legal, or continuity reasons
- Public register versioning and direct customer notification channel
- Removal, exit, deletion, and confirmation requirements

A provider is not approved merely because an adapter, environment variable, documentation example, or account exists.

### 6. International and Onward Transfers

For every transfer path, record:

- Exporter, importer, roles, affiliates, subprocessors, and signing authority
- Origin, destination, remote-access, transit, support, and backup countries
- Data, people, purposes, frequency, and duration
- Applicable transfer and localisation laws
- Approved mechanism and exact executed version
- Required modules, annexes, docking, local clauses, registrations, or regulator filings
- Transfer or data-protection impact assessment
- Government-access analysis and supplementary technical, contractual, and organisational measures
- Onward-transfer controls, change monitoring, suspension, challenge, and termination
- Method for providing required information or a copy of safeguards to affected people

If no valid mechanism is approved, the transfer, provider, feature, or market remains disabled.

### 7. Privacy Rights and Customer Assistance

Define secure routing, role confirmation, identity protection, search scope, customer approval, fulfilment format, exceptions, processor escalation, and evidence for rights requests. Assistance targets must fit the strictest enabled market and leave the customer enough time to meet its own deadline.

Profile Tailors must not respond directly to customer-controlled requests unless the customer authorises it or applicable law requires it. The DPA must address conflicting customer instructions, direct regulator or person contact, and requests involving multiple customers or other people.

### 8. Security Incidents

Define:

- Contractual incident and personal-data-breach definitions aligned to enabled markets
- Processor-to-customer clock trigger and notification target supported by 24/7 operations
- Secure notification channel and authorised contacts
- Required known facts, phased updates, cooperation, evidence, remediation, and root-cause report
- Customer, authority, affected-person, law-enforcement, insurer, and public communication control
- Cost allocation and assistance only after commercial and legal approval
- No admission restriction that prevents mandatory truthful reporting
- Preservation, privilege, confidentiality, and post-incident improvement

Do not substitute the GDPR 72-hour controller deadline for the processor's earlier contractual duty or for another country's rule.

### 9. Assessments, Regulators, and Prior Consultation

State how Profile Tailors supplies accurate service information for DPIAs, transfer assessments, security reviews, regulator inquiries, and prior consultation. Define owners, response channels, scope, confidentiality, costs, and restrictions needed to protect other customers, security, privilege, and trade secrets.

Assistance cannot promise legal advice or guarantee that the customer's processing complies with law.

### 10. Audit and Evidence

Use a tiered model appropriate to risk and law:

1. Current documentation and control evidence.
2. Independent reports or certifications only when actually held and in scope.
3. Written follow-up and remediation evidence.
4. Remote or on-site audit when legally required or justified by unresolved material risk.

Define frequency, notice, auditor competence and independence, confidentiality, non-disruption, multi-tenant safeguards, vulnerability handling, cost, remediation, and regulator rights. Avoid an absolute right to refuse audits or an unlimited customer right to inspect production systems.

### 11. Return, Deletion, and Exit

Tie the clause to implemented controls:

- Export format, scope, availability window, encryption, and customer verification
- Cancellation of jobs and revocation of sessions, API keys, and OAuth credentials
- Primary record and physical object deletion or approved anonymisation
- Subprocessor instruction and confirmation
- Cache, queue, index, export, analytics, and manual-copy handling
- Backup expiry and re-deletion after restoration
- Residual legal, security, dispute, suppression, and acceptance evidence
- Authorised holds, review, release, and restricted use
- Completion criteria, failure reconciliation, and evidence certificate

No fixed period belongs in the DPA until the matching control is implemented and approved.

### 12. Government and Third-Party Demands

Require validation of authority and scope, lawful challenge where appropriate, minimisation, secure production, preservation, conflict-of-law review, customer notice where permitted, confidentiality, emergency handling, and transparency reporting where lawful. Regional schedules may require additional obligations or prohibit disclosure.

### 13. Term, Liability, and Conflict

Align DPA duration with processing, not merely the commercial term. Privacy and security obligations survive while covered data remains. Liability, indemnity, insurance, third-party beneficiary, regulatory cooperation, and precedence require business and qualified legal approval for the contracting parties and countries.

### Regional Schedules

The final DPA contains only schedules approved for the actual scope:

| Region | Required review |
|---|---|
| EU/EEA and Spain | GDPR Art. 28 content, national overlays, ePrivacy context, representative/DPO, transfers, supervisory authority, consumer and employment distinctions |
| United States | Federal sector rules plus every applicable state controller/processor or business/service-provider contract requirement, sale/share/targeted-advertising restrictions, certification, deletion, and audit terms |
| Canada | PIPEDA and provincial private-sector or health-law accountability, comparable protection, access, breach, and service-provider terms |
| Brazil | LGPD processing-agent duties, ANPD transfer framework, security, incidents, data-subject assistance, and Portuguese requirements |
| Other Americas | Country-specific processor, registration, transfer, breach, rights, language, consumer, and localisation review |
| Japan | Entrustment, supervision, foreign-third-party, provision-record, APPI security, breach, and rights requirements |
| South Korea | Entrustment versus third-party provision, public disclosures, overseas transfer, PIPA security, breach, destruction, and domestic-representative requirements |
| India | DPDP commencement, Data Fiduciary/Data Processor allocation, security, breach, deletion, children, Significant Data Fiduciary, and parallel cyber duties |
| Singapore | Organisation/data intermediary roles, written obligations, security, retention, access/correction assistance, breach, and comparable overseas protection |
| Mainland China | Personal information processor/entrusted party terms, separate consent or other basis where applicable, PIPIA, localisation, security assessment or standard contract/certification, onward transfer, and regulator access |
| Hong Kong | Data user/data processor contractual means, retention, security, access/correction, outsourcing guidance, and voluntary breach practice |
| Other Asia | Country-specific processor terminology, consent or notice, security, breach, rights, registration, localisation, transfer, language, and representative requirements |

### Approval and Execution Record

Do not execute until the record contains:

- Immutable DPA, service agreement, order, and annex versions
- Product and technical truth approvals
- Security and privacy approvals
- Business approval by the authorised operating entity
- Qualified counsel approval for every schedule
- Customer legal name, signatory, authority, signature method, and execution time
- Service start, DPA effective date, term, renewal, and termination linkage
- Archived signed copy, content hashes, and delivery evidence
- Owner, review date, material-change triggers, and rollback or suspension plan

## Troubleshooting

- **The customer supplies its own DPA:** Map every clause to current evidence and approved deviations; do not sign operational promises by default.
- **The customer is also a processor:** Require documented authority from its controller and configure the role chain and instructions precisely.
- **A provider is not yet selected:** Keep the annex incomplete and the affected feature disabled; never insert alternatives separated by slashes.
- **A customer requests data residency:** Verify primary, backup, support, telemetry, incident, and subprocessor paths before agreeing.
- **The transfer mechanism changes:** Suspend affected transfer or use an approved continuity plan; a contract reference does not preserve an invalid mechanism.
- **Audit rights expose other tenants:** Provide proportionate evidence and scoped audit arrangements without preventing mandatory oversight.
- **Deletion conflicts with a hold:** Restrict the scoped residual data, document authority and review, and do not issue an unconditional deletion certificate.
- **A country schedule conflicts with the main DPA:** Apply the approved precedence clause and mandatory local law; escalate rather than silently choosing convenient language.

## References

- [`data-inventory.yaml`](data-inventory.yaml): Processing and retention source
- [`controller-processor-matrix.md`](controller-processor-matrix.md): Role and provider evidence
- [`subprocessor-register.md`](subprocessor-register.md): Vendor activation and notification record
- [`incident-response-runbook.md`](incident-response-runbook.md): Incident operations
- [`rights-request-runbook.md`](rights-request-runbook.md): Rights assistance workflow
- [`retention-and-erasure-control-plan.md`](retention-and-erasure-control-plan.md): Exit and deletion controls
- [`global-legal-readiness.md`](global-legal-readiness.md): Market overlays
- [GDPR, including Article 28](https://eur-lex.europa.eu/eli/reg/2016/679/oj)
- [European Commission Standard Contractual Clauses](https://commission.europa.eu/publications/standard-contractual-clauses-international-transfers_en)
