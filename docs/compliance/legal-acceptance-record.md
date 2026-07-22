# Legal Acceptance Record

> **Classification:** Internal — Product, Legal, Security, and Compliance
> **Status:** Design contract — not implemented
> **Publication impact:** Terms and Acceptable Use Policy remain non-effective

## Overview

Define the evidence required to prove that a specific principal affirmatively accepted a specific
immutable legal document before a legally relevant action. Passive browsing, account creation
without a separate affirmative action, a footer link, or continued use is not sufficient evidence
for initial acceptance under this design.

This contract applies to Terms of Service, Acceptable Use Policy, customer DPAs or order forms
accepted in-product, material amendments requiring renewed acceptance, and any separate optional
consent. Privacy notices and mandatory disclosures are recorded as presented or acknowledged only
when appropriate; they must not be mislabelled as consent.

Country law controls whether electronic acceptance is valid, which disclosures must precede it,
whether a consumer must consent to electronic records, which language is required, and whether a
particular clause needs a separate action. Each enabled country therefore requires an approved
overlay.

### Core Invariants

- Acceptance references an immutable document artifact; mutable URLs are not evidence.
- The person receives the complete document before the affirmative action.
- Optional consents are separate from service terms and from each other.
- Rejection and withdrawal paths are as clear as acceptance where the choice is optional.
- A server-controlled timestamp and authenticated or otherwise attributable principal establish the
  event.
- No preselected checkbox, bundled marketing consent, or inferred acceptance is allowed.
- The accepted language and document hash can be reproduced and exported later.
- A new material version does not overwrite historical acceptance.
- Account, workspace, and organisation authority are recorded separately.
- Failed, abandoned, expired, or superseded presentations are distinguishable from acceptance.

## Changes

| Version | Date       | Description                                                                           |
|---------|------------|---------------------------------------------------------------------------------------|
| 1.0     | 2026-07-17 | Added the immutable presentation, acceptance, withdrawal, and market-overlay contract |

## Usage

### Document Version Record

Every candidate document version requires:

| Field                   | Requirement                                                                                       |
|-------------------------|---------------------------------------------------------------------------------------------------|
| `document_id`           | Stable identifier for the document family, such as `terms-of-service`                             |
| `version`               | Immutable semantic or date-based version; never reused                                            |
| `document_type`         | Terms, AUP, DPA, order form, optional consent, notice, or another approved type                   |
| `language`              | Exact rendered language and regional variant                                                      |
| `enabled_countries`     | Explicit approved countries and subdivisions; no implicit worldwide value                         |
| `audience`              | B2B, consumer, workspace owner, member, API customer, waitlist applicant, or other approved class |
| `content_hash`          | Cryptographic hash of the exact human-readable artifact                                           |
| `artifact_uri`          | Durable versioned location from which the complete artifact can be reproduced                     |
| `effective_at`          | Approved effective time; not before all required approvals                                        |
| `supersedes`            | Previous version or null                                                                          |
| `change_classification` | Initial, non-material, material, or legally mandated change                                       |
| `approval_record`       | Product, technical, business, and qualified legal approvals                                       |
| `withdrawal_effect`     | Consequence of withdrawal where the legal basis permits withdrawal                                |
| `retention_rule`        | Approved record-retention rule and legal-hold behaviour                                           |

### Presentation Record

Create the presentation server-side before the affirmative action:

| Field                 | Requirement                                                                              |
|-----------------------|------------------------------------------------------------------------------------------|
| `presentation_id`     | Unique, unpredictable identifier                                                         |
| `document_version_id` | Exact document version shown                                                             |
| `principal_id`        | Authenticated principal or approved pre-account correlation identifier                   |
| `workspace_id`        | Workspace or organisation when the action is on its behalf                               |
| `authority_claim`     | Individual acceptance or representation that the actor may bind an organisation          |
| `presented_at`        | Server-controlled UTC timestamp                                                          |
| `locale`              | Locale actually rendered                                                                 |
| `country_context`     | Country and subdivision basis used to select the approved overlay                        |
| `surface`             | Registration, checkout, settings, re-consent, API administration, or another approved UI |
| `document_url`        | Versioned URL presented to the person                                                    |
| `content_hash`        | Hash verified at presentation time                                                       |
| `required_action`     | Exact affirmative action and label                                                       |
| `optional_choices`    | Separately identified optional consents shown in the same flow                           |
| `expires_at`          | Time after which a stale presentation cannot be accepted                                 |

Client-controlled IP address, user-agent, or device data may be recorded only after necessity,
notice, security, and retention review. It must not replace the server evidence above.

### Acceptance Event

The acceptance endpoint must atomically verify and store:

- Presentation exists, is unexpired, and belongs to the acting principal
- Document version is still approved for the principal's country, language, audience, and product
- Submitted content hash matches the presented immutable artifact
- Required optional choices are not bundled into the terms event
- Actor's organisation authority declaration is captured where relevant
- Server acceptance timestamp, presentation identifier, principal, workspace, document version,
  locale, country context, and hash
- UI surface and action label version
- Idempotency key and replay protection
- Resulting access decision and any required next document

The event is append-only. Corrections create linked administrative events with reason,
authorisation, and audit evidence; they do not mutate history silently.

### Separate Event Types

Use distinct event types and evidence for:

- `presented`
- `accepted`
- `declined`
- `withdrawn`
- `superseded`
- `reacceptance_required`
- `acceptance_exempted` only with a documented legal and product basis
- `administratively_corrected`

Marketing permission, waitlist early-access consent, analytics consent, sensitive-data consent,
cross-border consent, and service terms must never share one acceptance event merely because they
appear on one screen.

### Access Enforcement

Before a protected action, the server verifies the current accepted version required for:

- Account registration completion
- Workspace creation or ownership acceptance
- Paid order or subscription activation
- API key creation
- Social-account connection and customer instruction
- Publication or other customer-data processing
- Material terms update

The final mapping depends on product and market approval. A frontend-only check is not sufficient.

### Evidence Export and Audit

An authorised operator must be able to export:

1. The exact accepted document artifact and hash.
2. Product, business, technical, and legal approval for that version and country set.
3. Presentation and affirmative-action records.
4. Principal, workspace, and organisation-authority linkage.
5. All later withdrawal, supersession, correction, and reacceptance events.
6. Delivery or availability evidence for the person's archival copy.
7. The applicable country overlay and UI version.

The export must minimise unrelated personal data, be access-controlled, and record the auditor and
purpose.

### Country Overlay Checklist

Qualified counsel approves, for every country and customer class:

- Validity and form of electronic records, signatures, clickwrap, and organisation authority
- Required pre-contract information and order-button wording
- Consumer electronic-record consent, durable medium, downloadable copy, and record availability
- Language, accessibility, age, parental-authority, and capacity requirements
- Separately accepted clauses, including arbitration or other unusual terms where permitted
- Cooling-off, cancellation, withdrawal, renewal, price-change, and refund effects
- Stamp, registration, witness, qualified-signature, or paper requirements for excluded document
  types
- Evidence admissibility, retention, limitation period, legal hold, and deletion exceptions
- Cross-border evidence storage, localisation, disclosure, and law-enforcement constraints

No country is approved by similarity to another country's electronic-transactions law.

### Minimum Test Evidence

- A person cannot accept an unapproved, expired, wrong-country, wrong-language, or superseded
  version.
- A person cannot accept for another principal or workspace without authorised context.
- Replaying the same request is idempotent and cannot create conflicting events.
- Optional consent remains false when the person accepts only the Terms.
- Decline prevents only the action that legally requires acceptance.
- The accepted artifact can be reproduced byte-for-byte and its hash verified.
- Material version changes require reacceptance before the mapped protected action.
- A withdrawn optional consent stops the related processing without deleting the historical proof.
- An acceptance record cannot be changed through ordinary application update or delete endpoints.
- Evidence export includes every required event and excludes unrelated account data.

## Troubleshooting

- **The terms URL always points to the latest version:** Store and present a versioned immutable
  artifact; a redirect to current text is not evidence of historical content.
- **The user was already registered before acceptance existed:** Do not backfill acceptance. Present
  the approved version and record a real affirmative event before the protected action.
- **A workspace owner accepted for the company:** Preserve individual identity, organisation
  authority, workspace, and the exact organisational terms; membership alone may not establish
  authority.
- **One checkbox covers terms and marketing:** Split the choices, purposes, records, and withdrawal
  effects.
- **The locale changed after presentation:** Expire the presentation and create a new one for the
  exact language shown.
- **The country cannot be determined reliably:** Do not infer worldwide approval. Use an approved
  country-selection and validation process or block the transaction.
- **A document changes only for formatting:** Recompute the artifact hash and classify the change
  under the approved versioning policy; never reuse a hash or version.
- **A person requests deletion:** Apply the approved acceptance-record retention and legal-hold
  rule, minimise the linkage where permitted, and record the decision; do not promise unconditional
  erasure.

## References

- [`legal-publication-gate.md`](legal-publication-gate.md): Approval prerequisites
- [`legal-document-register.md`](legal-document-register.md): Document ownership and status
- [`global-legal-readiness.md`](global-legal-readiness.md): Country activation overlays
- [`data-inventory.yaml`](data-inventory.yaml): Current processing evidence
- [`openspec/specs/legal-pages/spec.yaml`](../../openspec/specs/legal-pages/spec.yaml): Canonical
  legal-page requirements
