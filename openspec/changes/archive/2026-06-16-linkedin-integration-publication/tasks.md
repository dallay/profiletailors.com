# Tasks: LinkedIn Integration Publication

## Review Workload Forecast

| Field                      | Value                                                               |
|----------------------------|---------------------------------------------------------------------|
| Review budget              | 400 changed lines                                                   |
| Estimated workload         | High                                                                |
| Chained PRs recommended    | Yes                                                                 |
| Proposed delivery strategy | feature-branch-chain                                                |
| Work-unit balance          | Domain (PR1) → Infra (PR2) → Frontend (PR3), each slice deliverable |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: feature-branch-chain
400-line budget risk: High

## Phase 1: Domain Model Expansion

- [x] 1.1 Add `PENDING`, `DISABLED`, `REQUIRES_RECONNECT`, `DELETED` to `SocialConnectionStatus`;
  add `BLOCKED` to `PublicationStatus` in `domain/PublishingModels.kt`
- [x] 1.2 Add `publicUrl: String?` to `PublicationDraft`; add `LinkedinCapabilityBundle` enum and
  `GrantedScopeBundle` in `domain/PublishingModels.kt`
- [x] 1.3 Create `domain/NotificationEvent.kt` with `NotificationEvent` data class and
  `NotificationCategory` enum
- [x] 1.4 Add `RefreshAwareCredentialResolver` port, `ReconnectRequiredException`, extend
  `ProviderPublishResult` in `domain/PublishingProviderPorts.kt`
- [x] 1.5 Add `markBlocked` to `PublicationRepository` and `NotificationEventRepository` port in
  `domain/PublishingRepositories.kt`
- [x] 1.6 Add `markBlocked` transition rules to `PublicationLifecyclePolicy` in
  `domain/PublishingPolicies.kt`

## Phase 2: Application Layer

- [x] 2.1 Add `BLOCKED` status and notification DTOs to `application/PublishingApi.kt`
- [x] 2.2 Add `ListPublicationsHandler` with filtering; update `CreatePublicationHandler` for
  capability validation in `application/PublishingHandlers.kt`

## Phase 3: Infrastructure — Credential Resolver

- [x] 3.1 Extend `LinkedInCredentials` with expiry/refresh metadata in
  `infrastructure/credentials/LinkedInCredentialGateway.kt`
- [x] 3.2 Create `RefreshAwareCredentialResolver.kt` — refresh-ahead, optimistic locking, reconnect
  triggering
- [x] 3.3 Unit test: resolver refresh success, expired token throws `ReconnectRequiredException`,
  lock conflict retries

## Phase 4: Infrastructure — LinkedIn Adapters

- [x] 4.1 Update `RealLinkedInPublisher` to use `RefreshAwareCredentialResolver` port; update
  `RealLinkedInConnectionProvider` to persist expanded metadata in `LinkedInPublishingAdapters.kt`
- [x] 4.2 Update `LinkedInCapabilityValidator` for config-driven capability bundles; update
  `LinkedInAssetUploaderAdapters.kt` to use `/rest/images`, enforce limits
- [x] 4.3 Unit test: capability validator rejects gated capabilities with fixtures

## Phase 5: Infrastructure — Persistence, Worker, API

- [x] 5.1 Add `markBlocked` implementation and update queries in `R2dbcPublishingRepositories.kt`
- [x] 5.2 Create `R2dbcNotificationEventRepository.kt`
- [x] 5.3 Update `PublishingWorker.kt` — preflight gate, reconnect/blocked classification,
  BLOCKED-recovery scan
- [x] 5.4 Wire resolver and notification publisher in `PublishingSchedulingConfiguration.kt`; add
  list endpoint in `PublishingControllers.kt`
- [ ] 5.5 Integration tests: credential gateway, notification repo, full worker flow with
  Testcontainers

## Phase 6: Schema Migration

- [x] 6.1 Liquibase migration: add enum values to status columns, `public_url` to publications,
  `notification_events` table, credential metadata columns
- [x] 6.2 Add configurable `linkedin.version` (YYYYMM) to `application.yaml`

## Phase 7: Frontend Scheduler

- [x] 7.1 Refactor `CalendarGrid.vue` — single left time-axis, add monthly view with status colors
- [x] 7.2 Add LinkedIn-only filter in `ChannelFilter.vue`; add BLOCKED indicator and reconnect
  prompt in `PostItem.vue` and `SchedulerView.vue`
- [x] 7.3 Update `scheduler.ts` store — monthly view state, LinkedIn filter, BLOCKED handling,
  reconnect state

## Phase 8: Verification

- [x] 8.1 Unit test: `PublicationLifecyclePolicy.markBlocked` transitions and retry backoff logic
- [x] 8.2 Unit test: worker preflight blocks non-publishable accounts without provider calls
- [x] 8.3 Run `./gradlew :server:smp:check` and `pnpm build` in frontend
