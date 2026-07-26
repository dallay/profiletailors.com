# Design: Repair Refactor QA CI

## Technical Approach

Repair the PR at the production contracts introduced by the extraction and port migration, without changing routes, API payloads, state transitions, or visuals. Remove the scheduler's obsolete local implementation before resolving its extracted-composable interfaces. Then restore frontend reactive and barrel contracts, repair Kotlin compilation at the application-port boundary, and only update test doubles to match the proven runtime contract.

## Architecture Decisions

| Decision | Alternatives considered | Rationale |
|---|---|---|
| Runtime first, mocks second | Make Vitest mocks permissive first | A mock can hide an invalid composable return shape; build/type-check establishes the application contract. |
| Keep extracted ownership | Move logic back into views/components | The extraction is intentional; repair imports, `Ref`/`.value` use, and explicit application-barrel exports instead. |
| Preserve Kotlin port shape | Reintroduce cross-context direct calls or broaden port types | Ports remain application-facing contracts; adapters translate to media/audit domain types. |
| Gate in dependency order | Run full CI repeatedly | Parser and Kotlin compilation failures prevent useful downstream test signals. |

## Root-Cause Families and Fix Order

1. **Scheduler parser/build blocker:** `SchedulerView.vue` retains legacy declarations after moving grid/drag logic to `useCalendarGrid` and `useDragAndDrop`; remove duplicates and retain one owner per identifier.
2. **Frontend extraction contracts:** dashboard chart scaling/visualization, governance loader/filter/action/dialog, media filters/selection/display/actions, publishing scheduler/composer composables, settings, and shared composables disagree on reactive values or public exports. Normalize consumers to each composable's returned `Ref`/computed/function contract; repair explicit module and shared barrels.
3. **Kotlin port migration blockers:** repair stale media references in `MediaHandlers.kt`; keep publishing's `PublishingMediaAssetResolver` adapter boundary and remove the illegal default argument from the single abstract method in `PublishingMediaPorts.kt`; convert governance audit `Map<String, Any?>` metadata to audit-domain `Map<String, String>` at `AuditHookGovernanceMutationAuditPort.kt` rather than widening `MutationAuditFact`.
4. **Independent CI hygiene:** correct only targeted Biome and accessibility violations once compilation/type checking is clean.
5. **Tests:** align affected mocks after the production contracts compile; do not weaken assertions or alter intended behavior.

## Data Flow

```text
Vue view/component
  -> application composable (Refs + actions)
  -> explicit application/shared barrel
  -> store/API

Publishing application -> PublishingMediaAssetResolver adapter
  -> media application port

Governance application -> GovernanceMutationAuditPort adapter
  -> AuditHook -> MutationAuditFact(details: Map<String, String>)
```

`SchedulerView.vue` remains orchestration only: URL state and publishing store feed calendar/drag composables; child scheduler components receive derived state and emit existing events.

## File Changes

| File | Action | Description |
|---|---|---|
| `apps/web/app/src/modules/publishing/views/SchedulerView.vue` | Modify | Delete duplicate legacy scheduler declarations; preserve extracted grid/drag boundary. |
| `apps/web/app/src/modules/{dashboard,governance,media,publishing,settings}/application/*.ts` | Modify | Restore composable input/output and reactive contracts. |
| `apps/web/app/src/modules/{dashboard,governance,media,publishing}/application/index.ts` | Modify | Export only the intended public composables. |
| `apps/web/app/src/shared/composables/{index.ts,useApiError.ts,useDeleteConfirmation.ts,useFormValidation.ts,useModalState.ts,usePagination.ts}` | Modify | Restore shared public exports and consumer-compatible return shapes. |
| `apps/web/app/src/modules/**/*.{vue,test.ts}` | Modify | Apply targeted consumer/a11y/lint repairs; update mocks only after runtime repair. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/media/application/MediaHandlers.kt` | Modify | Replace stale port references with current media application contracts. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingMediaPorts.kt` | Modify | Make the functional port's sole method valid while retaining resolver semantics. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/governance/infrastructure/AuditHookGovernanceMutationAuditPort.kt` | Modify | Adapt governance metadata to audit-domain string metadata. |

## Interfaces / Contracts

```ts
// Composables expose Ref/computed values; consumers read .value in script.
type ComposableState<T> = { value: T }
```

```kotlin
fun interface PublishingMediaAssetResolver {
    suspend fun resolveReadyAssets(workspaceId: String, assetIds: List<String>): List<PublishingResolvedAssetSummary>
}
// MutationAuditFact.details remains Map<String, String>.
```

## Testing Strategy

| Layer | What to Test | Approach |
|---|---|---|
| Static | Parser, imports, Kotlin ports | App build/type-check; backend compilation first. |
| Unit | Composable contracts and port adapters | Focused Vitest/JUnit; mocks mirror proven production shapes. |
| Integration | Publishing-media and governance-audit wiring | Existing focused backend tests, then fast BDD/Postgres lanes. |
| E2E | Dashboard/scheduler unaffected flows | Run affected frontend E2E only after app gates pass. |

Verification ladder: parser/build -> type-check -> Biome -> focused unit tests -> backend compile/unit -> backend BDD/Postgres -> affected frontend E2E -> CI rerun.

## Migration / Rollout

No migration required. No schema, API, dependency, feature flag, or behavior change.

## Open Questions

- [ ] Confirm the current PR merge ref is used for final CI reruns; local uncommitted follow-up changes have advanced some frontend failures.
