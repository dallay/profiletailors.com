# Japan APPI Compliance — Standalone Readiness Checklist

> **Classification:** Internal — Market Release Control
> **Status:** Draft — Japan Market Readiness Checklist
> **Review date:** 2026-07-23
> **Owner:** Legal and Compliance Owner

## Overview

This document provides the standalone readiness checklist for launching Profile Tailors in Japan under the **Act on the Protection of Personal Information (APPI)**.

No enablement, local targeting, or processing of Japanese users' personal data may occur unless every item in this checklist contains verified, immutable technical/engineering evidence and has been formally approved by qualified Japanese legal counsel.

---

## Changes

| Version | Date       | Description |
|---------|------------|-------------|
| 1.0     | 2026-07-23 | Standalone APPI readiness checklist for Japan launch |

---

## Usage

Use this checklist to gather evidence and track readiness for Japan market entry. Each section must link to verified technical implementations and legal documentation before launch.

### 1. DPO and Local Representation

Although the APPI does not strictly mandate a formal "Data Protection Officer" in the European GDPR sense, it requires a robust local contact point and designated personnel responsible for data management.

- [ ] **Designate Personal Information Manager:** Formally appoint an individual or team responsible for handling personal information security, compliance, and responding to inquiries from data subjects and the Personal Information Protection Commission (PPC).
- [ ] **Establish Local Inquiries Channel:** Set up an accessible, Japanese-language contact point (e.g., email or web form) specifically for privacy inquiries.
- [ ] **Determine Foreign Operator Representation:** If Profile Tailors does not have an office or legal entity in Japan, verify if the PPC requires a designated local representative or contact agent to handle regulatory communication and accept service of process.

### 2. Notices and Local-Language Support

All communications must be clear, transparent, and tailored to Japanese standards.

- [ ] **Japanese-Language Privacy Notice:** Draft and review a complete Japanese translation of the Privacy Policy. The notice must specify:
    - The purpose of using personal information (specified as concretely as possible).
    - Measures taken for security control.
    - Information about the operator (company name, address, and representative name).
    - Procedures for responding to requests for disclosure, correction, or discontinuation of use.
- [ ] **Strict Specified Purposes:** Verify that the "Purpose of Use" is not vague. APPI prohibits altering the purpose of use beyond what is reasonably considered relevant to the original purpose.
- [ ] **Localized Support System:** Confirm that customer support workflows can ingest, translate, and respond to Japanese privacy rights requests or complaints within statutory timelines.

### 3. Consent and Opt-In Mechanics

The APPI places strict boundaries on consent, particularly for transferring or sharing data.

- [ ] **Separate Consent for Third-Party Provision:** Ensure that any provision of personal data to a third party (where the third party acts as an independent controller) is backed by explicit, prior consent from the user, unless a statutory exception applies.
- [ ] **No Opt-Out Provisioning (Pre-ticked Boxes):** Confirm that pre-ticked consent boxes or passive opt-out mechanisms are not used for third-party data sharing. Consent must be a positive, active choice.
- [ ] **Record-Keeping for Data Transfers:** Implement a system to record transfers of personal data to/from third parties. Records of providing or receiving personal data must be maintained for the statutory period (typically 1 or 3 years).

### 4. Cross-Border Transfers & Foreign Third-Party Provision

Under APPI Article 28, transferring personal data to an entity located outside Japan requires strict safeguards.

- [ ] **Information Provision Obligations:** When transferring personal data to a foreign third party (outside Japan), the operator must provide data subjects with detailed information beforehand:
    - The name of the target country.
    - The system for personal information protection in that country.
    - The measures to be taken by the recipient to protect personal information.
- [ ] **Equivalence Mechanisms:** If transferring to foreign processors, verify they are located in a country recognized by the PPC as having an equivalent level of protection, or ensure they are bound by contracts (equivalent to APPI standards) implementing "Equivalent Action" measures.
- [ ] **"Entrustment" vs. "Provision":** Formally classify all foreign infrastructure vendors (such as cloud hosting, databases, or CDNs) as "entrusted parties" (processors) under strict supervision rather than third-party recipients, and sign compliant "entrustment" contracts.

### 5. Security, Vendor Supervision, and Deletion

Supervision over employees and entrusted parties is a core APPI pillar.

- [ ] **Vendor Supervision Contracts:** Ensure that all processing/hosting agreements with subcontractors include provisions for active supervision, auditing, security requirements, and prompt incident reporting.
- [ ] **Internal Security Measures:** Document and implement "Systematic, Human, Physical, and Technical" security control measures, which must be described in the public privacy policy or made available upon data subject request.
- [ ] **Anonymization and Pseudonymization Controls:** If data is anonymized or pseudonymized for secondary analysis, confirm that the technical processes meet the specific, highly technical definitions of "Anonymously Processed Information" or "Pseudonymously Processed Information" under the APPI.

### 6. Breach Notification Workflow

The APPI enforces a mandatory dual-notification reporting system for severe personal data breaches.

- [ ] **PPC Reporting Clock:** Implement an incident response workflow that ensures the PPC is notified:
    - *Promptly / Preliminary Report:* Usually within 3 to 5 days of discovery.
    - *Definitive Report:* Within 30 days of discovery (or 60 days in cases of malicious intent/cyberattacks).
- [ ] **Affected Individuals Notification:** Set up automated or manual workflows to notify affected individuals immediately when a breach occurs that threatens their rights or interests (such as leaks of sensitive data, financial data, or large-scale leaks of 1,000+ individuals).
- [ ] **Rehearsals and Playbooks:** Conduct a simulation drill of a personal data breach specifically testing the PPC notification templates and timeline compliance.

### 7. Data-Residency and Infrastructure Dependencies

- [ ] **Data Residency Mapping:** Map the physical locations of all active databases, backup storage, and caches. Verify if Japanese users' data will reside on Japanese servers, or if it will be hosted in approved foreign jurisdictions (e.g., EU/EEA, US under equivalent safeguards).
- [ ] **Integrated Platforms Mapping:** Confirm that LinkedIn OAuth or other social integrations utilized by Japanese users comply with platform rules regarding cross-border access.

### 8. Legal Approval and Evidence Sign-off

- [ ] **Qualified Local Counsel Sign-off:** Obtain formal legal approval from qualified Japanese counsel validating the Japanese Terms of Service, Privacy Policy, and local representative status.
- [ ] **Immutable Technical Evidence Ledger:** Ensure that engineering tests (e.g., confirming cookie-blocking before consent, security of database access, and mock data deletion scripts) are run, verified, and linked to the Country Activation Record.

---

## Troubleshooting

-   **What happens if we cannot designate a local representative in Japan?**
    *   Consult legal counsel. For foreign business operators with no physical office in Japan, the PPC may still exercise jurisdiction and issue administrative orders or fines. Having a local contact agent is highly recommended to manage compliance risk.
-   **Is EU GDPR compliance sufficient for Japan?**
    *   No. While GDPR covers many security and user rights principles, it does not fulfill the APPI's specific prior-information disclosure rules for foreign transfers (Article 28), the unique distinction of "Anonymously/Pseudonymously Processed Information," or the specific PPC reporting timelines.

---

## References

-   [`global-legal-readiness.md`](global-legal-readiness.md): Global Market Register
-   [`market-entry-asia.md`](market-entry-asia.md): Asia Expansion Strategy
-   [Japan PPC Official APPI English Translation](https://www.ppc.go.jp/en/legal/)
-   [PPC Leak/Breach Action Reporting Portal](https://www.ppc.go.jp/personalinfo/legal/leakAction/)
