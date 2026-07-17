# Consent and Preference Register

> **Classification:** Internal — Privacy and Product Operations
> **Status:** Design register — most user-facing controls not implemented
> **Register date:** 2026-07-17

## Overview

Separate contractual acceptance, privacy notices, customer instructions, consent-based purposes, opt-outs, and product preferences. These concepts have different legal effects and must not share a checkbox, database flag, or withdrawal consequence merely for implementation convenience.

Consent is used only after an approved market and purpose analysis determines that consent is the correct basis. Where another lawful basis applies, the product still records required notices, instructions, objections, or preferences without falsely labelling them consent.

### Evidence Invariants

- One record identifies one actor, purpose, controller, version, language, market context, and affirmative choice.
- Optional consent is off by default and independent of access to unrelated service functions.
- Presentation and affirmative action are recorded separately.
- Withdrawal is as accessible as the original choice and stops the specific future processing.
- Historical proof is retained under an approved rule without continuing the withdrawn processing.
- Changed purposes, recipients, technology, controller, or material text trigger a new review and, where required, renewed choice.
- A privacy notice acknowledgement is not consent to every described processing purpose.
- Customer instructions and OAuth authorisation are not automatically the legal basis for Profile Tailors' controller activities.

## Changes

| Version | Date | Description |
|---|---|---|
| 1.0 | 2026-07-17 | Added current and future purpose-level choice records and evidence requirements |

## Usage

### Current Register

| ID | Choice or instruction | Current repository evidence | Classification | Status and blocker |
|---|---|---|---|---|
| CPR-001 | Join the early-access waitlist | Separate early-access flag, consent version, locale, source, form identifier, and timestamps exist in the data model | Candidate consent or requested pre-contract action; market review required | Backend model exists; complete UI submission, notice, withdrawal, evidence export, and lawful-basis approval are not verified |
| CPR-002 | Receive marketing related to the waitlist | Separate optional marketing flag exists | Optional marketing consent or market-specific exception | Must remain independent and off by default; sender identity, channel rules, withdrawal, suppression, and proof are not implemented |
| CPR-003 | Accept Terms and AUP | No versioned clickwrap evidence | Contract acceptance, not privacy consent | Missing; implement [`legal-acceptance-record.md`](legal-acceptance-record.md) before relying on the documents |
| CPR-004 | Receive mandatory privacy information | Draft policies and blocked routes | Notice presentation, not consent | Public notice remains blocked; future presentation evidence must not imply agreement to non-optional processing |
| CPR-005 | Connect a LinkedIn account | OAuth state and connection/credential model | Customer instruction plus third-party authorisation; controller basis requires separate analysis | Adapter exists but production enablement, scopes, platform terms, DPA, deletion, and market approval are unverified |
| CPR-006 | Publish or schedule customer content | Authenticated workspace actions and publishing records | Customer instruction when Profile Tailors acts as processor | Must be tied to authorised workspace action, current terms/DPA, platform account, audit evidence, and revocation/cancellation controls |
| CPR-007 | Store refresh session | HttpOnly refresh cookie | Strictly necessary session operation where approved; not optional consent | Cookie attributes exist; final notice, domain measurement, session controls, account deletion, and country analysis remain pending |
| CPR-008 | Store sidebar and interface preferences | `sidebar_state` cookie plus local storage for theme, locale, workspace, and dashboard state | Preference or necessary storage depending on item and market | Purpose, security attributes, expiry, device-sharing, deletion, and consent classification require review |
| CPR-009 | Ahrefs Web Analytics | Conditional script enabled by configuration; default cookieless behaviour | Analytics processing basis and opt-out/consent depend on verified operation and market | Keep disabled until provider contract, role, data, location, retention, transfer, runtime, and market decision are approved |
| CPR-010 | Future non-essential cookies, pixels, advertising, or behavioural analytics | None approved | Prior consent or opt-out regime may apply by market | Prohibited until a compliant preference platform, geolocation basis, proof, withdrawal, and pre-load blocking are implemented |
| CPR-011 | API key creation and scoped use | API-key hash/verifier and scope model | Authenticated service instruction, not general consent | Terms acceptance, purpose, scope UI, revocation, expiry, audit, and deletion controls remain incomplete |
| CPR-012 | AI processing or training choice | No approved production AI processing evidence | Feature-specific notice, contract, opt-out, or consent depends on model and market | Freeze until AI inventory, provider, purpose, training, human review, prohibited use, DPIA, and country approval exist |

### Required Record Fields

Every optional consent or revocable preference stores:

- Stable `choice_id` and purpose identifier
- Acting principal or approved pre-account correlation identifier
- Controller legal entity and product surface
- Workspace or organisation when relevant
- Country and subdivision context used for the approved rule
- Language and exact notice or consent version
- Presented text artifact and cryptographic hash
- Presentation timestamp, surface, and choice configuration
- Affirmative, rejected, or unchanged action and server timestamp
- Source, form, campaign, or integration without embedding unnecessary personal data
- Recipient or provider categories and transfer context when material to the choice
- Withdrawal method and effect
- Superseded record link and re-consent trigger
- Proof retention rule, legal hold, and authorised evidence access

### Withdrawal and Suppression

Withdrawal must:

1. Authenticate or otherwise attribute the request proportionately.
2. Identify the specific purpose without forcing account deletion.
3. Stop new processing and downstream dispatch within the approved operational target.
4. Propagate to active processors, campaigns, queues, exports, and customer systems where required.
5. Add a minimised suppression record when needed to prevent re-enrolment.
6. Preserve historical proof separately from active audience or processing tables.
7. Confirm the effect, residual legal processing, and complaint route in the person's language.

### Consent Change Review

Re-run product, technical, privacy, and legal approval when any of the following changes:

- Controller or contracting entity
- Purpose, data category, source, recipient, or transfer destination
- Provider, SDK, cookie, pixel, model, or tracking technique
- Required versus optional status or service consequence
- Target audience, country, language, age, or customer type
- Retention, withdrawal, suppression, or evidence design
- Text, UI prominence, button label, default, choice granularity, or accessibility

### Minimum Test Evidence

- Marketing remains false when early access is accepted.
- Rejecting optional analytics does not block strictly necessary service access.
- No non-essential request is sent before the applicable affirmative choice.
- Withdrawal prevents future sends or collection and reaches every active processor.
- Reopening preferences reflects server truth and does not silently re-enable a choice.
- Locale, version, controller, purpose, country, and content hash can be reproduced.
- A new material version creates a new event and retains the old proof.
- Terms acceptance and every optional consent generate distinct events.
- Account deletion handles proof and suppression under their separate approved retention rules.
- A child or representative flow cannot proceed without the approved market-specific authority controls.

## Troubleshooting

- **The product team calls every checkbox consent:** Classify the underlying purpose and legal effect first; rename notice, instruction, preference, or acceptance events accurately.
- **Marketing is required to join the waitlist:** Separate it and keep marketing optional unless qualified market review documents a lawful exception.
- **A cookieless tool still processes request data:** Evaluate the processing and transfer independently from cookie use; “cookieless” is not “no personal data”.
- **The user clears browser storage:** Browser deletion does not update server-side consent or suppression truth; preference UI must reconcile both safely.
- **A provider offers a consent banner:** Verify configuration, blocking, evidence, localisation, accessibility, withdrawal, and controller obligations; installation alone is not compliance.
- **Withdrawal breaks an essential service:** Confirm whether the processing is actually optional and whether consent was the correct basis; do not misrepresent contract necessity as revocable consent.
- **Country is uncertain:** Keep the optional processing disabled until an approved applicability method selects a valid rule.

## References

- [`legal-acceptance-record.md`](legal-acceptance-record.md): Contract and optional-choice separation
- [`data-inventory.yaml`](data-inventory.yaml): Current processing evidence
- [`data-inventory.md`](data-inventory.md): Browser storage and provider summary
- [`legal-publication-gate.md`](legal-publication-gate.md): Public claim prerequisites
- [`global-legal-readiness.md`](global-legal-readiness.md): Country consent, cookies, marketing, and child overlays
