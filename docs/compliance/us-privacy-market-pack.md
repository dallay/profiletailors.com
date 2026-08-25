# US Privacy Market Pack

> **Classification:** Internal — Legal and Compliance
> **Status:** Remediation — US availability is disabled in production
> **Effective Date:** 2026-07-23
> **Owner:** Legal and Compliance Team

## Overview

This Market Pack establishes the legal readiness baseline and operational controls required before targeting, onboarding, or processing the data of consumers in the United States.

**US availability remains disabled in production.** The platform's global status remains `Blocked` or `Remediation` for targeting US users. The product will not offer local signup, local currency billing, or target US marketing until all baseline technical controls, production contracts, and qualified US legal reviews are completed.

---

## 1. FTC Act Truthfulness and Security Review

### 1.1 Statutory Basis
Under Section 5 of the Federal Trade Commission (FTC) Act (15 U.S.C. § 45), the FTC prohibits "unfair or deceptive acts or practices in or affecting commerce." This applies to any representations made in public-facing policies, marketing copy, onboarding paths, or sales commitments.

### 1.2 Applicability Thresholds
*   **Universal Commerce Nexus:** Applies to all commercial entities operating in or targeting the US.
*   **Non-Profit Exemption:** Generally excludes bona fide non-profits, though the FTC asserts jurisdiction if an entity operates for the profit of its members.

### 1.3 Truthfulness and Consent Review
To prevent "deceptive" claims, all marketing, interface labels, and policies must reflect current technical truth:
1.  **AI Assertions:** Any claims regarding automated post optimization or audience analysis must not overstate capability. No "automated decision making" or "predictive AI" may be claimed unless active, tested pipelines exist.
2.  **Pricing and Trials:** All trial and early-access copy must clearly disclose terms. Standard auto-renewal rules require explicit consent and simple 1-click cancellation paths.
3.  **No Dark Patterns:** Choice architecture (such as consent banners or cookie preferences) must not use deceptive design (e.g., hiding "Reject All" or making opt-out paths artificially difficult) which the FTC deems unfair/deceptive.

### 1.4 "Reasonable Security" Obligation
The FTC enforces an organic obligation to maintain "reasonable administrative, technical, and physical safeguards." Profile Tailors ensures alignment by:
*   Enforcing salted-hash storage for password credentials.
*   Enforcing API key rotation and cryptographic signature verification.
*   Enforcing standard session-token expirations (7 days for refresh tokens).
*   Conducting routine dependency and vulnerability scanning.

---

## 2. CAN-SPAM Act and Email Operations

### 2.1 Statutory Basis
The Controlling the Assault of Non-Solicited Pornography and Marketing (CAN-SPAM) Act of 2003 (15 U.S.C. §§ 7701-7713) regulates commercial email messages.

### 2.2 Applicability Thresholds
*   **Strictly Applicable:** Applies to any commercial email sent to US recipients. It does not require a volume threshold.
*   **Transactional vs. Marketing Distinction:** The law distinguishes between "commercial" (marketing) and "transactional or relationship" messages.

### 2.3 Message Categorization Rules

| Category | Definition | Required Controls |
| :--- | :--- | :--- |
| **Transactional / Relationship** | Primary purpose is to facilitate, complete, or confirm a commercial transaction; deliver security alerts, password resets, or waitlist status. | *   No deceptive headers or subject lines.<br>*   Must identify the sender entity.<br>*   *Exempt* from opt-out requirements. |
| **Commercial / Marketing** | Primary purpose is to promote or advertise a commercial product or service (such as new feature announcements or premium plans). | *   No deceptive headers or subject lines.<br>*   Must include a clear "Opt-Out" or "Unsubscribe" mechanism.<br>*   Must include a valid physical postal address of the sender.<br>*   Must honor opt-out requests within 10 business days.<br>*   Requires active suppression lists. |

### 2.4 Profile Tailors Operational Controls
1.  **Waitlist Consent Separation:** Under `Consent and Preference Register (CPR-002)`, waitlist registration (early access) and optional marketing consent are captured as independent, separate choices.
2.  **Suppression Processing:** Any "Unsubscribe" click in marketing emails must trigger downstream suppression in the mail provider within 10 business days, prohibiting any further commercial sends.

---

## 3. COPPA Residual Assessment (18+ Policy)

### 3.1 Statutory Basis
The Children's Online Privacy Protection Act (COPPA) (15 U.S.C. §§ 6501-6508) and the FTC COPPA Rule (16 C.F.R. Part 312) restrict the collection of personal information from children under 13.

### 3.2 Applicability Thresholds
*   **Targeted Services:** Web services or mobile apps directed to children under 13.
*   **Actual Knowledge:** General-audience services that acquire "actual knowledge" that they are collecting personal information from a child under 13.

### 3.3 Residual Risk Assessment & Underage Account Procedure
Profile Tailors is **not directed to children** and operates a strict **18+ eligibility policy** enforced via:
1.  **Mandatory Registration Gate:** The account registration form requires an explicit clickwrap checkbox confirming the user is 18 years of age or older (`confirmedAgeEligibility`).
2.  **Factual Discovery Escalation:** If Profile Tailors receives a report (e.g., via `privacy@profiletailors.com` or support) or discovers through internal moderation that a user is under 18 (and specifically under 13), the **Underage Account Procedure (COMP-007)** is triggered immediately:
    *   **Immediate Suspension:** The account is frozen, and active sessions are terminated.
    *   **Verified ID / Purging:** The user must provide proof of age within the appeal window. If they fail to do so, or confirm they are underage, their personal data and workspace content are permanently purged within 30 days.

---

## 4. CCPA / CPRA Notice, Rights, and GPC

### 4.1 Statutory Basis
The California Consumer Privacy Act of 2018 (CCPA), as amended by the California Privacy Rights Act of 2020 (CPRA) (Cal. Civ. Code § 1798.100 et seq.), regulates how businesses handle California residents' personal information.

### 4.2 Applicability Thresholds
The CCPA/CPRA is **not universally applicable** to all businesses. It applies only to a for-profit entity doing business in California that meets **one or more** of these criteria:
1.  Has annual gross revenues in excess of **$25 million**.
2.  Annually buys, receives, sells, or shares the personal information of **100,000 or more** California consumers or households.
3.  Derives **50% or more** of its annual revenues from selling or sharing consumers' personal information.

Profile Tailors currently does not target consumers in California at a scale meeting these thresholds. However, to ensure readiness, the technical controls are designed to support CCPA/CPRA requirements.

### 4.3 Notice at Collection
If activated, Profile Tailors will provide a compliant "Notice at Collection" on the registration form and home page. This notice must outline:
*   The categories of personal information collected.
*   The purposes for which they are used.
*   Whether that category is "sold" or "shared".
*   The applicable retention criteria per category.

### 4.4 Sensitive Personal Information Limitations
Profile Tailors collects credentials (email and hashed passwords). Under CCPA, login credentials constitute "Sensitive Personal Information." However, because credentials are used solely to provide the core authentication service, no "Right to Limit" is triggered under Cal. Civ. Code § 1798.121 (since the use falls under the statutory exceptions).

### 4.5 "Do Not Sell / Share" Opt-Out
Profile Tailors **does not sell** or **share** personal information (as those terms are defined under the CCPA/CPRA to cover transfers for monetary/valuable consideration or cross-context behavioral advertising).
*   No advertising pixels or tracking scripts are deployed.
*   Analytics are handled via cookieless Ahrefs Web Analytics which does not track individuals across sites.

### 4.6 Global Privacy Control (GPC) behavior
Under California regulations, businesses must process browser-level opt-out preference signals, such as the Global Privacy Control (GPC), as a valid request to opt-out of the "sharing" or "sale" of personal information.

#### Technical Implementation and Test Verification
Profile Tailors implements native GPC signal detection in `<head>` via `ConsentScript.astro` and `ConsentBanner.astro`:
*   **Detection Logic:** Checks `navigator.globalPrivacyControl === true`.
*   **Behavior:** If GPC is detected, non-essential tracking (such as analytics scripts) is automatically disabled/blocked by default, and the preference banner defaults the analytics category to "OFF".
*   **Verification:** This behavior is verified by automated tests:
    *   **Unit Tests:** `apps/web/marketing/src/components/consent/ConsentScript.test.ts` asserts that a positive `globalPrivacyControl` signal correctly blocks analytics by default and sets the internal DNT flag.
    *   **E2E Tests:** `apps/web/marketing/e2e/consent.spec.ts` mocks GPC injection (`gpc-signal blocks analytics by default`) and verifies the UI toggle is defaulted to OFF.

---

## 5. Common Controller/Processor State Law Baseline

### 5.1 Statutory Basis
Several US states have enacted comprehensive consumer privacy frameworks:
*   **Virginia Consumer Data Protection Act (VCDPA)**
*   **Colorado Privacy Act (CPA)**
*   **Connecticut Data Privacy Act (CTDPA)**
*   **Utah Consumer Privacy Act (UCPA)**
*   **Texas Data Privacy and Security Act (TDPSA)**

### 5.2 Applicability Thresholds
Like California, these laws are **not universally applicable**. They rely on specific consumer volume or revenue thresholds:

| State | Consumer Volume Threshold | Revenue / Sale Threshold |
| :--- | :--- | :--- |
| **Virginia** | 100,000+ Virginia consumers | 25,000+ consumers AND 50%+ revenue from sale of data |
| **Colorado** | 100,000+ Colorado consumers | 25,000+ consumers AND sale/discount of data |
| **Connecticut** | 100,000+ Connecticut consumers | 25,000+ consumers AND 25%+ revenue from sale of data |
| **Utah** | 100,000+ Utah consumers | AND gross revenue of $25M+ |
| **Texas** | Any business operating in Texas | Excludes small businesses as defined by the SBA (though small businesses must still honor the right to opt-out of sale) |

### 5.3 Baseline Controller Obligations
1.  **Data Minimization:** Profile Tailors collects only the data necessary to plan, schedule, and publish social posts. No auxiliary behavioral profiles or device scans are performed.
2.  **Data Protection Assessments (DPAs):** Required for high-risk processing (processing sensitive data, profiling, or targeted advertising). Since Profile Tailors does not engage in these practices, formal DPIA obligations are minimal.
3.  **Processor Contracts (Data Processing Addendum):** All infrastructure providers (Oracle Cloud, Cloudflare) are contractually bound by DPAs that restrict data use solely to processing on our instructions.

### 5.4 State Rights Routing through Common DSAR Workflow
US state laws recognize consumer privacy rights:
*   **Right to Access / Know:** Confirm if we process data and obtain a copy.
*   **Right to Delete:** Request deletion of personal information.
*   **Right to Correct:** Request correction of inaccurate personal information.
*   **Right to Portability:** Obtain a copy of the data in a portable format.

Profile Tailors handles these state rights via a **unified backend and frontend DSAR workflow**:
*   **Frontend settings UI:** Under `PrivacySection.vue` and `DsarRequestForm.vue`, users can submit requests of type `ACCESS`, `EXPORT`, `CORRECTION`, or `DELETION` directly.
*   **Backend endpoint:** The request is processed by `/api/v1/privacy/requests` in `PrivacyController.kt` which validates and routes the command through Spring MediatR queries and commands (`SubmitAccessRequestCommand`, `SubmitDeletionRequestCommand`, etc.).
*   **Processing and Tracking:** All submitted requests are recorded in the common `DataSubjectRequest` aggregate and tracked on the database until completion.

---

## 6. State Breach Notification Matrix

### 6.1 Integration with Incident response Workflow
Any suspected data breach is handled in accordance with the **Incident Response Runbook (COMP-004)**.

To ensure state-specific statutory deadlines feed the incident response workflow, the Incident Commander must consult the primary deadlines set in **`docs/compliance/incident-sla-table.yaml`**.

The runbook enforces that the **shortest applicable deadline** among affected states is selected as the operational SLA.

### 6.2 US State Breach SLA Matrix

| Jurisdiction Code | Triggering Threshold | Authority Notification Deadline | Consumer Notification Deadline |
| :--- | :--- | :--- | :--- |
| **US-CA** (California) | Breach of unencrypted personal info of CA residents. | Without unreasonable delay (Sample notice to AG if >500 residents notified). | Most expedient time possible and without unreasonable delay (Civ. Code §§ 1798.29, 1798.82). |
| **US-VA** (Virginia) | Breach of unencrypted personal info likely to cause identity theft or other fraud. | Without unreasonable delay (To Attorney General). | Without unreasonable delay (Va. Code § 18.2-186.6). |
| **US-CO** (Colorado) | Breach of personal info of CO residents. | Without unreasonable delay (To AG if >500 residents notified; within 30 days). | Within **30 days** after determination of breach (Colo. Rev. Stat. § 6-1-716). |
| **US-TX** (Texas) | Breach of personal info of TX residents. | Without unreasonable delay (To AG if >250 residents notified; within 14 days). | Within **60 days** after determination of breach (Tex. Bus. & Com. Code § 521.053). |
| **US-FL** (Florida) | Breach of personal info of FL residents. | Within **30 days** of determination (To AG if >500 residents notified). | Within **30 days** of determination (Fla. Stat. § 501.171). |

All security events must be documented internally immediately (`internal_documentation`: `internal_policy_immediate`) to preserve evidence.

---

## 7. DMCA Agent and Safe-Harbor Decision

### 7.1 Statutory Basis
The Digital Millennium Copyright Act (DMCA) (17 U.S.C. § 512) provides a "safe harbor" limiting the liability of online service providers for copyright infringement by their users.

### 7.2 Applicability & Decision
Because Profile Tailors allows users to upload media assets (such as images and video) and publish drafts, the service qualifies as hosting user-generated content.
To claim DMCA safe harbor protection, Profile Tailors adopts the following:
1.  **Registered Agent:** Must register a designated agent with the US Copyright Office.
2.  **Public Takedown Policy:** Provide a clear DMCA notice path with the agent's name, physical address, and email in the Terms of Service.
3.  **Repeat Infringer Policy:** Adopt and reasonably implement a policy for suspending or terminating accounts of repeat infringers.
4.  **Takedown Report View:** Implement governance tools (`GovernanceTakedownView.vue` and `TakedownReportDialog.vue`) to record, investigate, and execute content takedowns when valid infringement notifications are received.

---

## 8. US Privacy Addendum and Opt-Out UX

### 8.1 US Privacy Addendum
If US targeting is enabled, the platform will adopt a **US Privacy Addendum** appended to the primary Privacy Policy. This addendum will incorporate:
*   State-specific rights disclosures (VCDPA, CPA, CTDPA, TDPSA).
*   A clear statement that the service does not "sell" or "share" data as defined under state statutes.
*   Instructions on how users can appeal a denied rights request (via `privacy@profiletailors.com`), routing appeals to a supervisor.

### 8.2 Opt-Out UX and Consent Settings
1.  **Consent Preference Center:** Users can access the Cookie Preference Center in the footer at any time to toggle non-essential cookies.
2.  **DNT / GPC Compliance:** The application automatically respects the `navigator.globalPrivacyControl` and `navigator.doNotTrack` browser signals, ensuring that a user's choice is honored instantly without requiring manual toggles.
3.  **In-App Right to Opt-Out / Delete:** Users can submit deletion or restriction requests directly from their workspace settings pane (`PrivacySection.vue`), completing the unified consent and privacy opt-out experience.
