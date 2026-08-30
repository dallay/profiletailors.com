# Observability Contracts & SLA Matrix

**Last Updated:** 2026-08-30
**Status:** Active
**Scope:** System-wide Service Level Agreements (SLAs), Service Level Objectives (SLOs), Service Level Indicators (SLIs), and Observability Standards
**Audience:** Platform Engineers, Backend Engineers, Operations, SRE

---

## 📐 Overview

This document defines the official Service Level Agreements (SLAs) and Service Level Objectives (SLOs) for Profile Tailors API functions and bounded contexts. It establishes latency targets, availability expectations, throughput boundaries, and the observability metrics (SLIs) used to monitor compliance.

---

## 📊 Function-Level SLA & SLO Matrix

| Bounded Context / API Group | Target Endpoint Pattern | Availability Target (SLO) | Latency SLA (p95) | Latency SLA (p99) | Throughput / Rate Limit Cap | Key SLI Metric / Instrument |
|---|---|---|---|---|---|---|
| **Authentication & Identity** | `/api/auth/*`, `/api/identity/*` | **99.9%** | `< 150ms` | `< 300ms` | `100 req/min per IP` | `http_server_requests_seconds_bucket{uri=~"/api/auth/.*"}` |
| **Publishing & Scheduling** | `/api/publishing/*`, `/api/publications/*` | **99.5%** | `< 250ms` | `< 500ms` | `60 req/min per workspace` | `publishing_job_execution_duration_seconds`, `publishing_stale_claims_total` |
| **Media Assets & CAS Storage** | `/api/assets/*`, `/api/media/*` | **99.5%** | `< 200ms` (meta)<br>`< 1000ms` (binary) | `< 400ms` (meta)<br>`< 2500ms` (binary) | `30 uploads/min per workspace` | `media_cas_deduplication_ratio`, `storage_operations_latency_seconds` |
| **Governance & Consent** | `/api/governance/*`, `/api/privacy/*` | **99.9%** | `< 100ms` | `< 200ms` | `120 req/min per IP` | `governance_consent_recording_outcomes_total` |
| **Tenancy & Workspaces** | `/api/workspaces/*`, `/api/tenancy/*` | **99.9%** | `< 150ms` | `< 300ms` | `120 req/min per workspace` | `http_server_requests_seconds_bucket{uri=~"/api/workspaces/.*"}` |
| **Platform Administration** | `/api/admin/*`, `/api/platformadmin/*` | **99.0%** | `< 300ms` | `< 800ms` | `30 req/min per operator` | `platformadmin_operator_access_total` |
| **Public Ingress / Marketing** | `/`, `/api/waitlist/*` | **99.9%** | `< 100ms` | `< 250ms` | `200 req/min per IP` | `http_server_requests_seconds_bucket{uri=~"/api/waitlist/.*"}` |

### Publishing Background Worker Delivery SLA
- **Execution Timeliness:** Scheduled posts MUST be claimed and initiated for provider delivery within `60 seconds` of their `scheduled_at` timestamp.
- **Lease Fencing Recovery:** Expired worker claims MUST be released and made available for retry within `5 minutes` (`SMP_PUBLISHING_WORKER_STALE_GRACE`).

---

## 🔍 Observability Standards & Telemetry Contracts

### 1. Prometheus Metrics Naming & Conventions
All Spring Boot backend metrics are exported via Prometheus Actuator at `:9091/actuator/prometheus` (or internal monitoring scrapers).

- **HTTP Requests:** `http_server_requests_seconds_bucket{exception, method, outcome, status, uri}`
- **JVM Health:** `jvm_memory_used_bytes`, `jvm_gc_pause_seconds_bucket`
- **Database Connection Pool (R2DBC):** `r2dbc_pool_acquired_connections`, `r2dbc_pool_pending_acquire_connections`
- **Domain Counters & Timers:**
  - `identity_password_recovery_outcomes_total`
  - `publishing_jobs_processed_total{status="PUBLISHED|FAILED|BLOCKED"}`
  - `media_cas_deduplication_bytes_saved_total`

### 2. Distributed Tracing & Correlation Identifiers
All requests across API endpoints and background workers MUST propagate correlation identifiers via W3C Trace Context headers (`traceparent`, `tracestate`) or domain headers (`X-Correlation-ID`).

- **Pivot Identifiers:** `workspaceId`, `jobId`, `principalId`, `waitlistEntryId`, `invitationId`.
- **Worker Execution:** Background workers inherit or generate a unique `jobId` and `workerId` (`worker-<UUID>`) that is attached to all log MDC contexts and outbound HTTP requests.

### 3. Log Redaction & Privacy Safeguards
In accordance with platform security and GDPR policies:
- **STRICTLY FORBIDDEN IN LOGS/METRICS:** Plaintext passwords, authentication tokens (JWT, OAuth refresh/access tokens), encryption keys, user emails, raw IP addresses, or password reset URLs.
- **Allowed Log Attributes:** Fixed category codes, operation names, bounded status strings, duration in milliseconds, and prefixed entity IDs (`ws-UUID`, `user-UUID`, `pub-UUID`).

---

## 🚨 Error Budgets & SLA Review Cadence

1. **Error Budget Calculation:**
   - **Monthly Budget (99.9% Availability):** Maximum `43.8 minutes` of cumulative downtime per month.
   - **Monthly Budget (99.5% Availability):** Maximum `3.6 hours` of cumulative downtime per month.
2. **Alerting Thresholds:**
   - **P1 Alert (Immediate Pager):** p95 latency exceeds 2x SLA limit for > 5 minutes, or HTTP 5xx error rate exceeds 2% over 5 minutes.
   - **P2 Alert (Ticket / Next-day):** Error budget consumption rate exceeds 20% in 24 hours.
3. **Review Cadence:** SLA performance and error budget burn rates are reviewed monthly by Engineering and Product leads.

---

## 📚 References

- [`docs/README.md`](./README.md)
- [`docs/monitoring/prometheus-grafana-setup.md`](./monitoring/prometheus-grafana-setup.md)
- [`docs/monitoring/actuator-security.md`](./monitoring/actuator-security.md)
- [`docs/infrastructure/private-beta-correlation-matrix.md`](./infrastructure/private-beta-correlation-matrix.md)
- [`docs/publishing-failure-modes.md`](./publishing-failure-modes.md)
