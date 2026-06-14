# Tasks: LinkedIn Channel Avatar Support

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 250–350 |
| 400-line budget risk | Low |
| Chained PRs recommended | No |
| Suggested split | Single PR |
| Delivery strategy | single-pr |
| Chain strategy | size-exception |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: size-exception
400-line budget risk: Low

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Full backend + migration + frontend + tests | PR 1 | Additive change, all layers; single PR under 400 lines |

## Phase 1: Database Migration (blocking for rollout)

- [x] 1.1 Create `server/smp/src/main/resources/db/changelog/publishing/012-add-avatar-to-social-accounts.yaml` — Liquibase changeset adding nullable `avatar_url VARCHAR(1024)` to `social_accounts`. Update `db/changelog/publishing/db.changelog-publishing.yaml` to include it.
  - **Files**: `012-add-avatar-to-social-accounts.yaml`, `db.changelog-publishing.yaml`
  - **Estimate**: small
  - **Verification**: `./gradlew :server:smp:test` passes; Liquibase diff validates column exists

## Phase 2: Backend Core (blocking for rollout)

- [x] 2.1 Add `avatarUrl: String? = null` to `ProviderAccountProfile` in `PublishingProviderPorts.kt`
  - **Files**: `server/smp/src/main/kotlin/.../publishing/domain/PublishingProviderPorts.kt`
  - **Estimate**: small
  - **Verification**: Compile succeeds; existing tests pass

- [x] 2.2 Add `avatarUrl: String? = null` to `SocialAccount` in `PublishingModels.kt`
  - **Files**: `server/smp/src/main/kotlin/.../publishing/domain/PublishingModels.kt`
  - **Estimate**: small
  - **Verification**: Compile succeeds; existing tests pass

- [x] 2.3 Add `avatarUrl: String? = null` to `ConnectedSocialChannel` read model in `ConnectedSocialChannelReadRepository.kt`
  - **Files**: `server/smp/src/main/kotlin/.../publishing/domain/ConnectedSocialChannelReadRepository.kt`
  - **Estimate**: small
  - **Verification**: Compile succeeds

- [x] 2.4 Extend `LinkedInUserInfoResponse` with `@JsonProperty("picture") val picture: String? = null` and map `picture → avatarUrl` when building `ProviderConnectionResult.account` in `LinkedInPublishingAdapters.kt`. Validate URL is HTTPS (reject data-URI, non-HTTPS).
  - **Files**: `server/smp/src/main/kotlin/.../publishing/infrastructure/linkedin/LinkedInPublishingAdapters.kt`
  - **Estimate**: medium
  - **Verification**: Unit tests: picture present+valid → avatarUrl set; picture absent → null; picture data-URI → null

- [x] 2.5 Update R2DBC repository: add `avatar_url` to INSERT/SELECT/UPDATE SQL, update `toSocialAccount()` reader, update `bindSocialAccount()` binder (use `bindNullable`).
  - **Files**: `server/smp/src/main/kotlin/.../publishing/infrastructure/persistence/R2dbcPublishingConnectionRepositories.kt`
  - **Estimate**: medium
  - **Verification**: Repository unit/integration tests pass; H2 round-trip with avatarUrl works

- [x] 2.6 Update `ConnectedSocialChannel.toSummary()` and `PublishingApi.ConnectedSocialChannelSummary` DTO to include `avatarUrl: String? = null`
  - **Files**: `server/smp/src/main/kotlin/.../publishing/application/PublishingHandlers.kt`, `server/smp/src/main/kotlin/.../publishing/application/PublishingApi.kt`
  - **Estimate**: small
  - **Verification**: Handler unit test asserts avatarUrl copied; controller integration test asserts JSON contains avatarUrl

## Phase 3: Frontend

- [x] 3.1 Add `avatarUrl?: string` to `ConnectedSocialChannelSummary` interface and `Channel` type; map field in `apiChannelToChannel()` in `publishing.ts`
  - **Files**: `apps/web/app/src/stores/publishing.ts`
  - **Estimate**: small
  - **Verification**: Unit test for mapper populates avatarUrl

- [x] 3.2 Update sidebar channel rendering: render `<img :src="channel.avatarUrl" @error="onAvatarError(channel)" v-if="channel.avatarUrl && !channel.avatarLoadFailed">` with identical dimensions to badge; implement `onAvatarError` to set `avatarLoadFailed = true`; render provider badge fallback otherwise. Add `alt="{channel.displayName} avatar"`.
  - **Files**: `apps/web/app/src/App.vue`
  - **Estimate**: small
  - **Verification**: Component test — avatarUrl present → <img> rendered; avatarUrl absent → badge; error event → badge

- [x] 3.3 Update channel selector in CreatePostModal.vue with same avatar + fallback pattern
  - **Files**: `apps/web/app/src/components/CreatePostModal.vue`
  - **Estimate**: small
  - **Verification**: Component test — same three scenarios as 3.2

- [x] 3.4 Ensure consistent CSS sizing (32×32, same border-radius) between avatar <img> and fallback badge to avoid layout shift
  - **Files**: `apps/web/app/src/App.vue`, `apps/web/app/src/components/CreatePostModal.vue`
  - **Estimate**: small
  - **Verification**: Visual inspection; component tests confirm no layout shift class changes

## Phase 4: Tests

- [x] 4.1 Backend: extend `LinkedInPublishingAdaptersTest.kt` — test picture present/HTTPS, absent, data-URI, non-HTTPS scenarios
  - **Files**: `server/smp/src/test/kotlin/.../publishing/infrastructure/linkedin/LinkedInPublishingAdaptersTest.kt`
  - **Estimate**: small
  - **Verification**: `./gradlew :server:smp:test --tests *LinkedInPublishingAdaptersTest*`

- [x] 4.2 Backend: extend `PublishingHandlersTest.kt` — assert `toSummary()` copies avatarUrl
  - **Files**: `server/smp/src/test/kotlin/.../publishing/application/PublishingHandlersTest.kt`
  - **Estimate**: small
  - **Verification**: `./gradlew :server:smp:test --tests *PublishingHandlersTest*`

- [x] 4.3 Backend: extend `PublishingControllersTest.kt` — assert GET /api/publishing/channels JSON contains avatarUrl when present, omits when null
  - **Files**: `server/smp/src/test/kotlin/.../publishing/infrastructure/http/PublishingControllersTest.kt`
  - **Estimate**: small
  - **Verification**: `./gradlew :server:smp:test --tests *PublishingControllersTest*`

- [x] 4.4 Frontend: unit test for `apiChannelToChannel` mapping of avatarUrl in publishing store
  - **Files**: `apps/web/app/src/stores/publishing.test.ts`
  - **Estimate**: small
  - **Verification**: `pnpm test` in apps/web/app

- [x] 4.5 Frontend: component tests for App.vue and CreatePostModal.vue — avatarUrl present, absent, error fallback
  - **Files**: `apps/web/app/src/App.test.ts`, `apps/web/app/src/components/CreatePostModal.test.ts`
  - **Estimate**: medium
  - **Verification**: `pnpm test` in apps/web/app

## Phase 5: Observability & Rollout

- [x] 5.1 Add debug log in LinkedIn adapter when `picture` is parsed or rejected (non-HTTPS/data-URI)
  - **Files**: `server/smp/src/main/kotlin/.../publishing/infrastructure/linkedin/LinkedInPublishingAdapters.kt`
  - **Estimate**: small
  - **Verification**: Log message appears in test output for rejection scenarios

- [x] 5.2 Add counter metric `publishing.linkedin.avatar.persisted` incremented when avatarUrl is persisted
  - **Files**: `server/smp/src/main/kotlin/.../publishing/infrastructure/persistence/R2dbcPublishingConnectionRepositories.kt`
  - **Estimate**: small
  - **Verification**: Metric appears in actuator/prometheus endpoint (integration test or manual)

- [ ] 5.3 Staging verification: deploy backend to staging, connect LinkedIn test account, confirm avatar_url populated in DB and GET /api/publishing/channels returns avatarUrl
  - **Files**: N/A (manual)
  - **Estimate**: small
  - **Verification**: DB query shows avatar_url; API response includes field; SPA renders avatar
