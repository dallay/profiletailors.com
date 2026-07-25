# Cloudflare Tunnel Ingress Specification

## Purpose

Provide a stable, private-origin API ingress while Dokploy exclusively owns the active SMP Swarm namespace and the 0.1.0 production rollout.

## Requirements

### Requirement: Full-Hostname Public Route

Cloudflare MUST publish the complete hostname `api.profiletailors.com` through the remotely managed tunnel to `http://backend:7638`. The route MUST NOT use a path matcher or waitlist-only exception.

#### Scenario: Request reaches the public API listener

- GIVEN the published hostname route and connector are healthy
- WHEN a client requests any path on `https://api.profiletailors.com`
- THEN Cloudflare SHALL forward it to `backend:7638`

#### Scenario: Waitlist does not alter ingress

- GIVEN a request targets a waitlist path
- WHEN it enters the public hostname
- THEN the same hostname-wide route MUST apply

### Requirement: Private Origin and Readiness

The connector and backend MUST remain single-replica services on private networks. Backend port `7638`, management port `9091`, and PostgreSQL MUST NOT be host-published. Management readiness MUST remain private at `http://backend:9091/actuator/health/readiness` on `edge`.

#### Scenario: Internal readiness succeeds

- GIVEN the active backend task is running on `edge`
- WHEN a permitted internal validation probes the readiness URL
- THEN it MUST receive HTTP `200` with `"status":"UP"`

#### Scenario: Direct origin access is unavailable

- GIVEN an external client bypasses Cloudflare
- WHEN it attempts TCP access to ports `7638`, `9091`, or PostgreSQL
- THEN no public host listener MUST accept the connection

### Requirement: Dokploy-Owned Production Rollout

Dokploy MUST exclusively create, update, roll back, and own the active SMP Swarm namespace for 0.1.0. The repository's direct `scripts/deploy.sh` MUST NOT deploy, update, or create a second SMP stack. Production evidence MUST identify the Dokploy-managed active namespace rather than assume a repository example stack name.

#### Scenario: Dokploy performs a production rollout

- GIVEN a production rollout is approved
- WHEN SMP services are changed in the active namespace
- THEN Dokploy MUST perform the rollout without a direct repository stack deployment

#### Scenario: Repository deploy script is blocked from production use

- GIVEN an operator is preparing a 0.1.0 production rollout
- WHEN choosing a deployment mechanism
- THEN `scripts/deploy.sh` MUST NOT be used to target Swarm

### Requirement: Repository Validation Boundaries

Repository-local validation MAY render, lint, or inspect static deployment artifacts and MUST NOT mutate live Swarm, Dokploy, Cloudflare, DNS, secrets, or the active namespace. `cloudflared tunnel ingress validate` MUST NOT be accepted as proof of the remotely managed route.

#### Scenario: Local validation is non-mutating

- GIVEN an operator validates repository deployment artifacts
- WHEN validation completes
- THEN no live deployment or infrastructure resource MUST be created or updated

#### Scenario: Remote route is verified independently

- GIVEN local configuration validation succeeds
- WHEN route acceptance is assessed
- THEN operators MUST inspect the Cloudflare hostname route independently

### Requirement: Dokploy-Managed Acceptance

Dokploy-managed deployment acceptance MUST confirm active-service convergence, internal readiness on `9091`, and public authorization behavior. Spring Security MUST remain the endpoint authorization boundary.

#### Scenario: Production acceptance passes

- GIVEN Dokploy has completed a rollout in the active namespace
- WHEN acceptance probes internal readiness and `GET https://api.profiletailors.com/api/auth/me` with the API `Accept` header and no credentials
- THEN readiness MUST be `200` with `"status":"UP"` and the API response MUST be `401`

#### Scenario: Management listener is not public ingress

- GIVEN a client requests an Actuator path through the public hostname
- WHEN Cloudflare forwards traffic to `backend:7638`
- THEN port `9091` MUST remain outside public routing
