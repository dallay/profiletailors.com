# ADR-0013: RateLimitTier vs SubscriptionPlan

- Status: Accepted
- Date: 2026-07-22
- Decision owners: Principal Architect
- Scope: Identity context, billing bounded context (future)
- Supersedes: None
- Superseded by: None
- Related:
    - OpenSpec: DALLAY-497
    - Issues/PRs: DALLAY-497

## Context

The identity module currently defines a `RateLimitTier` enum (`FREE`, `BASIC`, `PROFESSIONAL`) on the
`Principal` entity. These tiers control API rate limits and feature access gating. However, there is
no billing system, subscription management, or payment processing in the codebase.

The naming overlap with commercial subscription plans (e.g., "Basic Plan", "Professional Plan") creates
ambiguity: a `RateLimitTier` is a technical enforcement policy, not a commercial offering. Mixing
these concerns would couple the identity context to future billing domain logic, violating hexagonal
architecture boundaries.

## Decision drivers

- Separation of operational enforcement from commercial packaging
- Prevent premature coupling between identity and future billing contexts
- Allow `RateLimitTier` to exist independently before billing is implemented
- Keep the door open for a `SubscriptionPlan` entity that MAY select a `RateLimitTier` as one of
  its entitlements

## Decision

**`RateLimitTier` MUST remain a technical concept in the identity context.** It describes a set of
rate limits and feature flags applied to a principal irrespective of payment status.

Future billing context MUST introduce its own `SubscriptionPlan` entity without renaming or
replacing `RateLimitTier`. A `SubscriptionPlan` MAY reference a `RateLimitTier` as one of its
entitlements, but the identity context MUST NOT depend on the billing context.

The existing enum values `FREE`, `BASIC`, `PROFESSIONAL` are acceptable as rate-limit tier names.
When billing is introduced, a "Starter Plan" or "Team Plan" subscription MAY map to
`RateLimitTier.BASIC` or any other tier without requiring enum renaming.

## Scope and boundaries

- **Identity context**: Owns `RateLimitTier` — no changes needed.
- **Billing context** (future): Owns `SubscriptionPlan`, `Entitlement`, `PaymentMethod` — must
  not leak into identity.
- **Dependency rule**: Billing MAY depend on Identity (to read a principal's tier); Identity MUST
  NOT depend on Billing.

## Alternatives considered

### Rename `RateLimitTier` to `FeatureTier` or `Plan`

- Description: Rename the existing enum to avoid confusion with billing concepts.
- Advantages: Clearer naming today.
- Disadvantages: Churn with no immediate benefit; `RateLimitTier` accurately describes what it
  does (rate limiting). Renaming would not prevent future coupling.
- Reason rejected: The coupling risk is architectural, not lexical. An ADR boundary is sufficient.

### Merge `RateLimitTier` into a future `SubscriptionPlan`

- Description: Remove `RateLimitTier` entirely and let billing own all tiering.
- Advantages: Single source of truth for plan definitions.
- Disadvantages: Creates a circular dependency — identity needs rate limits at login, before
  billing context is loaded. Also violates the dependency rule (identity → billing).
- Reason rejected: Premature optimization. Keep identity autonomous.

## Consequences

### Positive

- Identity remains independently deployable and testable
- Billing can be designed without constraints from identity's enum naming
- `RateLimitTier.FREE` correctly describes a rate-limited free access tier, not a commercial plan

### Negative

- Two parallel concepts (`RateLimitTier`, `SubscriptionPlan`) with overlapping semantics
- Developers must understand the distinction when reading code

### Risks

- Someone might be tempted to add billing-related fields to `RateLimitTier` — mitigated by this ADR

### Accepted trade-offs

- The word "Professional" in the enum might confuse readers until billing is implemented. The ADR
  documents the intent.

## Compliance and enforcement

- ArchTest in identity module: no imports from `billing` package (when it exists).
- ArchTest in billing module (future): may import identity `RateLimitTier` but not modify it.

## Verification

- [ ] Even after billing is implemented, `RateLimitTier` remains in the identity package.
- [ ] No `SubscriptionPlan` or billing types appear in identity classes.

## Migration or remediation

None required. No billing code exists yet.

## Follow-up actions

- [ ] Document the distinction in the AGENTS.md or a shared glossary when billing work begins.

## Revisit conditions

- If identity needs to read `SubscriptionPlan` to compute effective rate limits, revisit the
  dependency direction.
