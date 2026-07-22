# Privacy Rights Request Runbook

> **Classification:** Internal — Privacy Operations
> **Status:** Design contract — intake channels and workflows not implemented
> **Publication impact:** Public rights instructions remain blocked

## Overview

Define the end-to-end handling of requests to access, know, correct, delete, restrict, object or opt
out, withdraw consent, obtain portability, appeal a decision, identify recipients or sources, and
exercise other privacy rights recognised by an enabled jurisdiction.

Rights vary by country, subdivision, person, data, purpose, legal basis, controller or processor
role, and applicable exception. The operator must select an approved country overlay, record the
exact deadline and clock trigger, and avoid promising a universal catalogue of rights.

### Control Principles

- Provide accessible intake without requiring creation of a new account.
- Confirm controller or processor role before responding substantively.
- Verify identity proportionately without collecting unnecessary new data.
- Protect other people, customer confidential data, security evidence, and legally privileged
  material.
- Search every verified system, processor, browser-controlled area, backup class, and manual record
  in scope.
- Separate fulfilment, denial, partial fulfilment, and no-data-found outcomes.
- Explain applicable exceptions and appeal or complaint routes in clear language.
- Preserve an auditable decision record without retaining the request payload indefinitely by
  default.
- Never retaliate or discriminate where applicable law prohibits it.
- Do not erase evidence subject to an authorised legal, security, fraud, or litigation hold.

## Changes

| Version | Date       | Description                                                                                    |
|---------|------------|------------------------------------------------------------------------------------------------|
| 1.0     | 2026-07-17 | Added global intake, verification, search, decision, fulfilment, appeal, and evidence controls |

## Usage

### 1. Intake and Clock

Create a case immediately when a request arrives through an approved web form, email, support route,
postal address, authorised agent, customer, authority, or another legally valid channel.

Record:

- Case identifier and immutable intake timestamp in UTC
- Original request and channel
- Requesting person, claimed relationship, country and subdivision
- Requested right or plain-language desired outcome
- Account, principal, workspace, organisation, waitlist, social connection, or other correlation
  data supplied
- Accessibility, language, authorised-agent, guardian, or representative needs
- Suspected fraud, coercion, account compromise, safety, or litigation risk
- Applicable legal entity and controller/processor role
- Approved country overlay, deadline, clock trigger, extension rules, acknowledgement requirement,
  and owner

If jurisdiction or role is uncertain, preserve the shortest plausible operational clock and
escalate. Do not wait for perfect classification before acknowledging receipt where acknowledgement
is required.

### 2. Role and Scope Triage

Use the [data inventory](data-inventory.yaml), ROPA, customer contract, and processor matrix to
determine:

- Whether Profile Tailors is controller, processor, independent controller, or not the relevant
  organisation
- Which customer or controller must be involved
- Which purposes, data subjects, systems, providers, and countries are in scope
- Whether the request concerns personal data, customer content about another person, business
  information, or mixed records
- Whether a specialised consumer, employment, health, financial, child, communications, platform, or
  sector regime applies

When acting as a processor, follow documented customer instructions and the DPA while preserving any
direct legal duty. Do not disclose customer-controlled data directly without confirming authority
and applicable law.

### 3. Identity and Authority Verification

Choose the least intrusive reliable method based on the right, data sensitivity, account state, harm
from wrongful disclosure or deletion, and available authenticated context.

Permitted design patterns include:

- Reauthentication and confirmation through an existing verified channel
- Proof of control of the relevant email or social account
- Signed workspace-owner or authorised-agent evidence where legally valid
- Limited matching against information already held
- Escalated review for high-risk deletion, portability, sensitive data, or account compromise

Do not request a government identifier merely by default, store raw identity documents without an
approved need, reveal whether an unrelated person has an account, or use unverifiable knowledge
questions. Record the method, result, reviewer, and any data created solely for verification.

### 4. Locate and Preserve In-Scope Data

Create a scoped search plan from the processing activities:

| Area                        | Current evidence target                                                                                                   |
|-----------------------------|---------------------------------------------------------------------------------------------------------------------------|
| Identity and authentication | Accounts, principal identities, credentials metadata, verification tokens, refresh sessions                               |
| Tenancy and authorisation   | Workspaces, ownership, memberships, roles, permissions                                                                    |
| Lead capture                | Waitlist record, early-access and marketing choices, source, locale, status timestamps                                    |
| Social connections          | LinkedIn connection, account, encrypted credential metadata, OAuth state where retained                                   |
| Publishing                  | Drafts, scheduled content, jobs, delivery attempts, notifications, provider references                                    |
| Media                       | Asset metadata, storage objects, import sources, references, garbage-collection state                                     |
| API access                  | Key identifiers, prefixes, scopes, verifier metadata, status and timestamps                                               |
| Audit and security          | Audit events, authentication and operational evidence, incident or abuse holds                                            |
| Communications              | Conditional transactional-email events and provider records when activated                                                |
| Analytics and delivery      | Conditional analytics and selected host, proxy, database, storage, or monitoring records                                  |
| Client device               | Cookies and local storage controlled by the person's browser; provide deletion guidance where the server cannot access it |

Record each searched system, query or method, time range, operator, result, limitation, processor
request, and evidence location. Apply a scoped preservation hold before fulfilment if routine
deletion could destroy responsive records.

### 5. Legal and Security Review

For every responsive category, decide whether it is:

- Fulfilled as requested
- Fulfilled in part with redaction or transformation
- Not held or no longer identifiable
- Controlled by a customer or another organisation
- Exempt, restricted, or delayed under an approved rule
- Subject to another person's rights or confidential information
- Necessary for security, fraud prevention, legal claims, compliance, or an authorised hold

The decision record identifies the approved legal basis, facts, reviewer, scope, and appeal or
complaint path. Avoid generic statements such as “security reasons” without a documented explanation
that can lawfully be given.

### 6. Fulfil Safely

- Access or know: provide intelligible data and required contextual information, not raw internal
  database dumps by default.
- Correction: update authoritative records and propagate corrections to processors or recipients
  where required and feasible.
- Deletion: execute the approved erasure plan, revoke credentials and sessions, break references
  safely, notify relevant processors, and record residual exceptions.
- Restriction or objection: stop or isolate the applicable processing without disabling unrelated
  lawful purposes.
- Portability: use a structured, commonly usable, secure format for eligible data and an
  authenticated delivery channel.
- Consent withdrawal or opt-out: update the specific purpose and suppression state without treating
  it as account termination unless necessary and disclosed.
- Appeal: route to a reviewer independent of the original decision where required.

Before delivery, verify recipient, scope, redactions, malware safety, archive encryption, expiry,
download limits, and out-of-band secret delivery. Record delivery and access evidence.

### 7. Close and Learn

Close only after:

- Every system and processor has responded or a documented limitation is approved
- Fulfilment, denial, extension, appeal, or complaint communication is delivered
- Deletion, correction, restriction, opt-out, or export controls are verified
- Residual records and holds have an owner and review date
- Case evidence is minimised and assigned an approved retention rule
- Repeated product or data-map gaps become tracked remediation work

### Minimum Test Scenarios

- Authenticated access request returns only the correct principal and workspace scope.
- Unauthenticated and authorised-agent flows verify authority without excessive collection.
- A processor-role request routes to the correct customer without silently closing the case.
- Deletion revokes sessions, credentials, and API keys and handles linked publishing/media records
  safely.
- Marketing withdrawal does not remove early-access status unless requested and legally required.
- Export excludes password hashes, token verifiers, encryption material, other members' data, and
  internal secrets.
- A legal hold preserves only scoped records and prevents a false deletion-complete message.
- Browser-only storage is explained without claiming server-side deletion.
- Deadline, extension, appeal, and delivery evidence are reproducible.
- A request spanning multiple jurisdictions applies the approved combined decision and shortest
  applicable deadline.

## Troubleshooting

- **The requester cannot access the account:** Use an approved recovery and alternative-verification
  path; do not require login as the only method.
- **The email address matches multiple contexts:** Verify each account, workspace, waitlist,
  customer, and social-connection relationship separately.
- **A customer controls the content:** Preserve the request and notify the customer under the DPA;
  do not assume Profile Tailors may disclose or erase it directly.
- **Deletion would break financial, security, or legal evidence:** Apply the approved exception,
  minimise and restrict the residual record, schedule review, and explain what can lawfully be
  explained.
- **A processor misses the internal deadline:** Escalate contractually; the external deadline does
  not pause.
- **The person requests all information:** Clarify only when necessary; do not use clarification to
  reset a clock unless the applicable overlay permits it.
- **The jurisdiction is uncertain:** Use the strictest plausible operational target and obtain
  qualified review.
- **No data is found:** Record the searches and send an approved no-data outcome without confirming
  unrelated accounts.

## References

- [`data-inventory.yaml`](data-inventory.yaml): Systems, data categories, providers, and retention
  evidence
- [`ropa.md`](ropa.md): Controller and processor processing records
- [`controller-processor-matrix.md`](controller-processor-matrix.md): Customer and provider roles
- [`global-legal-readiness.md`](global-legal-readiness.md): Country overlay requirements
- [`legal-publication-gate.md`](legal-publication-gate.md): Public rights statement approval
- [`legal-acceptance-record.md`](legal-acceptance-record.md): Consent and contract evidence
  distinctions
