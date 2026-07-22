# Data Protection Impact Screening and Assessment

> **Classification:** Internal — Privacy, Security, Product, and Legal
> **Status:** Screening framework — no production activity approved by this document
> **Review trigger:** Before new processing and after material change

## Overview

Screen every processing activity, feature, provider, market, and material change for elevated risk
and for a legally required privacy or data-protection impact assessment. When uncertain, perform the
fuller assessment rather than using a checklist score to avoid review.

Different jurisdictions use different terminology, thresholds, lists, regulator consultation duties,
filing requirements, and record periods. This framework supplies a common evidence core; qualified
counsel must add the applicable country and sector overlays.

### Screening Principles

- Screen before implementation or production activation, not after launch.
- Evaluate actual combined processing, not isolated database tables.
- Include affected people and customer-controlled content subjects, not only account holders.
- Consider product, privacy, security, safety, discrimination, speech, consumer, child, worker, and
  societal impacts where relevant.
- Distinguish inherent risk, implemented measures, residual risk, and accepted risk.
- Do not treat a vendor assessment, security review, or legitimate-interests assessment as a
  substitute for the DPIA.
- Consult affected stakeholders and specialists where appropriate and safe.
- Keep processing disabled when high residual risk requires regulator consultation or cannot be
  justified.

## Changes

| Version | Date       | Description                                                                          |
|---------|------------|--------------------------------------------------------------------------------------|
| 1.0     | 2026-07-17 | Added global screening, full assessment, consultation, approval, and change controls |

## Usage

### 1. Processing Change Record

- Feature, processing activity, owner, and target date
- Business and user outcome
- Controller, processor, customer, provider, and platform roles
- Enabled countries, affected-person locations, data locations, and remote access
- Data, sources, people, purposes, frequency, scale, and retention
- Technology, algorithms, models, matching, monitoring, profiling, and automation
- Decisions, recommendations, content actions, publication, and human review
- Providers, subprocessors, platforms, transfers, and contracts
- Alternative designs considered
- Existing data inventory, architecture, threat model, transfer assessment, and legal-basis
  references

### 2. Mandatory Escalation Screening

Escalate for full assessment and country review if any item applies or may apply:

- Sensitive, special-category, biometric, genetic, health, financial, authentication,
  precise-location, communications, criminal, or similarly protected data
- Children, teenagers, vulnerable people, workers, creators under power imbalance, or people not
  using the service whose content is processed
- Large-scale, systematic, persistent, cross-context, publicly sourced, scraped, or unexpected
  processing
- Tracking, behavioural advertising, profiling, scoring, ranking, recommendation, moderation,
  surveillance, or systematic monitoring
- Automated decisions or material effects on access, price, visibility, opportunity, safety, rights,
  or reputation
- AI models, generative output, training, fine-tuning, embeddings, inference, agents, biometric
  processing, or high-impact use
- Social content containing third-party personal data, images, opinions, political or union
  material, health, sexuality, or other sensitive context
- Credential, OAuth, API key, session, security, fraud, abuse, incident, or law-enforcement
  processing
- Combining datasets, importing from third parties, inferring new attributes, or changing purpose
- Cross-border transfer, localisation conflict, remote access, or provider government-access risk
- New technology, untested architecture, novel business model, or processing people may not
  reasonably expect
- Difficult withdrawal, deletion, correction, objection, explanation, or human intervention
- Breach or misuse with potential physical, financial, identity, employment, legal, speech,
  discrimination, or reputational harm
- Country authority list, sector rule, customer contract, regulator direction, or prior incident
  requiring assessment

Absence of a checked item does not prove low risk.

### 3. Full Processing Description

Document with data-flow diagrams:

- Collection and notice surface
- Every field, file, free-text, media, metadata, derived value, and secret
- Source, accuracy, update, correction, and provenance
- Purpose and legal basis per controller; customer instruction per processor activity
- Necessity of each field and operation
- Access roles, tenancy, support, and privilege
- Storage, cache, queue, index, analytics, logging, export, and backup paths
- Provider, platform, subprocessor, and onward disclosure
- Country, transfer, localisation, and government-demand path
- Retention trigger, action, hold, backup expiry, and evidence
- Rights, consent, preference, opt-out, appeal, complaint, and redress flow
- Failure, misuse, model, content, and incident scenarios

### 4. Necessity and Proportionality

For each purpose:

- Is the purpose specific, lawful, understandable, and within the approved product?
- Can the outcome be achieved without personal data or with less data, precision, duration,
  centralisation, access, or transfer?
- Is collection limited to what the person or customer expects and was told?
- Is the legal basis appropriate and evidenced for each market?
- Are consent and withdrawal valid where used?
- Are contract-necessity claims essential rather than merely convenient?
- Are legitimate interests defined and balanced where applicable?
- Are sensitive-data, child, public-source, or automated-decision conditions satisfied?
- Are accuracy, fairness, transparency, explainability, and human-review measures proportionate?
- Can rights be exercised effectively without harming other people or security?
- Are retention, provider, transfer, and access choices the least intrusive feasible design?

Record rejected alternatives and why they were insufficient.

### 5. Risk Register

Assess harms to people, customers, and others, including:

- Unauthorised disclosure, identity theft, credential compromise, fraud, stalking, or physical
  safety
- Loss, corruption, unavailability, publication error, duplicate posting, or inability to delete
- Chilling effects, surveillance, manipulation, discrimination, exclusion, or unfair treatment
- Incorrect inference, hallucination, bias, automation error, or lack of human review
- Exposure of private, confidential, child, intimate, political, health, employment, or location
  content
- Account takeover, cross-tenant access, malicious insider, supply-chain compromise, or unsafe
  support access
- Unlawful marketing, tracking, platform use, transfer, government access, or secondary purpose
- Inability to access, correct, export, object, withdraw, appeal, complain, or obtain remedy
- Deceptive UI, bundled consent, dark patterns, inaccessible controls, or language mismatch
- Retention beyond need, incomplete deletion, backup reappearance, or provider lock-in
- Customer misuse of the product and foreseeable abuse of publishing or API features

For each scenario record affected people, cause, existing controls, likelihood, severity,
detectability, scale, duration, reversibility, uncertainty, and inherent and residual rating.
Narrative evidence controls the decision; a numeric score alone does not.

### 6. Measures and Ownership

Measures may include:

- Remove the feature, field, provider, transfer, or market
- Local or on-device processing
- Data minimisation, aggregation, anonymisation, or protected pseudonymisation
- Purpose and role separation
- Stronger authentication, authorisation, tenancy, encryption, and key control
- Safer defaults, granular choices, friction for high-risk actions, and clear confirmation
- Human review, explanation, appeal, override, and quality thresholds
- Child, vulnerable-person, sensitive-data, and abuse restrictions
- Provider contract, subprocessor, transfer, localisation, and government-demand controls
- Retention, deletion, holds, restore re-deletion, and evidence
- Monitoring, incident, red-team, misuse, content, model, and drift testing
- Accessible notice, consent, opt-out, rights, support, and complaint routes
- Customer configuration, training, warnings, approval, and audit controls

Each measure needs an owner, implementation reference, acceptance test, due date, monitoring, and
residual-risk effect.

### 7. Stakeholder and Specialist Consultation

Record whether and how the assessment involved:

- Product, engineering, security, privacy, legal, operations, support, accessibility, and business
  owners
- Customer controllers and administrators
- Representative users and affected non-users where feasible
- Child safety, human rights, content safety, discrimination, employment, consumer, sector, or
  local-country specialists
- DPO, representative, security officer, works council, regulator, or other required body

Document reasons when consultation is unsafe, disproportionate, legally restricted, or would
compromise security. Consultation does not transfer decision accountability.

### 8. Country and Sector Overlay

Qualified counsel records:

- Whether an assessment is mandatory, recommended, filed, registered, published, or retained
- Authority lists or blacklists and exemption criteria
- Required contents, language, signature, DPO advice, and data-subject consultation
- Thresholds for children, biometrics, sensitive data, automated decisions, monitoring, scale, or
  transfers
- Prior consultation or authorisation trigger when residual high risk remains
- AI, platform, cyber-security, consumer, employment, health, financial, telecom, or public-sector
  parallel assessments
- Review and record period, regulator access, confidentiality, and publication rules

### 9. Decision and Approval

Permitted outcomes:

- **Approved:** Necessity, legal conditions, measures, and residual risk are accepted for exact
  markets and configuration.
- **Approved with preconditions:** Activation is blocked until every named measure has evidence.
- **Prior consultation required:** Processing remains disabled pending authority process and final
  decision.
- **Redesign required:** Current design is not necessary, proportionate, or safe enough.
- **Rejected:** Processing must not proceed.

Record product, security, privacy, business, DPO where applicable, and qualified legal decisions;
dissent; conditions; activation control; review date; and rollback owner.

### 10. Review Triggers

Reopen on changes to purpose, data, audience, scale, model, decision effect, provider, country,
transfer, role, retention, access, customer use, security, law, authority guidance, complaint,
incident, abuse pattern, or evidence that a prior assumption is wrong.

## Troubleshooting

- **The feature is only a pilot:** Pilot processing can still cause harm; limit data and users,
  obtain approvals, and assess before real personal data.
- **The customer is controller:** Profile Tailors still assesses its processor design, security,
  providers, transfers, and foreseeable misuse and supplies accurate customer-assessment
  information.
- **Data is public:** Public availability does not remove purpose, fairness, sensitivity, context,
  accuracy, rights, or transfer risks.
- **A vendor provides its own DPIA:** Use it as evidence only; assess the actual Profile Tailors
  configuration, roles, data flows, and markets independently.
- **The risk score is below a threshold:** Review qualitative severe, irreversible,
  vulnerable-person, legal, and uncertainty factors before deciding.
- **Residual high risk remains:** Keep processing disabled and follow the approved
  regulator-consultation or rejection path.
- **The system changes frequently:** Define configuration boundaries, automated evidence,
  monitoring, and change triggers; do not approve an undefined future system.

## References

- [`data-inventory.yaml`](data-inventory.yaml): Processing activity source
- [`global-legal-readiness.md`](global-legal-readiness.md): Country and AI overlays
- [`international-transfer-assessment-template.md`](international-transfer-assessment-template.md):
  Cross-border assessment
- [`vendor-due-diligence-checklist.md`](vendor-due-diligence-checklist.md): Provider evidence
- [`consent-and-preference-register.md`](consent-and-preference-register.md): Choice and withdrawal
  controls
- [`rights-request-runbook.md`](rights-request-runbook.md): Rights and redress controls
- [GDPR Article 35](https://eur-lex.europa.eu/eli/reg/2016/679/oj)
