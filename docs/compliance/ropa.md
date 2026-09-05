# Record of Processing Activities

> **Classification:** Internal — Legal and Compliance
> **Status:** Draft — not an approved GDPR Article 30 record
> **Version:** 2.0
> **Last verified:** 2026-07-17

## Overview

This draft Record of Processing Activities (ROPA) is derived from
[`data-inventory.yaml`](data-inventory.yaml). It records known processing and exposes missing
evidence; it does not assert compliance or approve production launch.

The ROPA cannot become active until the controller/processor legal person, establishment,
representative and contact details are complete, production providers and transfers are selected,
lawful bases are approved, and missing retention and rights controls are implemented.

### Organisation record

| Field                                | Current record                                                   |
|--------------------------------------|------------------------------------------------------------------|
| Legal person                         | **Unresolved — publication blocker**                             |
| Brand/product                        | Profile Tailors; Dallay appears as a project or brand identifier |
| Establishment and registered address | **Unresolved**                                                   |
| Controller contact                   | **Unresolved**                                                   |
| DPO                                  | Not assessed; no appointment evidence                            |
| EU/EEA representative                | Not assessed; depends on establishment and territorial scope     |
| UK or other local representatives    | Not assessed; depends on enabled markets                         |
| Security contact                     | **Unresolved and unverified**                                    |
| ROPA owner                           | **Unassigned**                                                   |

## Changes

| Version | Date       | Description                                                                              |
|---------|------------|------------------------------------------------------------------------------------------|
| 1.0     | 2026-07-17 | Initial ROPA containing assumed entity, providers, regions, bases, and retention         |
| 2.0     | 2026-07-17 | Revalidated against repository evidence; added browser storage and explicit control gaps |

## Usage

This ROPA is used for Article 30 readiness, DPIA screening, data-subject requests, transfer
mapping, incident response, provider reviews, and policy drafting. It must be read together with
the evidence-state fields in the YAML inventory.

The words **proposed**, **configurable**, **conditional**, **unknown**, and **missing** are
operative controls. They MUST NOT be removed from a public derivative without the evidence and
approvals required by [`legal-publication-gate.md`](legal-publication-gate.md).

### Processing record

#### PA-001 — Accounts, authentication, and sessions

| Article 30 field  | Record                                                                                                               |
|-------------------|----------------------------------------------------------------------------------------------------------------------|
| Role and purpose  | Proposed controller; registration, login, email verification, JWT access, and refresh sessions                       |
| Data subjects     | Registered users                                                                                                     |
| Data categories   | Email, username, display identity, identifiers, BCrypt password/refresh verifiers, verification and session metadata |
| Recipients        | PostgreSQL host not selected; Resend conditional and not contractually evidenced                                     |
| Transfers         | Unknown until database/email providers and regions are selected                                                      |
| Proposed basis    | Contract necessity; pending entity, audience, and counsel approval                                                   |
| Retention         | Access token defaults to 15 minutes and refresh session/cookie to seven days; account-erasure period missing         |
| Security evidence | BCrypt; configurable JWT; HttpOnly/Secure-by-default refresh cookie; session revocation                              |
| Required action   | Implement account deletion, expired-record cleanup, rights workflow, and provider contracts                          |

#### PA-002 — Social publishing and scheduling

| Article 30 field  | Record                                                                                                         |
|-------------------|----------------------------------------------------------------------------------------------------------------|
| Role and purpose  | Proposed processor; execute customer-directed composition, scheduling, and publication                         |
| Data subjects     | Workspace authors, social-account owners, and people represented in customer content                           |
| Data categories   | LinkedIn profile/account data, publication content, media, author, schedule, jobs, attempts, responses, errors |
| Recipients        | LinkedIn configurable; no other production social backend evidenced                                            |
| Transfers         | LinkedIn entity, processing locations, and transfer route require verification                                 |
| Instruction       | Customer DPA and documented publishing instruction required                                                    |
| Retention         | Unpublished hard deletion exists; no general post-termination or time-based cleanup                            |
| Security evidence | Workspace authorization, AES-GCM OAuth credentials, worker disabled by default                                 |
| Required action   | Approve DPA/platform terms, implement customer deletion/return schedule, verify locations                      |

#### PA-003 — Hosting and application delivery

| Article 30 field  | Record                                                                                        |
|-------------------|-----------------------------------------------------------------------------------------------|
| Role and purpose  | Proposed controller; deliver marketing, dashboard, API, and assets                            |
| Data subjects     | Visitors, users, and API consumers                                                            |
| Data categories   | Network/request data only as captured by the future production host, proxy, or CDN            |
| Recipients        | Production host/CDN not selected                                                              |
| Transfers         | Unknown                                                                                       |
| Proposed basis    | Contract for users; separate pre-contract/legitimate-interest analysis for visitors           |
| Retention         | Unknown; no production access-log setting or deletion control evidenced                       |
| Security evidence | Configurable CORS and credential validation; provider WAF/TLS claims are not evidenced        |
| Required action   | Select architecture/provider/regions and record logging, DPA, transfer, and deletion settings |

#### PA-004 — Waitlist and optional marketing

| Article 30 field  | Record                                                                                                                                                                                                           |
|-------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Role and purpose  | Proposed controller; early-access registration and separate optional marketing                                                                                                                                   |
| Data subjects     | Prospective users                                                                                                                                                                                                |
| Data categories   | Email, two consent flags, consent version, locale, source, form, metadata, lifecycle timestamps                                                                                                                  |
| Recipients        | PostgreSQL host not selected                                                                                                                                                                                     |
| Transfers         | Unknown                                                                                                                                                                                                          |
| Proposed basis    | Consent; wording and validity require market review                                                                                                                                                              |
| Retention         | Missing; no withdrawal endpoint, anonymisation job, or 30-day control                                                                                                                                            |
| Security evidence | Rate limiting; versioned and separated consent fields; collection form gated behind WAITLIST_ENABLED=false default                                                                                               |
| Activation state  | Public marketing collection is disabled by default (WAITLIST_ENABLED=false). The EarlyAccessStatus static message is shown until WAITLIST_ENABLED=true is explicitly configured.                                 |
| Required action   | Approve notice and market basis, then implement submission, withdrawal, suppression, anonymisation/deletion, consent evidence, and request handling before activation — and only then flip WAITLIST_ENABLED=true |

#### PA-005 — Workspaces and membership

| Article 30 field  | Record                                                                                 |
|-------------------|----------------------------------------------------------------------------------------|
| Role and purpose  | Proposed controller; collaboration, ownership, roles, and access                       |
| Data subjects     | Workspace owners and members                                                           |
| Data categories   | Workspace metadata, principal relationships, membership status, roles, and permissions |
| Recipients        | PostgreSQL host not selected                                                           |
| Transfers         | Unknown                                                                                |
| Proposed basis    | Contract necessity; customer model pending                                             |
| Retention         | Missing; ownership relationship operations do not prove workspace/member erasure       |
| Security evidence | Active membership and permission checks; workspace isolation                           |
| Required action   | Implement workspace closure, member erasure/anonymisation, legal holds, and export     |

#### PA-006 — LinkedIn connection and OAuth credentials

| Article 30 field  | Record                                                                                                 |
|-------------------|--------------------------------------------------------------------------------------------------------|
| Role and purpose  | Proposed processor for customer-directed social connection; controller security operations may coexist |
| Data subjects     | Workspace members connecting LinkedIn accounts                                                         |
| Data categories   | Profile/account identifiers, tokens, scopes, expiries, encrypted payloads, OAuth state                 |
| Recipients        | LinkedIn configurable                                                                                  |
| Transfers         | Provider entity, processing locations, and transfer mechanism unknown                                  |
| Instruction       | Customer connection instruction and DPA required                                                       |
| Retention         | Missing; no automatic credential erasure after revocation or termination evidenced                     |
| Security evidence | AES-GCM encryption, production key validation, signed/time-limited OAuth state                         |
| Required action   | Implement revocation erasure and metadata cleanup; approve platform and transfer terms                 |

#### PA-007 — API and service credentials

| Article 30 field  | Record                                                                                           |
|-------------------|--------------------------------------------------------------------------------------------------|
| Role and purpose  | Proposed controller; issue, rotate, validate, and revoke service access                          |
| Data subjects     | Credential-associated human or service principals                                                |
| Data categories   | Lookup hash, BCrypt verifier, principal, provider, status, timestamps, replacement relationships |
| Recipients        | PostgreSQL host not selected                                                                     |
| Transfers         | Unknown                                                                                          |
| Proposed basis    | Contract and service security; legal review pending                                              |
| Retention         | Missing; revocation exists but no 90-day or other purge control                                  |
| Security evidence | BCrypt verifier and active-status authentication checks                                          |
| Required action   | Define and implement post-revocation deletion criterion and audit evidence                       |

#### PA-008 — Media storage and external media import

| Article 30 field  | Record                                                                                                        |
|-------------------|---------------------------------------------------------------------------------------------------------------|
| Role and purpose  | Proposed processor for customer media; proposed controller for chosen Unsplash integration telemetry          |
| Data subjects     | Workspace users and people represented in media                                                               |
| Data categories   | Files, filenames, types, sizes, hashes, storage keys, upload state, external IDs/authors/URLs and searches    |
| Recipients        | Object-storage provider not selected; Unsplash conditional and disabled by default                            |
| Transfers         | Unknown until providers and regions are selected                                                              |
| Instruction/basis | Customer DPA/instruction for uploads; contract/necessity review for external search                           |
| Retention         | Verified seven-day grace before physical object deletion; GC row remains; five failures require manual action |
| Security evidence | Workspace controls, HMAC-signed preview links, SHA-256, scheduled GC                                          |
| Required action   | Select storage provider/region; document database-row retention and manual GC incident process                |

#### PA-009 — Publication delivery operations

| Article 30 field  | Record                                                                                          |
|-------------------|-------------------------------------------------------------------------------------------------|
| Role and purpose  | Proposed processor; queue, execute, retry, and record customer publishing                       |
| Data subjects     | Workspace authors and people represented in content                                             |
| Data categories   | Publication/author IDs, jobs, worker leases, attempts, provider responses/errors, notifications |
| Recipients        | LinkedIn configurable                                                                           |
| Transfers         | Unknown until LinkedIn relationship is approved                                                 |
| Instruction       | Customer DPA and publishing instruction required                                                |
| Retention         | Relationship deletion for unpublished content exists; no seven/90/180-day time cleanup          |
| Security evidence | Workspace isolation, attribution, leases and bounded retry configuration                        |
| Required action   | Implement completion/termination retention and provider-response minimisation/scrubbing         |

#### PA-010 — Audit and governance events

| Article 30 field  | Record                                                                                                |
|-------------------|-------------------------------------------------------------------------------------------------------|
| Role and purpose  | Proposed controller; authorization evidence, security monitoring, and incident investigation          |
| Data subjects     | Users and service principals generating events                                                        |
| Data categories   | Principal/workspace, request, permission, role, decision, reason, target, details, timestamp          |
| Recipients        | PostgreSQL host not selected                                                                          |
| Transfers         | Unknown                                                                                               |
| Proposed basis    | Legitimate-interest/security assessment pending; GDPR Arts. 5(2) and 24 are not standalone bases      |
| Retention         | Missing; no one/five-year archive/delete control or legal-hold workflow                               |
| Security evidence | Insert/read repositories; hooks configurable and disabled by default; database immutability unproved  |
| Required action   | Approve collection scope/basis, enable required coverage, minimise details, implement retention/holds |

#### PA-011 — Analytics, metrics, logs, and errors

| Article 30 field  | Record                                                                                                     |
|-------------------|------------------------------------------------------------------------------------------------------------|
| Role and purpose  | Proposed controller; operations/security metrics and optional marketing analytics                          |
| Data subjects     | Visitors, users, and API consumers                                                                         |
| Data categories   | Metrics/tags, emitted identifiers/log context, and conditional Ahrefs request/usage data                   |
| Recipients        | Ahrefs conditional; logs/metrics provider not selected; no Sentry/Vercel Analytics integration evidenced   |
| Transfers         | Unknown                                                                                                    |
| Proposed basis    | Operational legitimate interest and market-dependent analytics consent; assessments pending                |
| Retention         | Missing; no 30-day/90-day/13-month controls evidenced                                                      |
| Security evidence | Authorization for detailed Actuator information; configurable management exposure                          |
| Required action   | Select sinks/regions, minimise and scrub, contract providers, implement retention, verify Ahrefs behaviour |

#### PA-012 — Cookies and local device storage

| Article 30 field  | Record                                                                                                                |
|-------------------|-----------------------------------------------------------------------------------------------------------------------|
| Role and purpose  | Proposed controller; authentication, preferences, workspace selection, and local dashboard state                      |
| Data subjects     | Visitors and dashboard users                                                                                          |
| Data categories   | `pt_refresh`, `sidebar_state`, theme/settings, workspace ID/name, locally persisted publications, feature preference  |
| Recipients        | User browser/device; server receives `pt_refresh`                                                                     |
| Transfers         | Follows server/hosting transfer for refresh sessions; other values remain on device unless used in requests           |
| Proposed basis    | Item-specific strict-necessity/contract assessment; non-essential items require applicable choice/basis               |
| Retention         | Refresh and sidebar cookies default to seven days; local storage has no automatic expiry                              |
| Security evidence | Refresh cookie HttpOnly and Secure by default; access token kept in memory                                            |
| Required action   | Classify every item, remove development persistence from production, clear user/workspace/content state appropriately |

### Article 30 completion gaps

| Required information or control                                         | State                                                          |
|-------------------------------------------------------------------------|----------------------------------------------------------------|
| Controller/processor legal name and contacts                            | Missing                                                        |
| Joint-controller assessment                                             | Not completed                                                  |
| DPO and representative assessment                                       | Not completed                                                  |
| Approved purpose and lawful basis per controller activity               | Pending legal review                                           |
| Selected production recipients, countries and safeguards                | Missing                                                        |
| Processor Article 28 contracts                                          | Missing or not evidenced                                       |
| Approved retention criteria and implemented deletion evidence           | Missing except limited media/session/request deletion controls |
| General description of production technical and organisational measures | Partial; provider and runtime evidence missing                 |
| Data-subject request workflow                                           | Not evidenced as complete                                      |
| DPIA screening and transfer assessments                                 | Not evidenced                                                  |
| ROPA owner, approval and review history                                 | Missing                                                        |

### Activation rule

Change this document from **Draft** to **Active** only when:

1. Every organisation field is complete.
2. Every production recipient is **Selected** in the processor matrix.
3. Each controller purpose has approved applicability and lawful-basis evidence.
4. Each processor purpose has an approved customer DPA and instruction model.
5. Retention controls and rights operations have linked tests or operational evidence.
6. Transfers, DPIAs, security measures, representatives, and market addenda are complete.
7. A named owner and qualified reviewer approve the immutable version.

## Troubleshooting

- **The product is not launched:** keep the ROPA in Draft, but document real test/staging personal
  data and production decisions before processing begins.
- **A provider is known operationally but absent here:** repository evidence alone is insufficient;
  add its controlled contract, region, data-flow, subprocessor, deletion, and transfer evidence.
- **A retention period has no job:** mark it missing. A policy or table is not a control.
- **The same feature has controller and processor purposes:** record them separately; do not use
  “controller instruction” as Profile Tailors' lawful basis for its own controller processing.
- **The YAML changes:** update this ROPA and processor matrix in the same pull request.

## References

- [`data-inventory.yaml`](data-inventory.yaml)
- [`data-inventory.md`](data-inventory.md)
- [`controller-processor-matrix.md`](controller-processor-matrix.md)
- [`legal-publication-gate.md`](legal-publication-gate.md)
- [EUR-Lex — GDPR Article 30](https://eur-lex.europa.eu/eli/reg/2016/679/art_30/oj)
- [European Commission — controller and processor obligations](https://commission.europa.eu/law/law-topic/data-protection/information-business-and-organisations/obligations_en)
