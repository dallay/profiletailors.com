# Incident Response Runbook

> **Classification:** Internal — Compliance
> **Status:** Preliminary
> **Review cycle:** Quarterly or after each security incident

## Overview

Define the process for detecting, assessing, containing, eradicating, and recovering from privacy and security incidents, including personal data breaches as defined by GDPR Art. 4(12). This runbook covers all production services operated by Profile Tailors under Dallay.

### Roles and Responsibilities

| Role | Responsibility | Primary Contact |
|------|----------------|-----------------|
| **Incident Commander (IC)** | Coordinates response, makes containment decisions, communicates status | On-call rotation |
| **Security Lead** | Technical forensics, root cause analysis, eradication | Security team |
| **Privacy Lead** | Data breach assessment, notification decisions, DPO liaison | TBD |
| **Communications Lead** | Authority and data subject notifications, public statements | TBD |
| **Legal Counsel** | Legal obligations, regulatory coordination | External counsel |

### Incident Classification

| Severity | Definition | Examples | Notification SLA |
|----------|------------|----------|------------------|
| **P1 — Critical** | Confirmed breach of personal data with high risk to data subjects | Unauthorised access to user database, credential exfiltration | 24h to DPO / legal |
| **P2 — High** | Suspected or confirmed breach with likely risk to data subjects | Access log anomaly, successful phishing of service account | 48h to DPO / legal |
| **P3 — Medium** | Security event with potential impact to confidentiality, integrity or availability | WAF bypass attempt, failed brute force detected | 72h logged |
| **P4 — Low** | Informational or non-personal-data event | Port scan, false positive alert | Next business day |

### Detection Sources

- Application error rate alerts (Vercel Observability)
- Authentication anomaly detection
- Upstream provider security notifications
- Manual report from user or third party

## Changes

| Version | Date | Description |
|---------|------|-------------|
| 1.0 | 2026-07-17 | Initial runbook with 5-phase response process and classification table |

## Usage

### 1. Triage (≤30 min)

1. Acknowledge alert or report.
2. Assign severity based on initial information.
3. Declare incident and assemble response team if ≥P2.
4. Record initial findings in incident log.

### 2. Containment (≤2h for P1/P2)

1. Prevent further unauthorised access (rotate keys, block IPs, suspend accounts).
2. Preserve evidence (logs, snapshots, audit trail).
3. Isolate affected systems if feasible.
4. Document all actions taken with timestamps.

### 3. Investigation and Assessment

1. Determine root cause.
2. Identify affected personal data categories and data subjects.
3. Assess risk to data subjects' rights and freedoms.
4. Determine whether notification obligations are triggered (GDPR Art. 33-34).

### 4. Notification

1. If notification required: prepare and send authority notification within 72h of awareness.
2. If high risk to data subjects: prepare and send data subject notification without undue delay.
3. Use templates in `breach-notification-authority-template.md` and `breach-notification-subject-template.md`.

### 5. Remediation and Closure

1. Implement permanent fixes.
2. Update security controls to prevent recurrence.
3. Complete post-incident review within 14 days.
4. Update runbook with lessons learned.

## Troubleshooting

- **Unclear severity classification:** Default to P3 until more information is available. The Incident Commander can reclassify at any stage.
- **Unable to reach DPO/Privacy Lead:** Escalate to Legal Counsel. Document the delay and attempt timestamps for the regulatory notification record.
- **Missing evidence for notification decision:** Use the [data inventory](data-inventory.yaml) to identify the affected processing activity and its legal basis. Preserve all logs and system state before remediation.

## References

- GDPR Art. 33-34: Breach notification requirements
- [`incident-sla-table.yaml`](incident-sla-table.yaml): Jurisdiction-specific notification deadlines
- [`breach-notification-authority-template.md`](breach-notification-authority-template.md): Authority notification template (Art. 33)
- [`breach-notification-subject-template.md`](breach-notification-subject-template.md): Data subject notification template (Art. 34)
- [`data-inventory.yaml`](data-inventory.yaml): Processing activity records
