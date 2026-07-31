# Global Legal Readiness

> **Classification:** Internal — Legal and Compliance
> **Status:** Draft — no market is approved for production launch
> **Review date:** 2026-07-17
> **Owner:** Legal or a formally designated compliance owner

## Overview

This document is the legal market-entry register for Profile Tailors. It covers the European
Union and European Economic Area, the Americas, and the principal Asian markets in which an
online social-media management service could be offered.

This register is a release-control document, not a declaration that Profile Tailors complies in
every listed jurisdiction. A market is enabled only after its applicability triggers have been
assessed, the controls in this document have evidence, and qualified local counsel has approved
the public documents and commercial model where the row requires it.

The following are not sufficient evidence of legal readiness:

- A passing build, unit test, or static-page check.
- A draft policy banner or a placeholder for the legal entity, address, governing law, or vendor.
- A planned retention period without an implemented and tested deletion control.
- A list of possible infrastructure providers instead of the contracted production provider.
- A generic GDPR section reused for a country with different notices, rights, transfer, language,
  registration, representative, or breach-notification requirements.

## Changes

| Version | Date       | Description                                                    |
|---------|------------|----------------------------------------------------------------|
| 0.1     | 2026-07-17 | Initial market register for the EU/EEA, the Americas, and Asia |

## Usage

### Market status vocabulary

| Status                      | Meaning                                                                                                           |
|-----------------------------|-------------------------------------------------------------------------------------------------------------------|
| **Blocked**                 | The market must not be targeted, sold into, or enabled in production.                                             |
| **Assessment required**     | Applicability depends on targeting, volume, revenue, entity, data, or feature facts that have not been recorded.  |
| **Implementation required** | The law applies and one or more required operational controls lack evidence.                                      |
| **Counsel review required** | Product facts are documented, but qualified counsel must approve the local contract or regulatory interpretation. |
| **Approved**                | The market owner, evidence references, counsel approval, and review date are recorded.                            |

The current global status is **Blocked**. There is no evidence in this repository of an approved
legal entity identity, final production vendor set, enforceable market-specific terms, or a
completed launch approval for any region.

### Global baseline

Every market inherits this baseline. A local row may add stricter requirements but may not remove
these controls.

| Domain                  | Required evidence before launch                                                                                                                                                                                                            |
|-------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Legal identity          | Exact contracting entity, legal form, registration and tax identifiers, registered address, support and legal contacts, and authority to use the Profile Tailors and Dallay names.                                                         |
| Commercial model        | Written decision on B2B, B2C, or both; eligible age; supported countries; currencies; taxes; trial, renewal, cancellation, refund, and withdrawal rules.                                                                                   |
| Contract formation      | Clickwrap acceptance of a specific Terms and AUP version, timestamp, actor, workspace, locale, and immutable evidence of the text accepted.                                                                                                |
| Product truth           | Production feature and data-flow inventory, including social platforms, AI functions, payments, email, analytics, cookies, local storage, user-generated content, and support tools.                                                       |
| Vendors                 | One selected production vendor per function, service and storage locations, role, subprocessor chain, DPA, transfer mechanism, security review, and contract reference. Alternatives and planned vendors are excluded from public notices. |
| Privacy operations      | Tested access, correction, deletion, export, objection or opt-out, consent withdrawal, identity verification, appeal where applicable, and request-deadline workflow.                                                                      |
| Retention               | A purpose-based schedule linked to implemented deletion or anonymisation jobs, litigation holds, backups, logs, and test evidence. No public fixed period may be stated without that evidence.                                             |
| Security and incidents  | Risk assessment, access control, encryption, audit evidence, incident owner, 24/7 escalation path, jurisdiction-specific notification decision tree, and rehearsal record.                                                                 |
| International transfers | Data exporter/importer map and the mechanism required by each source jurisdiction, including transfer assessments and supplementary measures where required.                                                                               |
| Marketing and tracking  | Channel-specific consent or opt-out rules, suppression lists, sender identity, cookie/SDK scan, consent records, and a prohibition on non-essential tracking before the required choice.                                                   |
| Children                | A documented age gate and prohibition on knowingly processing children where the required parental-consent and child-safety controls are not implemented.                                                                                  |
| Content and platforms   | Platform API terms, user-content licence, notice-and-action process, repeat-abuse process, copyright and trademark handling, and illegal-content escalation.                                                                               |
| Open source and IP      | AGPL-3.0 obligations for the published software are separated from SaaS terms, trademarks, logos, domain names, and proprietary content; the corresponding-source route for the deployed version is verified.                              |
| AI                      | Inventory of models and providers, provider terms and DPA, training-use setting, human review, prohibited uses, transparency labels, output-risk controls, and market-specific AI assessment.                                              |
| Accessibility           | Conformance target, audit evidence, accessible support path, and remediation process for the marketing site and application.                                                                                                               |
| Governance              | Named accountable owner, legal-change review at least quarterly, evidence retention, approval record, and an emergency market-disable procedure.                                                                                           |

### European Union and EEA

**Coverage:** all EU Member States plus Iceland, Liechtenstein, and Norway. Spain is the initial
country layer because the project currently presents Spanish-language pages and appears to plan
an establishment or launch there.

| Regime                                               | Applicability trigger                                                                                | Launch requirements                                                                                                                                                                                                                           | Current status                                                                                                                           |
|------------------------------------------------------|------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------|
| GDPR and national implementing law                   | Establishment in the EEA, or offering goods/services to or monitoring people in the EEA              | Verified controller/processor roles; Arts. 12–14 notices; lawful basis per purpose; processor contracts; rights operations; ROPA; DPIA screening; breach workflow; DPO/representative assessment; transfer mechanism and assessment           | **Blocked** — identity, contacts, vendor facts, legal bases, retention controls, transfer evidence, and rights operations are incomplete |
| ePrivacy and national cookie rules                   | Storing or accessing information on a user's device, or direct electronic marketing                  | Pre-storage consent for non-essential cookies/SDKs; equally accessible reject and withdraw controls; consent evidence; accurate cookie/local-storage inventory; national marketing rules                                                      | **Blocked** — the draft cookie inventory names unverified cookies and providers                                                          |
| Consumer and distance-selling law                    | B2C sales or free-to-paid consumer onboarding                                                        | Pre-contract information; total price and taxes; renewal/cancellation; withdrawal and refund analysis; fair terms; mandatory consumer venue and remedies; language/localisation review                                                        | **Assessment required** — B2B/B2C decision and billing model are not final                                                               |
| Digital Services Act                                 | The service qualifies as an intermediary, hosting service, or online platform for a covered function | Service classification; points of contact; terms transparency; notice-and-action and statement-of-reasons controls where applicable; trader and advertising duties if applicable                                                              | **Assessment required** — social publishing alone does not establish the classification                                                  |
| EU AI Act                                            | Profile Tailors provides or deploys an AI system in the EU                                           | Role and risk classification per use case; AI literacy; prohibited-practice screen; provider documentation; transparency and labelling for applicable interactive or synthetic-content features; high-risk assessment if the use case changes | **Assessment required** — no canonical AI-system inventory exists                                                                        |
| European Accessibility Act and national law          | Covered B2C e-commerce or digital service after the applicable date, subject to scope and exemptions | Applicability opinion, accessibility statement, product and support audit, remediation evidence                                                                                                                                               | **Assessment required**                                                                                                                  |
| Spain: LSSI, consumer, tax and corporate disclosures | Spanish establishment, targeting, electronic contracting, or marketing                               | Aviso legal with real entity details; electronic-contract records; commercial-communication rules; consumer wording if B2C; invoice/VAT and registration facts                                                                                | **Blocked** — the public draft uses `TBD` for the registered address and does not identify the actual provider                           |

EU/EEA activation also requires a country addendum when national rules materially differ, including
consumer language, cookies and marketing, employment data, supervisory authority, age of digital
consent, and local registration or representative requirements.

### Americas

The United States must be tracked at federal and state level; “CCPA compliant” is not a substitute
for assessing other state privacy laws or federal sector rules.

| Market or regime                                          | Applicability trigger                                                                                                   | Launch requirements                                                                                                                                                                                                                                | Current status                                                                                                       |
|-----------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------|
| United States — FTC Act and general consumer protection   | Offering the service to US customers or making privacy/security claims                                                  | Truthful and substantiated privacy, security, pricing, trial, renewal, cancellation, and AI claims; reasonable security; incident and law-enforcement response; no dark patterns                                                                   | **Implementation required** — current drafts contain factual claims not supported by deployment evidence             |
| United States — state comprehensive privacy laws          | State-specific targeting plus consumer, revenue, data-volume, sale, targeted-advertising, or profiling thresholds       | State applicability register; notices; access/correction/deletion/portability; sale/share/targeted-ad opt-outs; recognised universal opt-out signals where required; sensitive-data consent; appeals; processor contracts; assessments             | **Assessment required** — no state threshold or consumer-volume register exists                                      |
| California CCPA/CPRA and 2026 regulations                 | The statutory business thresholds or covered relationships are met                                                      | Notice at collection; privacy policy; request and appeal workflows; sale/share and targeted-advertising controls; opt-out preference signals; contract clauses; risk assessment, ADMT, and cybersecurity-audit screening under current regulations | **Assessment required** — do not publish a California-rights section until applicability and operations are verified |
| United States — COPPA and state child/teen rules          | Service directed to children, actual knowledge of under-13 users, or a state child/teen trigger                         | Age and audience assessment; parental consent where required; minimisation; retention/deletion; child-specific design and advertising restrictions                                                                                                 | **Blocked for children**                                                                                             |
| United States — CAN-SPAM and state auto-renewal rules     | Commercial email or recurring subscriptions                                                                             | Sender identity, postal address, unsubscribe and suppression controls; clear renewal terms and easy cancellation; state-specific review                                                                                                            | **Implementation required**                                                                                          |
| Canada — PIPEDA and substantially similar provincial laws | Commercial handling of personal information involving Canada, subject to federal/provincial allocation                  | Accountability owner; meaningful consent or lawful exception; limiting collection/use/retention; access/correction; safeguards; breach assessment, records and notification; vendor accountability; Quebec/Alberta/British Columbia assessment     | **Assessment required**                                                                                              |
| Brazil — LGPD                                             | Processing in Brazil, offering goods/services to people in Brazil, or data collected in Brazil                          | Processing-agent roles; Portuguese notice; legal basis; data-subject channel; security and incident process; DPO/`encarregado` assessment; ANPD transfer mechanism; processor contracts                                                            | **Counsel review required** — Brazil's transfer clauses cannot be replaced automatically by EU SCCs                  |
| Mexico — LFPDPPP                                          | Private-party processing in Mexico or another statutory territorial connection                                          | Spanish privacy notice; consent and ARCO rights; transfer clauses/consent analysis; security; processor terms; local authority and 2025 legal-change verification                                                                                  | **Counsel review required**                                                                                          |
| Argentina — Law 25,326                                    | Personal databases or processing within its scope                                                                       | Consent or other basis; notice; access/rectification/suppression; database-registration assessment; security; international-transfer mechanism                                                                                                     | **Counsel review required**                                                                                          |
| Colombia, Chile and Peru                                  | Targeting or processing people in the country under the applicable national privacy and consumer rules                  | Country counsel must confirm registration/representative, notice, consent, rights, transfers, breach, marketing, consumer, tax, and contract requirements; Chile's Law 21,719 transition for 1 December 2026 must be tracked                       | **Counsel review required**                                                                                          |
| Other American countries                                  | A country is added to signup, billing, advertising, sales outreach, app-store distribution, or local-language targeting | Complete the country activation checklist and add an official-source row before launch                                                                                                                                                             | **Blocked by default**                                                                                               |

### Asia

Asia is not one legal market. Several jurisdictions impose local representative, language,
registration, breach, security, or outbound-transfer duties that cannot be satisfied by an EU DPA
alone. Launch expansion is staged using a wave-based strategy.

*Detailed regional guidance and wave allocations are maintained in the [`market-entry-asia.md`](market-entry-asia.md) strategy document.*

| Market or regime                                                               | Applicability trigger                                                                                                         | Launch requirements                                                                                                                                                                                                                                                                                                           | Current status                                                                                   |
|--------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------|
| **First Wave**                                                                 |                                                                                                                               |                                                                                                                                                                                                                                                                                                                               |                                                                                                  |
| Japan — APPI                                                                   | Handling personal information in connection with offering goods/services to people in Japan                                   | Japanese notice; purpose specification; security and vendor supervision; breach workflow; rights; foreign-third-party and cross-border disclosure/consent analysis; see the standalone [`readiness-checklist-japan.md`](readiness-checklist-japan.md)                                                                         | **Counsel review required** — must meet checklist requirements and have verified evidence      |
| Singapore — PDPA                                                               | Collection, use, or disclosure of personal data by an organisation with the relevant Singapore nexus                          | Public DPO contact; notification and consent or exception; purpose limitation; access/correction; security; retention; comparable protection for overseas transfers; notifiable-breach assessment; see the standalone [`readiness-checklist-singapore.md`](readiness-checklist-singapore.md)                                 | **Counsel review required** — must meet checklist requirements and have verified evidence      |
| **Second Wave**                                                                |                                                                                                                               |                                                                                                                                                                                                                                                                                                                               |                                                                                                  |
| South Korea — PIPA                                                             | Targeting Korean users or processing with a direct and significant impact, including relevant foreign operators               | Korean privacy policy; lawful processing; children-under-14 controls; cross-border transfer disclosures and basis; consignment/provision distinction; rights workflow; 72-hour breach workflow; domestic representative assessment; see [`market-entry-asia.md`](market-entry-asia.md)                                          | **Counsel review required** — tracking foreign-operator domestic representative guidance         |
| India — Digital Personal Data Protection Act and 2025 Rules                    | Processing digital personal data in India or offering goods/services to people in India, according to the phased commencement | Clear notice; consent and withdrawal or legitimate-use assessment; rights and grievance workflow; child-data controls (under 18 age gate, parental consent, targeted ad ban); security and breach obligations; see [`market-entry-asia.md`](market-entry-asia.md)                                                           | **Assessment required** — obligations must be mapped to the official phased enforcement timeline |
| Philippines — Data Privacy Act                                                 | Processing with a Philippine link or covered extraterritorial processing of citizens/residents                                | DPO; privacy management program; transparency, legitimate purpose and proportionality; security; rights; processor accountability; registration/automated-processing assessment; breach workflow; comparable transfer protection                                                                                              | **Counsel review required**                                                                      |
| ASEAN, Hong Kong, Indonesia, Thailand, Malaysia, Taiwan                        | Active targeting or business operations in the respective country / region                                                     | Respective local laws, notification, local representative or DPO, consent/notice, and local-language requirements. Standard registration or representative and transfer mechanism verification.                                                                                                                              | **Blocked by default** — individual country assessment and activation records required           |
| **Dedicated Assessments**                                                      |                                                                                                                               |                                                                                                                                                                                                                                                                                                                               |                                                                                                  |
| China — PIPL, Data Security Law, Cybersecurity Law and outbound-transfer rules | Processing in China or extraterritorial offering/monitoring of people in China                                                | Chinese notice; separate consent where required; sensitive-data and children controls; local representative assessment; personal-information protection impact assessment; data localisation/volume assessment; approved outbound-transfer route; government-request restrictions; local hosting and ICP/cybersecurity review | **Blocked** — launch requires a dedicated China architecture and counsel review; cannot be enabled via generic Asia config |
| Vietnam — personal-data protection and cybersecurity rules                     | Processing personal data of people in Vietnam or covered local operations                                                     | Vietnamese notice/consent and rights; processing and outbound-transfer impact dossiers where applicable; local representative/presence and cybersecurity assessment; breach and government-filing workflow                                                                                                                    | **Blocked pending local counsel and architecture review** — cannot be enabled via generic Asia config |
| Other Asian countries                                                          | A country is added to signup, billing, advertising, sales outreach, app-store distribution, or local-language targeting       | Complete the country activation checklist and add an official-source row before launch                                                                                                                                                                                                                                        | **Blocked by default**                                                                           |

### Country activation checklist

A pull request enabling a country must link evidence for every item below:

1. Country, targeting signals, customer type, language, currency, payment method, tax treatment,
   and launch owner.
2. Legal applicability memo covering privacy, consumer, electronic marketing, cookies/device
   storage, children, content/platform, AI, accessibility, cybersecurity, breach, tax, sanctions,
   and sector-specific rules.
3. Local entity, representative, DPO, registration, licence, filing, or data-localisation decision.
4. Country-specific controller/processor and cross-border-transfer map.
5. Approved public notices and contract localisation, with no placeholders.
6. Tested rights, consent/opt-out, deletion, export, incident, cancellation, and support workflows.
7. Qualified-counsel approval reference, approval date, expiry/review date, and unresolved risks.
8. Rollback switch that prevents new signup, billing, and targeted acquisition in the country.

### Immediate Profile Tailors decision record

| Decision                                                   | Current result                                                                                                                                       |
|------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------|
| Publish the current legal pages                            | **No.** They contain placeholders and unverified operational claims.                                                                                 |
| Treat the existing OpenSpec verification as legal approval | **No.** It validates rendering and internal spec consistency only.                                                                                   |
| Claim global compliance                                    | **No.** Each market remains blocked or requires assessment and counsel review.                                                                       |
| Safe next public scope                                     | Decide the real entity and B2B/B2C model, select production vendors and regions, implement the global baseline, then approve one narrow country set. |

## Troubleshooting

- **A law is not listed:** The market remains blocked. Add an official-source row and obtain the
  required review before enabling it.
- **A threshold appears not to be met:** Record the supporting revenue, volume, targeting, and
  data-practice evidence and the next reassessment date. Do not remove baseline privacy and
  consumer-protection controls.
- **A vendor or region changes:** Stop publication of affected claims, update the data-flow and
  transfer records, execute the required terms, retest controls, and repeat market approval.
- **Product behaviour conflicts with a policy:** Product truth wins. Block publication until either
  the product control or the policy is corrected and verified.
- **Sources or effective dates conflict:** Use the enacted text and regulator guidance, record the
  uncertainty, and obtain local counsel review. Proposals are not treated as enacted law.

## References

### European Union and Spain

- [European Commission — data protection information for organisations](https://commission.europa.eu/law/law-topic/data-protection/information-business-and-organisations_en)
- [EUR-Lex — Regulation (EU) 2016/679 (GDPR)](https://eur-lex.europa.eu/eli/reg/2016/679/oj)
- [European Commission — Digital Services Act](https://digital-strategy.ec.europa.eu/en/policies/digital-services-act)
- [European Commission — AI Act framework and implementation](https://digital-strategy.ec.europa.eu/en/policies/regulatory-framework-ai)
- [AEPD — cookie guidance](https://www.aepd.es/guias/guia-cookies.pdf)
- [BOE — Law 34/2002 on information-society services and electronic commerce](https://www.boe.es/buscar/act.php?id=BOE-A-2002-13758)

### Americas

- [US FTC — privacy and security guidance](https://www.ftc.gov/business-guidance/privacy-security)
- [California Privacy Protection Agency — laws and regulations](https://cppa.ca.gov/regulations/)
- [Colorado Attorney General — Colorado Privacy Act](https://coag.gov/resources/colorado-privacy-act/)
- [Office of the Privacy Commissioner of Canada — privacy guide for businesses](https://www.priv.gc.ca/en/privacy-topics/privacy-laws-in-canada/the-personal-information-protection-and-electronic-documents-act-pipeda/pipeda-compliance-help/guide_org/)
- [Brazil ANPD — international data transfers](https://www.gov.br/anpd/pt-br/assuntos/assuntos-internacionais/transferencia-internacional-de-dados/international-affairs)
- [Mexico DOF — 20 March 2025 privacy-law decree](https://www.dof.gob.mx/abrirPDF.php?anio=2025&archivo=20032025-VES.pdf&repo=repositorio%2F)
- [Argentina — updated Law 25,326](https://www.argentina.gob.ar/normativa/nacional/64790/actualizacion)
- [Colombia SIC — personal-data protection authority](https://www.sic.gov.co/tema/proteccion-de-datos-personales)
- [Chile BCN — Law 19,628 and deferred Law 21,719 text](https://www.bcn.cl/leychile/Navegar?idNorma=141599&idVersion=2026-12-01)
- [Peru ANPD — privacy-law compendium](https://www.gob.pe/institucion/anpd/colecciones/3482-normativa-de-proteccion-de-datos-)

### Asia

- [`market-entry-asia.md`](market-entry-asia.md): Asia Market Entry Strategy Document
- [`readiness-checklist-japan.md`](readiness-checklist-japan.md): Standalone APPI Compliance Checklist for Japan
- [`readiness-checklist-singapore.md`](readiness-checklist-singapore.md): Standalone PDPA Compliance Checklist for Singapore
- [China — Personal Information Protection Law](https://en.spp.gov.cn/2021-12/29/c_948419.htm)
- [Japan PPC — APPI laws and policies](https://www.ppc.go.jp/en/legal/)
- [South Korea PIPC — foreign business operator guidance](https://www.pipc.go.kr/eng/user/ltn/new/noticeDetail.do?bbsId=BBSMSTR_000000000001&nttId=2488)
- [India MeitY — Digital Personal Data Protection Rules 2025](https://www.meity.gov.in/documents/act-and-policies/digital-personal-data-protection-rules-2025-gDOxUjMtQWa)
- [Singapore PDPC — data protection obligations](https://www.pdpc.gov.sg/overview-of-pdpa/the-legislation/personal-data-protection-act/data-protection-obligations)
- [Hong Kong PCPD — cross-border data transfer guidance](https://www.pcpd.org.hk/english/resources_centre/publications/files/GN_crossborder_e.pdf)
- [Philippines National Privacy Commission — Data Privacy Act](https://privacy.gov.ph/data-privacy-act/)
- [Malaysia Personal Data Protection Commissioner — laws and cross-border guidance](https://www.pdp.gov.my/ppdpv1/en/akta/personal-data-protection-guidelines-on-cross-border-transfer-of-personal-data-cbpdt/)
