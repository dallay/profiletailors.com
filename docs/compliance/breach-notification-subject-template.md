# Breach Notification — Affected Person Template

> **Status:** Draft — jurisdiction adaptation, accessibility review, and legal approval required

## Overview

Provide clear, practical notice to an affected person when applicable law or an approved risk
decision requires it. This template is jurisdiction-neutral: notification thresholds, timing,
mandatory content, delivery, language, and permitted delay differ across markets.

The message must describe verified facts, explain realistic harm-reduction actions, and identify the
responsible legal entity. It must not overstate certainty, security controls, or legal conclusions.

## Changes

| Version | Date       | Description                                                                                            |
|---------|------------|--------------------------------------------------------------------------------------------------------|
| 2.0     | 2026-07-17 | Replaced the GDPR-only text and unresolved Profile Tailors signature with a global controlled template |
| 1.0     | 2026-07-17 | Initial GDPR Art. 34 draft                                                                             |

## Usage

Create one approved source version, then produce accessible, legally reviewed translations for
affected audiences. Delete bracketed instructions before delivery.

### Message Control

- **Incident reference:** [REFERENCE]
- **Jurisdiction and notification basis:** [LAW / RISK DECISION]
- **Required delivery deadline:** [DATE / TIME / TIME ZONE]
- **Audience and language:** [GROUP / LANGUAGE]
- **Delivery method:** [EMAIL / IN-APP / POST / OTHER]
- **Legal and communications approval:** [NAME / TIME]

### Subject

[CLEAR DESCRIPTION, FOR EXAMPLE: Important security notice about your Profile Tailors account]

### Opening

Hello [NAME OR NEUTRAL SALUTATION],

[FULL APPROVED LEGAL ENTITY NAME] is writing to tell you about a security incident involving your
personal information. [STATE WHETHER THIS PERSON IS CONFIRMED AFFECTED OR MAY BE AFFECTED.]

### What Happened

[DESCRIBE THE VERIFIED EVENT, RELEVANT DATES, AND WHETHER IT HAS BEEN CONTAINED. DISTINGUISH CONFIRMED FACTS FROM THE CONTINUING INVESTIGATION.]

### Information Involved

[LIST THE DATA CATEGORIES RELEVANT TO THIS RECIPIENT. DO NOT INCLUDE THE PERSON'S SECRETS OR UNNECESSARY PERSONAL DATA IN THE NOTICE.]

### What This Could Mean for You

[EXPLAIN PLAUSIBLE CONSEQUENCES IN PLAIN LANGUAGE, INCLUDING ANY HEIGHTENED RISK RELEVANT TO THIS PERSON.]

### What We Have Done

[LIST VERIFIED CONTAINMENT, REMEDIATION, AUTHORITY, PROCESSOR, AND MONITORING ACTIONS THAT MAY BE DISCLOSED.]

### What You Can Do

[PROVIDE INCIDENT-SPECIFIC, ACCESSIBLE STEPS. INCLUDE ONLY ACTIONS THAT REDUCE THE IDENTIFIED RISK.]

Possible actions when factually relevant include:

- Reset a credential through the official service and avoid reusing it elsewhere.
- Revoke active sessions or connected applications.
- Watch for targeted phishing that refers to the exposed information.
- Contact an appropriate financial, identity-protection, or public authority using independently
  verified details.

### Contact and Assistance

- **Responsible legal entity:** [FULL LEGAL NAME — UNRESOLVED FOR PROFILE TAILORS]
- **Privacy or incident contact:** [MONITORED EMAIL / PHONE / HOURS / ACCESSIBILITY OPTIONS]
- **DPO, representative, or local agent:** [IF APPLICABLE]
- **Independent authority or remedy information:** [WHEN REQUIRED]
- **Updates:** [TRUSTED STATUS PAGE OR CONTACT METHOD]

We are sorry for the concern and inconvenience this incident may cause.

[APPROVED SIGNATORY]\
[FULL LEGAL ENTITY NAME]

### Delivery Evidence

- **Final message version and hash:** [LOCATION / HASH]
- **Recipients or selection query:** [EVIDENCE REFERENCE]
- **Delivery started and completed:** [ISO 8601]
- **Failures and alternative delivery:** [COUNT / ACTION]
- **Translation and accessibility review:** [REVIEWER / VERSION]

## Troubleshooting

- **It is unclear whether notice is required:** Apply the jurisdiction matrix and documented risk
  threshold with counsel; automatic over-notification can itself create privacy and security harm.
- **The facts may change:** State what is known now, provide a safe update channel, and issue a
  corrected or supplemental notice when material facts change.
- **The person is in multiple relevant jurisdictions:** Apply the most protective compatible
  requirements and document any required local variants.
- **Direct delivery could expose more data:** Select a safer approved channel and minimise
  information in subject lines, envelopes, push notifications, and shared devices.
- **Translation is required:** Use professional legal translation and review; key parity alone does
  not establish legal equivalence.

## References

- [`incident-sla-table.yaml`](incident-sla-table.yaml): Jurisdiction deadlines, triggers, and
  authority links
- [`incident-response-runbook.md`](incident-response-runbook.md): Global assessment and
  communication process
- [`breach-notification-authority-template.md`](breach-notification-authority-template.md):
  Authority filing record
- [`data-inventory.yaml`](data-inventory.yaml): Processing activities and affected data categories
- [`legal-publication-gate.md`](legal-publication-gate.md): Entity, translation, and approval
  controls
