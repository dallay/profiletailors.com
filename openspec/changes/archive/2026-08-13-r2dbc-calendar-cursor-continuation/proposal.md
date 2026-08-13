# Proposal: Production R2DBC Calendar Cursor Continuation

**Linear**: DALLAY-550 — parent epic DALLAY-526.
**Closes**: verify-report WARNING #3 from archived `linkedin-company-pages-community-inbox` PR2.

## Intent

`findImportedPosts` ignores its opaque calendar cursor and always returns `nextCursor = null`. Add production keyset continuation without changing the HTTP response contract.

## Scope

### In Scope
- Versioned six-field cursor `(v, workspaceId, publishedAt, provider, socialAccountId, externalPostId)` with typed codec and workspace binding.
- Strict keyset continuation, `limit + 1`, covering Liquibase index, 400 Problem Details, and codec/PostgreSQL/BDD/Liquibase tests.

### Out of Scope
- Sync checkpoints, provider pagination, unrelated repository methods, controllers, handlers, query/response shapes, SPAs, and new dependencies.

### Guarantee Scope

No duplicates or omissions in a stable-snapshot client sequence, modulo concurrent `published_at` rewrites; comparison is strict `>`.

## Capabilities

### New Capabilities
- None.

### Modified Capabilities
- `social-content-sync`: calendar cursor decoding, binding validation, production keyset continuation, and 400 behavior.

## Approach

Use a pure-Kotlin codec beside `PageCursor`, mirroring `AuditEventCursorCodec`. Encode the six-field versioned payload as unpadded URL-safe Base64; reject malformed or unsupported values with `InvalidSocialContentCursorException`. The reader validates cursor `workspaceId` against the request-context workspace before SQL. SQL independently derives `workspace_id` from authenticated request context and never uses cursor provenance for authorization. Keyset ordering remains the four fields `(published_at, provider, social_account_id, external_post_id)`, excluding `workspaceId` as binding metadata. No HMAC/signing is in scope.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `server/smp/.../publishing/domain/SocialContentModels.kt` | Modified | Cursor version, codec, exception. |
| `server/smp/.../publishing/infrastructure/persistence/R2dbcSocialContentRepositories.kt` | Modified | Binding validation, request-scoped SQL, keyset and `+1`. |
| `server/smp/.../publishing/infrastructure/http/PublishingProblemDetailsHandler.kt` | Modified | 400 mapping. |
| `server/smp/src/main/resources/db/changelog/publishing/019-add-social-content-calendar-keyset-index.yaml` | New | Covering index and rollback. |
| `server/smp/src/main/resources/db/changelog/db.changelog-master.yaml` | Modified | Include 019 after 018. |
| `server/smp/src/test/resources/features/social-content-calendar-cursor.feature` | New | Production-reader BDD scenarios. |
| `openspec/changes/r2dbc-calendar-cursor-continuation/specs/social-content-sync/spec.md` | Modified | Delta requirements and scenarios. |

## Risks

| Risk | Mitigation |
|------|------------|
| Concurrent timestamp rewrites | Document bounded guarantee; stable snapshots remain deterministic. |
| Unsigned Base64 is not cryptographically tamper-proof | Binding validation plus independent request-context SQL scope maintains the security property; no HMAC is in scope. |
| Sort degradation or skipped migration | Add covering index, master include, and changelog tests. |

## Rollback Plan

1. Revert additive codec, exception, handler, and reader changes.
2. Run Liquibase rollback for `idx_social_content_posts_calendar_keyset`; removing `<include>` alone is insufficient.
3. Restore PR2 behavior with no calendar continuation; the existing range index remains.

## Dependencies

Existing R2DBC repository, `PageCursor`, `SocialContentPage`, and `AuditEventCursorCodec` precedent. No new dependencies.

## Success Criteria (DALLAY-550)

- [ ] **AC1, AC3** Production R2DBC page 2 continues from `nextCursor` with no stable-snapshot duplicates or omissions.
- [ ] **AC2** SQL uses strict four-field keyset predicate and matching four-field ORDER BY.
- [ ] **AC4** `nextCursor` is an opaque, versioned six-field Base64url-without-padding payload, bounded and `null` on the final page.
- [ ] **AC5** Cross-workspace cursor is explicitly rejected with 400 `INVALID_SOCIAL_CONTENT_CURSOR`; SQL independently uses authenticated request-context workspace isolation.
- [ ] **AC6** Existing validation, headers, Problem Details, BDD scenarios, and backend test lanes remain green.
- [ ] `just backend-test-fast`, `just backend-bdd-fast`, `just backend-test-postgres`, `just backend-bdd-postgres`, and `just ci` pass.
