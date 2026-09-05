# Latin America (LATAM) Privacy Applicability Matrix

> **Classification:** Internal — Legal and Compliance
> **Status:** Active — Prepared for Staged Expansion (Argentina, Colombia, Chile, Uruguay, Peru, and Ecuador)
> **Last Verified:** 2026-07-18
> **Owner:** Compliance Team

---

## 1. Governance Directive: No Aggregate "LATAM Compliant" Claims

**CRITICAL POLICY:** Profile Tailors **prohibits** making any generalized, aggregated marketing or legal claim of being "LATAM Compliant" or "Latin America Compliant."

Latin American data protection frameworks are fragmented. Each nation has its own distinct statutory requirements, regulatory bodies, database registration mandates, local representative/DPO triggers, and breach-reporting timelines. Reusing a generic template or claiming regional compliance in the aggregate represents a severe risk of deceptive marketing under consumer protection laws and non-compliance under local privacy laws.

Every country listed below must be assessed, enabled, and managed **individually and independently** under its specific country activation overlay.

---

## 2. Applicability Matrix Summary

| Country | Principal Privacy Law | Regulatory Authority | Record State | Country Owner |
|---|---|---|---|---|
| **Argentina** | Ley 25.326 de Protección de Datos Personales | Agencia de Acceso a la Información Pública (AAIP) | Blocked (Staged) | Yuniel Acosta |
| **Colombia** | Ley Estatutaria 1581 de 2012 | Superintendencia de Industria y Comercio (SIC) | Blocked (Staged) | Yuniel Acosta |
| **Chile** | Ley 19.628 (Transitioning to Law 21.719) | APDP (Agencia de Protección de Datos Personales) | Blocked (Staged) | Yuniel Acosta |
| **Uruguay** | Ley 18.331 de Protección de Datos Personales | URCDP (Unidad de Regulación y Control de Datos) | Blocked (Staged) | Yuniel Acosta |
| **Peru** | Ley 29.733 de Protección de Datos Personales | Autoridad Nacional de Protección de Datos (ANPD-JUS) | Blocked (Staged) | Yuniel Acosta |
| **Ecuador** | Ley Orgánica de Protección de Datos (LOPDP) | Superintendencia de Protección de Datos Personales | Blocked (Staged) | Yuniel Acosta |

---

## 3. Country-by-Country Analysis

### 3.1 Argentina

- **Applicability Trigger:** Processing personal data in databases located in Argentina, or extraterritorial processing where Argentine databases are integrated or targeted.
- **Decision State:** **Blocked (Staged)** — Targeting is disabled. Standard Spanish policy is drafted but blocked from production.
- **DPO / Local Representative:** No statutory DPO mandate for foreign operators under Law 25,326, but the controller must establish a clear contact channel (`privacy@profiletailors.com`).
- **Registration Obligation:** **High Risk / Mandatory.** Article 3 of Law 25,326 requires **all** personal databases (public and private) to be registered with the AAIP. This registration is a prerequisite to launch in Argentina and cannot be bypassed.
- **Lawful Basis & Consent:** Heavily consent-centric. Consent must be free, express, informed, and written or obtained electronically via clickwrap. Legitimate interest is not a generally recognized legal basis for commercial data collection under current law.
- **International Data Transfers:** Outbound transfers to countries lacking adequate protection (as decided by AAIP) are prohibited. Spain (EU) is adequate. To transfer data from Argentina to non-adequate servers, AAIP-approved Standard Contractual Clauses (Disposition No. 60-E/2016) must be executed.
- **Breach Notification SLA:** While current Law 25,326 does not contain an explicit hourly deadline, **AAIP Resolution No. 86/2021** establishes non-binding but heavily enforced guidelines requiring controllers to report security breaches to the AAIP and affected individuals **promptly**, with regulatory expectations of notification within **48 hours** of awareness.

---

### 3.2 Colombia

- **Applicability Trigger:** Processing personal data collected or targeted within Colombian territory, or where Colombian law applies by contract or international law.
- **Decision State:** **Blocked (Staged)** — Targeting disabled.
- **DPO / Local Representative:** Article 2.2.2.25.4.4 of Decree 1074 of 2015 requires designating a personal data officer or department (Oficial de Protección de Datos) to process data-subject requests.
- **Registration Obligation:** The National Registry of Databases (RNBD) is mandatory only for legal entities exceeding specific asset thresholds. Profile Tailors, as an individual operator, is currently **exempt** from RNBD filing but must adhere to all other operational controls of Law 1581.
- **Lawful Basis & Consent:** Prior, express, and informed consent (Autorización) is mandatory. The controller must be able to **prove** that consent was obtained (verifiable consent record). Clickwrap registration must capture this specific authorization.
- **International Data Transfers:** Outbound transfers to non-adequate countries (as defined by the SIC) are prohibited without explicit consent or SIC-approved transfer agreements. Germany (EU) is recognized as adequate by the SIC.
- **Breach Notification SLA:** Under Article 15 of Decree 1377 of 2013, the controller must report any security incident or breach to the SIC within **15 business days (15 días hábiles)** of detection via the SIC's electronic portal.

---

### 3.3 Chile

- **Applicability Trigger:** Processing private/personal data within Chile, or extraterritorial targeting of Chilean residents.
- **December 1, 2026 Transition (Law No. 21.719):**
  - **CRITICAL TRANSITION:** Chile’s privacy regime is undergoing a total overhaul under **Law No. 21.719**, which comprehensively amends Law No. 19,628 and takes full effect on **1 December 2026**.
  - Under the new reform, a formal regulatory authority (Agencia de Protección de Datos Personales) is established, and strict GDPR-aligned penalties are introduced.
- **Decision State:** **Blocked (Staged)** — Tracking the December 1, 2026 transition closely. No active targeting is permitted until the new agency is operational and contractual clauses are updated.
- **DPO / Local Representative:** Voluntary under the new Law 21.719, but highly recommended as part of a compliance program.
- **Registration Obligation:** No general registration applies under the transitional or current framework for foreign individual operators.
- **Lawful Basis & Consent:** Consent must be free, specific, informed, and express. Legitimate interest is introduced as a lawful basis under Law 21.719, but is subject to strict proportionality testing.
- **International Data Transfers:** Law 21.719 restricts transfers to non-adequate countries, requiring GDPR-style standard contractual clauses or explicit consent.
- **Breach Notification SLA:**
  - *Current (Law 19,628):* No statutory breach-reporting deadline.
  - *Reform (Law 21,719 - Effective Dec 1, 2026):* The controller must report any security breach to the Agency and affected individuals **without delay** and no later than **72 hours** after becoming aware of it.

---

### 3.4 Uruguay

- **Applicability Trigger:** Processing personal data in Uruguay, or extraterritorial processing with a Uruguayan link.
- **Decision State:** **Blocked (Staged)** — Targeting disabled.
- **DPO / Local Representative:** Under Decree No. 64/020, private entities that process sensitive data or perform large-scale processing must designate a DPO. Profile Tailors’ processing is low-risk, making it **exempt** from the mandatory DPO appointment, but a contact channel must remain active.
- **Registration Obligation:** **Mandatory.** Law No. 18.331 requires all databases containing personal data to be registered with the URCDP.
- **Lawful Basis & Consent:** Express, free, and informed consent is the default. Consent must be obtained via an active choice.
- **International Data Transfers:** Outbound transfers are restricted. Uruguay has EU-level adequacy and has issued its own adequacy list. Spain/Germany are adequate.
- **Breach Notification SLA:** **Extremely Strict.** Under Article 4 of Decree 64/020, the controller must notify the URCDP and affected individuals of any security breach **immediately** upon detection, and in any case within **24 hours**. Failure to report within 24 hours is a severe regulatory violation.

---

### 3.5 Peru

- **Applicability Trigger:** Processing personal data in Peru, or targeting Peruvian residents.
- **Decision State:** **Blocked (Staged)** — Targeting disabled.
- **DPO / Local Representative:** No statutory DPO requirement for foreign individual operators, but a personal data contact must be identified.
- **Registration Obligation:** **Mandatory.** Under Law No. 29,733, all databases ( bancos de datos de administración privada) must be registered in the National Registry of Personal Data Protection (administered by ANPD-JUS).
- **Lawful Basis & Consent:** Consent must be prior, informed, express, and unequivocal (previo, informado, expreso e inequívoco). Tacit consent is not permitted.
- **International Data Transfers:** Must be registered as a cross-border transfer (transferencia transfronteriza) in the database registration. Contractual standard clauses are required for non-adequate recipients.
- **Breach Notification SLA:** Under ANPD guidelines, the controller must document all security events. General breaches must be reported to the ANPD **without delay** once confirmed, with regulatory expectations of notification within **48 hours**.

---

### 3.6 Ecuador

- **Applicability Trigger:** Processing personal data of data subjects residing in Ecuador by a controller established in Ecuador, or by an overseas controller targeting Ecuadorian residents.
- **Decision State:** **Blocked (Staged)** — Targeting disabled.
- **DPO / Local Representative:** Article 48 of the LOPDP mandates the appointment of a DPO for public entities, large-scale processing, or processing of sensitive data. Profile Tailors is **exempt**.
- **Registration Obligation:** Required under Superintendencia guidelines as part of global accountability records.
- **Lawful Basis & Consent:** Consent (free, specific, informed, and unequivocal), contract performance, legal obligation, or legitimate interest (Art. 7 LOPDP).
- **International Data Transfers:** Requires adequacy determination by the Superintendencia or standard contractual clauses providing equivalent protection.
- **Breach Notification SLA:** Under Article 40 of LOPDP, the controller must report any security breach to the Superintendencia and affected individuals within **2 days (48 hours)** of becoming aware of the incident.

---

## 4. Rights Mapping to Common Workflows

All six LATAM countries recognize rights that map to Profile Tailors' common privacy workflows, but feature specific local extensions:

- **Common Workflow (Access / Rectification / Deletion):**
  - Standard identity verification (proportionate, email-based) is utilized.
  - All responses must be delivered in clear Spanish.
- **Local Extensions:**
  - **Uruguay (Habeas Data Action):** Users have direct access to a specialized judicial process (Acción de Habeas Data) if URCDP timelines are missed.
  - **Colombia (Authorization Proof):** Colombian users can request proof of the specific authorization (consent) record given at registration. Profile Tailors must maintain timestamped clickwrap logs.
  - **Argentina (No-fee Right):** Under Law 25,326, access rights must be exercisably free of charge at intervals of no less than six months, unless a legitimate interest is proven.

---

## 5. Review and Maintenance

This matrix is updated quarterly by the Compliance Team. No market listed in this matrix may be set to "Active" in the Global Legal Readiness register until a formal Country Activation Record has been compiled, evidence verified, and local counsel signs off.
