# ADR-0024: Durable Admin-Mutable Operational Configuration

- Status: Accepted
- Date: 2026-09-19
- Decision owners: Principal Architect
- Scope: `identity` and `platformadmin` bounded contexts; any future Back Office-exposed runtime configuration value
- Supersedes: None
- Superseded by: None
- Related:
  - OpenSpec: `openspec/changes/672-registration-mode-config/`
  - Issues/PRs: GitHub #672

## Context

`registration.mode` (`OPEN`/`INVITE_ONLY`/`CLOSED`) has always bound once at process startup from
`SMP_REGISTRATION_MODE` via Spring `@ConfigurationProperties`. Changing it requires redeploying the
single-replica, `stop-first` `backend` service — real downtime for a value that legitimately needs
to change during live operation (for example, closing registration during an incident, or opening it
for a launch window).

No existing ADR (0001–0021) covers a value that must be: (1) admin-mutable at runtime without a
redeploy, (2) durable across restarts and redeploys, (3) auditable with before/after values, and
(4) safe by default when unset. Every prior admin-mutable state in this repository (users,
invitations, waitlist entries, platform role assignments) models a business entity with its own
table and lifecycle. Operational configuration is a different shape: a small, rarely-changed,
platform-wide toggle with no owning aggregate — and the RFC underlying the Back Office epic (#656)
frames "operational configuration" as a category, with registration mode as only the first value.
A registration-mode-only design would either force a second migration the first time a new
operational value is added, or tempt an unreviewed one-off table per value.

## Decision drivers

- Must not silently revert an admin's change on the next deploy (rules out in-memory-only state).
- Must stay correct if `backend` is ever scaled beyond `replicas: 1` (rules out any cache without an
  invalidation mechanism, since no cross-instance invalidation channel exists in this repository
  today).
- Must extend to additional operational values without a second architectural decision or a second
  migration per key.
- Must reuse the existing Back Office permission/audit/idempotency seams (`PlatformPermission`,
  `AdministrativeAuditPublisher`, `AdminAuditEvent`) rather than inventing a parallel mechanism.
- Must preserve today's fail-closed-to-`CLOSED` default when no explicit value has ever been set.

## Decision

Operational configuration values that Back Office can read and mutate at runtime MUST be persisted
in a single shared key/value table (`platform_operational_config`: `config_key` primary key,
`config_value`, `version`, `updated_at`), not in memory and not one table per value. Reads MUST be
read-through with no caching layer — every read queries the current row directly, matching the
zero-caching behavior the codebase already has for `RegistrationPolicy.evaluate()` today. Writes
MUST capture the previous and new value atomically in a single statement
(`UPDATE ... FROM (subquery) ... RETURNING previous, new`), not an optimistic-lock
compare-and-swap keyed to a client-supplied expected value: operational-configuration writes are
"set this now," not "apply my edit if nothing changed since I last read," so there is no
client-expected-previous-value to compare against.

Each operational configuration key MUST have: an owning bounded-context port (for example,
`RegistrationModeGateway` in `identity`) that reads/writes only its own key with a typed domain
value, a seed/fallback to the pre-existing Spring `@ConfigurationProperties` default when no row
exists yet (preserving today's fail-closed behavior with zero migration-time coupling to live
Spring property values), and an `AdminAuditEvent` action recording operator, previous value, and
new value. New operational configuration keys MUST reuse this same table and pattern rather than
introducing a second table or a second architectural shape.

## Scope and boundaries

- `identity` owns the `RegistrationMode` domain type and the `RegistrationModeGateway` port; it is
  the only bounded context permitted to interpret the `registration.mode` key's value.
- `platformadmin` composes the write path (permission check, audit, idempotency) exactly as it does
  for user-control commands; it MUST NOT interpret operational-configuration values itself, only
  pass them through to the owning bounded context's port.
- The shared table is infrastructure, not a bounded-context aggregate: it has no `@AggregateRoot`
  marker and no business invariants beyond key uniqueness and CHECK-constrained known keys/values.
  Future keys extend the CHECK constraint additively; they do not require a new table.

## Alternatives considered

### In-memory mutable holder (`AtomicReference`), no persistence

- Description: A thread-safe holder seeded from the existing Spring property at startup, mutated
  directly by an admin command.
- Advantages: Fastest to ship; no migration; trivially correct today given `replicas: 1`.
- Disadvantages: Silently reverts to the Spring-property default on every redeploy — directly
  contradicts the requirement that this be durable "operational configuration," not a transient
  toggle. Breaks the moment `backend` is scaled beyond one replica, with no cross-instance
  propagation.
- Reason rejected: Fails decision drivers 1 and 2 outright.

### DB-persisted value with an in-memory cache, invalidated on write

- Description: Same table, but reads hit a cached `AtomicReference` populated at startup and
  refreshed only on a local write.
- Advantages: Avoids a DB round-trip on the read path.
- Disadvantages: Reintroduces the exact cross-instance divergence problem decision driver 2 rules
  out — a write on one instance would leave every other instance silently stale with no invalidation
  channel (no pub/sub or LISTEN/NOTIFY mechanism exists in this repository). `evaluate()` already has
  zero caching today with no demonstrated latency problem, so this optimizes a cost that has not
  been shown to matter.
- Reason rejected: Speculative optimization that reintroduces the multi-instance correctness risk
  driver 2 is written to prevent.

### Optimistic-lock CAS keyed to a client-supplied expected value (mirrors `Invitation`/`WaitlistEntry`)

- Description: Reuse the `updateIfVersionMatches`-style compare-and-swap already used for
  `Invitation` and `WaitlistEntry`, where the write is rejected if the stored version does not match
  a version the client read earlier.
- Advantages: Consistent with the existing platformadmin concurrency idiom; well-understood by the
  team.
- Disadvantages: That pattern exists to reject a client acting on a value it fetched earlier and
  that has since changed underneath it. The Back Office "change registration mode" command carries
  no such client-read expected-previous-value — it is issued as "set this to X now," and the audit
  requirement is "record whatever the previous value actually was," not "reject if it changed."
  Forcing a CAS-with-client-expected-version shape here would require the write endpoint to
  round-trip a version it has no legitimate reason to know, adding complexity without matching the
  actual specified behavior (last-write-wins with an accurate before/after audit record).
- Reason rejected: Solves a problem this feature does not have; the single-statement
  `UPDATE ... FROM ... RETURNING` gives one round trip, one row lock, no retry loop, and an
  accurate atomic before/after capture without a mismatched client-version contract.

### One table per operational configuration value

- Description: A dedicated `registration_configuration` table (as first drafted before this ADR),
  with a future second table for the next operational value, and so on.
- Advantages: Slightly narrower per-table schema; no shared-key namespace to manage.
- Disadvantages: Every future operational value repeats the same migration/port/gateway/audit
  boilerplate and forces a second architectural review each time; contradicts the RFC's framing of
  "operational configuration" as a category the Back Office should support, not a one-off.
- Reason rejected: Does not meet decision driver 3 (extend without a second architectural decision).

## Consequences

### Positive

- Registration mode (and any future operational configuration value) is durable across restarts and
  redeploys, resolving the original operational pain point.
- Adding a second operational configuration key is a data-row addition plus a small owning-port
  adapter, not a new migration/table/architectural review.
- Reuses the existing Back Office permission/audit/idempotency seams exactly, so operators get a
  consistent experience and reviewers get a consistent pattern to check against.
- No new caching layer means no new class of cross-instance staleness bug to reason about.

### Negative

- Every read of an operational configuration value costs one indexed database round trip instead of
  an in-memory field access. For `registration.mode`, this read sits on the identity hot path
  (`register`, `capabilities/public`), so it adds latency to every registration attempt and every
  public-capabilities request, though the query is a single-row primary-key lookup.
- A shared key/value table is less type-safe at the schema level than a dedicated table per value;
  the CHECK constraint enforces known keys and each key's allowed values, pushing more validation
  into constraints and application code rather than the column type system.

### Risks

- If a future operational value has a materially different value shape (for example, a JSON blob or
  a list) rather than a small enum-like string, the shared `config_value VARCHAR` column may need a
  follow-up decision (a typed-value column per shape, or a documented JSON-string convention) — not
  addressed by this ADR.
- Concurrent writes to different keys are independent (no cross-key locking), which is correct for
  unrelated configuration values but means this table provides no multi-key transactional
  consistency if a future feature ever needs to change two operational values atomically together.

### Accepted trade-offs

- One additional indexed database read per identity-hot-path request is accepted in exchange for
  correctness (no cache-invalidation risk) and durability (no redeploy-revert risk). This trade-off
  should be revisited only if profiling shows a real latency problem, not preemptively.
- The shared-table shape trades per-value schema strictness for extensibility; this is accepted
  because the RFC explicitly frames this as a recurring capability, not a one-off.

## Compliance and enforcement

- Any new operational configuration value MUST reuse `platform_operational_config` and the pattern
  described here (owning bounded-context port, read-through no-cache, atomic
  `UPDATE ... FROM ... RETURNING` write, `AdminAuditEvent` on change) rather than introducing a new
  table or an in-memory holder.
- Code review MUST reject a new in-memory-only mutable configuration holder for any Back
  Office-exposed value without an explicit, separately reviewed exception recorded here or in a
  superseding ADR.

## Verification

- Integration test proving the seed/fallback path returns the pre-existing Spring-property default
  when no row exists.
- Integration test proving atomic previous/new value capture under concurrent writes (no lost
  update).
- BDD scenario proving a value set through Back Office survives a fresh read after a simulated
  restart (durability).

## Migration or remediation

No prior violations to remediate — this is the first durable admin-mutable operational
configuration value in the repository. `PropertyBackedRegistrationPolicy` (the current
startup-only implementation) is deleted, not deprecated in place, since keeping it as a second
`@Service` implementing the same port would create an ambiguous Spring bean.

## Follow-up actions

- [ ] Implement `platform_operational_config` table, `RegistrationModeGateway` port/adapter, and the
  Back Office read/write endpoints per `openspec/changes/672-registration-mode-config/design.md`.
- [ ] If a future operational value needs a non-string value shape, record that decision as a new
  ADR rather than silently extending this one.

## Revisit conditions

- If `backend` is scaled beyond `replicas: 1` and read latency on the identity hot path becomes a
  measured problem, revisit the "no cache" decision with a concrete invalidation mechanism proposal
  (for example, PostgreSQL `LISTEN`/`NOTIFY` or an existing event bus), not a local-only cache.
- If a future operational value requires atomic multi-key changes, revisit the shared-table
  transactional-scope assumption.
