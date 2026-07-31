# Country Activation Record — Brazil (LGPD Compliance)

> **Classification:** Internal — Market Release Control
> **Status:** Staged — prepared for independent enablement
> **Record ID:** CAR-AMER-BR-1.0
> **Last Verified:** 2026-07-18
> **Owner:** Compliance Team

---

## 1. Record Control

- **Country / subdivision / code:** Brazil / National / BRA (ISO 3166-1 alpha-3)
- **Record ID and version:** CAR-AMER-BR-1.0
- **Current state:** Staged (Remediation / Staged for Enablement)
- **Target activation and legal effective date:** Staged (Pending final activation trigger)
- **Product / business / security / privacy owners:** Yuniel Acosta (Trading as Profile Tailors)
- **Qualified local counsel and scope:** Staged (Pinheiro Neto Advogados — Technology & Privacy Practice Group)
- **Primary legal sources and access dates:**
  - *Lei Geral de Proteção de Dados Pessoais (LGPD), Lei nº 13.709/2018* (Last accessed 2026-07-18)
  - *ANPD Resolution CD/ANPD No. 2/2022 (Small Treatment Agents Exemption)* (Last accessed 2026-07-18)
  - *ANPD Resolution CD/ANPD No. 19/2024 (Standard Contractual Clauses for International Transfers)* (Last accessed 2026-07-18)
  - *ANPD Resolution CD/ANPD No. 15/2024 (Security Incident Reporting Regulations)* (Last accessed 2026-07-18)
- **Approval expiry / next review / change watcher:** 2027-07-18 / Quarterly / Privacy Lead
- **Suspension and rollback owner:** Individual Operator (Yuniel Acosta)

---

## 2. Legal and Business Presence

- **Entity & Contracting Form:** Yuniel Acosta, trading as Profile Tailors (individual operator, Spain). No local branch, office, or corporate presence in Brazil.
- **DPO / Encarregado pelo Tratamento de Dados Pessoais:**
  - **Exemption applied:** Under **ANPD Resolution CD/ANPD No. 2/2022 (Article 4)**, "small-sized treatment agents" (including startups, micro-enterprises, small businesses, and individual operators with low-risk processing) are **exempt** from the statutory requirement to appoint a formal DPO (Encarregado).
  - Profile Tailors formally invokes this small-business exemption.
  - **Compliance contact channel:** As required by Resolution No. 2/2022, Profile Tailors maintains a direct, publicly accessible email channel to receive communication and complaints from data subjects: **`privacy@profiletailors.com`**.
  - **Exemption watcher:** If Profile Tailors transitions to a high-risk processing classification (e.g., processing sensitive data at scale or large-scale profiling), a formal DPO must be appointed and registered with the ANPD.

---

## 3. Approved Product Scope

- **Marketing site:** Enabled (Portuguese Brazilian translation queued).
- **Waitlist/Registration:** Staged (WAITLIST_ENABLED=true). Early-access waitlist form is configured for Brazilian visitors, with a Portuguese check-box consent statement for optional communications.
- **Social platform integration:** LinkedIn OAuth only (enabled).
- **AI features:** AI-assisted scheduling (staged). Requires clear transparency disclosures regarding automated decision-making and the right to request review of fully automated decisions under LGPD Article 20.
- **Tracking & Cookies:**
  - Standard functional cookies (`pt_refresh`, `sidebar_state`) are enabled.
  - Non-essential/analytical cookies (Ahrefs or similar) are **disabled by default** for visitors connecting from Brazilian IP addresses. Activation requires an explicit, active opt-in consent through the Cookie Consent Banner.

---

## 4. Data and Privacy Approval

### 4.1 Territorial Scope and Legal Bases
- **Applicability:** Applies under LGPD Article 3 because:
  1. Profile Tailors offers services directly to individuals located in Brazil (via Portuguese language pages and social scheduling targeting).
  2. Personal data is collected from data subjects located in Brazil.
- **Controller/Processor roles:** Profile Tailors acts as Controller for account, workspace, and waitlist data. Acts as Processor for user-generated content published to LinkedIn.
- **Lawful Bases (LGPD Article 7):**
  - **Contract Execution (Art. 7, V):** Account registration, workspace creation, and executing user publishing instructions to LinkedIn.
  - **Legitimate Interest (Art. 7, IX):** Enhancing application security, investigating fraud, system telemetry, and maintaining audit logs. (A formal Legitimate Interest Assessment [LIA] has been recorded and is kept on-premises).
  - **Consent (Art. 7, I):** Optional marketing communications and newsletter subscriptions.
  - **Legal/Regulatory Obligation (Art. 7, II):** Fulfilling Spanish tax, financial disclosure, or court-mandated information retention.

### 4.2 Rights Map and LGPD Extensions

| Right | LGPD Statutory Basis | Operational Workflow |
|---|---|---|
| **Confirmation of Processing** | Art. 18, I | Confirm whether or not Profile Tailors processes the individual's personal data. Met via immediate standard email response. |
| **Access** | Art. 18, II | Provide a complete record of processed personal data. Delivered within **15 calendar days** as mandated by LGPD Art. 19, II (in an intelligible and complete format). |
| **Correction** | Art. 18, III | Correct incomplete, inaccurate, or out-of-date data. Propagated to infrastructure subprocessors immediately. |
| **Anonymisation, Blocking or Erasure** | Art. 18, IV | Apply to unnecessary, excessive, or non-compliant data. Handled via technical team within 15 days of verified request. |
| **Data Portability** | Art. 18, V | Export user data in structured, machine-readable format. Subject to ANPD-issued portability regulations (currently fulfilled using standard JSON exports). |
| **Consent Deletion** | Art. 18, VI | Erase personal data processed on the basis of consent, subject to statutory retention exceptions (e.g. tax records). |
| **Third-Party Sharing Info** | Art. 18, VII | Disclose the identity of public or private entities with whom the controller has shared personal data (Cloudflare, Oracle Cloud, LinkedIn). |
| **Consent Consequences** | Art. 18, VIII | Inform the user about the possibility of denying consent and the subsequent consequences (e.g. inability to access premium features). |
| **Consent Revocation** | Art. 18, IX | Revoke previously given consent. Consent withdrawal is executed immediately, free of charge, and without terminating core contract unless technically unavoidable. |
| **Automated Decision Review** | Art. 20 | Request a human review of decisions made solely on automated processing (e.g., automated fraud-flagging or AI features). Profile Tailors ensures human oversight of all system suspensions. |

---

## 5. Providers, Platforms, and Transfers

- **Subprocessor set:**
  - Cloudflare, Inc. (CDN / Security) — Global edge network.
  - Oracle Cloud (Frankfurt, Germany Region hosting) — Primary storage.
  - LinkedIn Corporation (API Integration) — Target social platform.
- **Transfer mechanism (LGPD Article 33):**
  - Outbound data transfers from Brazil are restricted. Relying solely on European Union GDPR standard clauses (EU SCCs) is **not sufficient** under Brazilian law.
  - **ANPD Resolution CD/ANPD No. 19/2024 Alignment:** Resolution No. 19/2024 established the official Brazilian Standard Contractual Clauses (Cláusulas-Padrão). To comply:
    1. Data processing agreements (DPAs) with subprocessors have been updated to incorporate the approved ANPD Standard Clauses.
    2. Alternatively, transfers to Oracle Cloud (Germany) are justified under **Article 33, IX of the LGPD**, as the transfer is necessary for the execution of the contract between the user and Profile Tailors (SaaS performance), and Germany is recognized as maintaining an adequate level of data protection under standard international adequacy evaluations.
    3. DPAs with Cloudflare and LinkedIn include the necessary security safeguards and contractual assurances required by Resolution 19/2024.

---

## 6. Public and Contractual Documents

- **Brazilian Portuguese Privacy Notice (Localisation):** A professionally translated, legally compliant Privacy Notice written in clear Portuguese has been prepared. This is separate from generic European Portuguese policies. It lists:
  - Clear and specific purposes of processing.
  - The contact channel (`privacy@profiletailors.com`) for LGPD complaints.
  - Explicit mention of LGPD Article 18 rights.
  - Details on international data transfers and the role of Resolution 19/2024 standard clauses.
- **Terms of Service Portuguese Localisation:** Staged as a localized terms file. Spain is named as the governing law, but Spanish choice of law does not deprive Brazilian consumers of their mandatory protections under the Brazilian Consumer Defense Code (Código de Defesa do Consumidor - CDC).

---

## 7. Consumer and Commercial Approval

- **Brazilian Consumer Defense Code (CDC - Lei nº 8.078/1990):**
  - Spanish contract terms are interpreted in favor of the Brazilian consumer.
  - **Unfair terms:** Any clause excluding liability for gross negligence, restricting consumer access to Brazilian courts, or allowing unilateral price changes without notice is null and void under CDC Art. 51. Contract terms are adapted to exclude these limitations for Brazilian subscribers.
  - **Right of Regret (Direito de Arrependimento - CDC Art. 49):** If paid tiers are introduced, Brazilian consumers have the right to withdraw from the contract within **7 calendar days** from subscription or service commencement, with a full refund of any fees paid.

---

## 8. Content, Platform, and Safety Approval

- **Marco Civil da Internet (Lei nº 12.965/2014):**
  - Profile Tailors complies with Brazil's internet civil framework.
  - **Connection Log Retention:** Article 15 mandates that autonomous system operators and application providers retain connection logs (including IP addresses, login timestamps, and logout timestamps) in a secure, confidential environment for a minimum of **6 months**.
  - Internal database architecture is configured to preserve authentication and access logs for 180 days to meet this statutory retention duty. Logs are encrypted and purged automatically after 180 days.

---

## 9. Accessibility and Language

- **Language:** Public interfaces, privacy notices, cookie banners, cookie policies, and customer support channels targeting Brazil must be available in Portuguese (Brazilian dialect).
- **Accessibility:** Compliance with the Brazilian Inclusion Law (Lei Brasileira de Inclusão - Lei nº 13.146/2015) is maintained via WCAG 2.1 AA compatibility on all landing pages and signup screens.

---

## 10. Operational Readiness Evidence

### 10.1 Breach Notification SLA Matrix (Resolution CD/ANPD No. 15/2024)

In the event of a security or confidentiality incident involving Brazilian personal data:

- **Incident Registry:** Every security incident (regardless of risk scale) must be recorded in an internal incident log, indicating the date, description, data categories affected, and containment measures. Logs are maintained for at least **5 years**.
- **Notifiable Incidents:** Under LGPD Article 48, notification to the ANPD and data subjects is mandatory if the incident may cause "relevant risk or damage" (danos e riscos relevantes) to the rights and freedoms of the data subjects.
- **SLA Deadline:** Under **Resolution CD/ANPD No. 15/2024**, notifications to both the ANPD and the affected data subjects must be submitted within **3 business days (3 dias úteis)** from the date the controller became aware of the incident.
- **Information Requirements:** Initial notifications may be preliminary/phased if details are incomplete. The report must contain:
  1. Description of the nature of the affected personal data.
  2. Information about the data subjects involved.
  3. Security and technical measures used for data protection.
  4. Risks and consequences of the incident.
  5. Remediation actions taken or planned.
  6. Contact details of the privacy officer/compliance lead.

---

## 11. Approval Record

| Approval | Decision and evidence |
|---|---|
| Product Truth | Verified — waitlist and LinkedIn flows only. |
| Technical Truth | Verified — Marco Civil 6-month log retention configured. |
| Privacy Operations | Verified — 15-day LGPD access response SLA implemented in rights runbook. |
| Qualified Local Legal | Staged — Pending review by Pinheiro Neto Advogados. |
| Release Status | Approved Inactive / Staged (Rollback path is immediate country routing block). |

---

## 12. Activation and Monitoring

- **Country Routing Gate:** IP-based routing is configured. Brazilian signups are allowed only if they click and accept the LGPD-specific Portuguese terms.
- **Suspension Trigger:** If any subprocessor fails to execute Resolution 19/2024 Standard Clauses, or if a connection log retention failure occurs, Brazilian registration will be suspended.
