# Exploration: mcp-write-tools (DALLAY-590)

> **Status**: DRAFT — produced by the explore phase. Consumed by the propose phase.
>
> **Date**: 2026-08-27
>
> **Scope**: Five MCP write tools — `create_publication`, `edit_publication`,
> `delete_publication`, `cancel_publication`, `retry_publication` — for AI agents.
>
> **Author**: sdd-explore sub-agent (read-only)

This document records the investigation of the existing publishing and MCP modules to
answer the four ADR-0019 blocking questions. Every claim is backed by a path. Where
the codebase does not answer a question, it is recorded under **Gaps and unknowns**.

---

## 0. Critical finding before any investigation point

The DALLAY-434 archive (2026-08-27) claims that "Four tools return workspace-isolated
data through `/api/mcp`". This is **partially aspirational**: the four tool classes
exist but are **not registered with the Spring AI MCP transport**. The MCP module
ships no `@McpTool` annotated beans; nothing wires the `PublicationTools`,
`ChannelTools`, `ProviderTools` adapters into Spring AI's tool scanner.

Evidence:

- `server/smp/src/main/kotlin/com/profiletailors/smp/mcp/tools/PublicationTools.kt:27`
  declares `class PublicationTools(private val mediator: Mediator, private val errorMapper: McpErrorMapper)`
  — **plain class, no `@Component`, no `@McpTool`**.
- `git log --all -S "@McpTool" -- server/smp/` returns no commit that introduced a
  Spring-AI-annotated tool method. The closest is `28959924 feat(mcp): server module
  foundation + compatibility spike`, which contains the placeholder spike only.
- `server/smp/src/main/kotlin/com/profiletailors/smp/mcp/infrastructure/McpConfiguration.kt`
  registers only `McpRateLimitFilter`. No `ToolCallbackProvider`, no `@Component` on
  the four tool classes.
- `server/smp/src/main/kotlin/com/profiletailors/smp/mcp/infrastructure/tools/McpPingTool.kt:21`
  is a stub `@Component` whose comment says "real tool registration will be
  implemented when MCP transport API is available".
- The MCP archive's verify-report (DALLAY-434 close-out) and QA report both document
  "all four PRs cleared the CI gate", but the BDD scenarios at
  `server/smp/src/test/kotlin/com/profiletailors/smp/bdd/glue/McpToolsBddSteps.kt:121`
  assert only `response.status.value() == 200`, never the tool's payload. The BDD
  proves the transport answers JSON-RPC, not that it executes a real tool.

**Implication for DALLAY-590**: any write tool we add must be annotated with `@McpTool`
and registered, AND the four existing read tools must be wired at the same time
(otherwise the new tools appear but the four legacy ones stay dead). The propose
phase MUST include a "fix the read-tool wiring" prerequisite, not just "add five
tools". This is not a write-tool-specific risk; it is the prerequisite for any MCP
tool to be reachable.

---

## 1. Publishing commands and handlers

### 1.1 Command DTOs (inputs / outputs)

All five commands live in
`server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingApi.kt:49-99`:

```kotlin
data class CreatePublicationCommand(
    val socialAccountId: String,
    val title: String? = null,
    val bodyText: String? = null,
    val assetIds: List<String> = emptyList(),
    val scheduleMode: ScheduleMode,
    val scheduledFor: Instant? = null,
    val nextSlotAfter: Instant? = null,
    val priority: Boolean = false,
) : CommandWithResult<PublicationResult>

data class EditPublicationCommand(
    val publicationId: String,
    val title: String? = null,
    val bodyText: String? = null,
    val assetIds: List<String>? = null,    // null = preserve existing; empty list = clear
    val scheduleMode: ScheduleMode,
    val scheduledFor: Instant? = null,
    val nextSlotAfter: Instant? = null,
    val priority: Boolean = false,
) : CommandWithResult<PublicationResult>

data class CancelPublicationCommand(val publicationId: String) : CommandWithResult<PublicationResult>
data class DeletePublicationCommand(val publicationId: String) : CommandWithResult<PublicationResult>

data class RetryPublicationCommand(
    val publicationId: String,
    val scheduleMode: ScheduleMode? = null,   // defaults to current
    val scheduledFor: Instant? = null,
    val nextSlotAfter: Instant? = null,
    val priority: Boolean? = null,
) : CommandWithResult<PublicationResult>
```

The shared return type `PublicationResult` is at `PublishingApi.kt:117-132`:

```kotlin
data class PublicationResult(
    val publicationId: String,
    val workspaceId: String,
    val socialAccountId: String,
    val status: PublicationStatus,
    val scheduleMode: ScheduleMode,
    val priority: Boolean,
    val title: String?,
    val bodyText: String?,
    val assetIds: List<String>,
    val scheduledFor: Instant?,
    val nextSlotAfter: Instant?,
    val externalPublicationId: String? = null,
    val publicUrl: String? = null,
    val publishedAt: Instant? = null,
)
```

`PublicationResult` is the **enqueue acknowledgement**: it carries the assigned
`publicationId` and the resolved `status` (`DRAFT` / `QUEUED` / `SCHEDULED`), but it
**does not carry** `failedAt`, `lastErrorCode`, `lastErrorMessage`, `blockedReason`,
`retryCount`, `createdAt`, or `updatedAt` — those live only on `PublicationDraft`
(see `PublishingModels.kt:149-180`) and on `ListPublicationItem`
(`PublishingApi.kt:210-227`).

### 1.2 Handlers — synchronous enqueue, async delivery

All five handlers are coroutines that complete after a single transactional write +
enqueue. They do **not** block on the LinkedIn boundary.

`CreatePublicationHandler.kt:113-117` (Create):

```kotlin
val persisted = transactionRunner.runAtomically {
    val created = publicationRepository.createDraft(queued)
    publicationJobRepository.enqueue(newJobFor(created, now))
    created
}
return persisted.toResult()
```

`EditPublicationHandler.kt:97-101` (Edit):

```kotlin
val persisted = transactionRunner.runAtomically {
    publicationRepository.updateEditableDraft(queued).also { persisted ->
        publicationJobRepository.replaceForPublication(newJobFor(persisted, now))
    }
}
return persisted.toResult()
```

`DeletePublicationHandler.kt:185-201` (Delete): does not enqueue a job — it deletes
the publication if pre-delivery (`deleteUnpublished`), pauses any recurring
template, and records a `RECURRENCE_PAUSED` notification event.

`CancelPublicationHandler.kt:231-235` (Cancel):

```kotlin
transactionRunner.runAtomically {
    publicationRepository.markCancelled(cancelled.id, cancelledAt)
    publicationJobRepository.cancel(cancelled.id, cancelledAt)
}
```

`RetryPublicationHandler.kt:276-281` (Retry): transitions the publication out of
`FAILED` (via `PublicationLifecyclePolicy.prepareRetry`) and replaces the job.

**Failure modes** (handled by `PublicationLifecyclePolicy` at
`PublishingPolicies.kt:36-264`):

- `Create`: throws `PublicationValidationException` (e.g. empty body, missing
  schedule field, schedule in the past) and `ProviderCapabilityValidationInput`
  failures; `MediaServiceUnavailableException` if the media context times out
  (`CreatePublicationHandler.kt:144-166` — 5-second budget via
  `withTimeoutOrNull`).
- `Edit`: throws `PublicationEditNotAllowedException` if status is not in
  `preDeliveryMutableStatuses()` (`DRAFT|QUEUED|SCHEDULED`); `PublicationNotFoundException`.
- `Delete`: throws `PublicationDeletionNotAllowedException` if status not
  pre-delivery; `PublicationNotFoundException`.
- `Cancel`: throws `PublicationCancellationNotAllowedException` if status not
  pre-delivery.
- `Retry`: throws `PublicationRetryNotAllowedException` if status is not `FAILED`.

### 1.3 `PublicationStatus` and transitions

`PublicationStatus` (`PublishingModels.kt:34-43`):

```kotlin
enum class PublicationStatus {
    DRAFT, QUEUED, SCHEDULED, PROCESSING, PUBLISHED, BLOCKED, FAILED, CANCELLED,
}
```

Transition rules in `PublicationLifecyclePolicy` (verbatim from `PublishingPolicies.kt`):

- `validateForCreation` (37-46): `status` MUST start `DRAFT`; body text or
  at least one asset required; `scheduleMode` validated against
  `scheduledFor`/`nextSlotAfter`/`now`.
- `queue` (72-91): moves `DRAFT` → `QUEUED` (for `NOW`) or `SCHEDULED` (for
  `SCHEDULED_AT` / `NEXT_SLOT`). Throws `PublicationAlreadyTerminalException` if
  status is `PUBLISHED` or `CANCELLED`.
- `requireEditable` (48-52): `DRAFT|QUEUED|SCHEDULED` only.
- `requireDeletable` (54-58): same as edit.
- `requireCancellable` (60-64): same as edit (worker has not claimed yet).
- `requireRetryable` (66-70): `FAILED` only.
- `markProcessing` (101-108): `QUEUED|SCHEDULED` → `PROCESSING` (worker claim).
- `markPublished` (110-126): anything except `CANCELLED` → `PUBLISHED`.
- `markFailed` (128-138): any → `FAILED`.
- `markBlocked` (145-162): `QUEUED|SCHEDULED|PROCESSING` → `BLOCKED` (account
  status went `DISABLED` or `REQUIRES_RECONNECT`).
- `prepareRetry` (208-216): `FAILED` only → re-enters `QUEUED` or `SCHEDULED`
  based on `scheduleMode`.

### 1.4 Idempotency

**There is no idempotency key on `CreatePublicationCommand`,
`EditPublicationCommand`, `CancelPublicationCommand`, `DeletePublicationCommand`,
or `RetryPublicationCommand`.** The handler generates a fresh
`"pub-${UUID.randomUUID()}"` on every create (`CreatePublicationHandler.kt:89`),
so a retried `create_publication` call from an agent will create **two distinct
publications** with identical content. There is no dedup table, no
`(workspaceId, idempotencyKey)` lookup, and no `Idempotency-Key` HTTP header on
the REST controllers (`PublishingControllers.kt:236-294`).

Idempotency **does** exist for social-content replies:
`server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/SocialContentModels.kt:52`
defines `value class IdempotencyKey(val value: String)` and
`ReplyToSocialCommentCommandHandler.kt:59` enforces
`ReplyIdempotencyConflictException` on key reuse. The X-Idempotency-Key header
is sent to LinkedIn at
`server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/linkedin/LinkedInCommunityManagement.kt:283`.
This is a precedent that does **not** currently extend to publication writes.

The closest cross-cutting infrastructure is the `claim_version` optimistic
concurrency control on `PublicationJob`
(`PublishingModels.kt:184-203`, `R2dbcPublicationJobRepository` writes use
`WHERE claim_version = :claimVersion`). That guards the worker claim against
double-processing; it does not dedup a retried create.

---

## 2. Publishing controllers

`server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingControllers.kt:231-385`:

| Operation | HTTP method + path | Returns |
|---|---|---|
| Create | `POST /api/publishing/publications` | `200` with `PublicationResult` |
| Edit | `PATCH /api/publishing/publications/{publicationId}` | `200` with `PublicationResult` |
| Cancel | `POST /api/publishing/publications/{publicationId}/cancel` | `200` with `PublicationResult` |
| Delete | `DELETE /api/publishing/publications/{publicationId}` | `200` with `PublicationResult` (or 4xx) |
| Retry | `POST /api/publishing/publications/{publicationId}/retry` | `200` with `PublicationResult` |
| Quick create | `POST /api/publishing/publications/quick-create` | `200` with `PublicationResult` |
| Reschedule | `PATCH /api/publishing/publications/{publicationId}/reschedule` | `200` with `PublicationResult` |
| Calendar | `GET /api/publishing/publications/calendar` | `CalendarResponse` |
| List | `GET /api/publishing/publications` | `ListPublicationsResponse` |

Observations:

- All write endpoints use `version = "1"` (the versioned-media-type path
  filter; the API surface is `application/vnd.api.v1+json` per
  `McpSecurityConfiguration`-line `:92` of the IAM spec, `0009-jwt-and-httponly-cookie-authentication.md`,
  and the BDD contracts).
- Controllers are **synchronous** — they await `mediator.send(command)` and
  return the `PublicationResult` once the transactional write + job enqueue
  completes. They do **not** return a 202 with a polling URL, and there is no
  `Location` header pointing at a status resource.
- All write endpoints are workspace-scoped via `resourceContextProvider.requireWorkspaceContext()`
  in the handler; the controller itself does not take a workspace path
  variable (unlike `RecurringScheduleController`).
- Validation runs at the handler layer (`PublicationLifecyclePolicy`) and
  surfaces as `IllegalArgumentException` / domain exceptions, mapped to HTTP
  statuses by `@RestControllerAdvice`. There is no idempotency header
  consumed on any write controller.
- `PublicationUpsertRequest` (`PublishingControllers.kt:404-418`) requires a
  non-blank `socialAccountId` and `scheduleMode`; `PublicationRescheduleRequest`
  (`PublishingControllers.kt:432-442`) requires `scheduleMode` for reschedule.

The REST contract is therefore: **write → ack**. No async task ID, no `Location`,
no callback URL. The MCP write tools must follow the same contract unless ADR-0019
diverges.

---

## 3. Platform boundary integration (worker + queue)

The publish pipeline is split across two runtime processes that share the
database via the `publication_jobs` table:

- **Submitter (sync with HTTP)** — the write handlers above call
  `publicationJobRepository.enqueue(...)` or `replaceForPublication(...)` inside
  the same atomic transaction that persists the `PublicationDraft`.
- **Worker (async, periodic poll)** — `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/scheduling/`:
  - `PublishingWorkerProperties` reads `smp.publishing.worker.*` (poll interval,
    claim lease, max retries, retry backoff, blocked-recovery interval).
  - `PublishingSchedulingConfiguration:33-37` builds a `ThreadPoolTaskScheduler`
    with `poolSize = 2`, prefix `publishing-worker-`.
  - `PublishingWorkerLifecycle` starts on bean init (`:97-103`) and ticks the
    worker every `pollInterval`.
  - `PublishingWorker.runOnce()` calls `PublicationJobRepository.claimNextDue(...)`
    (`:74-78` — default lease `PT2M`) and hands off to
    `PublishingJobExecutor.executeClaim`.
  - `PublishingJobExecutor.executeClaim` (`PublishingWorker.kt:57-129`) is where
    the LinkedIn boundary is actually touched via the `SocialPublisher` port,
    with try/catch ladders for `ReconnectRequiredException`,
    `PublishingFailureException`, `AssetNotReadyException`,
    `MediaServiceUnavailableException`, `PublicationValidationException`,
    `RetryablePublishingException`, `ProviderUploadException`, and a generic
    fallback. Each catch routes to `handlePublishFailure`,
    `handleReconnectRequired`, etc. which transition the publication to
    `FAILED` / `BLOCKED` via `publicationRepository.markFailed` /
    `markBlocked`, persist a `DeliveryAttempt`, and emit a `NotificationEvent`.

**Where does a publish fail that the agent would care about?**

- Provider transient failure (HTTP 5xx, rate limit, expired token) →
  `handlePublishFailure` → `markFailed` (with reason category from
  `PublishingFailureCategory`).
- Provider permanent failure (invalid content, deleted post) → `markFailed`.
- Account status went `REQUIRES_RECONNECT` while job was inflight →
  `handleReconnectRequired` → `markBlocked` with reason
  `ACCOUNT_RECONNECT_REQUIRED`.
- Job lease expired without completion → the stale-job recovery worker
  (`PublishingWorkerProperties.staleGrace`, default 5 min per `PublishingApi.kt:254`)
  releases the claim so a later poll can re-claim; the publication itself
  stays in `PROCESSING` until the next claim succeeds or fails.

**There is no push mechanism from the worker back to the agent.** The only
side-effects of a worker transition are:

1. A row update on `publications` (visible via `list_publications` /
   `get_calendar` / a future `get_publication` tool — none of which exists
   today; the closest read is `ListPublicationItem`).
2. A row insert on `notification_events` (queryable via
   `ListNotificationEventsQuery`, not exposed over MCP today).
3. An SSE event on `ChannelEventStreamRegistry` (consumed by the dashboard
   `PublishingChannelController.streamEvents` at `:165-184`). The SSE stream
   is browser-targeted and includes only `CONNECTED_CHANNEL_UPDATED` /
   `CONNECTED_CHANNEL_REMOVED` — not `PUBLICATION_FAILED` /
   `PUBLICATION_PUBLISHED` transitions.

---

## 4. Existing MCP patterns to reuse vs extend

Files under `server/smp/src/main/kotlin/com/profiletailors/smp/mcp/`:

| File | Reuse as-is? | Extend? | New? |
|---|---|---|---|
| `McpSecurityConfiguration.kt` | **Yes** — JWT + audience + workspace, scope-agnostic | No | No |
| `security/McpJwtConverter.kt` | **Yes** | No | No |
| `security/McpAuthenticationToken.kt` | **Yes** | No | No |
| `security/McpWorkspaceContextResolver.kt` | **Yes** | No | No |
| `security/McpWorkspaceMembershipChecker.kt` | **Yes (stub returns `Mono.just(true)`)** — must become real before write tools ship | Yes | No |
| `infrastructure/security/McpToolInvocationAuthorizer.kt` | No — currently allows any `mcp:*` scope (`:24`) | **Yes** — must enforce per-tool scope (mirrors the read-path §3 of `openspec/changes/mcp-server/specs/mcp-server/spec.md`) | No |
| `infrastructure/McpErrorMapper.kt` | **Yes** — but `ApplicationError` codes today cover reads only (`invalid_date_range`, `forbidden`); need `publication_not_found`, `publication_state_conflict`, `publication_validation_failed`, `media_unavailable`, `workspace_mismatch` codes added (matching the REST controllers' error surface) | **Yes** | No |
| `infrastructure/McpRateLimitFilter.kt` | **Yes** — bucket registry; new write buckets needed | **Yes** — add `mcp-publications-write` bucket with budget tuned to write-side risk (recommended ≤ 20/min/workspace) | No |
| `infrastructure/McpToolInvocationAuditFact.kt` | **Yes** as data class, but **NOT emitted anywhere today** (no `grep -rn "McpToolInvocationAuditFact"` in main/, only in the test). | **Yes** — must be emitted (and the emission must be tested). For write tools, the fact should also carry `publicationId` so an agent that retries can resolve the original `tools/call` to its side-effects | **Yes** — add `publicationId: String?` and `toolCallId: String?` fields, and emit from the write adapter |
| `infrastructure/McpConfiguration.kt` | **Yes** | **Yes** — add `ToolCallbackProvider` bean that picks up the new `@McpTool` annotated adapter classes | No |
| `infrastructure/oauth/ResourceMetadataController.kt` | **Yes**, but `scopesSupported` at `:35` lists only `mcp:channels:read` and `mcp:publications:read` | **Yes** — add `mcp:publications:write` to the supported list | No |
| `tools/McpToolMetadata.kt` | No — only read tools registered today | **Yes** — add 5 write tools, each with `mcp:publications:write` scope and a new `mcp-publications-write` rate-limit bucket | No |
| `tools/PublicationTools.kt` | **No** — not annotated with `@McpTool` (see §0) | **Yes** — annotate existing methods with `@McpTool`/`@McpToolParam`, generate JSON schema, add the 5 write methods | No |
| `tools/ChannelTools.kt`, `tools/ProviderTools.kt` | **No** — same wiring gap as `PublicationTools.kt` | **Yes** — annotate to register (prerequisite for the write tools to ship without leaving the read path broken) | No |

The single most consequential reuse decision: the existing `McpErrorMapper`'s
`ApplicationError` type already carries `code`, `category`, `message`,
`retryable`, `correlationId`. The write tools should return the same shape
(encoded as JSON inside `CallToolResult` text content) so an agent's error
parser does not need a new branch for writes.

`@McpTool` return type for write tools: confirmed via Context7
(`/spring-projects/spring-ai`, `mcp-annotations-server.adoc`,
`mcp-annotations-examples.adoc`) that `@McpTool` methods may return
`CallToolResult` directly when the method needs to discriminate success vs
failure (vs returning a domain type). The Spring AI pattern:

```java
CallToolResult.builder()
    .isError(true)
    .addTextContent("Error: " + e.getMessage())
    .build();
```

is supported alongside the simpler `return MyResult` form. We do **not** need
`generateOutputSchema = true` if we return a `CallToolResult`; we do if we
return the `PublicationResult` domain type. Recommendation: return
`PublicationResult` (or `ApplicationError`) as the domain type for the happy
path, and throw `RuntimeException` to map to a Spring-AI-conveyed error for
the failure path — keeping the read-tool error shape unchanged.

---

## 5. Idempotency and audit precedent

**Audit today**: `McpToolInvocationAuditFact` (data class) exists at
`server/smp/src/main/kotlin/com/profiletailors/smp/mcp/infrastructure/McpToolInvocationAuditFact.kt:19-35`,
with `toolName`, `scopeChecked`, `grantedScopes`, `workspaceId`,
`correlationId`, `outcome` (`SUCCESS|DENIED|RATE_LIMITED|ERROR`). Its `toMap()`
produces the log-ready payload.

**Audit emission**: missing. The class has no publisher; nothing in the MCP
module calls `McpToolInvocationAuditFact(...)`. The QA report (DALLAY-434
close-out, see commit `9eeeb3ce`) claims "Audit fact records denials" but the
code says otherwise. The `McpRateLimitFilter` returns an `ApplicationError`
and lets the caller (Spring AI) translate it; the fact is never instantiated.

**Implication for write tools**:

1. We cannot rely on audit emission as a retry-resolution mechanism for
   agents, because it does not exist today.
2. We should fix this in DALLAY-590 by emitting the fact on every tool
   invocation, and by adding `publicationId: String?` (and possibly
   `toolCallId: String?` or a Spring AI request `id`) to the fact so a
   retried call can be reconciled against its prior outcome.
3. The audit channel itself is also a gap. `toMap()` returns a plain
   `Map<String, Any>`; there is no Kafka topic, no log appender, no
   observability backend wired. The DALLAY-590 propose phase must decide
   where audit facts land (existing notification topic? structured log?
   new Kafka topic?) — this is a sibling ADR question.

**Idempotency**: see §1.4. No existing precedent on the publication write
path. The `IdempotencyKey` value class (`SocialContentModels.kt:52`) is the
closest existing model; reusing it (rather than inventing a new one) is the
pony-tail choice.

---

## 6. Scope model extension

Existing scope model (DALLAY-434):

- `mcp:channels:read` — read-only MCP channels access
- `mcp:publications:read` — read-only MCP publications/calendar access

Keycloak realm config and protocol mapper are documented in
`openspec/changes/mcp-server/spikes/SPIKE_OUTCOME.md:118-180` (DCR +
pre-registered clients, no CIMD) and §3.4 (multi-audience mapper). The
audience/scope mappers are realm-side; no SMP code change was needed for
read scopes.

Adding `mcp:publications:write`:

1. **Realm side** — declare the scope on the client scope that issues
   MCP tokens; ensure the audience mapper copies it into `scope` claim.
   This is a Keycloak-admin change in the realm JSON, mirrored for local
   dev. Reference: SPIKE_OUTCOME.md §2.5 lists the three onboarding
   paths (DCR / pre-registered / CIMD-deferred); scope declaration is
   orthogonal to client onboarding.
2. **SMP side — `ResourceMetadataController.kt:35`** — extend
   `scopesSupported` to include `mcp:publications:write`. The list is
   RFC 9728 metadata, so omitting the new scope would hide it from
   well-behaved MCP clients.
3. **SMP side — `tools/McpToolMetadata.kt:11-14`** — register the new
   write tools with `mcp:publications:write` scope.
4. **SMP side — `McpToolInvocationAuthorizer.kt:22-24`** — currently
   allows any scope starting with `mcp:`. This is a placeholder; the
   propose phase must replace it with per-tool enforcement so that a
   `mcp:publications:read`-only token cannot invoke write tools.
5. **SMP side — media writes** — `create_publication` may also write a
   media asset (via `PublicationAssetRepository`). The current SPA flow
   uses a separate media library upload. For DALLAY-590 v1, we should
   NOT introduce a `mcp:media:write` scope — only `create_publication`
   with existing `assetIds` is supported. New media upload from agents
   is out of scope for this change.

**Authorization rule** (proposal stage): write scope is additive on top
of read scope — a token with `mcp:publications:write` MUST also be granted
`mcp:publications:read` to inspect its own work. That is a Keycloak client
config choice (granted scopes are additive), not a code change.

---

## 7. Spring AI 2.0 write-tool behaviour (Context7 confirmation)

Library: `/spring-projects/spring-ai`. Sources consulted:
`mcp-annotations-server.adoc`, `mcp-annotations-examples.adoc`,
`upgrade-notes.adoc`.

Findings (verbatim from the docs):

- `@McpTool` methods MAY return a Kotlin/Java domain type (the default —
  Spring AI serialises via Jackson). For non-primitive returns, set
  `generateOutputSchema = true` to publish a JSON schema in
  `tools/list`.
- `@McpTool` methods MAY return `CallToolResult` directly. The pattern
  shown for an explicit error:

```java
return CallToolResult.builder()
    .isError(true)
    .addTextContent("Error: " + e.getMessage())
    .build();
```

- A `RuntimeException` thrown from an `@McpTool` method is **automatically
  converted to an error `CallToolResult`** and conveyed to the model.
  Checked exceptions and `Error` instances propagate as protocol-level
  failures, not tool errors.
- `@McpTool.McpAnnotations` exposes client hints, including
  `destructiveHint` (default `true` for write tools) and
  `idempotentHint` (default `false`). Setting
  `idempotentHint = true` is appropriate for `cancel_publication` /
  `delete_publication` (idempotent terminal transitions) and inappropriate
  for `create_publication` / `edit_publication` / `retry_publication`
  (each invocation has side-effects).
- The MCP SDK 2.0 breaking changes (per `upgrade-notes.adoc`) require
  `CallToolRequest.builder(name)` (no-arg deprecated) and enforce
  required fields at construction. This affects ONLY direct builders,
  not the annotation-driven flow we are using.

**Recommendation surfaced from the docs**: the existing
`PublicationTools.listPublications` and `getCalendar` already use
the simplest pattern (return domain type, throw on error). We should
extend that pattern — return `PublicationResult` from the 5 write tools,
throw `RuntimeException` carrying the `ApplicationError` on failure —
rather than introduce `CallToolResult` return shapes that diverge from
the read path.

The one place `CallToolResult` is warranted: if we need to surface a
discriminated outcome (e.g. "enqueued but not yet delivered"), then a
`CallToolResult.builder().addTextContent(json).build()` is the clean
way to attach structured metadata alongside the enqueue ack. The
propose phase will decide whether this is needed.

---

## 8. OpenSpec precedent — DALLAY-416 (AI-Powered Post Generator)

DALLAY-416 is the consumer of DALLAY-590. Searching the repo:

- No `openspec/changes/dallay-416*/` directory exists yet.
- The `mcp-server/specs/iam/spec.md` and `mcp-server/specs/workspace-scoped-oauth/spec.md`
  deltas from DALLAY-434 establish that MCP tokens carry workspace_id
  and audience; they do not specify the write-tool contract.
- `openspec/changes/private-beta-launch-readiness/specs/iam/spec.md`
  and `openspec/changes/private-beta-launch-readiness/specs/publishing/spec.md`
  reference the publishing surface but do not anticipate agent writes.

**There is no existing consumer contract** for what DALLAY-416 expects
from a write tool. The propose phase must either (a) coordinate with
DALLAY-416 to capture its expectations, or (b) define a minimal
contract (enqueue ack + `list_publications` for follow-up) that gives
the consumer enough to build on. Given the consumer is also in early design,
option (b) is the pony-tail choice.

---

## Gaps and unknowns

These are questions the codebase does NOT answer. Each one is a risk if
the ADR makes a decision without resolving it.

1. **No `@McpTool` annotated classes exist today** (see §0). The four
   read tools are not registered with the Spring AI transport. The write
   tools cannot ship without resolving this; resolving it requires
   wiring at least the existing read tools plus the new write tools in
   the same change.

2. **`McpToolInvocationAuditFact` is never emitted** (see §5). The
   proposal must decide where audit facts land (structured log via
   LoggerFactory? Reuse the existing notification topic? New Kafka
   topic?) and ensure the emission point lives in the same code path as
   the tool execution.

3. **`McpToolInvocationAuthorizer` is permissive** (see §6). Today it
   accepts any `mcp:*` scope. Per-tool enforcement is required before
   write tools can be safely exposed.

4. **`McpWorkspaceMembershipChecker` is a stub returning `true`** (see
   §4). This is acceptable for the read tools because the underlying
   queries are workspace-scoped, but the write tools gain direct
   mutation authority; a real membership check is required before
   shipping write tools to anyone other than the test harness.

5. **No `Idempotency-Key` header on the publishing REST controllers**.
   Whether the API consumer (DALLAY-416 or future) is expected to send
   `Idempotency-Key`, or whether the new contract will be tool-call-id
   based, is undecided.

6. **No real-time signal to the agent on `FAILED` / `PUBLISHED` /
   `BLOCKED` transitions** beyond what it can poll via `list_publications`.
   The SSE channel-event stream covers only connected-channel changes,
   not publication state transitions. If the ADR chooses a synchronous
   publish boundary (option 1(b) in §1), the failure model already has
   a delivery path; if it chooses enqueue-ack (1(a)), then the agent
   must poll.

7. **`PublicationResult` omits failure metadata**. After enqueue, the
   agent that retries must call `list_publications(status=...)` to see
   `failedAt`, `lastErrorCode`, `lastErrorMessage`, `blockedReason`. No
   `get_publication` tool exists yet; this is either an ADR scope
   decision (add `get_publication`) or a deliberate deferral.

8. **Per-workspace rate-limit budget for write tools**. Read buckets
   today are 60/min and 30/min (`McpRateLimitFilter.kt:21-24`). A
   write bucket should be lower (LinkedIn accepts ~100 posts/day per
   account, not per minute), but the right number depends on the
   consumer DALLAY-416's behaviour.

9. **Conflict detection between concurrent agent creates**. A workspace
   may have multiple MCP clients (Cursor, Claude Desktop) acting on
   behalf of the same user. `ConflictDetectionPolicy`
   (`PublishingPolicies.kt:307-353`) flags overlapping schedules at
   calendar-read time, but the create handler does not check conflicts
   before queueing. The agent can create overlapping publications and
   only learn about the conflict later. This is the existing SPA
   behaviour too — out of scope for DALLAY-590 to fix, but the ADR
   should call it out.

10. **No canonical `toolCallId` ↔ `publicationId` mapping**. Even if we
    add `toolCallId` to `McpToolInvocationAuditFact`, the MCP JSON-RPC
    request `id` is opaque to the application layer (Spring AI does not
    necessarily surface it on the tool method). The propose phase must
    verify whether Spring AI 2.0 surfaces the JSON-RPC `id` to tool
    methods, or whether correlation must rely on a client-supplied
    `idempotencyKey` field.

---

## Reuse map

What this change will reuse unchanged:

- `McpSecurityConfiguration.kt` — JWT + audience + workspace chain.
- `security/McpJwtConverter.kt`, `security/McpAuthenticationToken.kt`,
  `security/McpWorkspaceContextResolver.kt` — authentication contract.
- `infrastructure/McpErrorMapper.kt` — error mapping (with new error
  codes added).
- `infrastructure/McpRateLimitFilter.kt` — rate limit filter (with new
  bucket added).
- `infrastructure/oauth/ResourceMetadataController.kt` — RFC 9728
  metadata (with `scopesSupported` extended).
- `tools/McpToolMetadata.kt` — registry (with 5 new entries).
- All five publish command DTOs (`PublishingApi.kt:49-99`).
- All five publish handlers (read-only from MCP's perspective; we add
  the adapter layer on top).
- `PublicationLifecyclePolicy` and all exception types in
  `PublishingPolicies.kt` — domain invariants untouched.

What this change will extend (modify in place):

- `infrastructure/McpConfiguration.kt` — add `ToolCallbackProvider` bean
  (prerequisite for any tool to be reachable).
- `infrastructure/McpToolInvocationAuditFact.kt` — add `publicationId` /
  `toolCallId` optional fields.
- `infrastructure/security/McpToolInvocationAuthorizer.kt` — replace
  permissive check with per-tool scope enforcement from
  `McpToolMetadata`.
- `security/McpWorkspaceMembershipChecker.kt` — replace stub with real
  membership query.
- `infrastructure/McpErrorMapper.kt` — add write-specific error codes.
- `infrastructure/McpRateLimitFilter.kt` — add `mcp-publications-write`
  bucket.
- `infrastructure/oauth/ResourceMetadataController.kt` — extend
  `scopesSupported`.
- `tools/PublicationTools.kt` — add `@McpTool` annotations to the 4
  read methods + add 5 write methods.
- `tools/ChannelTools.kt`, `tools/ProviderTools.kt` — annotate with
  `@McpTool` to fix the read-path wiring (prerequisite, not new work).

What this change will add new:

- `tools/PublicationWriteTools.kt` (or merge into existing
  `PublicationTools.kt`) — 5 new methods, each `@McpTool`-annotated,
  suspending, mapped to the existing mediator commands. Annotation
  metadata: `annotations = @McpTool.McpAnnotations(
  destructiveHint = true, idempotentHint = false)` for create/edit/retry,
  `idempotentHint = true` for cancel/delete.
- `application/McpWriteAuditEmitter.kt` (or `infrastructure/`) — single
  point that emits `McpToolInvocationAuditFact` for every tool call,
  including the `publicationId` extracted from the `PublicationResult`
  when available.
- `application/IdempotencyRecordRepository.kt` + R2DBC adapter +
  `idempotency_records` migration — if the ADR-0019 decision is to add
  client-supplied idempotency keys to the write tools.
- New BDD scenarios under `server/smp/src/test/resources/features/`
  covering each write tool's happy path + 4 representative failure
  modes per tool.
- `openspec/changes/mcp-write-tools/specs/mcp-server/spec.md` — delta
  for the `mcp-server` capability extending the catalog to 9 tools and
  the scope list to include `mcp:publications:write`.
- `openspec/changes/mcp-write-tools/specs/iam/spec.md` — delta for IAM
  documenting the new write scope and the realm-side configuration
  required.
- ADR-0019 (`docs/architecture/adr/0019-mcp-write-tools.md`) — the
  decisions for the four blocking questions (companion doc; this
  exploration cites it but does not draft it).