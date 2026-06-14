# Design: LinkedIn Channel Avatar Support

## Technical Approach

Add an optional avatarUrl that flows from LinkedIn OIDC userinfo through the LinkedIn adapter into the provider profile, persist it on the SocialAccount row, surface it in read models and the publishing API, map it into the frontend publishing store, and render it in the SPA with a graceful fallback for missing or broken images. The implementation follows existing patterns in the publishing bounded context (hexagonal, provider ports, r2dbc repositories, DTO mapping) and reuses current connection/sync flows — avatar is additive and optional.

## Architecture Decisions

### Decision: Add nullable avatar_url column to social_accounts

**Choice**: Create a Liquibase changeset to add `avatar_url VARCHAR(1024)` nullable to `social_accounts`.

**Alternatives considered**:
- Create a separate table for account media metadata — rejected for scope and KISS: single optional URL is simpler and more efficient for reads.
- Proxy/copy images to internal CDN or storage — rejected as out of scope; existing plan uses remote URL only.

**Rationale**: The change is additive; a nullable column is backwards compatible and requires no data migration. 1024 length gives room for long provider URLs and query strings.

### Decision: Keep avatarUrl optional everywhere

**Choice**: Domain model, provider profile, API DTOs, and frontend fields are nullable/optional.

**Alternatives considered**:
- Make avatar required for LinkedIn connections — rejected because LinkedIn may omit picture or user profile may not expose it.

**Rationale**: Avoids breaking existing connections and preserves fallback UI.

### Decision: Map LinkedIn OIDC `userinfo.picture` directly into avatarUrl

**Choice**: Read `picture` (if present) from `/v2/userinfo` response and map to ProviderAccountProfile.avatarUrl.

**Alternatives considered**:
- Parse complex profile picture structures or proxy to a thumbnail service — rejected as out of scope.

**Rationale**: Simpler mapping and aligns with the proposal; preserves privacy (no image bytes stored).

### Decision: Render remote image, fall back to provider badge when error

**Choice**: Use <img :src="channel.avatarUrl || undefined"> (Vue binding) and an @error handler to flip a reactive fallback flag used to display the provider badge (existing UI).

**Alternatives considered**:
- Pre-validate URLs server-side or proxy images through backend — rejected due to scope and performance/complexity.

**Rationale**: Fast UX, minimal infra changes, consistent with other fallback UIs in app.

## Data Flow

LinkedIn OIDC → LinkedIn adapter → ProviderAccountProfile → SocialAccount persistence → ConnectedSocialChannel read model → API DTO → Frontend store mapping → UI

    LinkedIn /v2/userinfo (picture) ──→ LinkedInPublishingAdapters.LinkedInUserInfoResponse.picture
           └─mapped→ ProviderAccountProfile.avatarUrl
                     └─persist→ social_accounts.avatar_url (nullable VARCHAR(1024))
                                └─read→ R2dbcConnectedSocialChannelReadRepository SELECT avatar_url
                                         └─map→ ConnectedSocialChannel.avatarUrl
                                                   └─PublishingHandlers.toSummary()→ ConnectedSocialChannelSummary.avatarUrl
                                                              └─HTTP GET /api/publishing/channels→ JSON { avatarUrl }
                                                                            └─frontend store apiChannelToChannel()→ Channel.avatarUrl
                                                                                         └─App.vue / CreatePostModal.vue render <img>

## File Changes

| File | Action | Description |
|------|--------|-------------|
| server/smp/src/main/resources/db/changelog/publishing/012-add-avatar-to-social-accounts.yaml | Create | Liquibase changeset: add nullable `avatar_url` column (varchar(1024)) to social_accounts | 
| server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/PublishingModels.kt | Modify | Add `avatarUrl: String? = null` to `SocialAccount` data class | 
| server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/ConnectedSocialChannelReadRepository.kt | Modify | Add `avatarUrl: String? = null` to `ConnectedSocialChannel` read model | 
| server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/PublishingProviderPorts.kt | Modify | Add `avatarUrl: String? = null` to `ProviderAccountProfile` | 
| server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/linkedin/LinkedInPublishingAdapters.kt | Modify | Extend `LinkedInUserInfoResponse` with `@JsonProperty("picture") val picture: String? = null` and map `picture` into `ProviderAccountProfile.avatarUrl` when building ProviderConnectionResult.account | 
| server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/R2dbcPublishingConnectionRepositories.kt | Modify | Update INSERT/SELECT/UPDATE SQL in social_accounts paths to include `avatar_url`, update toSocialAccount() to read `avatar_url`, and bindSocialAccount() to bind `avatarUrl` | 
| server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingHandlers.kt | Modify | `ConnectedSocialChannel.toSummary()` and any mapping to `ConnectedSocialChannelSummary` to include `avatarUrl` | 
| server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingApi.kt | Modify | Add `val avatarUrl: String? = null` to `ConnectedSocialChannelSummary` DTO | 
| server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/linkedin/LinkedInPublishingAdaptersTest.kt | Modify/Add tests | Update tests that parse userinfo response to include picture scenarios | 
| server/smp/src/test/kotlin/com/profiletailors/smp/publishing/application/PublishingHandlersTest.kt | Modify/Add tests | Verify `toSummary()` copies avatarUrl value | 
| server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingControllersTest.kt | Modify/Add tests | Test GET /api/publishing/channels includes avatarUrl | 
| apps/web/app/src/stores/publishing.ts | Modify | Add `avatarUrl?: string` to `Channel` and `ConnectedSocialChannelSummary` types, and map API field in `apiChannelToChannel` | 
| apps/web/app/src/App.vue | Modify | Render channel avatar image with :src binding and @error handler falling back to provider badge | 
| apps/web/app/src/components/CreatePostModal.vue | Modify | Render avatar with same fallback pattern | 
| apps/web/app/src/tests/*.spec.ts | Add | Unit tests for store mapping and component fallback rendering when avatarUrl is missing or broken |

Notes:
- All file paths above reference existing patterns in the publishing module. Code changes should follow current naming and binding helpers (bindNullable, toSocialAccount()).
- The Liquibase changeset should be numbered sequentially (012 or next number in directory). Keep author and changeSet id consistent with repo style.

## Interfaces / Contracts

Kotlin code snippets showing the minimal type changes (follow style in repo):

- PublishingModels.kt (SocialAccount)

    data class SocialAccount(
        val id: String,
        val socialConnectionId: String,
        val workspaceId: String,
        val provider: SocialProvider,
        val providerAccountId: String,
        val kind: SocialAccountKind,
        val displayName: String,
        val profileUrn: String? = null,
        val avatarUrl: String? = null,
        val status: SocialConnectionStatus,
        val createdAt: Instant? = null,
    )

- ConnectedSocialChannelReadRepository.kt (read model)

    data class ConnectedSocialChannel(
        val socialAccountId: String,
        val connectionId: String,
        val provider: SocialProvider,
        val accountKind: SocialAccountKind,
        val displayName: String,
        val status: SocialConnectionStatus,
        val profileUrn: String?,
        val avatarUrl: String? = null,
        val connectedAt: Instant?,
        val lastSyncedAt: Instant?,
    )

- PublishingProviderPorts.kt (provider profile)

    data class ProviderAccountProfile(
        val providerAccountId: String,
        val displayName: String,
        val kind: SocialAccountKind,
        val profileUrn: String? = null,
        val avatarUrl: String? = null,
    )

- LinkedInPublishingAdapters.kt (map LinkedIn picture)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class LinkedInUserInfoResponse(
        @JsonProperty("sub") val sub: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("given_name") val givenName: String? = null,
        @JsonProperty("family_name") val familyName: String? = null,
        @JsonProperty("email") val email: String? = null,
        @JsonProperty("picture") val picture: String? = null,
    ) {
        fun displayName(): String = ...
    }

    // When building ProviderConnectionResult.account:
    account = ProviderAccountProfile(
        providerAccountId = providerAccountId,
        displayName = profile.displayName(),
        kind = SocialAccountKind.PERSONAL_PROFILE,
        profileUrn = "urn:li:person:$providerAccountId",
        avatarUrl = profile.picture, // <-- new mapping
    ),

- R2DBC repository SQL additions (examples)

    INSERT INTO social_accounts (
        id, social_connection_id, workspace_id, provider, provider_account_id, account_type, display_name, profile_urn, avatar_url, status
    ) VALUES (
        :id, :socialConnectionId, :workspaceId, :provider, :providerAccountId, :accountType, :displayName, :profileUrn, :avatarUrl, :status
    )

    // SELECT should include avatar_url and toSocialAccount() should read it.

- API DTO (PublishingApi.kt)

    data class ConnectedSocialChannelSummary(
        val socialAccountId: String,
        val connectionId: String,
        val provider: SocialProvider,
        val accountKind: SocialAccountKind,
        val displayName: String,
        val status: SocialConnectionStatus,
        val profileUrn: String?,
        val avatarUrl: String? = null,
        val connectedAt: Instant?,
        val lastSyncedAt: Instant?,
    )

- Frontend (TypeScript snippets)

    // apps/web/app/src/stores/publishing.ts
    export interface ConnectedSocialChannelSummary {
      socialAccountId: string
      connectionId: string
      provider: SocialProvider
      accountKind: SocialAccountKind
      displayName: string
      status: SocialConnectionStatus
      profileUrn?: string
      avatarUrl?: string
      connectedAt?: string
      lastSyncedAt?: string
    }

    function apiChannelToChannel(api: ConnectedSocialChannelSummary): Channel {
      return {
        id: api.socialAccountId,
        provider: api.provider,
        displayName: api.displayName,
        avatarUrl: api.avatarUrl,
        // ... rest unchanged
      }
    }

## Testing Strategy

| Layer | What to Test | Approach |
|-------|-------------|----------|
| Unit (backend) | PublishingHandlers.toSummary() maps avatarUrl correctly | Add test to PublishingHandlersTest that constructs a ConnectedSocialChannel with avatarUrl and asserts DTO value | 
| Unit (backend) | LinkedIn adapter mapping of LinkedInUserInfoResponse.picture | Extend LinkedInPublishingAdaptersTest: include picture present/absent cases | 
| Repository (backend) | R2DBC bindings and read mapping for avatar_url | Add unit test in R2dbcPublishingRepositoriesUnitTest / R2dbcPublishingConnectionRepositoriesTest verifying insert/select round-trip with H2 fallback using bindNullable and toSocialAccount reading avatar_url | 
| Controller (integration) | GET /api/publishing/channels includes avatarUrl | Update PublishingControllersTest to assert returned JSON contains avatarUrl when present | 
| Frontend unit | store mapping apiChannelToChannel maps avatarUrl | Add Jest/Vitest unit test for publishing store mapping | 
| Frontend component | Avatar rendering and error fallback | Test App.vue and CreatePostModal.vue components: when avatarUrl missing or when image loads error, fallback badge renders; when present, <img> uses avatarUrl | 
| E2E (manual or CI) | End-to-end LinkedIn connect flow with picture present | Recommended as manual/CI smoke: simulate LinkedIn provider returning picture and confirm UI shows avatar. Can be added to future E2E tasks. |

Test data permutations:
- picture present and valid
- picture present but host returns 4xx/5xx (component fallback)
- picture absent/null

CI guidance:
- R2DBC tests currently run against H2 fallback. Since SQL changes are additive, ensure Liquibase change runs in DB during integration tests or use Testcontainers Postgres to validate new column; keep H2 fallbacks where appropriate.

## Migration / Rollout

- Liquibase changeset adds nullable `avatar_url` column. No backfill required.
- Field is additive and optional: safe to deploy backend first; frontend will ignore missing field. Recommended rollout:
  1. Backend: apply DB migration and deploy backend that writes and reads avatarUrl.
  2. Frontend: deploy SPA changes that render avatarUrl if present.

Feature flag: not required. Change is additive.

## Open Questions

- None blocking. Implementation follows repository and DTO mapping patterns already present.

## Next Steps

- Implement code changes and Liquibase changeset described above (sdd-tasks: implement, unit tests, integration tests, frontend mapping and components).

