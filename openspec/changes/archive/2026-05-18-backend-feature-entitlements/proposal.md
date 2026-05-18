# Proposal: Backend Feature Entitlements

## Intent

Make feature entitlements executable in the existing backend proving slice without broadening into package, billing, or administration work. This change turns one workspace-scoped entitlement key into real persisted state and requires that entitlement in addition to principal permission for `GET /api/authorization/workspace-access/current`, proving that feature availability and authorization are separate platform concerns.

## Scope

### In Scope
- Persist one workspace-scoped entitlement state model sufficient to answer whether a workspace is enabled for one proving-slice feature key.
- Wire a real persistence-backed entitlement resolver through the existing authorization seam instead of the current no-op resolver.
- Enforce executable entitlement gating on `/api/authorization/workspace-access/current` so allow requires both feature availability and principal permission.
- Add an explicit runtime deny/audit reason for missing entitlement so feature denials remain distinguishable from permission denials.
- Prove allow/deny behavior with H2 and PostgreSQL integration coverage for:
  - entitled workspace + authorized principal -> allow
  - non-entitled workspace + authorized principal -> deny for missing entitlement
  - entitled workspace + unauthorized principal -> deny for missing permission

### Out of Scope
- Plan, package, SKU, bundle, or subscription modeling.
- Billing integration or any provider-specific entitlement source.
- Entitlement CRUD, admin, operator, or assignment APIs/workflows.
- Multi-context entitlements beyond one workspace-scoped proving key.
- Quotas, usage metering, consumption semantics, inheritance, fallback chains, or time-windowed entitlement semantics.
- New customer-facing product capabilities or new protected endpoints.
- Broader cache/invalidation implementation beyond preserving existing platform seams.

## Approach

Use the current `EntitlementResolver` seam and existing workspace-access proving slice as the narrow execution point. Add a minimal persisted workspace entitlement record and a single R2DBC-backed resolver, then update the existing workspace authorization decision flow so the slice denies when the workspace lacks the required feature entitlement even if the principal permission succeeds.

The design intent is intentionally small:
- one proving feature key
- one workspace-scoped authoritative persistence path
- one explicit deny reason for entitlement failure
- no product/package engine
- no admin surface

This preserves the platform rule already present in the main authorization spec: entitlement controls feature availability, while permission controls whether a principal may act inside an available feature.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `openspec/specs/authorization/spec.md` | Modified | Narrow breadth boundary must now permit executable entitlement gating on the existing proving slice and clarify the one-feature workspace entitlement proof. |
| `openspec/specs/platform/spec.md` | Referenced | Existing caching and authoritative-state rules already cover entitlements; proposal keeps implementation minimal and avoids premature cache breadth. |
| `openspec/specs/governance/spec.md` | Referenced | Runtime decision proof must distinguish missing entitlement from missing permission. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/application/WorkspaceAuthorizationService.kt` | Modified | Current entitlement result is ignored; this becomes the central enforcement point for combined entitlement + permission evaluation. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/infrastructure/AuthorizationBootstrapConfiguration.kt` | Modified | Replace `NoOpEntitlementResolver` wiring with a persistence-backed resolver for the proving key. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/domain/AuthorizationModels.kt` | Modified | Extend the minimal entitlement model only as needed for one workspace-scoped executable key. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platform/application/PlatformContracts.kt` | Modified | Add an explicit authorization/audit reason code for missing entitlement. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/application/GetCurrentWorkspaceAccessSummaryQuery.kt` | Modified | Declare or preserve the proving-slice feature requirement used for runtime gating. |
| `server/smp/src/main/resources/db/changelog/authorization/*.yaml` | Modified | Add the minimal schema needed to persist workspace entitlement state for the proving key. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/integration/WorkspaceAccessSummaryEndpointIntegrationTest.kt` | Modified | Add H2-backed allow/deny proof for entitlement and permission separation. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/integration/WorkspaceAccessSummaryEndpointPostgresIntegrationTest.kt` | Modified | Add PostgreSQL-backed allow/deny proof for the same executable entitlement behavior. |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Scope drifts into package or billing architecture | Medium | Keep proposal/spec language limited to one workspace-scoped feature key and explicitly defer all commercial modeling. |
| Entitlement denial becomes indistinguishable from permission denial | Medium | Add an explicit platform reason code and require audit-ready proof scenarios for both deny classes. |
| The proving slice is interpreted as a customer-facing paid feature commitment | Medium | State clearly in specs/design that this is a platform proof on an existing slice, not product packaging policy. |
| Persistence model becomes over-generalized too early | Medium | Model only the fields needed to answer authoritative enabled/disabled state for one workspace-scoped feature key. |
| H2 behavior diverges from PostgreSQL assumptions | Low/Medium | Require PostgreSQL integration proof for the same allow/deny matrix, not H2-only coverage. |

## Rollback Plan

If the change causes incorrect denials or introduces schema/application instability, revert the entitlement enforcement path and restore the no-op entitlement resolver on the proving slice. Back out the minimal entitlement schema/changelog introduced for this feature key, remove the entitlement requirement from the current-slice authorization flow, and rerun the existing workspace-access authorization suites to confirm the system returns to permission-only behavior on `/api/authorization/workspace-access/current`.

## Dependencies

- Existing exploration artifact: `openspec/changes/backend-feature-entitlements/exploration.md`
- Existing authorization, platform, and governance main specs
- Existing H2 and PostgreSQL integration harness for `/api/authorization/workspace-access/current`

## Success Criteria

- [ ] The change proposal stays limited to one persisted workspace-scoped entitlement key for the existing proving slice.
- [ ] The resulting specs/design can require allow only when both workspace entitlement and principal permission succeed.
- [ ] The resulting specs/design can require explicit deny proof for missing entitlement distinct from missing permission.
- [ ] The resulting tasks can verify H2 and PostgreSQL outcomes for entitled allow, non-entitled deny, and unauthorized deny without adding new product endpoints.
- [ ] Deferred items remain explicit: plan/package modeling, billing integration, entitlement admin APIs, multi-context breadth, quotas/usage semantics, and new endpoints.
