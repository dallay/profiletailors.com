# Proposal: LinkedIn Channel Avatars

## Intent

Connected LinkedIn channels already appear in the app, but they do not carry real profile avatars. This change adds durable avatar support by propagating an optional `avatarUrl` from LinkedIn OIDC user info through persistence, APIs, store mapping, and UI rendering.

## Scope

### In Scope
- Persist optional `avatarUrl` on LinkedIn-connected social accounts
- Read LinkedIn OIDC `/v2/userinfo` `picture` when present during connection completion/sync
- Expose `avatarUrl` from `GET /api/publishing/channels` and map it in the frontend publishing store
- Render channel avatars in connected-channel UI with graceful fallback for missing or broken images

### Out of Scope
- Backfilling avatars for already-connected accounts beyond normal reconnect/sync flows
- Non-LinkedIn providers or richer media transformations/proxying

## Capabilities

### New Capabilities
- `channel-avatar-ui`: Render connected channel avatars with initials/provider fallback when `avatarUrl` is absent or fails to load

### Modified Capabilities
- `channel-list-api`: Add optional `avatarUrl` to connected channel summaries returned by `GET /api/publishing/channels`
- `publishing`: Persist safe avatar metadata on social accounts when LinkedIn provides `picture`

## Approach

Add an optional `avatarUrl` field to the publishing account model, database, read models, and API DTOs. Populate it from LinkedIn OIDC `userinfo.picture` when available, without making avatar presence required. On the frontend, map the field into channel state and render the image only when valid; otherwise fall back to the existing badge/initial treatment so channel lists remain stable.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `server/smp/src/main/resources/db/changelog/publishing/` | Modified | Add nullable avatar column/change set for `social_accounts` |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/` | Modified | Propagate `avatarUrl` through domain, application, persistence, and API models |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/linkedin/` | Modified | Read LinkedIn `userinfo.picture` and map safely |
| `apps/web/app/src/stores/publishing.ts` | Modified | Map API `avatarUrl` into channel state |
| `apps/web/app/src/App.vue` | Modified | Render avatar image with fallback on error/missing value |
| `apps/web/app/src/views/SettingsView.vue` | Modified | Show connected LinkedIn avatars with fallback |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| LinkedIn omits `picture` despite current scopes | Med | Keep `avatarUrl` optional and preserve fallback UI |
| External image URL breaks or expires | Med | Handle image error and swap to fallback immediately |
| Schema/API drift across backend and SPA | Low | Update DTO, repository, controller, and store tests together |

## Rollback Plan

Revert the new database change, remove `avatarUrl` from publishing models and channel API responses, and restore UI to fallback-only avatars. Because the field is additive and optional, rollback does not require reconnecting channels.

## Dependencies

- LinkedIn OIDC userinfo must continue returning `picture` under current `openid profile email` behavior when available

## Success Criteria

- [ ] Newly connected LinkedIn channels show the LinkedIn profile avatar when `picture` is available
- [ ] `GET /api/publishing/channels` returns optional `avatarUrl` without exposing secrets
- [ ] Missing or broken avatar images fall back cleanly with no broken-image UI
- [ ] Backend and frontend tests cover present, absent, and broken-avatar paths
