# Data Inventory

> **Classification:** Internal — Compliance
> **Status:** Active
> **Schema version:** 1.1

## Overview

This document records all processing activities performed by Profile Tailors (Dallay) as required by GDPR Art. 30. The source of truth is [`data-inventory.yaml`](data-inventory.yaml); this Markdown is derived from it and MUST be kept in sync.

**Processing entity:** Dallay (Profile Tailors)
**DPO contact:** Not appointed

## Changes

| Version | Date | Description |
|---------|------|-------------|
| 1.0 | 2026-07-17 | Initial data inventory with 4 processing activities |
| 1.1 | 2026-07-17 | Expanded to 11 processing activities covering all bounded contexts |

## Usage

This data inventory serves as:

- The **Record of Processing Activities** (Art. 30) for regulatory compliance.
- Input for **Data Protection Impact Assessments (DPIAs)** for high-risk activities.
- Evidence for **Article 5 accountability** obligations.
- Reference for **breach notification** assessments under Art. 33-34.
- Input for **Privacy Policy, Terms, and Cookie Policy** drafting (see DALLAY-488).

## Processing Activities

### pa-001: User account registration and management

| Field | Value |
|-------|-------|
| **Purpose** | Create and manage user accounts, authentication, and session management |
| **Role** | Controller |
| **Legal basis** | Contract — GDPR Art. 6(1)(b) |
| **Personal data** | Email, username, password hash (bcrypt), principal identifier, display identity |
| **Data subjects** | Registered users |
| **Recipients** | Internal (EEA); Database hosting provider |
| **Retention** | Delete 30 days after account deletion (statutory exceptions apply) |
| **Evidence** | `server/smp/.../identity/` domain models and schemas |

### pa-002: Social media publishing and scheduling

| Field | Value |
|-------|-------|
| **Purpose** | Schedule, compose, and publish content to connected social media accounts |
| **Role** | Processor |
| **Legal basis** | Controller instruction |
| **Personal data** | Social media account identifiers, published content, delivery records |
| **Data subjects** | End recipients; social media account owners |
| **Recipients** | LinkedIn (US), Twitter/X (US), Facebook (US), Instagram (US), TikTok (US) |
| **Retention** | Archive 90 days after account deletion (delivery attempt logs retained 180 days) |
| **Evidence** | `server/smp/.../publishing/` domain models and schemas |

### pa-003: Web application hosting and delivery

| Field | Value |
|-------|-------|
| **Purpose** | Host and deliver the Profile Tailors web application and API |
| **Role** | Controller |
| **Legal basis** | Contract — GDPR Art. 6(1)(b) |
| **Personal data** | IP address, browser user agent, usage analytics, request metadata |
| **Data subjects** | Website visitors and application users |
| **Recipients** | Vercel Inc. (US) |
| **Retention** | 90 days (access logs), 26 months (aggregated analytics) |
| **Evidence** | `apps/web/marketing/astro.config.ts`, `apps/web/app/vite.config.ts` |

### pa-004: Lead capture and waitlist management

| Field | Value |
|-------|-------|
| **Purpose** | Manage early-access waitlist signups and marketing communications |
| **Role** | Controller |
| **Legal basis** | Consent — GDPR Art. 6(1)(a) |
| **Personal data** | Email, marketing preferences, consent status & version, locale, metadata |
| **Data subjects** | Waitlist signups and prospective users |
| **Recipients** | Internal processing only (EEA) |
| **Retention** | Anonymize 30 days after consent withdrawal |
| **Evidence** | `shared/lead-capture/waitlist/`, `server/smp/.../lead-capture/` |

### pa-005: Workspace and membership management

| Field | Value |
|-------|-------|
| **Purpose** | Manage team workspaces, memberships, roles, and ownership |
| **Role** | Controller |
| **Legal basis** | Contract — GDPR Art. 6(1)(b) |
| **Personal data** | Membership records, ownership records, role assignments, workspace metadata |
| **Data subjects** | Workspace members and owners |
| **Recipients** | Internal processing only (EEA) |
| **Retention** | 30 days after removal (audit trail exceptions apply) |
| **Evidence** | `server/smp/.../tenancy/` domain models and schemas |

### pa-006: OAuth authentication and social account connections

| Field | Value |
|-------|-------|
| **Purpose** | Authenticate users and connect social media accounts for publishing |
| **Role** | Controller (auth) / Processor (social connections) |
| **Legal basis** | Contract — GDPR Art. 6(1)(b) |
| **Personal data** | OAuth subject identifier, provider connection ref, encrypted tokens |
| **Data subjects** | Registered users connecting social accounts |
| **Recipients** | Auth0/Clerk (US, independent controller); Social platforms (US) |
| **Retention** | Encrypted tokens deleted immediately on revocation; metadata 30 days |
| **Evidence** | `server/smp/.../publishing/001-create-social-connections.yaml`, `server/smp/.../publishing/008-create-secure-credentials.yaml` |

### pa-007: API key and service credential management

| Field | Value |
|-------|-------|
| **Purpose** | Issue and manage API keys for programmatic and service-to-service access |
| **Role** | Controller |
| **Legal basis** | Contract — GDPR Art. 6(1)(b) |
| **Personal data** | API key lookup hash, secret verifier, principal association |
| **Data subjects** | API key holders (technical users) |
| **Recipients** | Internal processing only (EEA) |
| **Retention** | 90 days after revocation (rotation audit trail) |
| **Evidence** | `server/smp/.../credentials/002-create-api-key-credentials.yaml` |

### pa-008: Media asset storage and management

| Field | Value |
|-------|-------|
| **Purpose** | Upload, store, and serve media assets for social media content |
| **Role** | Processor |
| **Legal basis** | Controller instruction |
| **Personal data** | Original filenames, media content (may contain personal data), upload metadata |
| **Data subjects** | End users; workspace members |
| **Recipients** | Cloudflare R2 / AWS S3 (processor, EEA/US); Social platforms (independent controller) |
| **Retention** | 7 days after deletion (GC grace period), then permanent erasure |
| **Evidence** | `server/smp/.../media/` domain models and schemas |

### pa-009: Content publishing and delivery operations

| Field | Value |
|-------|-------|
| **Purpose** | Execute scheduled content publishing and manage delivery lifecycle |
| **Role** | Processor |
| **Legal basis** | Controller instruction |
| **Personal data** | Publication content, author identifier, job metadata, delivery error logs |
| **Data subjects** | Workspace members; social media audience |
| **Recipients** | Social media platforms (independent controller, US) |
| **Retention** | 90 days (delivery logs), 7 days (jobs) |
| **Evidence** | `server/smp/.../publishing/004-create-publications.yaml`, `server/smp/.../publishing/006-create-publication-jobs.yaml` |

### pa-010: Audit and governance logging

| Field | Value |
|-------|-------|
| **Purpose** | Record security-relevant events for compliance and incident response |
| **Role** | Controller |
| **Legal basis** | Legal obligation — GDPR Art. 5(2) (accountability), Art. 24 |
| **Personal data** | Actor principal ID, workspace ID, request details, role info, event details |
| **Data subjects** | All users whose actions generate audit events |
| **Recipients** | Internal processing only (EEA) |
| **Retention** | 1 year (standard), 5 years (security incidents) |
| **Evidence** | `server/smp/.../governance/001-create-audit-events.yaml`, `R2dbcAuditEventReader.kt` |

### pa-011: Analytics, observability, and error monitoring

| Field | Value |
|-------|-------|
| **Purpose** | Monitor application performance, track errors, and analyze product usage |
| **Role** | Controller |
| **Legal basis** | Legitimate interest — GDPR Art. 6(1)(f) |
| **Personal data** | IP address, request metadata, error stack traces (minimized by design) |
| **Data subjects** | Application users, API consumers |
| **Recipients** | Vercel Analytics (US), Sentry (US, planned), Grafana/Prometheus (EEA) |
| **Retention** | 30 days (detailed logs), 13 months (aggregated metrics), 90 days (errors) |
| **Evidence** | `docs/monitoring/prometheus-grafana-setup.md`, `docs/monitoring/actuator-security.md` |

## Troubleshooting

- **YAML and Markdown out of sync:** Update `data-inventory.yaml` first, then regenerate this Markdown.
- **Missing evidence references:** Each processing activity should link to the source code or configuration that implements the associated control.
- **New processing activities:** When adding new features that process personal data, create a new entry in `data-inventory.yaml` and update this document.

## References

- GDPR Art. 30: Records of processing activities
- [`data-inventory.yaml`](data-inventory.yaml): Machine-readable source of truth
- [`controller-processor-matrix.md`](controller-processor-matrix.md): Third-party processor map
- [`ropa.md`](ropa.md): GDPR Art. 30 formal record
- [`incident-response-runbook.md`](incident-response-runbook.md): Breach response process
