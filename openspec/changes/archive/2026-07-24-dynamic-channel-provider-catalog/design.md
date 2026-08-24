# Design: Dynamic Channel Provider Catalog

## Technical Approach

Replace the credential-only response at `GET /api/publishing/channels/providers` with a
workspace-resolved catalog. The backend owns state, reason, ordering, and connection eligibility;
the SPA owns typed presentation and the OAuth action mapping. This adds no plans, numeric limits, or
provider integrations.

## Architecture Decisions

| Decision                     | Alternatives considered                    | Rationale                                                                                      |
|------------------------------|--------------------------------------------|------------------------------------------------------------------------------------------------|
| Resolve access on the server | Client-derived configuration and locks     | Global configuration and workspace policy cannot be inferred or enforced safely by the client. |
| Application policy ports     | Controller checks; subscription domain now | Preserves `domain ← application ← infrastructure` and admits future adapters without billing.  |
| Re-check before OAuth start  | Trust a prior catalog response             | State can become stale or be bypassed; initiation is the enforcement boundary.                 |
| One typed SPA registry       | Per-component mappings; coercion           | Prevents inconsistent icons/labels and false branding of unknown providers.                    |

## Data Flow

```
SPA workspace switch
  ├─ GET catalog ──> controller ──> ListProviderCatalogQuery
  │                                  └─ global + entitlement + capacity policy
  └─ GET channels ──> existing workspace channel query

available action ──> OAuth initiate ──> re-evaluate policy ──> authorization URL
```

`globallyConfigured && globallyEnabled && workspaceEntitled && capacityAvailable` yields
`AVAILABLE`; an entitlement/capacity failure yields `LOCKED`; unimplemented, disabled, or
invalid/missing global credentials yield `HIDDEN`. The HTTP adapter omits `HIDDEN` from public
output. Entitlement and capacity are permissive seams now; capacity will apply only to new
connections.

## File Changes

| File                                                                                                                | Action | Description                                                  |
|---------------------------------------------------------------------------------------------------------------------|--------|--------------------------------------------------------------|
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/PublishingProviderCatalog.kt`                  | Create | Catalog state/reason models and policy ports.                |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingProviderCatalogHandlers.kt`     | Create | Workspace catalog query and OAuth authorization guard.       |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingConnectionHandlers.kt`          | Modify | Invoke guard before LinkedIn initiation.                     |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingControllers.kt`         | Modify | Map query result, replacing credential-only DTO.             |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/PublishingApplicationConfiguration.kt` | Modify | Wire global, permissive entitlement, and capacity adapters.  |
| `apps/web/app/src/modules/publishing/infrastructure/publishing.store.ts`                                            | Modify | Fetch typed catalog and prevent stale workspace data.        |
| `apps/web/app/src/shared/lib/provider-presentation.ts`                                                              | Create | Typed labels, icons, action keys, and neutral fallback.      |
| `apps/web/app/src/layouts/AppShell.vue`                                                                             | Modify | Remove static list; load catalog and channels per workspace. |
| `apps/web/app/src/layouts/sidebar/SidebarConnectSection.vue`                                                        | Modify | Render available CTA and non-actionable locked rows.         |
| `apps/web/app/src/shared/components/SocialProviderIcon.vue`                                                         | Modify | Use centralized registry and neutral fallback.               |
| `server/smp/src/test/resources/features/publishing-channels.feature`                                                | Modify | Add catalog and policy/OAuth BDD scenarios.                  |

## Interfaces / Contracts

```kotlin
enum class ProviderCatalogState { AVAILABLE, LOCKED, HIDDEN }
enum class ProviderLockReason { NOT_ENTITLED, CAPACITY_REACHED }

data class ProviderCatalogItem(
    val provider: String,
    val accountKinds: Set<String>,
    val state: ProviderCatalogState,
    val reason: ProviderLockReason?,
    val channelLimit: Int?, // always null in this change
    val connectedChannelCount: Int,
    val canConnectMore: Boolean,
)
data class ProviderCatalogResponse(val providers: List<ProviderCatalogItem>)

fun interface ProviderCatalogPolicy {
    suspend fun evaluate(provider: SocialProvider, workspaceId: String): ProviderCatalogItem
}
```

The workspace-scoped endpoint returns product-ordered, non-hidden items. The SPA mirrors this as a
discriminated TypeScript union; only `AVAILABLE` dispatches a registry action. Unknown providers
render neutral text/icon and no action. A denied OAuth start uses existing problem-details with a
stable policy reason; fetch errors preserve non-actionable state rather than imply availability.

## Testing Strategy

| Layer | What to Test                                                                  | Approach                                                                                             |
|-------|-------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------|
| Unit  | State precedence, permissive seams, OAuth guard                               | Kotlin handler/policy tests with fake ports.                                                         |
| HTTP  | Contract, order, hidden omission, denial mapping                              | WebFlux controller tests.                                                                            |
| BDD   | Catalog states, workspace isolation, OAuth denial                             | Mandatory tagged `publishing-channels.feature` scenarios using `WebTestClient` and required headers. |
| SPA   | Mapping, unknown/icon fallback, locked CTA absence, stale workspace responses | Vitest store, sidebar, shell, and channel-row tests.                                                 |

## Migration / Rollout

No data migration required. Deploy backend contract and SPA consumption together; retain no static
fallback. Existing channels remain publishable, editable, and disconnectable.

## Open Questions

None.
