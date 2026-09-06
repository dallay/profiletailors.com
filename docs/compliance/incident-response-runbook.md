# Incident Response Runbook

> **Classification:** Internal — Compliance
> **Status:** Draft — production use blocked pending ownership, contact, and counsel approval
> **Review cycle:** Quarterly, after each incident, and before enabling a new market

## Overview

Define the evidence-preserving process for detecting, assessing, containing, eradicating, and
recovering from privacy and security incidents. The runbook applies to every production service and
provider that the operating legal entity selects for Profile Tailors.

This document does not assume that every country uses the GDPR 72-hour rule. The incident team must
identify every connected jurisdiction and use the earliest applicable deadline in
the [jurisdiction matrix](incident-sla-table.yaml).

### Activation Blockers

- The operating legal entity, registered address, Privacy Lead, Communications Lead, and external
  counsel are unresolved.
- Production hosting, database, storage, monitoring, and delivery providers are not selected or
  evidenced.
- Alert routes and the incident-record system are not evidenced.
- Jurisdictions marked `counsel_confirmation_required` need local approval before market enablement.

### Roles and Responsibilities

| Role                | Responsibility                                                                     | Primary Contact |
|---------------------|------------------------------------------------------------------------------------|-----------------|
| Incident Commander  | Coordinates the response, preserves the decision clock, and approves containment   | Unassigned      |
| Security Lead       | Technical forensics, evidence preservation, root-cause analysis, and eradication   | Unassigned      |
| Privacy Lead        | Determines affected processing, jurisdictions, risk, and notification obligations  | Unassigned      |
| Communications Lead | Coordinates regulator, customer, processor, and data-subject communications        | Unassigned      |
| Legal Counsel       | Confirms privilege, applicable law, reporting thresholds, authority, and deadlines | Unassigned      |
| Processor Liaison   | Obtains facts and contractual notices from affected processors                     | Unassigned      |

### Incident Classification

| Severity      | Definition                                                                                                         | Example                                                 | Internal escalation target   |
|---------------|--------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------|------------------------------|
| P1 — Critical | Confirmed or strongly suspected personal-data compromise with high potential harm or an imminent external deadline | Credential exfiltration or unauthorised database access | Immediate; target 15 minutes |
| P2 — High     | Suspected or confirmed compromise with plausible risk or material service impact                                   | Successful phishing of a privileged service account     | Immediate; target 30 minutes |
| P3 — Medium   | Security event requiring investigation but no current evidence of personal-data compromise                         | Material authentication anomaly                         | Target 4 hours               |
| P4 — Low      | Informational event or confirmed false positive                                                                    | Unsuccessful port scan                                  | Next business day            |

Severity does not decide whether notification is legally required. A lower-severity event may still
trigger a short statutory deadline.

### Detection Sources

- Application, authentication, publishing, delivery, and audit signals implemented in the selected
  production stack
- Infrastructure, database, object-storage, email, social-platform, and other processor
  notifications
- User, customer, researcher, employee, or authority reports
- Repository or dependency security alerts

No managed observability provider is named because none is selected or evidenced in the repository.

## Changes

| Version | Date       | Description                                                                                           |
|---------|------------|-------------------------------------------------------------------------------------------------------|
| 2.0     | 2026-07-17 | Replaced the GDPR-only flow and unevidenced Vercel claim with a blocked, global jurisdiction workflow |
| 1.0     | 2026-07-17 | Initial preliminary runbook                                                                           |

## Usage

### 1. Declare and Start the Clocks

1. Acknowledge the signal and create an immutable incident record.
2. Record discovery time, awareness time, reporter, systems, processors, and every time zone in UTC.
3. Assign the Incident Commander and Security Lead immediately; engage the Privacy Lead and counsel
   for any possible personal-data impact.
4. Assign a provisional severity without delaying containment or legal assessment.
5. Preserve the original evidence and record every collection and access action.

Do not wait for complete facts before escalating. Several jurisdictions permit or require phased
reporting.

### 2. Contain and Preserve Evidence

1. Stop further unauthorised access using the least destructive effective action.
2. Rotate affected secrets, revoke sessions, suspend accounts, or isolate systems where justified.
3. Preserve logs, database and object metadata, deployment state, alerts, provider correspondence,
   and relevant configuration.
4. Keep an action timeline with actor, time, reason, expected effect, and observed result.
5. Issue a legal or evidence hold before routine retention controls can destroy relevant records.

### 3. Establish Facts and Jurisdictions

1. Map affected systems to the [data inventory](data-inventory.yaml) and controller/processor role.
2. Identify affected data categories, people, customers, markets, residences, establishments,
   processors, and transfer paths.
3. Determine whether credentials, sensitive data, children, financial data, large-scale data, or
   customer-controlled content are involved.
4. Ask each affected processor for discovery time, awareness time, scope, containment, locations,
   sub-processors, and contractual notices.
5. Maintain known, unknown, disputed, and inferred facts separately.

### 4. Decide Notifications

1. Select every applicable row in `incident-sla-table.yaml`; do not select only the controller's
   home jurisdiction.
2. Add state, provincial, sector, cyber-security, platform, contractual, customer, insurer, and
   law-enforcement duties that the matrix does not cover.
3. Use the earliest applicable deadline as the response deadline.
4. Record the legal threshold, supporting facts, decision owner, counsel reviewer, and decision time
   for every authority and affected group.
5. When facts are incomplete, submit a preliminary or phased report where the applicable regime
   allows it.
6. Use the authority and data-subject templates only after adapting their terminology and required
   fields to the applicable jurisdiction.

The Privacy Lead and counsel approve the legal decision. The Incident Commander ensures that
uncertainty does not silently consume a deadline.

### 5. Communicate

1. Keep authority, affected-person, customer, processor, public, employee, insurer, and platform
   messages consistent with the verified facts.
2. Avoid speculation, admissions beyond verified facts, and promises unsupported by implemented
   controls.
3. Provide a safe contact channel and practical harm-reduction steps.
4. Preserve submitted forms, delivery evidence, authority acknowledgements, message versions, and
   translations.
5. Track follow-up deadlines, supplemental reports, and authority questions.

### 6. Recover and Close

1. Verify eradication and safe restoration before normal operations resume.
2. Monitor for recurrence and secondary harm.
3. Complete a post-incident review with root cause, control failures, notification performance, and
   assigned remediation.
4. Reconcile the data inventory, ROPA, processor matrix, retention schedule, public notices,
   contracts, and technical controls.
5. Keep the incident record for the longest applicable legal, contractual, evidence-hold, or
   approved retention period.
6. Close only after counsel and the Incident Commander confirm that all follow-ups are complete.

## Troubleshooting

- **The jurisdiction is unknown:** Treat the shortest plausible deadline as operational, preserve
  the clock, and obtain urgent local advice.
- **Facts are incomplete:** Separate confirmed facts from hypotheses and use phased notification
  where allowed.
- **A processor is unresponsive:** Escalate through contractual and security contacts, document
  attempts, and do not pause the controller's deadline.
- **The Privacy Lead or counsel is unreachable:** Escalate to the legal-entity owner and incident
  executive; the current unresolved ownership is a production blocker.
- **Evidence conflicts:** Preserve every version, record provenance, and avoid overwriting the
  original.
- **Routine deletion may run:** Place a scoped evidence hold and document its legal owner and
  release criteria.
- **A market is absent from the matrix:** Do not infer its rule from a neighbouring country; obtain
  primary-source evidence and local approval.

## References

- [`incident-sla-table.yaml`](incident-sla-table.yaml): Operational jurisdiction matrix and primary
  authority links
- [`data-inventory.yaml`](data-inventory.yaml): Processing activities, providers, retention
  controls, and evidence
- [`controller-processor-matrix.md`](controller-processor-matrix.md): Role and provider-selection
  status
- [`ropa.md`](ropa.md): Processing-record summary and unresolved legal decisions
- [`breach-notification-authority-template.md`](breach-notification-authority-template.md):
  Preliminary authority template requiring jurisdiction adaptation
- [`breach-notification-subject-template.md`](breach-notification-subject-template.md): Preliminary
  affected-person template requiring jurisdiction adaptation
- [European Commission breach guidance](https://commission.europa.eu/law/law-topic/data-protection/information-business-and-organisations/obligations_en)
- [Brazil ANPD incident reporting](https://www.gov.br/anpd/pt-br/canais_atendimento/agente-de-tratamento/comunicado-de-incidente-de-seguranca-cis)
- [Canada OPC breach reporting](https://www.priv.gc.ca/en/report-a-concern/report-a-privacy-breach-at-your-organization/)
- [Japan PPC breach reporting](https://www.ppc.go.jp/personalinfo/legal/leakAction/)
- [Singapore PDPC breach reporting](https://www.pdpc.gov.sg/report-data-breach/before-you-report-a-data-breach-3/info)
