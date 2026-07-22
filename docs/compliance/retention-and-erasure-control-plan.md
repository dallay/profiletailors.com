# Retention and Erasure Control Plan

> **Classification:** Internal — Privacy, Security, and Data Operations
> **Status:** Remediation plan — production retention publication blocked
> **Source of truth:** `docs/compliance/data-inventory.yaml`

## Overview

Turn retention criteria into implemented, testable controls across primary databases, object
storage, caches, queues, search indexes, logs, analytics, browser storage, exports, processors,
backups, and manual operational records.

No target in this document is a public promise or approved legal period. A duration becomes
publishable only after business need, legal minimum and maximum, limitation periods, security, tax,
consumer, employment, platform, country, backup, hold, and customer-contract requirements are
approved and the complete deletion or anonymisation path is verified.

### Required Control States

| State           | Meaning                                                                                                                |
|-----------------|------------------------------------------------------------------------------------------------------------------------|
| Not implemented | No complete automatic or operator control was found.                                                                   |
| Partial         | Some expiry, revocation, hard deletion, or physical-object handling exists but the end-to-end record is incomplete.    |
| Implemented     | Trigger, scope, action, processors, backups, holds, evidence, monitoring, failure handling, and tests are operational. |
| Approved        | Implemented control plus business, security, privacy, and qualified legal approval for exact markets and data.         |

## Changes

| Version | Date       | Description                                                                                                      |
|---------|------------|------------------------------------------------------------------------------------------------------------------|
| 1.0     | 2026-07-17 | Added evidence-based control gaps, design requirements, and verification scenarios for all processing activities |

## Usage

### Current Control Register

| Activity                                        | Current state   | Verified capability                                                                                           | Missing control before approval                                                                                                                               |
|-------------------------------------------------|-----------------|---------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| pa-001 Accounts, authentication, and sessions   | Partial         | Refresh sessions expire and can be revoked; logout clears the refresh cookie                                  | Account closure, identity erasure or minimisation, expired verification cleanup, federated identity handling, legal holds, processors, backups, and proof     |
| pa-002 Publishing and scheduling                | Partial         | Unpublished publication deletion removes related jobs, asset links, attempts, and the publication             | Published/failed/cancelled schedules, customer termination, social-platform propagation, notification/audit retention, holds, backups, and processor proof    |
| pa-003 Hosting and delivery                     | Not implemented | No production provider is selected                                                                            | Select provider, configure logs and CDN/security data, approve period, deletion/export, incident holds, subprocessor flow-down, and evidence                  |
| pa-004 Waitlist                                 | Not implemented | Status and cancellation fields exist                                                                          | Withdrawal endpoint, marketing suppression, anonymisation or deletion, conversion rules, campaign processors, proof separation, and tests                     |
| pa-005 Workspaces and memberships               | Not implemented | Some relationship deletion exists for ownership-transfer workflows                                            | Workspace closure, member removal effects, orphan prevention, customer export, content allocation, audit residuals, holds, and proof                          |
| pa-006 Social connections and OAuth credentials | Not implemented | Credentials are encrypted and contain expiry metadata                                                         | Disconnect flow, token revocation, credential destruction, expired-state cleanup, provider propagation, customer termination, key rotation history, and proof |
| pa-007 API keys                                 | Not implemented | Secret verifiers are hashed and keys have status metadata                                                     | User revocation, expiry, account/workspace deletion, last-used policy, audit linkage, compromised-key emergency purge, and proof                              |
| pa-008 Media and imports                        | Partial         | Eligible physical objects can be garbage collected after a seven-day eligibility period; failures are tracked | Approval of trigger, database-row lifecycle, external-source data, customer deletion, processor copies, backups, manual-failure closure, holds, and proof     |
| pa-009 Delivery attempts and provider responses | Not implemented | Attempts are persisted for publishing operations                                                              | Approved diagnostic period, payload minimisation, error redaction, customer access, aggregation/anonymisation, purge, holds, and proof                        |
| pa-010 Audit and security evidence              | Not implemented | Audit events and operational records exist                                                                    | Purpose-specific classes, tamper protection, access, approved period, incident/legal holds, minimisation, archival, deletion, and proof                       |
| pa-011 Analytics, metrics, and logs             | Not implemented | Ahrefs is conditional; application metrics exist                                                              | Provider selection, field inventory, IP handling, sampling, approved period, opt-out or consent, deletion, aggregation, transfer, and proof                   |
| pa-012 Browser storage                          | Not implemented | Known cookie and local-storage keys are inventoried                                                           | Expiry by purpose, logout/account-deletion cleanup, shared-device safety, Secure/SameSite decision, preference UI, version migration, and tests               |

### Control Specification

Every retention control must define:

- Data class and processing-activity identifier
- Authoritative systems and every derived or replicated copy
- Creation event and approved retention purpose
- Start event, stop event, and clock source
- Fixed period or purpose-based criterion and country/customer overlays
- Delete, anonymise, aggregate, archive, revoke, detach, or return action
- Referential-integrity and multi-tenant isolation behaviour
- Processor and subprocessor instruction, confirmation, and exception handling
- Queue, cache, search index, export, analytics, and local-device handling
- Backup expiry, restore re-deletion, disaster-recovery, and snapshot behaviour
- Legal, security, fraud, incident, tax, dispute, and customer holds
- Hold authorisation, scope, review date, release, and non-overbroad enforcement
- Failure queue, retry, alert, owner, manual resolution, and maximum unresolved age
- Evidence event with counts, scope, result, failures, and non-sensitive samples
- Metrics, periodic reconciliation, access control, and independent test coverage

### Erasure Orchestration Order

An approved account or workspace erasure workflow should:

1. Authenticate and authorise the request or approved customer instruction.
2. Freeze conflicting writes and create an idempotent erasure case.
3. Identify controller versus customer-controlled data and applicable holds.
4. Export data first when required and requested.
5. Revoke sessions, API keys, OAuth credentials, tokens, and active jobs.
6. Stop optional communications, analytics, and other future processing.
7. Cancel or safely resolve scheduled publication and delivery work.
8. Delete, return, anonymise, or detach primary records in referentially safe order.
9. Remove physical media, caches, indexes, queues, exports, and processor copies.
10. Minimise approved residual audit, suppression, acceptance, security, and legal records.
11. Schedule backup expiry and re-deletion after any restore.
12. Reconcile expected and actual objects, record failures, and notify only after the approved
    completion threshold is met.

### Anonymisation Standard

Calling a record anonymised requires evidence that direct and indirect identifiers, free text,
external identifiers, rare combinations, linkage keys, metadata, and recoverable mappings cannot
reasonably re-identify a person in the approved threat model. Hashing an email, removing a name, or
encrypting a retained identifier is not anonymisation by itself.

Where anonymisation cannot meet the approved standard, classify the output as pseudonymous personal
data and retain the full applicable controls.

### Backup and Restore Control

- Inventory backup types, regions, providers, encryption, access, cadence, and maximum lifetime.
- Do not edit immutable backups merely to create a false deletion claim.
- Prevent routine access and processing of data awaiting backup expiry.
- Maintain a deletion ledger that can be replayed after restoration.
- Test that restored environments reapply erasure and suppression before serving traffic or
  analytics.
- Suspend expiry only through an authorised, scoped, reviewed hold.
- Disclose backup treatment accurately in final policies and customer terms.

### Minimum Test Evidence

- Re-running an erasure job is safe and does not cross workspace boundaries.
- Concurrent writes cannot recreate deleted records without an approved new basis.
- Sessions, API keys, OAuth credentials, jobs, and queued messages stop after the applicable
  trigger.
- Media database state and physical object storage reconcile, including failed deletions.
- Processor deletion requests and confirmations link to the case.
- Legal holds preserve only scoped records and release correctly.
- An export or cache cannot survive unnoticed after primary deletion.
- A restored backup reapplies erasure before becoming available.
- Browser storage is cleared where the application controls it and documented where only the user
  controls it.
- Completion evidence never exposes secrets or deleted personal data.
- Monitoring detects overdue, failed, or unexpectedly growing retention classes.

### Implementation Sequence

1. Assign legal entity, data owner, security owner, and operator for every activity.
2. Approve data classes and purpose-based criteria for a narrow first market.
3. Implement account, workspace, waitlist, credential, API-key, publishing, and media orchestration.
4. Configure selected provider, log, analytics, backup, and processor retention.
5. Add hold, restore re-deletion, failure reconciliation, evidence, and monitoring.
6. Run destructive tests in isolated environments with representative relational data.
7. Obtain product, technical, business, privacy, and qualified legal approval.
8. Generate public retention language only from approved controls.

## Troubleshooting

- **A law requires keeping one field:** Minimise and restrict that field and purpose; do not retain
  the entire account by default.
- **A customer requests immediate deletion while a job is running:** Stop or safely settle the job,
  preserve idempotency, and prevent downstream delivery before declaring completion.
- **A processor cannot delete immediately:** Confirm the contractual and technical exception,
  isolate the data, record the expiry, and do not promise immediate deletion publicly.
- **A backup is immutable:** Use approved expiry plus restore re-deletion and restricted access;
  describe the residual accurately.
- **An anonymised dataset can still be linked:** Reclassify it as personal data and apply retention,
  security, rights, and transfer controls.
- **Deletion breaks audit integrity:** Replace identifiers with approved pseudonymous or event
  references only where necessary, restrict access, and document the residual basis and period.
- **The correct period is disputed across countries:** Apply the approved data-and-market rule,
  segregate where necessary, and use the shortest compatible period rather than publishing an
  invented global number.
- **A control has no test evidence:** Keep it `Partial` or `Not implemented`; logs claiming a job
  ran are not proof of complete erasure.

## References

- [`data-inventory.yaml`](data-inventory.yaml): Canonical retention status and evidence per activity
- [`data-inventory.md`](data-inventory.md): Human-readable processing and storage register
- [`rights-request-runbook.md`](rights-request-runbook.md): Request-driven deletion and export flow
- [`consent-and-preference-register.md`](consent-and-preference-register.md): Withdrawal,
  suppression, and proof separation
- [`controller-processor-matrix.md`](controller-processor-matrix.md): Provider deletion obligations
- [`legal-publication-gate.md`](legal-publication-gate.md): Conditions for publishing retention
  claims
