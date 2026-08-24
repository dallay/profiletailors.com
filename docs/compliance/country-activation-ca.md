# Country Activation Record — Canada (PIPEDA & Quebec/Provincial Divergence)

> **Classification:** Internal — Market Release Control
> **Status:** Staged — prepared for independent enablement
> **Record ID:** CAR-AMER-CA-1.0
> **Last Verified:** 2026-07-18
> **Owner:** Compliance Team

---

## 1. Record Control

- **Country / subdivision / code:** Canada / National (with CA-QC, CA-AB, CA-BC provincial
  overlays) / CAN (ISO 3166-1 alpha-3)
- **Record ID and version:** CAR-AMER-CA-1.0
- **Current state:** Staged (Remediation / Staged for Enablement)
- **Target activation and legal effective date:** Staged (Pending final activation trigger)
- **Product / business / security / privacy owners:** Yuniel Acosta (Trading as Profile Tailors)
- **Qualified local counsel and scope:** Staged (Fasken Martineau DuMoulin LLP — Canadian Privacy
  Practice)
- **Primary legal sources and access dates:**
    - *Personal Information Protection and Electronic Documents Act (PIPEDA)* (Last accessed
      2026-07-18)
    - *Quebec Act respecting the protection of personal information in the private sector (as
      amended by Law 25)* (Last accessed 2026-07-18)
    - *Alberta Personal Information Protection Act (PIPA)* (Last accessed 2026-07-18)
    - *British Columbia Personal Information Protection Act (PIPA)* (Last accessed 2026-07-18)
- **Approval expiry / next review / change watcher:** 2027-07-18 / Quarterly / Privacy Lead
- **Suspension and rollback owner:** Individual Operator (Yuniel Acosta)

---

## 2. Legal and Business Presence

- **Entity & Contracting Form:** Yuniel Acosta, trading as Profile Tailors (individual operator,
  registered address in Spain). No local Canadian corporate establishment.
- **DPO / Representative / Privacy Officer:**
    - Under PIPEDA, Alberta PIPA, and BC PIPA, an organization must designate an individual to
      oversee compliance (Privacy Officer).
    - Under **Quebec Law 25 (Section 3.1)**, the person with the highest authority within the
      enterprise (default: CEO / Individual Operator) acts as the Privacy Officer. For Profile
      Tailors, this is **Yuniel Acosta** (`privacy@profiletailors.com`). Writing delegations of this
      role are stored internally if needed.
- **Local representative / agent duties:** No statutory local representative is required for
  overseas operators targeting Canadian residents, but a contact channel must be public.
- **Filing and Registration Obligations:** No generic database registration or licensing obligation
  applies to Profile Tailors in Canada. Under Quebec Law 25, no general filing is required for
  private operators, but mandatory impact assessments and incident registries must be maintained
  on-premises.

---

## 3. Approved Product Scope

- **Marketing site:** Enabled (English and French Canadian localisations).
- **Waitlist/Registration:** Staged (WAITLIST_ENABLED=true). Early-access forms must explicitly
  state meaningful consent disclosures.
- **Social platform integration:** LinkedIn OAuth only (enabled). No other social platforms are
  authorized.
- **AI features:** AI-assisted content drafting and scheduling (staged). Requires explicit
  human-in-the-loop validation and transparency disclosures under Quebec Law 25 and Canada’s
  upcoming AIDA (Artificial Intelligence and Data Act).
- **Tracking & Cookies:**
    - Standard functional cookies (`pt_refresh`, `sidebar_state`) are enabled.
    - Non-essential/analytical cookies (Ahrefs or similar) are **disabled by default** in Canada.
    - For Quebec users: **Law 25 requires privacy by default**. All profiling, tracking, or
      identifying technologies (including cookies, local storage, and analytics) must be deactivated
      by default. Users must actively opt-in.

---

## 4. Data and Privacy Approval

### 4.1 Territorial Scope and Legal Bases

- **Applicability:** Applies to processing of personal information in the course of commercial
  activities in Canada (PIPEDA), and to individuals residing in Quebec (Law 25), Alberta (PIPA), and
  British Columbia (PIPA).
- **Controller/Processor roles:** Profile Tailors acts as Controller for account, workspace, and
  payment operations. Acts as Processor for user-generated content published to LinkedIn.
- **Lawful basis:** Canadian laws are heavily consent-centric.
    - **PIPEDA:** Requires "Meaningful Consent" (individuals must understand what they are
      consenting to).
    - **Quebec Law 25:** Consent must be clear, free, informed, and given for specific purposes.
      Consent must be requested **separately** for each purpose and cannot be bundled. Consent is
      invalid if obtained via pre-checked boxes or general terms.
    - **Legitimate Interest:** Not recognised as a standalone lawful basis under Canadian
      private-sector privacy laws. Processing must rely on explicit/implied consent, contract
      performance, or narrow statutory exceptions.

### 4.2 Rights Map and Provincial Extensions

| Right                     | Canadian / Provincial Base                   | Operational Workflow                                                                                                                                                                                               |
|---------------------------|----------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Access**                | PIPEDA s. 4.9; QC Law 25 s. 27; AB/BC PIPA   | Fulfilled within 30 calendar days (standard rights runbook). No charge permitted except minimal statutory fees which are waived by Profile Tailors.                                                                |
| **Correction**            | PIPEDA s. 4.9.5; QC Law 25 s. 30; AB/BC PIPA | Update records upon verified request. Propagate corrections to subprocessors.                                                                                                                                      |
| **Erasure (De-indexing)** | QC Law 25 s. 28.1 (Right to be Forgotten)    | Unique to Quebec. Users can request that personal info stop being disseminated or that hyperlinks providing access be de-indexed if dissemination causes grave injury. Handled via compliance escalation.          |
| **Data Portability**      | QC Law 25 s. 27 (Effective Sept 2024)        | Unique to Quebec. Users have a right to obtain a copy of their computerized personal information in a structured, commonly used technological format. Password hashes and internal security metadata are excluded. |
| **Consent Withdrawal**    | PIPEDA s. 4.3.8; QC Law 25; AB/BC PIPA       | Users may withdraw consent at any time, subject to legal/contractual restrictions and reasonable notice. Inform users of consequences (e.g., account termination if required for core service).                    |

---

## 5. Providers, Platforms, and Transfers

- **Subprocessor set:**
    - Cloudflare, Inc. (CDN / Security)
    - Oracle Cloud (Frankfurt, Germany Region hosting)
    - LinkedIn Corporation (API Integration)
- **Transfer mechanism (Canada Baseline):**
    - Under PIPEDA, organisations must use contractual or other means to provide a "comparable level
      of protection" while information is being processed by a third party (subprocessor). DPAs are
      executed with Cloudflare and Oracle Cloud containing strict confidentiality clauses.
- **Transfer mechanism (Quebec Law 25 Divergence):**
    - **Mandatory Privacy Impact Assessment (PIA):** Under Quebec Law 25 (Section 17), a PIA must be
      conducted before any personal information is transferred outside of Quebec (including
      transfers to other Canadian provinces or European data centers).
    - The PIA must assess:
        1. The sensitivity of the information.
        2. The purposes for which it will be used.
        3. The protection measures, including contractual agreements, that will apply.
        4. The legal framework of the recipient country (Germany is adequate under EU GDPR;
           Cloudflare is bound by US/EU cross-border safeguards).
    - **Contractual Requirement:** The transfer is permitted only if the PIA demonstrates adequate
      protection. A formal data transfer agreement or DPA must be in place, which has been verified
      for all subprocessors.

---

## 6. Public and Contractual Documents

- **Canada Privacy Notice Addendum:** Staged as a dedicated localized section in
  `apps/web/marketing/src/i18n/en.ts` and `es.ts` (with French Canadian translation queued).
- **Quebec French Language Requirement (Bill 96 / Charter of the French Language):**
    - Public contracts of adhesion (like clickwrap Terms of Service) and public privacy notices
      targeting Quebec consumers **must be drafted in French**.
    - To comply, English versions are accompanied by a French translation. Users must be presented
      with the French version first or equally, and may only select English after having the French
      version clearly offered.

---

## 7. Consumer and Commercial Approval

- **Classification:** B2B content-planning service. Eligible age is **18+** (affirming age
  eligibility during clickwrap onboarding is mandatory).
- **Pricing & Tax:** Local goods and services tax (GST), provincial sales tax (PST), and harmonized
  sales tax (HST) are calculated based on user's province when paid tiers are activated. Currently
  free.
- **Automatic Renewal:** Terms comply with provincial consumer protection laws (e.g., Quebec
  Consumer Protection Act, Ontario Consumer Protection Act), requiring clear disclosure of terms,
  advance notice of renewal, and straightforward electronic cancellation.

---

## 8. Content, Platform, and Safety Approval

- **Platform Rules:** Users must maintain valid LinkedIn developer connections and adhere to
  LinkedIn’s terms.
- **Notice-and-Action:** No specific Canadian online safety laws mandate proactive monitoring, but
  standard Acceptable Use Policy (AUP) reporting channels are monitored with a 48-hour response
  target.

---

## 9. Accessibility and Language

- **Accessibility:** Conforms to WCAG 2.1 AA. Meets the requirements of the *Accessibility for
  Ontarians with Disabilities Act (AODA)* and federal accessibility standards.
- **Language Coverage:**
    - English (Federal baseline).
    - French (Quebec mandatory baseline). Standard terms and policies have been translated into
      professional legal French.

---

## 10. Operational Readiness Evidence

### 10.1 Breach Notification SLA Matrix

In the event of a security or confidentiality incident involving Canadian personal information:

| Regime               | Trigger                               | Authority Notification       | Subject Notification                                   | Registry / Record Keeping                                            |
|----------------------|---------------------------------------|------------------------------|--------------------------------------------------------|----------------------------------------------------------------------|
| **Federal (PIPEDA)** | Real Risk of Significant Harm (RROSH) | OPCC "As soon as feasible"   | Affected individuals "As soon as feasible"             | Maintain records of **all** breaches for 24 months (PIPEDA s. 27.1). |
| **Quebec (Law 25)**  | Risk of "Serious Injury"              | CAI "Promptly"               | Affected individuals "Promptly"                        | Maintain a local "Confidentiality Incident Register" for 5 years.    |
| **Alberta (PIPA)**   | Real Risk of Significant Harm (RROSH) | OIPC Alberta "Without delay" | Affected individuals (Only if ordered by Commissioner) | Maintain incident documentation indefinitely or minimum 5 years.     |

- *RROSH Factors:* Sensitivity of the information, and probability of misuse/harm.
- *Serious Injury Factors (Quebec):* Sensitivity of information, anticipated consequences, and
  likelihood of malicious use.

---

## 11. Approval Record

| Approval              | Decision and evidence                                                          |
|-----------------------|--------------------------------------------------------------------------------|
| Product Truth         | Verified — scope restricted to LinkedIn publishing and waitlist.               |
| Technical Truth       | Verified — backend DB in Frankfurt, Cloudflare CDN, French routing active.     |
| Privacy Operations    | Verified — Quebec data portability and de-indexing requests mapped.            |
| Qualified Local Legal | Staged — Pending formal sign-off by Fasken LLP.                                |
| Release Status        | Approved Inactive / Staged (Rollback path is immediate country routing block). |

---

## 12. Activation and Monitoring

- **Country Routing Gate:** IP-based routing is configured. Canadian signups are allowed only if
  they click and accept the Canada Privacy Addendum and terms.
- **Suspension Trigger:** If any subprocessor fails to maintain "comparable protection", or if a
  Quebec PIA review fails on a new feature, Canadian registration will be immediately suspended.
