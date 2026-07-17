# Breach Notification — Supervisory Authority Template

> **GDPR Art. 33** — Notification to the supervisory authority within 72 hours of becoming aware of a personal data breach.

## Overview

This template documents the information required for a personal data breach notification under GDPR Art. 33. Complete all sections marked with `[MANDATORY]` and send to the lead supervisory authority for the controller's main establishment within 72 hours of becoming aware of the breach.

## Changes

| Version | Date | Description |
|---------|------|-------------|
| 1.0 | 2026-07-17 | Initial Art. 33 notification template |

## Usage

### 1. Controller Information

- **Entity name:** Dallay / Profile Tailors
- **Entity address:** [ADDRESS]
- **DPO contact:** [DPO EMAIL / PHONE / N/A]
- **Reference number:** INC-[YYYY]-[NNN]

### 2. Incident Overview

- **Date and time of breach:** [ISO 8601 timestamp]
- **Date and time of discovery:** [ISO 8601 timestamp]
- **Nature of breach:** [Select: confidentiality / integrity / availability breach]
- **Brief description:** [MANDATORY — Describe the incident, including the categories and approximate number of data subjects concerned and personal data records concerned]

### 3. Categories of Data and Data Subjects

- **Categories of personal data:** [e.g., email addresses, passwords, financial data]
- **Categories of data subjects:** [e.g., registered users, waitlist signups]
- **Approximate number of data subjects affected:** [NUMBER]
- **Approximate number of personal data records affected:** [NUMBER]

### 4. Likely Consequences

- [MANDATORY — Describe the likely consequences of the personal data breach]

### 5. Measures Taken or Proposed

- [MANDATORY — Describe measures taken or proposed to address the breach and mitigate possible adverse effects]

### 6. Cross-Border Processing

- Does this breach involve data subjects in multiple EU member states? [Yes / No]
- Lead supervisory authority: [AEPD / other]

### 7. Documentation

Attach:
- [ ] Incident timeline
- [ ] Root cause analysis (if available)
- [ ] Evidence preservation log
- [ ] Data flow diagram of affected processing

---

**Notification date:** [DATE]
**Submitted by:** [NAME, ROLE]

## Troubleshooting

- **Missing required information:** Complete as much as possible within the 72-hour window. GDPR Art. 33(1) explicitly permits phased notification — submit what is available and provide updates as information becomes available.
- **Cross-border uncertainty:** Contact the lead supervisory authority for guidance if the affected data subjects span multiple jurisdictions.

## References

- GDPR Art. 33: Notification to supervisory authority
- [`incident-response-runbook.md`](incident-response-runbook.md): Response process and classification
- [`breach-notification-subject-template.md`](breach-notification-subject-template.md): Data subject notification template (Art. 34)
- [`data-inventory.yaml`](data-inventory.yaml): Processing activity records
