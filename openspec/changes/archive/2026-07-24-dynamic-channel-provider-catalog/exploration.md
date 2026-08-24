## Exploration: Dynamic channel provider catalog (revisited)

### Current State

The sidebar has two conflated concepts today. `SidebarChannelsSection.vue` renders workspace-scoped
connected accounts from `GET /api/publishing/channels`; `SidebarConnectSection.vue` separately
renders a static four-item provider list from `AppShell.vue`. The only implemented action is
LinkedIn OAuth; the other rows are placeholders. The configured-provider endpoint returns LinkedIn
only when deployment-global credentials are complete, but the shell does not consume it.

No plan, subscription, numeric connection limit, or channel-capacity policy exists. Authorization
supports boolean workspace entitlements through `workspace_entitlements`, but publishing connection
handlers do not request an entitlement and the current provider endpoint does not evaluate workspace
policy. Product must define whether a locked list is an upsell/discovery surface, a
connection-capacity result, a provider-policy result, or a combination; its order, lock copy, unlock
destination, and whether locked providers remain visible without deployment credentials are not
inferable.

The connected-account API provides a provider ID, account kind, display name, and optional avatar
URL. Its sidebar row falls back to `getProviderBadge` when the image is absent or fails, but the
shared mapping is incomplete and unknown backend providers are incorrectly coerced to LinkedIn in
the store. `SocialProviderIcon.vue`, Settings, and provider-style badges use separate mappings; they
do not consistently cover Threads or Bluesky. The user-facing provider label must be **LinkedIn**;
`PERSONAL_PROFILE` remains a capability/account-kind detail rather than a provider label.

### Affected Areas

- `apps/web/app/src/layouts/AppShell.vue` — static provider list, LinkedIn-only action, and
  workspace bootstrap that loads channels but not provider policy.
- `apps/web/app/src/layouts/sidebar/SidebarChannelsSection.vue` and `SidebarChannelRow.vue` —
  connected-account list and avatar-to-badge fallback; the sidebar image case belongs here.
- `apps/web/app/src/layouts/sidebar/SidebarConnectSection.vue` — needs distinct available and locked
  provider rendering rather than placeholder/coming-soon behavior.
- `apps/web/app/src/modules/publishing/infrastructure/publishing.store.ts` — provider types, unsafe
  unknown-provider fallback, configured-provider state, channel mapping, and connection entry point.
- `apps/web/app/src/shared/components/SocialProviderIcon.vue`,
  `apps/web/app/src/shared/lib/provider-styles.ts`, and
  `apps/web/app/src/modules/settings/presentation/SettingsView.vue` — fragmented icon/badge mappings
  that need one exhaustive provider presenter with a neutral fallback.
-
`server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingControllers.kt`
and `.../application/PublishingApi.kt` — current global credential-only catalog contract; needs
available/locked reason and capacity/policy fields if the UI is server-driven.
-
`server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingConnectionHandlers.kt`
and `.../infrastructure/linkedin/LinkedInPublishingAdapters.kt` — existing LinkedIn connection entry
point and personal-profile capability.
- `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/domain/EntitlementResolver.kt`
  and `.../infrastructure/persistence/R2dbcWorkspaceEntitlementResolver.kt` — reusable boolean
  policy seam, but no publishing entitlement keys or numeric-limit model.
- `apps/web/app/src/layouts/sidebar/SidebarConnectSection.test.ts`, `SidebarChannelRow.test.ts`,
  `AppShell.test.ts`, `apps/web/app/src/modules/publishing/infrastructure/publishing.store.test.ts`,
  and `server/smp/src/test/resources/features/publishing-channels.feature` — tests currently encode
  static rows, avatar fallback, and LinkedIn-only configured-provider behavior.

### Approaches

1. **Client-derived available and locked lists** — Keep a known provider catalog in the SPA and
   combine it with configured providers plus a client-side capacity rule.
    - Pros: Smallest backend change; localized labels and icons stay client-owned.
    - Cons: Subscription, capacity, and policy become duplicate client logic; cannot safely enforce
      a limit; unknown configured providers and lock reasons remain ambiguous.
    - Effort: Medium

2. **Server-evaluated provider access catalog** — Return every product-supported provider with a
   machine-readable state (`AVAILABLE`, `LOCKED`, or hidden), connection capability, supported
   account kinds, optional capacity, and lock reason code; render connected accounts separately and
   dispatch available entries through a typed client action registry.
    - Pros: Separates configured availability from workspace policy; one enforceable source for list
      state and connection eligibility; removes phantom providers while supporting a locked list and
      future providers.
    - Cons: Requires product decisions and a stable contract; backend must apply the same policy
      when initiating a connection; client still needs an action and presentation mapping per
      supported provider.
    - Effort: High

3. **Dedicated subscription and quota domain first** — Introduce plans, subscriptions, quantitative
   entitlements, and connection-limit accounting before exposing provider access.
    - Pros: Complete foundation for billing-based capacity and consistent enforcement across the
      product.
    - Cons: Broadens a provider-catalog change into billing and quota architecture without a defined
      commercial model.
    - Effort: High

### Recommendation

Adopt approach 2, but gate proposal/spec work on product decisions for the locked state. Keep
labels, icons, and accessible fallback presentation in a single typed SPA registry; display *
*LinkedIn** and retain `PERSONAL_PROFILE` only as account metadata. The backend catalog should
calculate provider state from deployment configuration plus the chosen workspace policy/capacity
model, while the initiation endpoint rechecks that state. The shell should reload the catalog and
connected accounts on active-workspace changes, render connected accounts independently, and expose
one connection entry point for each available, action-supported provider.

It is inferable that deployment credentials determine whether a provider can operate and that
connected channels belong to a workspace. It is not inferable whether a provider is lockable, which
entitlement unlocks it, how many channels each workspace may connect, whether a full capacity should
lock every provider or only new connections, or where an unlock action navigates.

### Risks

- Locked-list rules are undefined; implementing assumed plans, limits, or policy reasons would
  create incorrect product behavior.
- Global deployment configuration, workspace authorization, and connection capacity are distinct
  concerns; the current backend only implements the first and a generic boolean entitlement seam.
- Existing unknown-provider coercion can brand an unsupported account as LinkedIn; incomplete icon
  mappings can produce text-only or inconsistent fallback visuals, including the sidebar avatar
  fallback.
- The current OAuth endpoint validates provider credentials but not capacity/policy; UI-only locks
  can be bypassed.
- Tests must replace static-list/coming-soon assertions with catalog states, workspace switches,
  connection denial at capacity/policy, icon fallback, and BDD API scenarios.

### Ready for Proposal

No — tell the user that configured availability and connected accounts are already distinct, but
plans, numeric limits, and lock semantics do not exist. Before a revised proposal, define the
provider set and ordering, lock visibility rules, entitlement/capacity source and values,
enforcement behavior, lock copy/unlock destination, and whether credentials are deployment-wide or
workspace-specific.
