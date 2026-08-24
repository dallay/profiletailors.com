# Market Entry — Asia Strategy

> **Classification:** Internal — Legal, Compliance, and Architecture
> **Status:** Draft — Future baseline strategy and reference
> **Review date:** 2026-07-23
> **Owner:** Legal or a formally designated compliance owner

## Overview

This strategy document sets out the legal, regulatory, and architectural requirements for launching
Profile Tailors in Asian markets. Due to the high complexity and variation in local laws, Asia
cannot be treated as a single regulatory region. Launch enablement must be staged across phased
waves and individual dedicated assessments.

No market may be enabled or targeted unless it has completed its standalone readiness checklist,
holds verified engineering and operational evidence, and has obtained qualified local legal counsel
approval.

### Wave-Based Strategy

```text
First Wave (Immediate Focus)
  ├── Japan: APPI (Act on the Protection of Personal Information)
  └── Singapore: PDPA (Personal Data Protection Act)

Second Wave (Deferred Expansion)
  ├── South Korea: PIPA (Personal Information Protection Act)
  ├── India: DPDP (Digital Personal Data Protection Act)
  └── ASEAN & Philippines: Selected regional expansions

Dedicated High-Risk Assessments (SaaS Isolation Required)
  ├── China: PIPL (Personal Information Protection Law) & Data Security Regime
  └── Vietnam: PDPL (Decree 13/2023/ND-CP) & Cybersecurity Decree 53
```

---

## Changes

| Version | Date       | Description                                  |
|---------|------------|----------------------------------------------|
| 1.0     | 2026-07-23 | Initial strategy framing for Asian expansion |

---

## Usage

### 1. First Wave: Japan and Singapore

These jurisdictions represent the primary target markets in Asia. Both have mature data protection
frameworks that share procedural commonalities with the GDPR but contain unique operational
triggers.

* **Japan (APPI):** Requires explicit consent for foreign third-party data provision, strict local
  vendor supervision ("entrustment"), direct breach reporting to the PPC within 3–5 days depending
  on severity, and clear Japanese language notices.
* **Singapore (PDPA):** Focuses heavily on the mandatory designation of a Data Protection Officer (
  DPO) registered with ACRA, comparable protection for overseas transfers, mandatory breach
  notification to the PDPC (within 3 calendar days of determination), and strict purpose limitation.

*Detailed standalone checklists for these markets are maintained separately:*

* [`readiness-checklist-japan.md`](readiness-checklist-japan.md)
* [`readiness-checklist-singapore.md`](readiness-checklist-singapore.md)

### 2. Second Wave: Korea, India, and ASEAN

These markets contain high operational complexity, especially around child data, local
representation, and phased regulatory implementation timelines.

#### South Korea (PIPA) — Foreign-Operator Guidance

South Korea's Personal Information Protection Act (PIPA) is one of the strictest data privacy laws
globally. Under the Personal Information Protection Commission (PIPC) guidance, foreign operators
who target Korean users (determined by active localization, Korean language support, local
marketing, or high volume of Korean traffic) are subject to full extraterritorial enforcement.

* **Domestic Representative:** Foreign operators without a local establishment but meeting specific
  statutory thresholds (e.g., active Korean user counts, sales, or data storage volume) must
  designate a local domestic representative to receive service of process and handle regulatory
  queries.
* **Consignment vs. Provision:** South Korea maintains a strict distinction between "consignment" (
  entrusting personal data to a processor) and "provision" (disclosing data to a third party).
  Consignments require public disclosure in the privacy policy and a written agreement outlining
  security measures and supervisor oversight. Provision requires explicit, separate consent from the
  data subject.
* **Breach Notification:** Data breaches must be reported to the PIPC and affected individuals
  within 72 hours of discovery (reduced from older timelines in recent amendments).
* **Cross-Border Transfer Disclosures:** Outbound data transfers require extensive public
  disclosures, detailing the recipient country, receiving entity, purpose, transfer date, and
  personal data items transferred.
* **Retention and Destruction:** Personal data must be permanently destroyed immediately upon
  fulfilling the purpose. If law requires preservation, the data must be physically or logically
  separated from active user databases.

#### India (DPDP Act) — Phased Obligations and Child Data

The Digital Personal Data Protection Act (DPDP Act) and its corresponding Rules establish a
clean-slate framework in India. Launching in India requires tracking phased obligations and strict
compliance with child-data protection rules.

* **Phased Commencement Timeline:** Obligations are not enacted all at once but phased according to
  official rules. Operators must track the enforcement schedule:
    1. *Phase 1 (Immediate):* General principles, clear and separate notice requirements, and
       consent withdrawal workflows.
    2. *Phase 2 (Within 6 months):* Rights of data principals (access, correction, erasure) and
       grievance redressal mechanisms (appointing a Grievance Officer).
    3. *Phase 3 (Within 12 months):* Strict data fiduciary obligations, vendor processor management,
       and security breach notifications to the Data Protection Board of India (DPBI).
    4. *Phase 4 (TBD):* Significant Data Fiduciary (SDF) audit requirements and potential
       sector-specific cross-border transfer blacklists.
* **Strict Child-Data Protection:** India maintains a very high standard for processing data
  belonging to children (defined strictly as individuals under 18 years of age).
    1. *Verifiable Parental Consent:* Processing any personal data of a child requires verifiable
       parental consent or consent from a lawful guardian. This requires implementing robust
       age-verification gates and verifiable consent flows before registration.
    2. *Prohibited Practices:* Operators must not engage in any processing of personal data that is
       likely to cause detrimental effects on the well-being of a child.
    3. *Targeted Advertising Ban:* Tracking, behavioral monitoring, or targeted advertising directed
       at children is strictly prohibited on the platform.
* **Grievance Redressal:** A dedicated Grievance Officer must be appointed with their contact
  details made public, and a local resolution mechanism must be established.

#### ASEAN and Philippines (DPA)

The Philippines Data Privacy Act of 2012 (DPA) requires a formal privacy management program.

* **DPO Registration:** Mandatory registration of the DPO and the processing system with the
  National Privacy Commission (NPC) if thresholds (such as processing data of 1,000+ individuals or
  sensitive personal information) are met.
* **Automated Processing Decisions:** Explicit restrictions and notification requirements on
  automated decision-making and profiling.
* **Breach Notification:** 72-hour notification clock to the NPC and affected data subjects for
  breaches involving sensitive personal information.

### 3. Dedicated High-Risk Assessments: China and Vietnam

Due to severe security regimes, local hosting mandates, and strict state controls, **China and
Vietnam cannot be enabled through a generic, unified Asia configuration**. Attempting to route
traffic or host data for these markets through standard global infrastructure is a major compliance
violation.

#### China (PIPL, DSL, CSL)

China’s data sovereignty framework is comprised of three pillars: the Personal Information
Protection Law (PIPL), the Data Security Law (DSL), and the Cybersecurity Law (CSL).

* **No Generic Routing:** Personal information collected in China must not be stored, transferred,
  or routed globally without completing highly formal administrative steps.
* **Outbound Data Transfer Routes:** Moving data out of China requires one of three strict paths:
    1. *Security Assessment:* Passing a formal security assessment conducted by the Cyberspace
       Administration of China (CAC) (typically required for operators processing data of over 1
       million individuals or exporting sensitive data).
    2. *Standard Contract:* Executing the CAC Standard Contract for Outbound Transfer of Personal
       Information and filing it, along with a formal Personal Information Protection Impact
       Assessment (PIPIA), with the local CAC within 10 days of execution.
    3. *Security Certification:* Obtaining a personal information protection certification from a
       CAC-approved professional institution.
* **Data Localisation Thresholds:** Foreign operators actively targeting China may be required to
  maintain localized hosting within mainland China and establish a local corporate entity or
  designate a domestic representative.
* **ICP and App Filing:** Offering web services or applications to Chinese users requires obtaining
  an ICP (Internet Content Provider) filing or license, which is only granted to local entities or
  joint ventures.
* **Separate Consent:** PIPL requires separate, explicit consent for specific processing
  activities (such as sharing data with third parties, public disclosure, or processing sensitive
  personal information).

#### Vietnam (PDPL - Decree 13/2023/ND-CP)

Vietnam’s Decree 13 on Personal Data Protection and Cybersecurity Decree 53 impose some of the
strictest data localization requirements in Southeast Asia.

* **Mandatory Data Localisation:** Under Decree 53, foreign enterprises providing
  telecommunications, internet, or digital services in Vietnam (which includes SaaS and social media
  publishing software) must store designated user data locally in Vietnam for a statutory minimum
  duration (at least 24 months) if they receive a security warning or order from the Ministry of
  Public Security (MPS).
* **Local Office or Representative:** Foreign operators subjected to the localization mandate must
  establish a local branch office or designate a legal representative in Vietnam.
* **Outbound Transfer Impact Dossier:** Under Article 43 of Decree 13, any transfer of Vietnamese
  citizens' personal data abroad requires the data exporter to formulate a highly detailed "Transfer
  of Personal Data Abroad Impact Assessment Dossier" and submit it to the Department of
  Cybersecurity and High-Tech Crime Prevention (A05) under the MPS within 60 days of the processing
  start.
* **Language and Consent:** Privacy notices, consent receipts, and rights request interfaces must
  support the Vietnamese language, and consent must be voluntarily given in a positive, written, or
  opt-in format.

---

## Architectural & Data-Residency Dependencies

Launching in Asian markets introduces strict engineering dependencies:

1. **Logical Data Residency:** If a market requires local storage (e.g., China, Vietnam, or strict
   interpretations of Korea/India), the backend architecture must support geographic routing. The
   global monolithic database must be split into regional shards, or local instances must be
   deployed.
2. **Consent & Age-Gate Infrastructure:**
    * **India:** Hard enforcement of a robust under-18 age-verification gate and verifiable parental
      consent flows. Blocking of tracking and analytics SDKs for minors.
    * **South Korea:** Strict enforcement of separate consent checkboxes for any third-party
      integrations, OAuth, or marketing purposes.
3. **Local Language & Translation Pipeline:**
    * All public notices, legal terms, and cookie settings must be professionally translated.
    * The frontend must handle dynamic locale loading (e.g., `ja`, `ko`, `zh`, `vi`, `hi`) without
      layout clipping or formatting breakages (mindful of Spanish and Asian text density
      differences).
4. **Security and Breach Monitoring:**
    * Engineering must implement rapid-detection alerts configured for SLA clocks: 72 hours for
      South Korea, 3 days for Singapore, and immediate/rapid reporting for India and Japan.

---

## Troubleshooting

- **Can we use a "Rest of Asia" toggle to activate multiple markets?**
    * **No.** Due to the distinct localization, local-language, DPO registration, and child-data
      requirements, a generic "Asia" toggle is prohibited. Each country must be activated
      individually following the complete Country Activation Record.
- **A user from Vietnam registers on our standard global instance. Is this allowed?**
    * Passive, unsolicited registrations ("reverse solicitation") do not automatically trigger local
      physical representation or localization decrees. However, if the service actively targets
      Vietnamese users (e.g., localized language, marketing, or pricing), compliance is triggered.
- **An infrastructure provider claims to support Asia-Pacific region. Does this solve compliance?**
    * No. Cloud regions (e.g., AWS Singapore) solve physical latency and data transit, but do not
      satisfy local regulatory filings, DPO registrations, local-language notices, or legal entity
      representation duties.

---

## References

- [`global-legal-readiness.md`](global-legal-readiness.md): Global Market Register
- [`country-activation-record-template.md`](country-activation-record-template.md): Step-by-step
  launch evidence template
- [`readiness-checklist-japan.md`](readiness-checklist-japan.md): Standalone Japan APPI Checklist
- [`readiness-checklist-singapore.md`](readiness-checklist-singapore.md): Standalone Singapore PDPA
  Checklist
- [South Korea PIPC Foreign Business Operator Guidance](https://www.pipc.go.kr/eng/user/ltn/new/noticeDetail.do?bbsId=BBSMSTR_000000000001&nttId=2488)
- [India DPDP Act 2023](https://www.meity.gov.in/)
- [Vietnam Decree 13/2023/ND-CP on Personal Data Protection](http://chinhphu.vn/)
- [China Personal Information Protection Law](http://www.npc.gov.cn/)
