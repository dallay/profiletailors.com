# Non-MCP Credential Consumer and Route Inventory

## Evidence boundary

Inspected the existing SMP HTTP controller mappings, `IdentitySecurityConfiguration`, `ApiKeyAuthenticationWebFilter`, `JwtPrincipalAuthenticationConverter`, `JwtAuthenticatedPrincipalMaterializer`, credential lookup adapters, dashboard and admin auth entry points, and the ratified ADR-0035/design. This is a source inventory, not a claim that external deployment logs or every operator-owned integration have been reviewed. The current configuration has a global JWT resource-server path and service-account materialization. MCP has its own security configuration and is excluded below. The API-key filter is an identity-wide filter whose exact route-specific application is governed by the current security/filter configuration; it is not treated as evidence of an API-key allowlist.

## Current non-MCP consumer and route evidence

| Consumer / route family | Route and methods evidenced in source | Credential | Owner | Disposition before cutover |
|---|---|---|---|---|
| Dashboard authenticated API | `/api/auth/me` GET; `/api/auth/me/preferences` GET/PUT; `/api/auth/me/security` GET and `/change-password` POST; `/api/v1/account/close` POST; workspace, publishing, media, ideas, analytics, authorization, governance and other non-MCP `/api/**` APIs exposed by SMP controllers | Local JWT bearer; current JWT resource-server chain | Owning SMP bounded context; human product access is owned by local principal and authorization policy | Migrate browser consumers to `pt-app` BFF session and local authorization. Retire acceptance of non-MCP bearer credentials at cutover. Do not retain a bearer route.
| Platform admin SPA | `/api/admin/**`, including dashboard, users, invitations, takedown reports, notifications, system, configuration, waitlist, audit, operators and publishing administration | Local JWT bearer under current global JWT processing | `platformadmin` context owns admin operations; local platform permission remains authoritative | Migrate browser consumer to separate `pt-admin` BFF session with validated LoA 2 and local platform permission. Retire non-MCP bearer acceptance at cutover.
| Invitation acceptance / bootstrap flows | `/api/invitations/accept` POST; `/api/bootstrap/requirements` GET and `/api/bootstrap/complete` POST | Public or invitation/bootstrap-specific flow; not identified as a machine bearer consumer by route mapping | `platformadmin` context | Reassess in Work Unit 2/cutover so invitation and manual provisioning remain; do not classify as permission to preserve a machine credential route.
| Local human auth and lifecycle | `/api/auth/register`, `/login`, `/refresh`, `/logout`, `/verify-email`, `/resend-verification`, `/forgot-password`, `/reset-password` POST; `/api/auth/me` GET; account security and preferences routes above | Local credentials, local refresh token, and/or local JWT bearer, depending on operation | `identity` context | Keycloak-owned BFF/lifecycle at hard cutover; manually provision local principal. Retire local human credential lifecycle and local token acceptance. No local-password rollback.
| Service-account principals | All non-MCP protected APIs that the global JWT chain can authenticate for a `SERVICE_ACCOUNT` principal; principal is resolved by `(principal_type, subject, provider)` and active service-account credential reference | JWT bearer with service-account credential reference | `credentials` owns service-account credential state; target route's owning SMP context owns resource authorization | Inventory each consuming integration with its route owner before cutover; migrate only to an approved human or MCP contract, then retire service-account/non-MCP bearer route. No service-account exception/allowlist.
| Non-MCP API-key requests | Non-MCP APIs reachable through `ApiKeyAuthenticationWebFilter` where the configured web filter accepts an API-key bearer value; filter materializes local principal through active API-key credential lookup | Local API-key bearer value | `credentials` owns key lifecycle; route's owning SMP context owns resource authorization | Migrate each browser consumer to BFF session or an approved MCP contract. Retire API-key acceptance on every non-MCP route at cutover. Preserve MCP keys only through the separate MCP chain.
| Health and public metadata | `/api/health-check` GET; `/api/capabilities/public` GET; public compliance ping and public redirect/metadata routes as configured | No non-MCP machine credential required by the endpoint contract | `platform` / `identity` / owning context | Remain public only where current route contract says public; not a non-MCP credential consumer. Verify matcher separation in Work Unit 2; no allowlist.

## Route-owner families requiring consumer-level reconciliation

The controller mapping inventory identifies these non-MCP API families under the current global JWT processing path. Route owner is the named bounded context. Current client implementations and external service-account/API-key owners are not fully discoverable from controller source alone, so their migration remains a pre-cutover operational action rather than a claim of completed migration.

| Bounded-context route family | Mapping evidence | Current credential / owner | Disposition |
|---|---|---|---|
| Ideas | `/api/ideas` CRUD, move, convert, columns | Local bearer; `ideas` | Migrate first-party SPA calls to `pt-app` BFF; remove bearer auth at cutover. |
| Analytics | `/api/analytics` overview, posts, best-times, export | Local bearer; `analytics` | Migrate first-party SPA calls to `pt-app` BFF; remove bearer auth at cutover. |
| Media | `/api/media/assets` upload, list, lookup, delete; preview/content; provider import; `/api/media/proxy` | Local bearer except any explicitly public proxy behavior; `media` / `platform` | Migrate human browser calls to BFF session; review provider/proxy integrations individually; no non-MCP API-key exception. |
| Tenancy and authorization | `/api/tenancy/**`, `/api/authorization/**` | Local bearer; `tenancy` / `authorization` | Migrate first-party browser calls to BFF session and retain local membership/resource authorization. |
| Publishing | `/api/publishing/**`, `/api/v1/workspaces/{workspaceId}/bulk/**`, `/api/v1/workspaces/{workspaceId}/recurring/**` | Local bearer; `publishing` | Migrate first-party browser calls to BFF session; provider OAuth access tokens are outbound provider credentials, not inbound service-account routes. |
| Governance and privacy | `/api/governance/**`, `/api/v1/privacy/requests/**` | Local bearer for protected endpoints; `governance` / `privacy` | Migrate human calls to BFF session; preserve local authorization and audit. |
| Hashtags, waitlists, and shortlinks | `/api/hashtags/**`, `/api/waitlists/**`, `/api/v1/links/**` | Local bearer where protected; owning `hashtags`, `leadcapture`, and `shortlinks` contexts | Migrate browser consumers to BFF where authenticated; preserve routes that are intentionally public without machine credentials. |
| Platform administration | `/api/admin/**` | Local bearer plus local permission checks; `platformadmin` | Migrate to separate `pt-admin` BFF state; no app-only or machine bearer path survives. |

## MCP boundary

`/api/mcp/**`, MCP API-key authentication, MCP workspace-scoped OAuth, resource metadata, audience/workspace/scope checks, and the MCP-specific security chain are excluded from this inventory and remain unchanged under ADR-0035. No route is retained by constructing a non-MCP allowlist.

## Completion boundary

This inventory records source-visible route families and disposition. External consumer names, named operators, and confirmation of each migration/retirement must be collected with route owners before production cutover. Work Unit 2 owns the actual security-chain and consumer compatibility changes. The inventory does not implement an allowlist, remove routes, or claim cutover readiness.
