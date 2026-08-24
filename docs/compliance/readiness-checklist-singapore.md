# Singapore PDPA Compliance — Standalone Readiness Checklist

> **Classification:** Internal — Market Release Control
> **Status:** Draft — Singapore Market Readiness Checklist
> **Review date:** 2026-07-23
> **Owner:** Legal and Compliance Owner

## Overview

This document details the standalone readiness checklist for launching Profile Tailors in Singapore
under the **Personal Data Protection Act (PDPA)**.

No enablement, regional targeting, or processing of Singapore-based personal data may occur unless
every item in this checklist contains verified technical/engineering evidence and has been formally
approved by qualified Singaporean legal counsel.

---

## Changes

| Version | Date       | Description                                              |
|---------|------------|----------------------------------------------------------|
| 1.0     | 2026-07-23 | Standalone PDPA readiness checklist for Singapore launch |

---

## Usage

Use this checklist to gather evidence and track readiness for Singapore market entry. Each section
must link to verified technical implementations and legal documentation before launch.

### 1. Data Protection Officer (DPO) Designation

Designating a DPO is a strict, legally binding requirement under the PDPA for all organizations
processing personal data in Singapore.

- [ ] **Appoint a DPO:** Formally appoint at least one individual as the Data Protection Officer (
  DPO). The DPO does not need to be a physical resident of Singapore, but they must be reachable.
- [ ] **Publicize DPO Contact Details:** Make the DPO’s contact details (such as a professional
  email address or telephone number) publicly available in the Privacy Policy and on the website.
- [ ] **Register the DPO with ACRA:** If Profile Tailors establishes a corporate entity or branch in
  Singapore, register the DPO’s details with the Accounting and Corporate Regulatory Authority (
  ACRA) via BizFile+.

### 2. Notification and Consent

The PDPA requires organizations to obtain consent before collecting, using, or disclosing personal
data, unless an exception applies.

- [ ] **Clear Purpose Notification:** Draft a concise, localized privacy notice describing exactly
  what personal data is collected and the specific purposes of collection, use, or disclosure.
- [ ] **Opt-In Consent Mechanics:** Verify that consent is obtained via an active opt-in (e.g.,
  ticking an unchecked checkbox) during account registration or before non-essential cookie storage.
- [ ] **Consent Withdrawal Workflow:** Implement a clear, simple workflow allowing users to withdraw
  consent for marketing communications, cookies, or secondary processing, with instructions on how
  this affects their account.
- [ ] **Reasonable Purpose Limitation:** Ensure that personal data collection is limited to what is
  reasonable to provide the service (e.g., do not force users to supply passport numbers or
  unnecessary identifiers to schedule social media posts).

### 3. Data Intermediary (Processor) Management

The PDPA defines a "Data Intermediary" (DI) as an organization that processes personal data on
behalf of another organization.

- [ ] **Data Intermediary Classification:** Formally identify all infrastructure providers (e.g.,
  Cloudflare, hosting providers) and social integrations (e.g., LinkedIn APIs) as Data
  Intermediaries.
- [ ] **DI Security & Protection Clauses:** Ensure all written agreements with DIs contain explicit
  clauses requiring them to protect personal data and comply with retention and breach notification
  obligations.
- [ ] **DI Retention Limits:** Verify that contract terms require DIs to return or safely destroy
  personal data once the purpose of processing is fulfilled.

### 4. Cross-Border Transfers (Transfer Limitation Obligation)

Under the Transfer Limitation Obligation, personal data must not be transferred outside Singapore
unless the destination country provides a standard of protection comparable to the PDPA.

- [ ] **Comparable Protection Verification:** Ensure that any transfer of personal data outside
  Singapore is governed by:
    - Binding corporate rules or Standard Contractual Clauses (SCCs) equivalent to PDPA standards.
    - Contracts requiring the recipient to provide comparable protection.
- [ ] **Transfer Destination Mapping:** Document the geographical locations of all cloud storage,
  CDN edges, databases, and backup infrastructure handling Singapore user data.

### 5. Mandatory Breach Notification

Singapore enforces a strict, legally mandated breach notification regime (within 3 calendar days of
determination).

- [ ] **Notifiable Breach Assessment:** Implement a internal assessment template to evaluate whether
  a data breach is "notifiable." A breach is notifiable if:
    - It is likely to result in significant harm to affected individuals (such as financial fraud,
      identity theft, or physical harm).
    - It is of a significant scale (involving the personal data of 500 or more individuals).
- [ ] **PDPC 3-Day Notification Clock:** Design and test an incident response protocol ensuring that
  if a breach is determined to be notifiable, the Personal Data Protection Commission (PDPC) is
  notified within **3 calendar days** (72 hours).
- [ ] **Affected Individuals Notification:** Set up communication templates and delivery channels to
  notify affected individuals as soon as practicable, parallel to or immediately after notifying the
  PDPC.
- [ ] **DI Breach Reporting SLA:** Ensure contracts with Data Intermediaries require them to notify
  Profile Tailors *immediately* (within 24 hours) upon suspecting or discovering any security
  breach.

### 6. User Rights (Access and Correction Obligations)

Data subjects in Singapore have a statutory right to access and correct their personal data.

- [ ] **Access Request Workflow:** Implement a secure procedure for users to request copies of their
  personal data and information about how their data has been used or disclosed within the past
  year.
- [ ] **Correction Request Workflow:** Implement a workflow allowing users to correct or update
  inaccurate personal data in active systems.
- [ ] **Fee and Timeline Compliance:** Ensure that access requests are fulfilled within **30 days**
  of receipt. If a fee is charged, it must be reasonable and estimated beforehand. If fulfillment
  takes longer than 30 days, notify the user with a reasonable estimate.

### 7. Retention Limitation Obligation

- [ ] **Purpose-Based Deletion Schedules:** Implement deletion or anonymization scripts that
  automatically remove user data when it is no longer necessary for legal or business purposes.
- [ ] **Backup and Audit Deletion:** Confirm that backup data retention timelines match the primary
  deletion policy and do not hold "stale" Singapore user data indefinitely.

### 8. Legal Approval and Sign-off

- [ ] **Qualified Singapore Legal Counsel Sign-off:** Obtain formal legal approval from qualified
  Singapore-based counsel validating the Singapore Terms of Service, Privacy Policy, and DPO
  appointment.
- [ ] **Immutable Technical Evidence Ledger:** Run, verify, and link all engineering and
  infrastructure compliance tests (such as cookie-consent enforcement, secure data isolation, and
  API access restrictions) to the Country Activation Record.

---

## Troubleshooting

- **What happens if our DPO is based outside Singapore?**
    * This is legally permissible under the PDPA, but you must ensure the DPO's contact details (
      e.g., email address) are publicized, and they are fully responsive to queries or requests
      during Singapore business hours.
- **Is GDPR compliance sufficient to meet PDPA requirements?**
    * While there is significant overlap, there are distinct differences. The PDPA requires explicit
      DPO designation (even for small organizations), a strict 3-calendar-day notification clock to
      the PDPC once a breach is determined to be notifiable, and unique definitions regarding Data
      Intermediaries and transfer safeguards.

---

## References

- [`global-legal-readiness.md`](global-legal-readiness.md): Global Market Register
- [`market-entry-asia.md`](market-entry-asia.md): Asia Expansion Strategy
- [Singapore PDPC Legislation and Guidelines](https://www.pdpc.gov.sg/overview-of-pdpa/the-legislation/personal-data-protection-act)
- [PDPC Portal for Reporting Data Breaches](https://www.pdpc.gov.sg/report-data-breach)
