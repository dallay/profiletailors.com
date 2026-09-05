# ADR-0014: Future Billing Architecture

- Status: Accepted
- Date: 2026-07-22
- Decision owners: Principal Architect
- Scope: Billing bounded context (future)
- Supersedes: None
- Superseded by: None
- Related:
  - OpenSpec: DALLAY-497
  - ADR-0013: RateLimitTier vs SubscriptionPlan
  - Issues/PRs: DALLAY-497

## Context

Profile Tailors has no billing system today. The product is AGPL-3.0-licensed, self-hosted, and in
pre-release. When commercial offerings are introduced, billing will need to support:

- Multiple subscription plans (Starter, Professional, Enterprise)
- Metered usage (e.g., posts published, workspaces, storage)
- Payment method management (cards, invoices)
- Invoicing and payment history
- Subscription lifecycle (trial, active, past-due, canceled, expired)
- Entitlement checks (feature gating based on subscription state)
- Integration with a payment provider (Stripe likely)

The Identity context already has `RateLimitTier` (see ADR-0013) for technical rate limiting. Billing
must not leak into Identity or other core contexts.

## Decision drivers

- Minimize coupling between billing and existing contexts
- Allow billing to be developed incrementally (MVP first, iterate)
- Support both self-hosted (license-key based) and SaaS (subscription) models
- Keep payment provider swappable (Stripe as initial, not exclusive)
- Support metered billing without redesign

## Decision

The billing capability will be structured as a **separate bounded context** (`billing`) following
hexagonal architecture within the modular monolith.

### Bounded context map

| Bounded Context | Responsibility | Depends on |
|---|---|---|
| **Billing** | Plans, subscriptions, payments, invoices, entitlements | Identity (principal lookup), shared kernel |
| **Identity** | Authentication, `RateLimitTier` | None |
| **Workspace** (Tenancy) | Multi-tenancy, memberships | Identity |

### Core domain model

```
SubscriptionPlan
  - id, name, description
  - entitlements: Set<Entitlement>
  - price: Money (or metered model)

Subscription
  - id, principalId, planId
  - status: Trial | Active | PastDue | Canceled | Expired
  - periodStart, periodEnd
  - cancelAtPeriodEnd: Boolean

Entitlement
  - type: MAX_WORKSPACES | MAX_POSTS | RATE_LIMIT_TIER | STORAGE_GB | FEATURE_FLAG
  - value: String (e.g., "10" for MAX_WORKSPACES, "PROFESSIONAL" for RATE_LIMIT_TIER)

PaymentMethod
  - id, principalId, provider (STRIPE), token, isDefault

Invoice
  - id, subscriptionId, amount, status (Pending | Paid | Overdue | Void)
  - periodStart, periodEnd, dueDate, paidAt
```

### Port abstractions

```
PaymentProviderPort          — charge, refund, createPaymentMethod, updatePaymentMethod
SubscriptionRepository       — save, findActiveByPrincipal, cancel
InvoiceRepository            — save, findOpenByPrincipal
EntitlementService           — resolve effective entitlements for a principal
```

### Integration points

- **Subscription lifecycle events**: `SubscriptionActivated`, `SubscriptionCanceled`,
  `SubscriptionExpired`, `PaymentFailed` — emitted via domain events.
- **Entitlement check API**: synchronous query from other contexts (e.g., "can principal P
  create workspace W?").

### Payment provider abstraction

`PaymentProviderPort` is a hexagonal output port. The initial adapter will be Stripe, but the
domain model and use cases must not import Stripe types. This allows:

- Swapping providers (e.g., Paddle, Lemon Squeezy)
- Testing with a mock provider
- Supporting self-hosted deployments that may use a different provider

### Metered billing readiness

`Entitlement.type` supports numeric values (`MAX_WORKSPACES`, `STORAGE_GB`) that a metered
subscription can count toward. The `Subscription` model supports usage-based pricing via an
optional `meteredUnits` field.

## Scope and boundaries

- Initial implementation (MVP): one `SubscriptionPlan` (Starter), Stripe checkout, webhook
  handling, basic entitlement checks.
- Future phases: recurring invoices, metered billing, admin dashboard, license keys for
  self-hosted.
- Out of scope for MVP: dunning, proration, multi-currency, tax calculation.

## Alternatives considered

### Embed billing in Identity

- Description: Add subscription fields (plan, stripeCustomerId) to `Principal`.
- Advantages: Simple, no new bounded context.
- Disadvantages: Couples identity to Stripe types, violates single responsibility, hard to test,
  impossible to swap providers.
- Reason rejected: Violates hexagonal architecture and would create a God entity.

### Library-only approach (no bounded context)

- Description: Use Stripe SDK directly from use cases without a billing domain model.
- Advantages: Fastest MVP.
- Disadvantages: Stripe leak everywhere, no domain model, hard to test, impossible to support
  self-hosted license model.
- Reason rejected: Short-term gain, long-term pain. Billing is core business logic, not
  infrastructure.

## Consequences

### Positive

- Clean separation of concerns — billing can be developed, tested, and deployed independently
- Payment provider swappable via `PaymentProviderPort`
- Self-hosted license model can coexist via a different adapter
- Domain events enable loose coupling with other contexts

### Negative

- Additional initial complexity (new module, new package structure)
- Cross-context queries for entitlement checks need a query interface or shared kernel type

### Risks

- Over-engineering the abstraction before knowing real Stripe integration details. Mitigated by
  iterative delivery — only the MVP abstractions are created upfront.
- Domain event delivery must be reliable (outbox pattern).

### Accepted trade-offs

- Stripe-specific features (e.g., Stripe Tax, Stripe Billing auto-dunning) may need adapter-level
  configuration that the domain model cannot fully abstract. These are acceptable leaks into the
  adapter layer.

## Compliance and enforcement

- ArchTest: billing module must not be imported by identity, publishing, or tenancy modules
  (dependency direction is billing → other contexts only via ports).
- ArchTest: billing domain must not import Stripe SDK or any infrastructure types.

## Verification

- [ ] Billing module compiles independently of Stripe dependencies in the domain and application
  layers.
- [ ] Stripe imports are only present in the `infrastructure` package of the billing context.

## Migration or remediation

None required. No billing code exists yet.

## Follow-up actions

- [ ] Create the `billing` module structure when billing work begins.
- [ ] Implement `PaymentProviderPort` with a Stripe adapter as first implementation.
- [ ] Wire subscription lifecycle events with outbox pattern.

## Revisit conditions

- If Stripe-specific features prove too complex to abstract cleanly, the `PaymentProviderPort`
  boundary may be relaxed to expose provider-specific options as `Map<String, Any>`.
