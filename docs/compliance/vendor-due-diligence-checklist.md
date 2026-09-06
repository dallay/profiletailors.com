# Vendor Due Diligence Checklist

> **Classification:** Internal — Procurement, Security, Privacy, Legal, and Operations
> **Status:** Control template — no production vendor approved by this document
> **Decision rule:** Missing material evidence blocks activation

## Overview

Evaluate every provider, platform, consultant, affiliate service, SDK, API, model, host, database,
storage system, email service, analytics tool, monitoring system, and other recipient before it
receives production data or becomes a customer-facing dependency.

Due diligence is proportionate to data, access, criticality, countries, customer promises,
substitutability, and harm, but it never accepts a provider merely because it is well known, offers
a standard DPA, or has a certification logo.

## Changes

| Version | Date       | Description                                                                                         |
|---------|------------|-----------------------------------------------------------------------------------------------------|
| 1.0     | 2026-07-17 | Added identity, role, security, privacy, transfer, resilience, contract, and exit evidence controls |

## Usage

### 1. Vendor and Service Identity

- Exact legal entity, registration, address, ownership, affiliates, and signing authority
- Product, plan, tenant, account, region, configuration, support tier, and service URL
- Sales entity versus processing, hosting, support, billing, and subprocessor entities
- Financial condition, insurance, sanctions, export-control, conflict, and concentration risk
- Intended activation date, owners, business need, alternatives, and exit target
- Whether the vendor can change terms, services, countries, subprocessors, or data uses unilaterally

### 2. Role and Data Flow

- Purpose and essential-means decision for each operation
- Controller, processor, subprocessor, independent controller, joint controller, recipient, or
  platform role by country
- Customer instruction and Profile Tailors independent purposes
- Data and affected-person categories, sensitivity, volume, frequency, and free-text/media exposure
- Collection, API, SDK, cookie, pixel, browser, server, support, telemetry, log, backup, and export
  flows
- Primary, replica, backup, support, administration, incident, and remote-access countries
- Onward recipients, subprocessors, affiliates, and integrations
- Data-use restrictions, combination, advertising, profiling, product improvement, and AI training
  settings

### 3. Security Evidence

- Security governance, accountable officer, policies, training, workforce screening where lawful,
  and access reviews
- Architecture, tenant isolation, authentication, privileged access, service accounts, and customer
  administration
- Encryption in transit and at rest, key ownership, rotation, hardware protection, and secret
  management
- Secure development, threat modelling, code review, dependencies, vulnerability disclosure, patch
  targets, and penetration testing
- Network, endpoint, physical, data-centre, remote-work, and support controls
- Logging, monitoring, alerting, time synchronisation, audit integrity, and customer visibility
- Incident response, 24/7 contact, notification target, forensics, evidence, remediation, and
  exercise history
- Backup, recovery, continuity, disaster recovery, RTO/RPO evidence, capacity, and dependency
  resilience
- Deletion, media sanitisation, backup expiry, restore re-deletion, legal holds, and certification
- Independent reports or certifications with exact entity, service, period, locations, controls,
  exceptions, and remediation

Certification does not prove controls outside its scope or after its audit period.

### 4. Privacy and Data Governance

- Public notices and internal records match the offered service
- Purpose limitation, instruction processing, confidentiality, minimisation, accuracy, and retention
- Rights search, correction, export, deletion, restriction, objection, and appeal assistance
- Child, sensitive, biometric, health, financial, authentication, and regulated-data restrictions
- Cookie, SDK, tracking, analytics, advertising, model, and training behaviour
- DPO, representative, local agent, registrations, or regulator contacts where applicable
- Privacy and transfer impact assessment support
- Government, law-enforcement, civil discovery, regulator, and emergency demand handling
- Transparency reporting, customer notice, legal challenge, and minimised disclosure
- Data return, portability, interoperability, and exit capability

### 5. Subprocessors and Supply Chain

- Complete legal-entity list, service, role, country, and data access
- Authorisation and change-notice terms
- Objection, alternative, emergency replacement, and termination process
- Equivalent flow-down for security, privacy, transfers, incidents, rights, audit, and deletion
- Vendor responsibility, monitoring, evidence, and enforcement
- Nested cloud, support, model, content delivery, observability, email, and professional-service
  dependencies
- Software supply chain, package provenance, signing, build, release, and vulnerability response

### 6. International Transfers and Localisation

- Every exporter/importer/onward route and role
- Applicable localisation, filing, assessment, certification, consent, notice, or approval
  requirement
- Executed transfer mechanism and annexes, not a marketing statement
- Destination legal environment and government-access assessment
- Supplementary technical, contractual, and organisational measures
- Ability to suspend, localise, migrate, or delete if the mechanism changes
- Customer and affected-person information requirements

Complete [`international-transfer-assessment-template.md`](international-transfer-assessment-template.md)
for every restricted route.

### 7. Contract Review

- Exact parties, service, plan, order, effective date, term, renewal, precedence, and archive
- DPA role, instructions, security annex, subprocessors, transfers, rights, incidents, audits,
  deletion, and assistance
- Service levels, support, maintenance, deprecation, API change, portability, continuity, and
  termination
- Confidentiality, intellectual property, open source, customer data, telemetry, feedback, and model
  training
- Warranties, liability, indemnity, insurance, regulatory costs, and remedies approved by the
  business and counsel
- Unilateral-change, suspension, acceptable-use, content-removal, and account-termination rights
- Data export, transition assistance, account closure, deletion evidence, and post-termination
  access
- Governing law, forum, arbitration, language, order-of-precedence, and mandatory local protections
- Audit, regulator, customer, and third-party beneficiary rights where required

### 8. Operational Verification

Before production activation:

- Verify account ownership, MFA, administrator roles, API keys, secrets, least privilege, and
  break-glass access.
- Capture configuration-as-code or controlled evidence for region, retention, logging, telemetry,
  training, sharing, and encryption choices.
- Observe actual network destinations, cookies/storage, payload fields, logs, and support paths.
- Test failure, retry, duplicate, timeout, deletion, export, key rotation, incident contact, and
  vendor outage.
- Add monitoring for configuration drift, new subprocessors, new countries, contract changes,
  security advisories, and unexpected data.
- Record deployment commit, environment, first-data time, owner, approval version, and rollback.

### 9. Risk and Decision

Record:

- Criticality and substitutability
- Inherent privacy, security, transfer, legal, availability, financial, platform, and concentration
  risks
- Evidence quality and age
- Required remediation, owner, due date, and activation precondition
- Residual risk and approved exception authority
- Decision: approved, approved inactive, conditional, rejected, suspended, or exiting
- Scope, countries, data, configuration, expiry, monitoring, and rollback

A numeric vendor score cannot override a missing mandatory contract, invalid transfer, unresolved
critical vulnerability, prohibited data use, or inability to delete.

### 10. Ongoing Review and Exit

Review on schedule and after incidents, material changes, audit findings, acquisitions, financial
distress, subprocessor or country changes, new data use, law changes, service degradation, or
customer complaint.

The exit plan covers:

- Replacement and data portability
- Freeze of new data and credentials
- Export completeness and integrity
- Customer and authority notice where required
- Primary, replica, cache, log, analytics, support, subprocessor, and backup deletion
- Contract termination, billing, access, DNS, secrets, integrations, and account closure
- Residual holds, certificates, expiry, monitoring, and final evidence

## Troubleshooting

- **The vendor will not answer a questionnaire:** Use reliable scoped evidence and contract rights;
  reject when material uncertainty remains.
- **The vendor is already used in development:** Prevent production data and credentials until
  approval; inventory and minimise test data.
- **A free plan has no DPA:** Do not send production personal data merely because the service is
  technically accessible.
- **The provider changes its terms online:** Preserve accepted versions, monitor change, reassess,
  and suspend affected use when required.
- **A certification report is confidential:** Establish a lawful review path and record scoped
  findings; absence of accessible evidence remains risk.
- **The vendor cannot support one country:** Disable that vendor-backed feature or the market; do
  not generalise approval from other countries.
- **Exit would interrupt customers:** Design portability and replacement before activation; business
  inconvenience does not create a transfer or privacy exception.

## References

- [`subprocessor-register.md`](subprocessor-register.md): Activation and status record
- [`customer-dpa-template.md`](customer-dpa-template.md): Contract requirements
- [`international-transfer-assessment-template.md`](international-transfer-assessment-template.md):
  Transfer routes
- [`dpia-screening-and-assessment.md`](dpia-screening-and-assessment.md): High-risk processing
  assessment
- [`controller-processor-matrix.md`](controller-processor-matrix.md): Current provider and role
  evidence
- [`retention-and-erasure-control-plan.md`](retention-and-erasure-control-plan.md): Provider exit
  and deletion
