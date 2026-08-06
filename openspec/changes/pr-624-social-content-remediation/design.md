# Design: PR #624 Social Content Foundation Remediation

## Technical Approach

Keep the existing package-by-context hexagonal boundary: domain owns models, typed failures, and repository/provider ports; application owns CQRS orchestration; infrastructure owns Spring properties, fakes, R2DBC, and Liquibase. Replace the current `SocialContentFoundationHandlers` façade with direct application handlers. Do not add mediator/Spring wiring: `SocialContentConfiguration` currently only binds properties, and no real `SocialContentProvider` or social-content repository adapter is wired. Tests construct handlers directly until an adapter exists.

Strict TDD order: add failing focused tests, implement the smallest domain/handler/port change, then run focused backend tests before integration checks.

## Architecture Decisions

| Decision | Choice | Alternatives rejected | Rationale |
|---|---|---|---|
| CQRS classes | `DiscoverSocialActorsQuery`/`DiscoverSocialActorsQueryHandler`; `SyncSocialPostsCommand`/`SyncSocialPostsCommandHandler`; `SyncSocialCommentsCommand`/`SyncSocialCommentsCommandHandler`; `ReplyToSocialCommentCommand`/`ReplyToSocialCommentCommandHandler` | Retain the mixed façade; use Spring annotations | Makes read discovery and state-changing sync/reply boundaries explicit without coupling application to Spring or the shared bus. |
| Typed failures | Add `SocialContentCapabilityDeniedException`, `SocialContentPaginationException` with `PaginationGuardReason { REPEATED_CURSOR, MAX_PAGES_EXCEEDED }`, and `ReplyIdempotencyConflictException`; retain `SocialContentProviderException` and `ReplyRejectedException` | `error()`/generic `IllegalStateException`; string errors | Callers and tests can distinguish authorization, provider, pagination, and idempotency failures. |
| Pagination | Introduce `SocialContentSyncLimits(pageSize, maxPages)`. Pass `pageSize` through provider read ports; handler owns the `maxPages` guard. Track every requested/returned non-null cursor and fail on repetition. A non-null cursor after page `maxPages` fails. | Unbounded `do/while`; silently stop at the bound | Prevents provider loops and partial “successful” imports while preserving provider-controlled cursors. |
| Checkpoints | Read workspace/actor/resource checkpoint; start at its cursor. Buffer and validate all pages first. Persist posts/comments, tombstone only after terminal completion, then save `cursor=null`, `max(previous HWM, page/item HWM)`, and `lastSuccessfulAt=now`. Guard/provider failure performs no writes and leaves the old checkpoint/tombstones intact. | Advance per page; clear checkpoint before fetching | Resume is deterministic and a failed bounded run cannot erase known-good state. |
| Replies | `claim` atomically creates `PROCESSING`; handler writes exactly one terminal `SUCCEEDED(external id)` or `FAILED(typed failure)`. Existing `SUCCEEDED`, `FAILED`, or `PROCESSING` is returned unchanged. Same-key/different-command is an idempotency conflict. No automatic recovery; `RetrySocialReplyCommand` must be an explicit future path with a new key. | Re-try `FAILED`/`PROCESSING` implicitly; save `PROCESSING` twice | Prevents duplicate provider calls and makes crash/recovery semantics observable. |
| Value/FK integrity | Override `PayloadCache.equals/hashCode` with `contentEquals/contentHashCode` (and copy bytes at construction). Add parent uniques `(workspace_id,id)` on `social_accounts` and `social_content_posts`; use composite FKs `(workspace_id,social_account_id)` for capabilities/posts/checkpoints/webhooks/replies and `(workspace_id,post_id)` for comments. | Reference equality; account-only FKs | Byte arrays are values, and separate workspace/account IDs currently permit cross-workspace references. |

## Data Flow

```text
Query/Command → capability + domain validation → provider (bounded pages)
                                      ↓
       buffer/guard success → repositories + tombstones → checkpoint

Reply command → atomic claim(PROCESSING) → provider once → terminal result
```

## File Changes

| File | Action | Description |
|---|---|---|
| `publishing/domain/SocialContentModels.kt`, `SocialContentPorts.kt` | Modify | Invariants, byte equality, typed failures, sync limits, port signatures, reply transitions/conflict contract. |
| `publishing/application/{DiscoverSocialActorsQueryHandler,SyncSocialPostsCommandHandler,SyncSocialCommentsCommandHandler,ReplyToSocialCommentCommandHandler}.kt` | Create | Dedicated handlers and bounded orchestration; remove the mixed implementation. |
| `publishing/infrastructure/fake/*` and focused publishing tests | Modify | Page-size/cursor fixtures, isolated repositories, all state/guard regressions. |
| `publishing/infrastructure/socialcontent/SocialContentProperties.kt` | Modify | Validate calendar month `01..12`; expose limits to composition. |
| `db/changelog/publishing/017-social-content-workspace-fks.yaml` and master | Create/Modify | Add composite uniqueness/FKs with rollback; retain `016` history. |
| `SocialContentLiquibaseChangelogTest.kt` | Modify | Assert composite constraints, inclusion, and rollback; add a Postgres FK test only if live proof is needed. |

## Testing Strategy

Unit tests first in `SocialContentModelsTest`, `SocialContentFoundationHandlersTest`, `FakeSocialContentProviderTest`, `FakeReplyCommandRepositoryTest`, and `SocialContentPropertiesTest`: typed denial/no provider call, max-page/repeated-cursor no-write behavior, resume/HWM rules, all reply states/conflicts, ByteArray equality, invariants, and `202600`/`202613` rejection. Static Liquibase tests cover inclusion, composite constraints, indexes, and rollback; a focused Testcontainers/Postgres test proves cross-workspace rejection if required. No new Cucumber scenarios: this foundation has no public HTTP surface; existing publishing BDD is unrelated.

## Review-Thread Decisions

- Missing `.agents/commands/architecture-docs-sync.md`: reply-only; the file is absent from this checkout and is not required by PR #624’s foundation. Report bounded documentation status; do not invent a command.
- `shared/shield/ratelimit` and PR #625 feedback: reply-only, explicitly out of scope; no changes there.
- `mutationAllowed`: reply-only because current reconciliation guard and `SocialContentModelsTest` already cover the reviewed behavior; do not duplicate remediation.
- Cucumber recommendations: reply-only and not applicable because this foundation exposes no public HTTP/API surface; existing publishing BDD covers other endpoints. Liquibase recommendations that ask for HTTP/Cucumber coverage receive the same reply-only classification; migration-specific FK/schema checks remain in code scope through static tests and targeted Postgres proof.

## Migration / Rollout

Apply the additive `017` migration after `016`; verify existing rows before deployment. Roll back the isolated changeset if validation fails. No feature flag or public rollout is required.

## Open Questions

- [ ] Confirm whether the eventual real provider can honor the new `pageSize` port argument; the fake must do so immediately.
- [ ] Run live Postgres composite-FK proof if the migration test harness is available.
