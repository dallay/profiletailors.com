# Country Activation Record — Mexico (LFPDPPP Compliance)

> **Classification:** Internal — Market Release Control
> **Status:** Staged — prepared for independent enablement
> **Record ID:** CAR-AMER-MX-1.0
> **Last Verified:** 2026-07-18
> **Owner:** Compliance Team

---

## 1. Record Control

- **Country / subdivision / code:** Mexico / National / MEX (ISO 3166-1 alpha-3)
- **Record ID and version:** CAR-AMER-MX-1.0
- **Current state:** Staged (Remediation / Staged for Enablement)
- **Target activation and legal effective date:** Staged (Pending final activation trigger)
- **Product / business / security / privacy owners:** Yuniel Acosta (Trading as Profile Tailors)
- **Qualified local counsel and scope:** Staged (Basham, Ringe y Correa, S.C. — Personal Data Practice Group)
- **Primary legal sources and access dates:**
  - *Ley Federal de Protección de Datos Personales en Posesión de los Particulares (LFPDPPP)* (Last accessed 2026-07-18)
  - *Reglamento de la Ley Federal de Protección de Datos Personales en Posesión de los Particulares (RLFPDPPP)* (Last accessed 2026-07-18)
  - *Lineamientos del Aviso de Privacidad (INAI)* (Last accessed 2026-07-18)
- **Approval expiry / next review / change watcher:** 2027-07-18 / Quarterly / Privacy Lead
- **Suspension and rollback owner:** Individual Operator (Yuniel Acosta)

---

## 2. Legal and Business Presence

- **Entity & Contracting Form:** Yuniel Acosta, trading as Profile Tailors (individual operator, Spain). No local physical establishment, subsidiary, or office in Mexico.
- **Personal Data Department (Departamento de Datos Personales):**
  - Under **LFPDPPP Article 30**, every private controller must designate a personal data department or officer to promote data protection and handle ARCO requests.
  - Profile Tailors formally designates **Yuniel Acosta** as the personal data officer.
  - **Compliance contact channel:** All ARCO inquiries and complaints must be routed to: **`privacy@profiletailors.com`**.
- **Filing and Registration Obligations:** Under Mexican law, there is no requirement to register personal databases with the regulatory authority (INAI). However, the controller must maintain internal records of processing activities and ARCO request resolutions.

---

## 3. Approved Product Scope

- **Marketing site:** Enabled (Spanish Mexican localisation).
- **Waitlist/Registration:** Staged (WAITLIST_ENABLED=true). Early-access form is configured with explicit Spanish-language consent options for Mexican visitors.
- **Social platform integration:** LinkedIn OAuth only (enabled).
- **AI features:** AI-assisted content composition (staged). Requires clear transparency disclosures in the Privacy Notice (Aviso de Privacidad) regarding the use of automated technologies and processing of content.
- **Tracking & Cookies:**
  - Standard functional cookies (`pt_refresh`, `sidebar_state`) are enabled.
  - Non-essential/analytical cookies (Ahrefs or similar) are **disabled by default** for Mexican IP addresses. They require the user's explicit consent via the Spanish Cookie Banner.

---

## 4. Data and Privacy Approval

### 4.1 Territorial Scope and Consent Rules

- **Applicability:** Applies under LFPDPPP Article 2 and RLFPDPPP Article 3 because Profile Tailors targets the Mexican market, collects personal data from Mexican residents, and offers local-language services.
- **Controller/Processor roles:** Profile Tailors acts as Controller for account, workspace, and payment operations. Acts as Processor for user-generated content published to LinkedIn.
- **Consent Framework (Articles 8, 9, and 15):**
  - **Tacit Consent (Consentimiento Tácito):** Permitted for ordinary personal data. If the Privacy Notice (Aviso de Privacidad) is made available, and the user does not actively object, consent is inferred.
  - **Express Consent (Consentimiento Expreso):** Required for the processing of financial, patrimonial, or sensitive personal data. While Profile Tailors does not process sensitive data, any introduction of paid subscription tiers (financial data) or API credential tokens requires express consent. Profile Tailors implements **express electronic consent** via an active, un-checked checkbox during signup: *"Acepto los Términos de Servicio y el Aviso de Privacidad."*
  - **Purpose Distinction:** The Privacy Notice must explicitly separate **Primary Purposes** (necessary for the service, e.g., account creation, LinkedIn publishing) from **Secondary Purposes** (non-essential, e.g., marketing, waitlist updates). Users must be allowed to opt-out of Secondary Purposes without having their core service terminated.

### 4.2 ARCO Rights Workflow (Access, Rectification, Cancellation, Opposition)

Under LFPDPPP Article 28, Mexican residents have the right to exercise ARCO rights at any time.

| Right | Statutory Definition | Operational Response |
|---|---|---|
| **Access (Acceso)** | Know what personal data is held, its origin, and the specific terms of its processing. | Deliver complete personal data file via secure download link. |
| **Rectification (Rectificación)** | Correct personal data when it is inaccurate, incomplete, or out-of-date. | Update system records within the operational system and propagate changes. |
| **Cancellation (Cancelación)** | Delete personal data from systems when it is no longer necessary for the primary purposes or violates compliance. | Initiate account suspension, purge databases, and revoke API keys, subject to legal locks (bloqueo). |
| **Opposition (Oposición)** | Object to processing for secondary purposes (marketing) or due to specific personal circumstances. | Stop the specific processing activity (e.g. move email address to suppression list) while keeping the account active. |

### 4.3 Statutory Deadlines and Extensions (Article 32)

Profile Tailors’ rights-request runbook is configured to respect Mexican statutory timelines, which operate in **Business Days (Días Hábiles)**, excluding weekends and official Mexican holidays:

1. **Intake and Triage:** Upon receipt of an ARCO request, verify the requester's identity. If information is missing, request clarification within **5 business days**; the user has **10 business days** to respond, or the case is closed.
2. **Resolution Decision (SLA: 20 Business Days):** The controller has a maximum of **20 business days** from receiving a complete request to evaluate and notify the user of the decision (approval or rejection). This may be extended once by up to **20 business days** for justified reasons.
3. **Execution/Fulfilment (SLA: 15 Business Days):** If approved, the action must be completed and delivered to the user within **15 business days** of the decision notice. This may be extended once by up to **15 business days**.

---

## 5. Providers, Platforms, and Transfers

- **Subprocessor set:**
  - Cloudflare, Inc. (CDN / Security) — Global edge network.
  - Oracle Cloud (Frankfurt, Germany Region hosting) — Primary storage.
  - LinkedIn Corporation (API Integration) — Target social platform.
- **Transfer Consent Requirements (Article 36 & 37):**
  - Personal data transfers to third parties must be explicitly detailed in the Privacy Notice (Aviso de Privacidad), listing the recipient, country, and purpose.
  - **No-Consent Exceptions:** Under LFPDPPP Article 37, consent is **not** required for transfers to:
    - Holding companies, subsidiaries, or affiliates under common control (not applicable here).
    - When the transfer is necessary by virtue of a contract executed or to be executed in the interest of the data subject (SaaS execution with Oracle Cloud/Cloudflare).
    - When the transfer is necessary or legally required for the safeguarding of public interest, or for the administration of justice.
  - Cross-border transfers are covered by data protection agreements that legally bind Oracle Cloud and Cloudflare to process the data strictly on the instructions of Profile Tailors, satisfying the equivalent security principles of LFPDPPP.

---

## 6. Public and Contractual Documents

- **Aviso de Privacidad (Spanish Privacy Notice):**
  - Must comply with the **Lineamientos del Aviso de Privacidad** issued by INAI.
  - Features a mandatory, dedicated section in `apps/web/marketing/src/i18n/es.ts`.
  - Discloses:
    1. The identity and address of the controller (Yuniel Acosta).
    2. Personal data collected (account data, connection tokens, metadata).
    3. Categorization of Primary and Secondary Purposes, with an explicit mechanism to opt-out of Secondary Purposes.
    4. Options and means to limit the use or disclosure of personal data.
    5. The procedure and requirements to exercise ARCO rights (including identity verification and the 20/15 business day timelines).
    6. Information on data transfers and whether user consent is required.
    7. The procedure to notify users of any future modifications to the notice.
- **Terms of Service Spanish Localisation:** Spanish-language Terms of Service are active. While Spain is designated as the governing law, Mexican consumers retain mandatory rights under the Federal Consumer Protection Law (LFPC).

---

## 7. Consumer and Commercial Approval

- **Ley Federal de Protección al Consumidor (LFPC):**
  - **Unfair terms:** Clauses that waive liability for fraud or gross negligence, or deprive Mexican consumers of local judicial jurisdiction, are null under LFPC.
  - **Advertising Truthfulness:** All marketing claims regarding features, availability, and AI integration must be truthful, substantiated, and free of deceptive practices (AUP alignment).

---

## 8. Content, Platform, and Safety Approval

- Mexican law does not mandate active filtering of social media content by application providers, but requires a functional notice-and-action mechanism to handle copyright or illegal content. Reports are resolved within 48 hours.

---

## 9. Accessibility and Language

- **Language:** Clear Spanish (Mexican variant) is required for all legal terms, cookie notices, and customer support.
- **Accessibility:** All public forms are compliant with WCAG 2.1 AA.

---

## 10. Operational Readiness Evidence

### 10.1 Breach Notification SLA Matrix (LFPDPPP Article 20)

In the event of a security or confidentiality breach involving Mexican personal data:

- **Incident Record:** Profile Tailors documents every security incident internally, including containment actions, affected fields, and data-subject impact analysis.
- **Immediate Notification Rule:** Under **LFPDPPP Article 20**, if a security breach occurs at any stage of processing that *significantly affects* the patrimonial or moral rights (derechos patrimoniales o morales) of data subjects, the controller must **immediately** notify the affected individuals.
- **Immediate (Inmediatamente) SLA:** Mexican jurisprudence defines "immediate" as without delay once the breach is confirmed and within **24 to 72 hours** of awareness, prioritized so that users can take defensive measures (such as rotating credentials, blocking bank cards, or monitoring for identity theft).
- **Notification Details:** The notification to affected data subjects must contain:
  1. The nature of the incident.
  2. The personal data compromised.
  3. Actionable recommendations for the user to protect their interests (e.g., rotating password).
  4. Remediation actions taken by the controller.
  5. The contact channel for assistance (`privacy@profiletailors.com`).

---

## 11. Approval Record

| Approval | Decision and evidence |
|---|---|
| Product Truth | Verified — waitlist and LinkedIn flows only. |
| Technical Truth | Verified — SSL/TLS, database encryption active. |
| Privacy Operations | Verified — ARCO response clock (20 business days) and execution clock (15 business days) implemented in rights runbook. |
| Qualified Local Legal | Staged — Pending review by Basham, Ringe y Correa, S.C. |
| Release Status | Approved Inactive / Staged (Rollback path is immediate country routing block). |

---

## 12. Activation and Monitoring

- **Country Routing Gate:** Mexican IP-based routing is active. Users registering from Mexico must accept the Spanish Aviso de Privacidad.
- **Suspension Trigger:** If any subprocessor fails to safeguard Mexican personal data, or if an ARCO timeline violation is detected, Mexican service signup will be immediately suspended.
