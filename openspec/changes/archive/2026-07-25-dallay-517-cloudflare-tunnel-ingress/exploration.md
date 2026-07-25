# Exploration: DALLAY-517 — Stable Cloudflare Tunnel ingress for SMP

## Current State

**Production infrastructure source of truth:** `/Users/acosta/Dev/dallay/profiletailors-deploy` (private repository, current branch `main`). The application monorepo provides the SMP image and generic self-hosting examples only; it does not own the official VPS tunnel topology.

The authoritative stack file is `production/docker-compose.yml`, deployed by `scripts/deploy.sh` on the Swarm manager. The tracked production environment declares `SWARM_STACK_NAME=profile-tailors-production`, so the deployed Swarm services are:

| Compose service | Swarm service name | Connectivity / role |
|---|---|---|
| `cloudflared` | `profile-tailors-production_cloudflared` | One replica; outbound Cloudflare Tunnel client on `edge` only. |
| `backend` | `profile-tailors-production_backend` | One SMP replica, constrained to the single `profiletailors.storage=true` node; listens on `7638`, management on `9091`. |
| `postgresql` | `profile-tailors-production_postgresql` | One persistent database replica on the same labelled storage node. |

`cloudflared` runs token-managed mode as `tunnel run --token-file /run/secrets/cloudflare_tunnel_token`. Its intended remotely managed public hostname route is exactly:

```text
https://api.profiletailors.com  ->  http://backend:7638
```

`backend` is the service-discovery name on the non-attachable `edge` overlay network. The `data` overlay is internal and joins only `backend` and `postgresql`. There are no host-published ports: `7638`, `9091`, and `5432` remain internal. Dokploy is the VPS management surface, but this repository contains no Dokploy-specific application manifest; the operational deployment mechanism is the Swarm script on the manager.

The stack's active public configuration is at `production/.env`: `PUBLIC_ORIGIN=https://api.profiletailors.com` and `SMP_CORS_ALLOWED_ORIGIN=https://app.profiletailors.com`. Secret source files are local-only under `production/secrets/`; `scripts/deploy.sh` creates immutable versioned Swarm secrets. The tunnel token source is `production/secrets/cloudflare-tunnel-token`, producing `profiletailors_cloudflare_tunnel_token_v1` and mounted into `cloudflared` as `/run/secrets/cloudflare_tunnel_token` with uid/gid `65532` and mode `0400`.

Cloudflare owns the tunnel connector, the `api` DNS CNAME, and the published hostname rule in the Zero Trust dashboard/API. The deployment repository owns the Swarm definition, public-origin/CORS wiring, token delivery, and operator runbook. SMP/Spring Security owns which endpoints are public, authenticated, or rate-limited; the Tunnel MUST forward the whole hostname without path rules. The source C4 document's 3+ replica Kubernetes/Cloud Run diagram remains a future target and does not describe this 0.1.0 deployment.

## Affected Areas

- `/Users/acosta/Dev/dallay/profiletailors-deploy/production/docker-compose.yml` — authoritative services, ports, networks, one-replica policy, and Cloudflare token mount.
- `/Users/acosta/Dev/dallay/profiletailors-deploy/production/.env` and `.env.example` — `SWARM_STACK_NAME`, `PUBLIC_ORIGIN`, and browser-origin CORS contract; no secrets belong here.
- `/Users/acosta/Dev/dallay/profiletailors-deploy/production/secrets/cloudflare-tunnel-token` — local, ignored tunnel-token source; must not be read, committed, or logged.
- `/Users/acosta/Dev/dallay/profiletailors-deploy/production/cloudflared/README.md` — operator instructions for the Cloudflare Zero Trust route and token-managed deployment.
- `/Users/acosta/Dev/dallay/profiletailors-deploy/scripts/{prepare,deploy}.sh` — safe secret preparation, Swarm preflight, deployment, readiness polling, and targeted rollback.
- `/Users/acosta/Dev/dallay/profiletailors-deploy/docs/{architecture,network-secrets,operations}.md` — production ownership and operational documentation.
- Cloudflare Zero Trust and DNS — externally owned connector, `api.profiletailors.com` published route, and `api` CNAME; intentionally not represented as secrets in Git.
- `server/smp/.../IdentitySecurityConfiguration.kt` and `application.yaml` (application repository) — contextual boundary only: health/public route exceptions, authenticated default, CORS binding, and ports; no authorization change belongs in DALLAY-517.
- `docs/architecture/c4/02-container.md` (application repository) — future 3+ replica diagram that must be labelled distinctly from the deploy-repository 0.1.0 topology.

## Approaches

1. **Use the existing remotely managed, token-based tunnel** — retain `cloudflared` as deployed, configure the Cloudflare published application route for the full hostname to `http://backend:7638`, and use live route/service probes as the deployment evidence.
   - Pros: aligns with the actual deployment source of truth; no new credentials format or service lifecycle; tunnel remains independent of backend rollout.
   - Cons: the Cloudflare route is external state; `cloudflared tunnel ingress validate` does not validate this remote dashboard route.
   - Effort: Low

2. **Convert to a locally managed ingress configuration** — replace token-run mode with a credential-file/config-file deployment based on `production/cloudflared/config.example.yml`.
   - Pros: a checked-in configuration can be tested with `cloudflared tunnel ingress validate` and `cloudflared tunnel ingress rule`.
   - Cons: changes the established 0.1.0 implementation, adds credentials-file secret handling, and is explicitly documented as unnecessary for 0.1.0.
   - Effort: Medium

## Recommendation

Choose approach 1. It is the real 0.1.0 implementation: one `profile-tailors-production_cloudflared` replica forwards **all** requests for `api.profiletailors.com` to `backend:7638`. Do not constrain the route to `/api/waitlists/*`, do not publish backend ports, and do not move authorization to Cloudflare path rules. Spring Security remains the authority for `/actuator/health/**`, explicitly public application endpoints, authentication, and rate limiting.

The proposal should explicitly correct the issue's `cloudflared tunnel ingress validate` acceptance criterion: that command validates local ingress-file rules only. For the accepted remote-route architecture, require Cloudflare route inspection plus the runtime probes below. Switch to approach 2 only if a version-controlled ingress file is a deliberate product requirement.

## Safe Implementation and Verification Sequence

1. On the Swarm manager, inspect—not modify—the current state: `docker stack services profile-tailors-production`, `docker service ps profile-tailors-production_cloudflared`, and the three service logs. Confirm exactly one ready storage-labelled node before any deployment.
2. In Cloudflare Zero Trust, create or reuse the remotely managed connector and set one published HTTP route: `api.profiletailors.com` to `http://backend:7638`, with no path matcher. Confirm the Cloudflare-created `api` CNAME targets the tunnel. Do not route to a VPS IP, `localhost`, port `9091`, or PostgreSQL.
3. Store the connector token only in `production/secrets/cloudflare-tunnel-token` with `0600` permissions. Let `scripts/deploy.sh` create/use `profiletailors_cloudflare_tunnel_token_v1`; never place the token in `.env`, the stack file, Git, logs, or a Docker command line.
4. Keep `PUBLIC_ORIGIN=https://api.profiletailors.com`. Defer browser-origin changes for the marketing waitlist to DALLAY-518: it must add the marketing origin to `SMP_CORS_ALLOWED_ORIGIN` and set marketing's existing `WAITLIST_API_BASE` to the stable API URL.
5. Render and deploy only through `scripts/deploy.sh`. It checks manager status, one storage node, non-empty secret sources, renders the stack with `docker stack config`, deploys with registry auth, and rolls back only changed services if readiness fails. Do not use a separate Compose stack or a Dokploy-managed duplicate deployment.
6. Verify connector health with `docker service logs profile-tailors-production_cloudflared`; verify Swarm convergence for `cloudflared`, `backend`, and `postgresql`.
7. From outside the VPS, verify `GET https://api.profiletailors.com/actuator/health/readiness` is UP and `GET /api/auth/me` with the API `Accept` header returns `401`. This proves full-host routing reaches SMP and Spring Security remains the authorization boundary.
8. After DALLAY-518, verify CORS preflight and a real waitlist POST from `https://profiletailors.com`. Verify direct public access to `:7638`, `:9091`, PostgreSQL, SSH, and Dokploy is denied or limited to the documented Tailscale administration path.

## Risks

- Live Cloudflare/DNS/firewall state was intentionally not queried or changed. This exploration proves the repository contract, not that the external route is currently healthy.
- The current production CORS value admits only `https://app.profiletailors.com`; the marketing browser origin remains DALLAY-518 work. The marketing code uses `WAITLIST_API_BASE`, while the Linear issue names `PUBLIC_WAITLIST_API_BASE`.
- Backend uses stop-first updates to avoid concurrent publishing workers. During replacement, `cloudflared` can briefly resolve no backend task and return 502; deployment polling and service logs are required evidence.
- The `cloudflared tunnel ingress validate` requirement conflicts with the selected remotely managed token route. Treating the optional example config as deployed evidence would be misleading.
- The application C4 target's 3+ replicas are future state; documentation must not imply HA or change the one-VPS, one-backend-replica 0.1.0 operational boundary.

## Ready for Proposal

Yes. Scope the proposal primarily to `/Users/acosta/Dev/dallay/profiletailors-deploy` plus Cloudflare operator actions and documentation. Keep application authorization unchanged; coordinate CORS/client API-base work with DALLAY-518.
