# Underage Account Procedure

> **Document ID:** COMP-007
> **Version:** v1.0.0
> **Last Updated:** 2026-07-18
> **Owner:** Legal & Compliance Team
> **Review Cadence:** Annual

## Overview

This document defines the process for handling suspected underage accounts discovered
post-registration on the Profile Tailors platform. The registration system gates account creation
with an 18+ checkbox affirmation, but does not perform document-level age verification. This
procedure covers the residual risk of underage users who bypass or falsify the registration gate.

---

## Changes

| Version | Date       | Author          | Changes           |
|---------|------------|-----------------|-------------------|
| v1.0.0  | 2026-07-18 | Compliance Team | Initial procedure |

---

## Usage

### 1. Report Intake

#### 1.1 Reporting Channels

Reports of suspected underage accounts may be received through:

- **Email:** `privacy@profiletailors.com` — dedicated privacy and compliance inbox.
- **Support Ticket:** In-app support system (tagged `compliance / underage`).
- **Third-Party Notice:** Received via legal counsel, regulatory authority, or trusted flagger
  program.
- **Internal Discovery:** Identified by Profile Tailors staff during moderation, content review, or
  data analysis.

#### 1.2 Required Information

To initiate an investigation, the intake record MUST capture:

| Field                           | Required | Notes                                                |
|---------------------------------|----------|------------------------------------------------------|
| Reporter name and contact       | Yes      | May be anonymised for good-faith reports             |
| Relationship to the subject     | Yes      | Parent, guardian, self-report, third-party           |
| Subject's account identifier    | Yes      | Email, username, or workspace ID                     |
| Basis for belief                | Yes      | Observed behaviour, documentation, or other evidence |
| Relationship to Profile Tailors | Yes      | User, non-user, regulatory body                      |
| Supporting evidence             | No       | Attachments accepted (screenshots, documents)        |

If reporting via email, a template is available at
`docs/compliance/report-templates/underage-intake-template.md`.

#### 1.3 Intake SLA

- **Acknowledgment:** Within 2 business days of receipt.
- **Initial triage:** Within 5 business days of receipt.

---

### 2. Investigation

#### 2.1 Account Activity Review

Upon receiving a valid report, the compliance team SHALL:

1. Confirm the account exists and is active on the platform.
2. Review account metadata:
    - Registration timestamp and IP address.
    - Email verification status.
    - Workspace provisioning record.
    - Consent records (`CONTRACT_ACCEPTANCE` with purpose `registration_terms_and_eligibility`).
3. Review account activity:
    - Published content (channels, posts, schedules).
    - Account settings and profile information.
    - Billing or subscription data (if applicable).
4. Cross-reference with any existing support tickets or prior reports.

#### 2.2 Identity Evidence

The compliance team MAY request identity evidence from the account holder or the reporter:

- **Government-issued ID** (passport, driver's license) — date of birth extracted, stored
  temporarily, and purged after verification.
- **Self-declaration form** — optional, for low-confidence reports.

Evidence handling:

- All identity documents MUST be processed through a secure, access-controlled channel.
- Identity documents MUST NOT be stored longer than 30 days after case resolution.
- Access to identity evidence MUST be logged and restricted to the compliance team.

#### 2.3 Investigation SLA

- **Investigation completed:** Within 10 business days of triage.
- **Complex cases:** May be extended to 20 business days with documented justification.

---

### 3. Account Suspension

#### 3.1 Immediate Freeze

If the investigation produces credible evidence that the account holder IS under 18:

1. **Immediate account freeze:**
    - All active sessions are invalidated.
    - Access to the dashboard is revoked.
    - Scheduled publications are paused (not deleted).
    - Active publication flows are cancelled.

2. **Notification sent to account email:**
    - Account has been temporarily suspended.
    - Reason for suspension (suspected underage registration).
    - Instructions for appeal (see Section 5).
    - Reference to this procedure (COMP-007).

3. **Internal notification:**
    - Compliance case is updated with verdict and freeze timestamp.
    - Engineering team is notified if data deletion is required (see Section 4).

#### 3.2 Suspension SLA

- **Freeze action:** Within 1 business day of investigation conclusion.
- **Notification:** Within 2 business days of freeze action.

---

### 4. Data Deletion vs. Retention

#### 4.1 Decision Framework

After suspension, the compliance team determines the data disposition based on the following:

| Scenario                                | Action                                                  |
|-----------------------------------------|---------------------------------------------------------|
| Account holder confirms underage status | Full deletion of personal data within 30 days           |
| Account holder contests underage status | Retain data during appeal process (Section 5)           |
| Appeal successful (age verified)        | Full account reinstatement, no deletion                 |
| Appeal unsuccessful                     | Full deletion within 30 days of final decision          |
| Legal hold in effect                    | Retain only data specified in legal hold notice         |
| Regulatory investigation active         | Retain until regulatory body confirms no further action |

#### 4.2 Deletion Scope

When deletion is ordered, the following data MUST be deleted:

- **Personal data:** Email, name, profile information, account metadata.
- **Workspace data:** All workspaces owned by the account, including content, settings, and
  configurations.
- **Consent records:** `CONTRACT_ACCEPTANCE` records associated with the account.
- **Session data:** Refresh tokens, access tokens, and session records.
- **Analytics data:** Aggregated analytics that cannot be attributed to the individual may be
  retained.

The following data MAY be retained under legal hold or regulatory obligation:

- **Transaction records:** Billing and payment records required by tax law (retained per statutory
  retention period).
- **Legal hold data:** Data subject to a valid legal hold order (retained until hold is released).
- **Anonymised logs:** System logs with personal identifiers removed.

#### 4.3 Retention Periods

| Data Category          | Retention                       | Authority                   |
|------------------------|---------------------------------|-----------------------------|
| Personal data          | 30 days post-suspension         | This procedure              |
| Transaction records    | 7 years (or as required by law) | Tax / accounting regulation |
| Legal hold data        | Duration of hold                | Legal order                 |
| Anonymised system logs | 90 days                         | Standard retention policy   |
| Identity evidence      | 30 days post-resolution         | This procedure              |

#### 4.4 Deletion Verification

After deletion, the compliance team SHALL:

1. Verify deletion by attempting to access the account (expected: not found).
2. Confirm consent records are deleted.
3. Log the deletion action with timestamp and operator identity.
4. Provide a deletion confirmation to the account holder.
    - For anonymous or untrusted reporters: provide only a neutral acknowledgement without
      confirming account existence or investigation status.
    - For the account holder and other authorized parties: provide full confirmation.

---

### 5. Appeal Process

#### 5.1 Filing an Appeal

The account holder may appeal the suspension by:

1. **Replying to the suspension notification email** with evidence of age.
2. **Submitting a support ticket** referencing the suspension notification ID.
3. **Submitting a written request** to `privacy@profiletailors.com`.

#### 5.2 Appeal Evidence

The account holder must provide government-issued ID showing date of birth indicating 18+ at
registration time, or a court order or regulatory determination regarding the account status.

#### 5.3 Review Process

1. Appeal is logged and assigned a case ID.
2. Compliance team reviews the evidence within 5 business days.
3. If evidence is sufficient:
    - Account is reinstated.
    - Notification sent to account holder.
4. If evidence is insufficient:
    - Account holder is notified with a request for additional evidence.
    - Additional evidence must be submitted within 10 business days.
    - After 10 business days with no response, the suspension stands and deletion proceeds.

#### 5.4 Reinstatement Criteria

An account SHALL be reinstated if ANY of the following conditions are met:

- The account holder provides valid government ID proving they were 18+ at registration.
- A regulatory body or court determines the account is not in violation.
- The report is determined to be a false report or malicious filing.

#### 5.5 Appeal SLA

| Step                        | Timeframe                    |
|-----------------------------|------------------------------|
| Appeal acknowledgment       | 2 business days              |
| Evidence review             | 5 business days              |
| Additional evidence window  | 10 business days             |
| Final decision notification | 2 business days after review |

---

## Troubleshooting

### Escalation

If a case cannot be resolved within standard SLAs:

1. Document the reason for delay.
2. Escalate to the Compliance Team lead.
3. Notify the account holder of the delay and revised timeline.

### Exception Handling

For cases involving:

- **Regulatory inquiries:** Consult legal counsel before taking action.
- **Legal holds:** Coordinate with the legal team to ensure compliance.
- **High-profile accounts:** Escalate to senior management.

---

## References

- **COMP-001:** Data Inventory — `docs/compliance/data-inventory.md`
- **COMP-002:** Retention and Erasure Control Plan —
  `docs/compliance/retention-and-erasure-control-plan.md`
- **COMP-003:** Rights Request Runbook — `docs/compliance/rights-request-runbook.md`
- **COMP-004:** Incident Response Runbook — `docs/compliance/incident-response-runbook.md`
- **COMP-005:** Consent and Preference Register —
  `docs/compliance/consent-and-preference-register.md`
- **COMP-006:** Legal Publication Gate — `docs/compliance/legal-publication-gate.md`
- **RQ-006:** Age Eligibility Enforcement Specification —
  `openspec/changes/archive/2026-07-18-age-eligibility-enforcement/spec.md`
