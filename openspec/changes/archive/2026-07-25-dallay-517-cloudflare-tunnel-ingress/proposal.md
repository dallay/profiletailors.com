# Proposal: Stable Cloudflare Tunnel Ingress for SMP

## Intent

Make `api.profiletailors.com` the stable public full-hostname ingress for the single SMP replica on the VPS, without exposing backend ports or shifting endpoint authorization from Spring Security.

## Scope

### In Scope
- Configure and document the remotely managed Cloudflare Tunnel route: `api.profiletailors.com` → `http://backend:7638`.
- Preserve the one-replica Swarm topology, outbound-only `cloudflared` connector, internal `edge`/`data` networks, and secret-backed tunnel token.
- Add deployment evidence: Cloudflare route inspection, Swarm/service logs, readiness `UP`, and unauthenticated `/api/auth/me` returning `401`.

### Out of Scope
- CORS and frontend API-base environment changes (DALLAY-518).
- Email configuration or delivery work.
- Distributed rate limiting (DALLAY-513); reconsider only when scaling beyond one backend replica.
- Application authorization changes, local ingress-file mode, published backend ports, or live changes during this change's planning phases.

## Capabilities

### New Capabilities
- `cloudflare-tunnel-ingress`: Stable full-hostname Cloudflare Tunnel routing to the private SMP service with Spring Security retained as the endpoint authorization boundary.

### Modified Capabilities
None. Existing application capability and authorization requirements do not change.

## Approach

Use the existing token-managed, remotely configured tunnel. Cloudflare owns the connector, DNS CNAME, and hostname route; `/Users/acosta/Dev/dallay/profiletailors-deploy` owns the Swarm stack, token delivery, deployment script, and runbook. Route the whole hostname—never path-specific traffic—to `backend:7638`. Do not use `cloudflared tunnel ingress validate` as acceptance evidence because it validates local config files, not this remote route.

## Affected Areas

| Area | Impact | Description |
|---|---|---|
| `/Users/acosta/Dev/dallay/profiletailors-deploy/production/docker-compose.yml` | Modified | Retain/document one-replica `cloudflared` → private backend topology. |
| `/Users/acosta/Dev/dallay/profiletailors-deploy/production/cloudflared/README.md` | Modified | Published-route setup and verification runbook. |
| `/Users/acosta/Dev/dallay/profiletailors-deploy/scripts/deploy.sh` | Modified | Deploy/readiness and targeted rollback evidence. |
| Cloudflare Zero Trust + DNS | Modified | Remote route and `api` CNAME. |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Remote route/DNS is unhealthy or differs from the repository contract | Med | Inspect Cloudflare and run external probes before deployment. |
| Stop-first backend update briefly returns `502` | Med | Use existing readiness polling and targeted service rollback. |
| Tunnel token exposure | Low | Keep it in the ignored, mode-`0600` secret source and Swarm secret only. |

## Rollback Plan

Remove or restore the Cloudflare published route, then use `scripts/deploy.sh` or targeted Swarm rollback for changed services. Keep backend ports unpublished and revoke/rotate the tunnel token if compromised.

## Dependencies

- Cloudflare Zero Trust/DNS access and a valid tunnel token.
- One ready Swarm storage-labelled VPS node.

## Success Criteria

- [ ] Internal readiness at `http://backend:9091/actuator/health/readiness` returns `UP` from the Swarm `edge` network.
- [ ] `GET https://api.profiletailors.com/api/auth/me` with the API `Accept` header returns `401` unauthenticated.
- [ ] No host ports expose SMP, management, or PostgreSQL; no path rule replaces Spring Security.
