# Data Inventory

> **Classification:** Internal — Compliance
> **Status:** Preliminary
> **Schema version:** 1.0

## Overview

This document records all processing activities performed by Profile Tailors (Dallay) as required by GDPR Art. 30. The source of truth is [`data-inventory.yaml`](data-inventory.yaml); this Markdown is derived from it and MUST be kept in sync.

**Processing entity:** Dallay
**DPO contact:** Not appointed (preliminary)

## Changes

| Version | Date | Description |
|---------|------|-------------|
| 1.0 | 2026-07-17 | Initial data inventory with 4 processing activities |

## Usage

This data inventory serves as:

- The record of processing activities (Art. 30) for regulatory compliance.
- Input for Data Protection Impact Assessments (DPIAs) for high-risk activities.
- Evidence for Article 5 accountability obligations.
- Reference for breach notification assessments under Art. 33-34.

### Processing Activities

#### pa-001: User account registration and management

| Field | Value |
|-------|-------|
| **Purpose** | Create and manage user accounts, authentication, and session management |
| **Role** | Controller |
| **Legal basis** | Contract — GDPR Art. 6(1)(b) |
| **Personal data** | Email address, username, password hash, IP address |
| **Data subjects** | Registered users |
| **Recipients** | Internal processing only (EEA) |
| **Retention** | Delete 30 days after account deletion |
| **Evidence** | `server/smp/.../PasswordHasher.kt` |

#### pa-002: Social media publishing and scheduling

| Field | Value |
|-------|-------|
| **Purpose** | Schedule, compose, and publish content to connected social media accounts |
| **Role** | Processor |
| **Legal basis** | Controller instruction |
| **Personal data** | Social media account identifiers, published content |
| **Data subjects** | End recipients of published content |
| **Recipients** | LinkedIn (US) |
| **Retention** | Archive 90 days after account deletion |
| **Evidence** | `server/smp/.../LinkedInConnectionGateway.kt` |

#### pa-003: Web application hosting and delivery

| Field | Value |
|-------|-------|
| **Purpose** | Host and deliver the Profile Tailors web application and API |
| **Role** | Controller |
| **Legal basis** | Contract — GDPR Art. 6(1)(b) |
| **Personal data** | IP address, browser user agent, usage analytics |
| **Data subjects** | Website visitors and application users |
| **Recipients** | Vercel Inc. (US) |
| **Retention** | Delete 90 days after session end |
| **Evidence** | `apps/web/marketing/astro.config.ts` |

#### pa-004: Lead capture and waitlist management

| Field | Value |
|-------|-------|
| **Purpose** | Manage early-access waitlist signups and marketing communications |
| **Role** | Controller |
| **Legal basis** | Consent — GDPR Art. 6(1)(a) |
| **Personal data** | Email address, marketing preferences, consent status |
| **Data subjects** | Waitlist signups and prospective users |
| **Recipients** | Internal processing only (EEA) |
| **Retention** | Anonymize 30 days after consent withdrawal |
| **Evidence** | `shared/lead-capture/waitlist/` |

## Troubleshooting

- **YAML and Markdown out of sync:** Run `pnpm --filter tools/compliance start` to validate `data-inventory.yaml` against the schema. If validation passes, update this Markdown to match.
- **Missing evidence references:** Each processing activity should link to the source code or configuration that implements the associated control. If a reference is missing or outdated, update the `evidence_references` field in the YAML.

## References

- GDPR Art. 30: Records of processing activities
- [`data-inventory.yaml`](data-inventory.yaml): Machine-readable source of truth
- [`incident-response-runbook.md`](incident-response-runbook.md): Breach response process
- [`check-data-inventory.ts`](../../tools/compliance/check-data-inventory.ts): Schema validation script
