# Design: LinkedIn Integration Publication

## Technical Approach

Extend the existing `com.profiletailors.smp.publishing` bounded context — domain models, provider
ports, infrastructure adapters, credential gateway, and worker — to satisfy production LinkedIn
publishing requirements. The change is additive and refinement-oriented: no new bounded context, no
new HTTP client technology, no framework migration. The existing hexagonal structure (domain →
application → infrastructure) and provider-neutral ports remain the architectural backbone.

The implementation targets the spec's MVP scope: personal-profile text + image publishing, encrypted
token lifecycle with refresh-aware resolution, BLOCKED/FAILED publication lifecycle states,
idempotent worker execution, and durable notification events. Organization/page publishing,
video/PDF/carousel, comments/threads, and analytics remain gated/non-MVP.

## Architecture Decisions

### Decision: Expand SocialConnectionStatus enum with first-class states

| Option                                                                          | Tradeoff                                            | Decision                                              |
|---------------------------------------------------------------------------------|-----------------------------------------------------|-------------------------------------------------------|
| Reuse `ACTIVE/REVOKED/EXPIRED/ERROR` + reason field                             | Migration-light but ambiguous for UI/worker         | Rejected — status mapping is core to worker preflight |
| Add `PENDING/DISABLED/REQUIRES_RECONNECT/DELETED` as first-class DB enum values | Requires schema migration but gives clear semantics | **Chosen** — aligns spec, API, and worker behavior    |

**Rationale**: The spec mandates first-class `PENDING`, `DISABLED`, `REQUIRES_RECONNECT`, `DELETED`
states. Reusing existing values with reason strings creates ambiguity in worker preflight checks and
API contracts. Additive enum expansion with backward-compatible DB migration is the safest path.

### Decision: Refresh-aware credential resolver as a dedicated domain port

| Option                                                    | Tradeoff                                                            | Decision                                             |
|-----------------------------------------------------------|---------------------------------------------------------------------|------------------------------------------------------|
| Refresh inside `RealLinkedInPublisher.resolveAccessToken` | Hides complexity from publisher but blurs side effects              | Rejected — couples credential lifecycle to publisher |
| Refresh in `PublishingJobExecutor`                        | Explicit workflow phases but couples worker to credential semantics | Rejected — worker stays generic                      |
| Dedicated `RefreshAwareCredentialResolver` port           | Additional abstraction but clean separation                         | **Chosen** — aligns with hexagonal dependency rule   |

**Rationale**: The domain/application layer must not depend on credential refresh mechanics. A
dedicated port (`RefreshAwareCredentialResolver`) lets the worker call one method to get a
valid access token, while the infrastructure implementation handles refresh-ahead, single-flight
locking, reconnect triggering, and encrypted token persistence. The existing
`LinkedInCredentialGateway` becomes an internal detail of this resolver.

### Decision: Publication status expansion with BLOCKED state

| Option                                     | Tradeoff                                           | Decision                                     |
|--------------------------------------------|----------------------------------------------------|----------------------------------------------|
| Reuse `FAILED` for blocked publications    | Simpler enum but conflates terminal and reversible | Rejected — BLOCKED is reversible             |
| Add `BLOCKED` as a new `PublicationStatus` | Requires schema migration but matches spec         | **Chosen** — BLOCKED is distinct from FAILED |

**Rationale**: The spec defines `BLOCKED` as reversible (publications can retry when account
status restores to ACTIVE). `FAILED` is terminal for DELETED accounts and irrecoverable errors.
Conflating them breaks retry semantics.

### Decision: Worker preflight checks before provider calls

| Option                                                                        | Tradeoff                                      | Decision                                     |
|-------------------------------------------------------------------------------|-----------------------------------------------|----------------------------------------------|
| Let provider adapter fail and handle 401/403 reactively                       | Simpler worker but wastes API calls and quota | Rejected — wastes LinkedIn quota             |
| Worker checks account status + capability eligibility before calling provider | One extra DB read but saves provider calls    | **Chosen** — spec requires pre-call blocking |

**Rationale**: The spec mandates that publications for non-publishable accounts (DISABLED,
REQUIRES_RECONNECT, DELETED) MUST NOT call LinkedIn. A worker preflight gate checks social account
status and capability eligibility before dispatching to the provider adapter.

### Decision: Capability bundles as configuration-driven validation

| Option                                                    | Tradeoff                                     | Decision                                                 |
|-----------------------------------------------------------|----------------------------------------------|----------------------------------------------------------|
| Hardcode capability checks in validator                   | Simple but inflexible for gated capabilities | Rejected — cannot feature-flag capabilities              |
| Configuration-driven capability matrix with feature flags | More complex but supports phased rollout     | **Chosen** — aligns with spec's capability-bundled model |

**Rationale**: The spec requires explicit capability bundles (personal text, personal image,
organization text, organization image, video, PDF, carousel, comments). Each bundle has its own
scope requirements and MVP status. A configuration-driven validator with feature flags allows
phased rollout without code changes per capability.

## Data Flow

### Publication Worker Execution (MVP)

```
PublishingWorker.pollOnce()
  │
  ├─ claimNextDue(jobRepository)
  │
  └─ PublishingJobExecutor.executeClaim(claim)
       │
       ├─ Preflight Gate
       │   ├─ Load SocialAccount → check status != DISABLED/REQUIRES_RECONNECT/DELETED
       │   ├─ CapabilityValidator.validate(account, publication, assets)
       │   └─ Block → record BLOCKED status + notification event (skip provider call)
       │
       ├─ Credential Resolution
       │   └─ RefreshAwareCredentialResolver.resolve(account)
       │       ├─ Load encrypted credentials
       │       ├─ Check access token expiry → refresh if needed
       │       ├─ Check refresh token absolute expiry → REQUIRES_RECONNECT if expired
       │       └─ Return fresh access token or throw reconnect exception
       │
       ├─ Media Upload (if assets)
       │   └─ AssetUploader.uploadAsset(asset, content, context)
       │       ├─ Initialize → POST /rest/images
       │       ├─ Upload binary → PUT uploadUrl
       │       └─ Confirm status → poll until AVAILABLE
       │
       ├─ Post Creation
       │   └─ SocialPublisher.publish(command)
       │       ├─ Build post body (author URN, commentary, visibility, distribution)
       │       ├─ POST /rest/posts with required headers
       │       └─ Extract x-restli-id
       │
       └─ Result Persistence
           ├─ Record DeliveryAttempt (phase, outcome, remote ID)
           ├─ Mark publication PUBLISHED (externalPublicationId, publicUrl=null)
           └─ Record notification event
```

### Token Refresh Flow

```
RefreshAwareCredentialResolver.resolve(account)
  │
  ├─ Load encrypted LinkedInCredentials from gateway
  │
  ├─ Check access token expiry
  │   ├─ Not expired → return access token
  │   └─ Expired or within refresh-ahead window →
  │       ├─ Check refresh token exists and not past absolute expiry
  │       │   ├─ No refresh token or past absolute expiry → throw ReconnectRequiredException
  │       │   └─ Refresh token valid →
  │       │       ├─ Optimistic lock: read current version, attempt refresh, write with version = oldVersion + 1
  │       │       │   ├─ If write affects 0 rows (OptimisticLockException) → re-read credential record
  │       │       │   │   └─ Use refreshed token if another process already completed refresh
  │       │       │   └─ If write succeeds → proceed with refresh
  │       │       ├─ POST /oauth/v2/accessToken (grant_type=refresh_token)
  │       │       ├─ Success → persist new encrypted credentials atomically
  │       │       │   ├─ Update access token + expiry
  │       │       │   └─ Update refresh token if LinkedIn rotated it
  │       │       └─ Failure (invalid_grant/revoked) → throw ReconnectRequiredException
  │       │
  │       └─ Return new access token
```

## File Changes

| File                                                             | Action | Description                                                                                                                                                                                                                                                           |
|------------------------------------------------------------------|--------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `domain/PublishingModels.kt`                                     | Modify | Expand `SocialConnectionStatus` with `PENDING/DISABLED/REQUIRES_RECONNECT/DELETED`. Add `BLOCKED` to `PublicationStatus`. Add `publicUrl: String?` to `PublicationDraft`. Add `LinkedinCapabilityBundle` enum and `GrantedScopeBundle` data class to `SocialAccount`. |
| `domain/PublishingProviderPorts.kt`                              | Modify | Add `RefreshAwareCredentialResolver` port. Add `ReconnectRequiredException`. Extend `ProviderPublishResult` with `publicUrl`.                                                                                                                                         |
| `domain/PublishingRepositories.kt`                               | Modify | Add `markBlocked` to `PublicationRepository`. Add `NotificationEventRepository` port.                                                                                                                                                                                 |
| `domain/PublishingPolicies.kt`                                   | Modify | Add `markBlocked` to `PublicationLifecyclePolicy`. Add blocked transition rules.                                                                                                                                                                                      |
| `domain/NotificationEvent.kt`                                    | Create | Domain model for durable notification events (provider, account, publication, outcome, suggested action).                                                                                                                                                             |
| `application/PublishingApi.kt`                                   | Modify | Add `BLOCKED` to API-facing publication status. Add notification event query DTOs.                                                                                                                                                                                    |
| `application/PublishingHandlers.kt`                              | Modify | Add `ListPublicationsHandler` with filtering by state/account/date. Update `CreatePublicationHandler` to validate capability bundles.                                                                                                                                 |
| `infrastructure/credentials/LinkedInCredentialGateway.kt`        | Modify | Extend `LinkedInCredentials` with `refreshTokenExpiresAtEpochSeconds`, `lastRefreshAttemptAt`, `lastRefreshStatus`, `grantedScopes`.                                                                                                                                  |
| `infrastructure/credentials/RefreshAwareCredentialResolver.kt`   | Create | Infrastructure adapter implementing `RefreshAwareCredentialResolver` port. Handles refresh-ahead, single-flight locking, reconnect triggering.                                                                                                                        |
| `infrastructure/linkedin/LinkedInPublishingAdapters.kt`          | Modify | Remove direct `resolveAccessToken` from `RealLinkedInPublisher`. Update `RealLinkedInConnectionProvider` to persist expanded credential metadata. Update `LinkedInCapabilityValidator` to support configuration-driven capability bundles.                            |
| `infrastructure/linkedin/LinkedInAssetUploaderAdapters.kt`       | Modify | Update to use `/rest/images` for image initialization and `/rest/videos` for video initialization (replacing legacy `/rest/assets`). Add media-type-specific initialization and polling.                                                                              |
| `infrastructure/persistence/R2dbcPublishingRepositories.kt`      | Modify | Add `markBlocked` implementation. Add `NotificationEventRepository` implementation. Update queries for new status values.                                                                                                                                             |
| `infrastructure/persistence/R2dbcNotificationEventRepository.kt` | Create | R2DBC repository for durable notification events.                                                                                                                                                                                                                     |
| `infrastructure/scheduling/PublishingWorker.kt`                  | Modify | Add preflight gate (status + capability check) before provider dispatch. Add reconnect/ blocked classification.                                                                                                                                                       |
| `infrastructure/scheduling/PublishingSchedulingConfiguration.kt` | Modify | Wire `RefreshAwareCredentialResolver` and notification event publisher.                                                                                                                                                                                               |
| `infrastructure/http/PublishingControllers.kt`                   | Modify | Add publication list endpoint with filtering. Add notification events endpoint.                                                                                                                                                                                       |
| Liquibase migration                                              | Create | Add new enum values to `social_connections.status` and `social_accounts.status`. Add `public_url` column to `publications`. Add `notification_events` table. Add credential metadata columns to `secure_credentials`.                                                 |
| `apps/web/app/src/views/SchedulerView.vue`                       | Modify | Add monthly calendar view, fix time-axis to single left column, add LinkedIn channel filtering, add BLOCKED status indicators, add reconnect prompts                                                                                                                  |
| `apps/web/app/src/stores/scheduler.ts`                           | Modify | Add monthly view state, LinkedIn-only filter support, BLOCKED publication handling, reconnect prompt state                                                                                                                                                            |
| `apps/web/app/src/components/scheduler/CalendarGrid.vue`         | Modify | Refactor time-axis to single left column, add monthly view rendering                                                                                                                                                                                                  |
| `apps/web/app/src/components/scheduler/PostItem.vue`             | Modify | Add BLOCKED status indicator, reconnect action link                                                                                                                                                                                                                   |
| `apps/web/app/src/components/scheduler/ChannelFilter.vue`        | Modify | Support single-channel LinkedIn selection                                                                                                                                                                                                                             |

## Interfaces / Contracts

### Refresh-Aware Credential Resolver (Domain Port)

```kotlin
// publishing/domain/PublishingProviderPorts.kt (addition)
fun interface RefreshAwareCredentialResolver {
    /**
     * Returns a valid access token for the given social account.
     * Handles refresh-ahead, single-flight locking, and reconnect triggering.
     * Throws ReconnectRequiredException when refresh is impossible.
     */
    suspend fun resolve(account: SocialAccount): String
}

class ReconnectRequiredException(
    message: String,
    val reason: ReconnectReason,
) : IllegalStateException(message)

enum class ReconnectReason {
    REFRESH_TOKEN_EXPIRED,
    REFRESH_TOKEN_REVOKED,
    REFRESH_UNAVAILABLE,
    INVALID_GRANT,
    INSUFFICIENT_SCOPES,
}
```

### Notification Event (Domain Model)

```kotlin
// publishing/domain/NotificationEvent.kt (new file)
data class NotificationEvent(
    val id: String,
    val workspaceId: String,
    val provider: SocialProvider,
    val socialAccountId: String,
    val publicationId: String?,
    val category: NotificationCategory,
    val message: String,
    val suggestedAction: String?,
    val publicUrl: String?,
    val occurredAt: Instant,
)

enum class NotificationCategory {
    PUBLICATION_SUCCEEDED,
    PUBLICATION_FAILED,
    PUBLICATION_BLOCKED,
    RECONNECT_REQUIRED,
    CAPABILITY_DENIED,
    MEDIA_PROCESSING_FAILED,
    AMBIGUOUS_OUTCOME,
}
```

### Publication Status Expansion

```kotlin
// publishing/domain/PublishingModels.kt (modification)
enum class PublicationStatus {
    DRAFT, QUEUED, SCHEDULED, PROCESSING, PUBLISHED, BLOCKED, FAILED, CANCELLED,
}

enum class SocialConnectionStatus {
    PENDING, ACTIVE, DISABLED, REQUIRES_RECONNECT, DELETED, ERROR,
}
```

### BLOCKED Auto-Retry with Exponential Backoff

BLOCKED publications MUST be automatically retried when the account status restores to ACTIVE. The
retry strategy uses exponential backoff:

- Initial delay: 1 minute
- Backoff multiplier: 2x
- Max delay: 1 hour
- Max retries: 5
- After max retries: transition to FAILED

Implementation: The `PublishingWorker` MUST include a BLOCKED-recovery scan that runs periodically (
e.g., every 5 minutes) to check for accounts that transitioned from non-publishable to ACTIVE, and
requeue any BLOCKED publications targeting those accounts.

### Notification Events Schema

Notification events MUST be persisted to a dedicated `notification_events` table:

```sql
CREATE TABLE notification_events (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    provider VARCHAR(50) NOT NULL,
    social_account_id UUID NOT NULL,
    publication_id UUID,
    category VARCHAR(50) NOT NULL,
    message TEXT NOT NULL,
    suggested_action TEXT,
    public_url TEXT,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notification_events_workspace ON notification_events(workspace_id, occurred_at DESC);
CREATE INDEX idx_notification_events_account ON notification_events(social_account_id, occurred_at DESC);
CREATE INDEX idx_notification_events_publication ON notification_events(publication_id);
```

## Testing Strategy

| Layer       | What to Test                                                                                                                                                               | Approach                                                            |
|-------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------|
| Unit        | `PublicationLifecyclePolicy.markBlocked` transitions, `RefreshAwareCredentialResolver` refresh/reconnect logic, `LinkedInCapabilityValidator` capability bundle evaluation | JUnit 5 + mockk, pure Kotlin tests                                  |
| Unit        | `LinkedInCapabilityValidator` rejects gated capabilities (video, PDF, carousel, comments)                                                                                  | Given/When/Then with `ProviderCapabilityValidationInput` fixtures   |
| Unit        | Worker preflight gate blocks DISABLED/REQUIRES_RECONNECT/DELETED accounts                                                                                                  | `PublishingJobExecutor` unit test with mock repositories            |
| Integration | `R2dbcLinkedInCredentialGateway` stores/loads expanded credential metadata                                                                                                 | Spring Boot test with Testcontainers PostgreSQL                     |
| Integration | `R2dbcNotificationEventRepository` persists and queries notification events                                                                                                | Spring Boot test with Testcontainers PostgreSQL                     |
| Integration | Full worker flow: preflight → credential resolution → publish → result persistence                                                                                         | `PublishingQueueIntegrationTest` extension with mock HTTP transport |
| Integration | Token refresh flow: expired access token → refresh → new token persisted                                                                                                   | Integration test with WireMockLinkedIn stub                         |
| E2E         | Personal text post end-to-end through worker with real LinkedIn sandbox                                                                                                    | Manual/scheduled test with LinkedIn Developer Portal sandbox        |

## Migration / Rollout

1. **Schema migration** (Liquibase): Add new enum values to status columns (backward-compatible,
   PostgreSQL `ALTER TYPE ... ADD VALUE IF NOT EXISTS`). Add `public_url` column to `publications`
   (nullable). Add `notification_events` table. Add credential metadata columns to
   `secure_credentials` (nullable, populated on next token exchange).

2. **Feature flags**: Organization/page publishing, video/PDF/carousel, comments/threads each gated
   behind separate configuration flags. Personal text + image publishing enabled by default when
   LinkedIn credentials are configured.

3. **Backward compatibility**: Existing `ACTIVE`/`REVOKED`/`EXPIRED`/`ERROR` status values remain
   valid. New values (`PENDING`, `DISABLED`, `REQUIRES_RECONNECT`, `DELETED`) are additive. Existing
   publications with current statuses are unaffected.

4. **No data migration required**: Existing credential records are compatible. Expanded metadata
   fields are nullable and populated on next token exchange or refresh.

## Resolved Questions

| Question                                 | Resolution                                                                |
|------------------------------------------|---------------------------------------------------------------------------|
| Should BLOCKED publications auto-retry?  | Yes — exponential backoff, max 5 retries, initial 1min, max 1hr delay     |
| `/rest/images` vs `/rest/assets`?        | Migrate to `/rest/images` and `/rest/videos` (modern Posts API endpoints) |
| Locking strategy for credential refresh? | Optimistic lock via DB version column                                     |
| Notification events storage?             | Dedicated `notification_events` table                                     |

## LinkedIn-Version Header — Operational Concern

The `Linkedin-Version` header (format: `YYYYMM`) MUST NOT be hardcoded. It MUST be a configurable
value stored in application configuration (`application.yaml`) or a properties file. The operational
runbook MUST include:

- A quarterly review cadence to check for LinkedIn API version deprecations
- A process to update the version value when LinkedIn releases a new API version
- Monitoring of LinkedIn API response headers for deprecation warnings
- A deployment procedure to update the version without downtime

The version value SHOULD default to the current LinkedIn API version at time of deployment and be
updated as part of regular maintenance.
