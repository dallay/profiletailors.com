# Proposal: Dynamic Channel Provider Catalog

## Intent

Replace static Connect placeholders with a backend-resolved provider catalog that exposes availability safely. The SPA must show only usable providers, never infer policy, plans, limits, or branding from local assumptions.

## Scope

### In Scope
- Return provider state (`AVAILABLE`, `LOCKED`, `HIDDEN`), a typed reason, `channelLimit: null`, `connectedChannelCount`, and `canConnectMore: true`.
- Evaluate access through `globallyConfigured && globallyEnabled && workspaceEntitled && capacityAvailable`; entitlement and capacity are permissive seams now.
- Render backend catalog results dynamically: hide `HIDDEN`; connect only `AVAILABLE`; centralize labels, icons, and neutral unknown-provider fallback in one typed SPA registry.
- Repair missing provider icons and replace static AppShell Connect data. The visible provider label is "LinkedIn"; personal profile remains account-kind metadata.

### Out of Scope
- Numeric workspace limits, billing/subscription implementation, or plan inference from rate-limiter tiers.
- New provider OAuth, publishing implementations, coming-soon/roadmap controls, or connected-channel model changes.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `publishing`: Define workspace-aware provider states, reason codes, capacity fields, and policy enforcement boundaries.
- `app-shell`: Render resolved available/locked providers and centralized presentation instead of a static connect list.
- `oauth-initiation-api`: Revalidate provider policy when OAuth initiation begins.

## Approach

Extend the provider catalog with product ordering: LinkedIn personal profile, LinkedIn organization, Instagram, Facebook, Threads, Bluesky, X, TikTok, Pinterest, YouTube. The backend resolves state and reason; `HIDDEN` means unimplemented, disabled, or invalid/missing global credentials. LinkedIn personal profile is the only initially available configured action. The SPA reloads the catalog and workspace channels together, renders locked entries without a CTA, and uses a typed action registry. Future capacity blocks only new connections; existing channels remain publishable, editable, and disconnectable. A future `UPGRADE_PLAN` action targets `/settings/billing`, but no CTA is rendered now.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `server/smp/.../publishing/.../PublishingControllers.kt` | Modified | Resolved provider catalog API |
| `server/smp/.../PublishingConnectionHandlers.kt` | Modified | OAuth-start policy revalidation |
| `apps/web/app/src/modules/publishing/infrastructure/publishing.store.ts` | Modified | Catalog state and workspace lifecycle |
| `apps/web/app/src/layouts/sidebar/SidebarConnectSection.vue` | Modified | State-driven connect list |
| `apps/web/app/src/shared/` | Modified | Typed provider presentation registry and icons |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| UI and OAuth policy diverge | Med | Revalidate policy at OAuth initiation |
| Unknown provider is misbranded | Med | Use a neutral typed-registry fallback |
| Future policy changes block existing channels | Low | Limit capacity enforcement to new connections |

## Rollback Plan

Revert the catalog API and SPA consumption together. No migrations, limits, subscriptions, or connected-channel records are introduced.

## Dependencies

- Valid global credentials and enablement for each provider to be exposed.

## Success Criteria

- [ ] The catalog returns typed state/reason and the specified permissive capacity fields without plan-tier inference.
- [ ] The public Connect UI hides `HIDDEN`; only configured LinkedIn personal profile is available today; no coming-soon or billing CTA appears.
- [ ] Workspace changes reload catalog and channels; OAuth initiation rechecks policy; unknown providers render neutrally.
