# LinkedIn Channel Avatars Specification

## Feature: LinkedIn Channel Avatar Support

### Overview

This specification formalizes the change proposed in openspec/changes/linkedin-channel-avatars/proposal.md: add durable avatar support for LinkedIn-connected channels by propagating an optional avatarUrl from LinkedIn OIDC userinfo through persistence, APIs, store mapping, and UI rendering.

### User Stories
- As a user, I want to see my LinkedIn profile picture in the sidebar when I connect my LinkedIn account
- As a user, I want to see my LinkedIn profile picture in the channel selector when creating a post
- As a user, I want to see a clean fallback (initials/badge) when the avatar is unavailable

### Scenarios (BDD)

1. Connected LinkedIn channel WITH avatar available — sidebar shows profile picture
   - GIVEN a workspace with an ACTIVE LinkedIn personal-profile social account that has a non-empty `avatar_url`
   - WHEN the SPA requests `GET /api/publishing/channels`
   - THEN the response includes `avatarUrl` for that channel
   - AND the sidebar renders an `<img src={avatarUrl}>` for that channel

2. Connected LinkedIn channel WITHOUT avatar (LinkedIn omits `picture`) — sidebar shows provider badge fallback
   - GIVEN a workspace with an ACTIVE LinkedIn personal-profile social account where `avatar_url` is NULL
   - WHEN channels are listed
   - THEN the frontend MUST render the provider badge/initials fallback in place of an `<img>`

3. Avatar URL is broken/expired at render time — `<img>` error triggers fallback without layout break
   - GIVEN a channel with a non-empty `avatar_url` that 404s or otherwise fails to load in the browser
   - WHEN the browser `<img>` element emits an `error` event
   - THEN frontend code MUST replace the image with the provider badge/initials fallback
   - AND this MUST NOT cause layout shift beyond the existing badge image size

4. New LinkedIn connection — avatar is persisted during connection flow
   - GIVEN a user completes LinkedIn OAuth and LinkedIn `userinfo` includes `picture`
   - WHEN the backend finalizes the connection and persists social account metadata
   - THEN `avatar_url` column on the `social_accounts` row MUST be populated with the safe `picture` value

5. Reconnect after avatar URL change — new URL is stored
   - GIVEN an existing LinkedIn connection with previous `avatar_url`
   - WHEN the user reconnects and LinkedIn `userinfo.picture` differs
   - THEN the repository upsert semantics MUST update `avatar_url` to the new value

### API Contract Changes

`GET /api/publishing/channels` (200)
- Response: application/json
- Body: array of channel summary objects. Add optional field:
  - `avatarUrl?: string | null` — absolute URL from LinkedIn userinfo.picture when present. MUST be present for channels whose persisted `avatar_url` is non-null. MUST NOT contain provider secrets.

API compatibility rules
- The field is additive and optional; older clients MUST ignore unknown fields. New clients MUST tolerate missing or null values.

### Data Model Changes

Database migration
- Add nullable column to `social_accounts` table:
  - `avatar_url VARCHAR(1024) NULL` — stores the provider-supplied avatar URL. Length chosen to accommodate common CDN URLs.
- Change set MUST be additive and backward-compatible.

Domain & persistence
- Update domain model `SocialAccount` to include `avatarUrl: String?`
- Repository upsert semantics MUST set `avatar_url` when provided and leave existing value unchanged when absent during partial updates (unless reconnect flow explicitly provides new value).

Security
- Do NOT store any provider secret values in `avatar_url`. Validate that the value is an HTTPS URL. If LinkedIn returns data-URI or non-HTTPS, sanitize or reject and leave column NULL.

### Frontend Changes

Type changes
- Channel interface (apps/web/app/src/stores/publishing.ts):
  - add `avatarUrl?: string | null`
- Mapper `apiChannelToChannel(responseItem)` MUST read `avatarUrl` from API response into channel model.

Rendering
- Sidebar (apps/web/app/src/App.vue or relevant sidebar component)
  - Use `<img :src="channel.avatarUrl" @error="onAvatarError(channel)" v-if="channel.avatarUrl"/>`
  - Implement `onAvatarError(channel)` to mark that channel's transient `avatarLoadFailed = true` (component state) and render fallback instead.
  - When `channel.avatarUrl` is null/absent or `avatarLoadFailed` is true, render provider badge/initials fallback with identical dimensions to avoid layout shift.

- CreatePostModal.vue (channel selector)
  - Same image usage and error handling as sidebar.

- CSS
  - Ensure avatar image and badge share same container size (e.g., 32x32) and border radius so swapping does not cause layout changes.

Accessibility
- Provide `alt` text for avatar images: `alt="{channel.displayName} avatar"`
- Fallback badge must expose accessible label for assistive technologies.

Client-side tests
- Unit tests for store mapper to populate `avatarUrl` correctly.
- Component tests (unit or integration) for sidebar and CreatePostModal to verify rendering when `avatarUrl` present, missing, and when image `error` fires.

### Backend Changes

LinkedIn connector
- When completing connection or during sync, read `picture` from LinkedIn OIDC userinfo `/v2/userinfo` if present.
- Validate that `picture` is an HTTPS URL and not a data URI. If invalid, log at debug and do not persist.

Service layer
- When persisting social account metadata, set `avatar_url` when value is provided by connector. Upsert semantics MUST replace the column when connector supplies a new value.

Repository
- Add `avatar_url` handling in read/write mappings between DB rows and domain objects.

Controller / API
- Include `avatarUrl` in the channel summary DTO returned by `GET /api/publishing/channels` when `avatar_url` is non-null.
- Ensure DTO does not leak provider tokens or other secrets.

Backend tests
- Unit tests for connector to parse and validate `picture` value.
- Integration tests for connection flow ensuring `avatar_url` persisted when `picture` present, and updated on reconnect.
- API tests for `GET /api/publishing/channels` to include `avatarUrl` in response when persisted and to omit when NULL.

### Acceptance Criteria
- [ ] Backend persists avatar URL when present in LinkedIn userinfo
- [ ] API returns avatar URL in connected channels response
- [ ] Frontend renders real avatar when URL is valid
- [ ] Frontender renders fallback badge when URL is missing or broken
- [ ] Tests (backend + frontend) cover present, absent, and broken-avatar paths and pass

### Test Cases

Backend
- Persist when `userinfo.picture` contains valid HTTPS URL
- Do not persist when `userinfo.picture` is missing, empty, not HTTPS, or data URI
- Upsert updates `avatar_url` on reconnect
- API includes `avatarUrl` in GET channels response when present

Frontend
- Mapper reads `avatarUrl` into channel model
- Sidebar renders `<img>` when `avatarUrl` present
- Sidebar renders fallback when `avatarUrl` missing
- When image `error` fires, component swaps to fallback and no exception thrown

### Rollout and Migration
- Deploy DB migration before server code that writes `avatar_url` (deploy in same release window preferred). Because column is nullable and additive, older server versions are compatible.
- Feature rollout is safe by default; UI will show fallback if API does not provide `avatarUrl`.

### Observability
- Add debug logs in LinkedIn connector when parsing `picture` and when sanitization rejects values
- Add metric: `publishing.linkedin.avatar.persisted` counter incremented when avatarUrl persisted

### Open Questions
- Should we proxy/normalize avatar images through our CDN to avoid expiry? OUT OF SCOPE for now.
- What retention policy for avatar_url? Currently store as provided; future TTL/refresh considered.

---

