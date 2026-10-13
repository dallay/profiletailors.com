# ADR-0028: Defer Shortlinks Capabilities Beyond Core V1

- Status: Accepted
- Date: 2026-10-04 (original deferral); amended 2026-10-09
- Decision owners: Principal Architect
- Scope: `server/smp/shortlinks`, repository-wide documentation and OpenSpec workflow
- Supersedes: None
- Superseded by: None
- Related:
  - OpenSpec: `.agents/sdd/changes/archive/2026-10-04-url-shortener-beyond-core-v1/`
  - PR #1305 `feat(shortlinks): add Core V1 backend with idempotent management and safe redirect`
  - Issue tracking: deferred to a future change; not yet assigned

## Context

Profile Tailors' URL shortener was delivered as Core V1 in PR #1305 (HEAD
`3178a883c2936d04b083a9d184e81e6a921504c5`). Core V1 covers the link lifecycle
(create, read, edit, disable, enable, delete), ownership/workspace scoping,
idempotency, status-aware redirect semantics, hex/aggregate-friendly domain
layering and the production-quality test surfaces required by the repository
constitution.

A subsequent proposal requested extending the shortener with an extensive feature
set: durable async click events with outbox + edge-log reconciliation,
Cloudflare for SaaS custom-hostname provisioning, link-level analytics APIs,
GDPR-grade privacy lifecycle, QR generation, abuse and quarantine automation,
SLO/capacity claims (99.99%, 250k redirects/s burst, etc.) and load-test
verification. Detailed operational contracts for those capabilities were
produced in a Drive document
`url-shortener-system-design.md` and incrementally incorporated into the SDD
artifacts.

After review, the product team concluded that those capabilities were designed
for a system with significant live traffic and regulated personal data, while
Profile Tailors is currently at early-access with no active shortlink users.
The full plan would add substantial backend complexity (broker cluster,
Logpush→R2 reconciliation pipeline, secret rotation, custom-hostname polling,
analytics API surface, QR encoder dependency) without an immediate user need.

## Decision drivers

- No active shortlink users today; first access path is internal.
- Core V1 already covers the operational primitives the product needs now.
- Capability surface area must be proportional to current usage to keep the
  constitution's hexagonal/DDD contracts, simplicity and review budget healthy.
- Deferred capabilities must be re-introducible without rework when the
  trigger conditions are met.

## Decision

1. The change `url-shortener-beyond-core-v1` is withdrawn. Its SDD artifacts
   move to `.agents/sdd/changes/archive/2026-10-04-url-shortener-beyond-core-v1/`
   and must not be reused verbatim.
2. Remaining work on Core V1 (test hardening, light verification, follow-up
   cleanup) is tracked as a non-versioned RPI task in the agent's local
   workspace (see `docs/README.md` → "RPI artifacts" for the policy), not as
   a new SDD cycle.
3. The deferred capabilities (asynchronous click capture, custom domains,
   analytics, QR, abuse/quarantine automation, SLO/capacity programs) will be
   re-opened only when an explicit trigger is met. Triggers are listed below;
   any of them may initiate a new SDD change.
4. Adding the deferred capabilities now, in part or as a whole, requires a new
   OpenSpec cycle. Designers MAY read the archived exploration/proposal/specs
   for context, but MUST regenerate specs and design against the current
   repository state and updated product requirements.

## Amendment — 2026-10-09

The publishing-shortlinks and workspace click-metrics slice is authorized as a bounded exception to the original deferral. At publication submit, shortlink creation is automatic and best-effort: on shortlink creation failure, the original URL remains usable and publication submission is not blocked. The authoritative publishing contract is `.agents/sdd/specs/publishing-shortlinks/spec.md`. This amendment does not authorize the remaining advanced capabilities listed below; they remain deferred until their stated triggers are met.

## Scope and boundaries

- The deferred capabilities are: durable click delivery and `ClickRecorded`
  outbox, edge-log reconciliation, custom-domain provisioning through
  Cloudflare for SaaS, link-level analytics APIs (`summary`, `timeseries`,
  `breakdown`), QR encoder integration (ZXing core 3.5.4), abuse and
  quarantine workflows, capacity/SLO programs and load-test infrastructure.
- RPI #1305 verification and Core V1 maintenance remain in scope as ordinary
  engineering work.
- Core V1 MUST continue to satisfy its MUSTs: HTTP/HTTPS-only destinations with
  422 on other schemes; UUIDv7 IDs; `Base62` 10-char cryptographic codes; alias
  reserved list; workspace scoping; idempotency 24 h; `If-Match` 412; 302 +
  `no-store` redirect; status-aware response codes; L1 ≤30 s + Redis ≤5 min
  with negative caching only after primary confirmation; cache TTL clamped
  by `expiresAt`; invalidate ≤5 s, quarantine immediately; redirect MUST NOT
  fetch the destination.

## Alternatives considered

### Proceed with three stacked PRs (delivery, domains, analytics+QR)

- Description: Execute the full plan written in the Drive document and the SDD
  artifacts.
- Advantages: Full capability surface immediately; extensive normative coverage.
- Disadvantages: Adds substantial infrastructure (broker, Logpush→R2,
  Cloudflare for SaaS pipeline, secret rotation, analytics surface, QR
  encoder) without current users. Increases review budget and risk; weakens
  constitution's simplicity principle.
- Reason rejected: Current product stage does not justify the operational
  footprint; triggers below cover the moment when it will.

### Implement only the analysis-only or QR-only slices

- Description: Pick the smallest capability increments and ship them now.
- Advantages: Lower upfront cost.
- Disadvantages: Arbitrary scope selection; still adds infrastructure not yet
  needed; mixes incompatible ownership models with Core V1.
- Reason rejected: Trigger conditions below make clearer product-led
  decisions; pick-on-the-fly creates orphan capabilities that re-architecture
  rules forbid.

### Defer to existing capability trigger (chosen)

- Description: Archive the change, keep Core V1 as the live truth, track
  remaining Core V1 work as RPI; revive only when triggers are observed.
- Advantages: Honest scope-to-need ratio; constitution-aligned; preserves the
  Drive document as background reading without committing to it.
- Disadvantages: Context must be rebuilt when the time arrives; risk of drift
  between Drive document and repository at that point.
- Mitigation: The triggers and `Related:` references below make that rebuilding
  observable, and the Drive document itself is the explicit starting point.

## Consequences

### Positive

- Shortlinks surface stays proportional to current product usage.
- Review budget and onboarding cost remain low.
- Core V1 hardening proceeds without an artificial second cycle.
- The architectural intent behind the Drive document is preserved as a
  reference rather than forgotten or partially adopted.

### Negative

- Capabilities the Drive document calls out are not yet validated against the
  current codebase.
- Activation later requires explicitly re-scoping and re-spec'ing against
  the present repository state.

### Risks

- Drift between Drive document and repository when the trigger fires.
  Mitigation: rebuild specs/design/tasks; do not reuse archived artifacts
  verbatim.
- A product-led decision to ship one of the deferred slices before any
  trigger fires must not bypass this ADR.

### Accepted trade-offs

- The Drive document remains in Google Drive only; it is not committed to
  the repository. Its design intent is captured in this ADR, not in code.
- Core V1's MUSTs are sufficient for the current product. Limits, retention
  and load-test programs are deferred until real traffic exists.

## Compliance and enforcement

- New work that introduces durable click delivery, custom-domain provisioning,
  link analytics, QR endpoints, broker/Logpush adapters or SLO/capacity
  programs MUST open a fresh OpenSpec change and reference this ADR.
- Static-analysis debt rules in `.agents/AGENTS.md` continue to apply; this
  ADR does not authorise any suppressions or baseline entries.

## Bounded follow-up decision: publication shortlinks and workspace click counts

On 2026-10-06, the product decision explicitly authorizes the scoped change in
`.agents/sdd/changes/workspace-shortlinks-post-clicks/`. This is a narrow exception to the
original deferral, not a reversal of it: the approved scope is explicit shortlink integration
when composing publications and workspace-isolated counts of successful shortlink redirects.

The design keeps link ownership in Core V1, derives tenant identity from authenticated workspace
context for protected analytics, and leaves public redirects free of management/analytics data.
It excludes durable outbox delivery, Edge/Logpush reconciliation, custom domains, QR, retention
policy, visitor identity/PII, abuse automation, and capacity/SLO guarantees. Clicks are proposed as
non-deduplicated recorded active-link redirect attempts, without identity data or an accuracy
promise. Tracking must not block the redirect on failure; redirect latency remains a verification
risk and must be measured before accepting this mechanism.

Post composition must make shortening explicit and present the resulting URL. The original URL and
content remain unchanged unless the user opts in; if shortening fails, the original destination is
preserved and publication may proceed with it or the user may retry. Link creation is proposed
before publication submission because the existing publication handler persists/enqueues the
submitted body. This decision is bounded to the named OpenSpec change; it does not authorize the
larger deferred shortlinks design.

## Verification

- The change directory is archived under `.agents/sdd/changes/archive/2026-10-04-url-shortener-beyond-core-v1/`.
- `.agents/sdd/changes/archive/2026-10-04-url-shortener-beyond-core-v1/state.yaml`
  declares `current_phase: archive` with `next: null`.
- This ADR is referenced from `docs/architecture/adr/README.md`.
- RPI work for Core V1 hardening continues in the agent's local, non-versioned
  RPI workspace. See `docs/README.md` → "RPI artifacts" for the policy.

## Migration or remediation

None. Core V1 is unchanged. Re-activation requires a fresh OpenSpec change.

## Follow-up actions

- [x] Move SDD change into the archive folder with `current_phase: archive`.
- [x] Update RPI task to make Core V1 hardening the only active scope.
- [x] Add this ADR and update `docs/architecture/adr/README.md` index.
- [ ] When any trigger fires, open a new SDD change with a refreshed
      exploration/proposal/spec/design/tasks.

## Revisit conditions

Any of the following revives the deferred capabilities under a fresh SDD
change:

- Sustained shortlink traffic above a low single-digit RPS for a full month.
- Adoption of shortlinks by an external-facing customer or cohort.
- A regulated privacy request (right-to-erasure, lawful access) for a
  shortlink's data.
- A production incident that requires quarantine/automation, durability or
  observability beyond what Core V1 provides.
- Adoption of the marketing surface's redirect/share feature in production.
- A product request to add QR generation, link analytics or custom domains.
