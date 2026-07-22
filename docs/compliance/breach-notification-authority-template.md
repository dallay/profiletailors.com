# Breach Notification — Authority Template

> **Status:** Draft — jurisdiction adaptation and legal approval required before submission

## Overview

Provide a fact-controlled working record for notification to a privacy, cyber-security,
consumer-protection, sector, or other competent authority. This is not a GDPR-only form and does not
establish which authority, threshold, content, or deadline applies.

Use the [incident SLA matrix](incident-sla-table.yaml), the affected authority's current form, and
qualified counsel. Where an authority requires its own portal or form, that official mechanism
controls.

## Changes

| Version | Date       | Description                                                                                 |
|---------|------------|---------------------------------------------------------------------------------------------|
| 2.0     | 2026-07-17 | Replaced the GDPR-only, assumed-entity template with a jurisdiction-neutral evidence record |
| 1.0     | 2026-07-17 | Initial GDPR Art. 33 draft                                                                  |

## Usage

Delete instructions and inapplicable fields from the submitted version. Do not guess; label
incomplete facts and use phased reporting only where permitted.

### Filing Control

- **Incident reference:** [REFERENCE]
- **Jurisdiction and legal instrument:** [JURISDICTION / LAW / SECTION]
- **Authority and official submission channel:** [AUTHORITY / PORTAL OR ADDRESS]
- **Notification threshold:** [THRESHOLD AND WHY IT IS MET]
- **Deadline and clock trigger:** [DEADLINE / TRIGGER / TIME ZONE]
- **Awareness or determination time:** [ISO 8601 / FACT OWNER]
- **Submission type:** [INITIAL / SUPPLEMENTAL / FINAL]
- **Legal approver:** [NAME / ROLE / APPROVAL TIME]

### Reporting Organisation

- **Full legal entity name:** [REQUIRED — UNRESOLVED FOR PROFILE TAILORS]
- **Registration number and jurisdiction:** [IF REQUIRED]
- **Registered address:** [REQUIRED — UNRESOLVED FOR PROFILE TAILORS]
- **Role:** [CONTROLLER / PROCESSOR / OTHER LOCAL TERM]
- **Representative, DPO, privacy contact, or local agent:** [AS APPLICABLE]
- **Primary incident contact:** [NAME / ROLE / EMAIL / PHONE / AVAILABILITY]

Do not submit “Dallay / Profile Tailors” as the legal entity unless the operating legal person has
been formally established and approved.

### Incident Timeline

- **Occurrence or estimated start:** [ISO 8601 / CONFIDENCE]
- **End or containment:** [ISO 8601 / STATUS]
- **Discovery:** [ISO 8601 / SOURCE]
- **Controller awareness or legal determination:** [ISO 8601 / RATIONALE]
- **Material actions and decisions:** [TIMELINE OR ATTACHMENT]

### Nature and Scope

- **Incident type:** [CONFIDENTIALITY / INTEGRITY / AVAILABILITY / COMBINATION]
- **What happened:** [VERIFIED PLAIN-LANGUAGE DESCRIPTION]
- **Affected systems and processing activities:** [INVENTORY IDS]
- **Affected locations and processors:** [LOCATIONS / PROVIDERS / ROLES]
- **Data categories:** [CATEGORIES]
- **Sensitive, credential, child, financial, or other regulated data:** [DETAILS]
- **Affected-person categories:** [CATEGORIES]
- **Estimated affected people:** [NUMBER / RANGE / METHOD]
- **Estimated affected records:** [NUMBER / RANGE / METHOD]
- **Unknowns and investigation plan:** [ITEM / OWNER / EXPECTED UPDATE]

### Risk and Consequences

- **Likely or possible harms:** [HARM / LIKELIHOOD / SEVERITY]
- **People at heightened risk:** [GROUPS AND WHY]
- **Risk methodology:** [METHOD]
- **Notification decision:** [RATIONALE AGAINST THE APPLICABLE THRESHOLD]

### Containment and Remediation

- **Immediate containment:** [ACTION / TIME / RESULT]
- **Evidence preserved:** [EVIDENCE / CUSTODIAN]
- **Remediation completed or planned:** [ACTION / OWNER / DATE]
- **Measures affected people can take:** [PRACTICAL STEPS]
- **Recurrence monitoring:** [CONTROL / PERIOD]

### Other Notifications

- **Affected people:** [STATUS / DATE / METHOD]
- **Customers or controllers:** [STATUS / CONTRACT DEADLINE]
- **Other authorities:** [AUTHORITY / STATUS / REFERENCE]
- **Insurer, platform, law enforcement, or other party:** [STATUS / LEGAL BASIS]

### Submission Record

- **Submitted at:** [ISO 8601]
- **Submitted by:** [NAME / ROLE]
- **Authority acknowledgement or reference:** [REFERENCE]
- **Exact submitted version and hash:** [LOCATION / HASH]
- **Required follow-up:** [ITEM / DEADLINE / OWNER]

## Troubleshooting

- **The authority or deadline is unclear:** Preserve the earliest plausible clock and obtain urgent
  local advice; do not default to GDPR.
- **Information is incomplete:** Mark unknowns and confirm whether preliminary or phased reporting
  is legally permitted.
- **Multiple markets are affected:** Create a filing-control record for each authority and reconcile
  terminology and facts across submissions.
- **The organisation is a processor:** Follow the contract and applicable law immediately; do not
  submit as controller without confirming the role.
- **An official form differs:** Use the authority's current form and retain this document as the
  internal evidence record.

## References

- [`incident-sla-table.yaml`](incident-sla-table.yaml): Jurisdiction deadlines, thresholds, and
  primary authority links
- [`incident-response-runbook.md`](incident-response-runbook.md): Global response and decision
  workflow
- [`breach-notification-subject-template.md`](breach-notification-subject-template.md):
  Affected-person communication template
- [`data-inventory.yaml`](data-inventory.yaml): Processing activities and evidence
- [`controller-processor-matrix.md`](controller-processor-matrix.md): Role and provider status
