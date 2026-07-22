## Exploration: unpublished publication edit/delete

### Current State

The backend publishing context already supports editing unpublished publications via
`PATCH /api/publishing/publications/{publicationId}` backed by `EditPublicationCommand`,
`EditPublicationHandler`, `PublicationLifecyclePolicy.requireEditable()`, and
`publicationRepository.updateEditableDraft(...)`. The backend also supports cancellation via
`POST /api/publishing/publications/{publicationId}/cancel`, but there is no real delete command,
repository method, or HTTP endpoint. The editable/deletable business rule already exists in domain
policy: only `DRAFT`, `QUEUED`, and `SCHEDULED` are allowed for edit/cancel.

On the frontend, the scheduler store is still mixed-mode. `reschedulePublication()` calls the
backend, but `deletePost()` and `updatePost()` are local-only mutations. `PostDetailModal.vue`
exposes delete and reschedule only; there is no edit form or call to the backend edit endpoint.
`SchedulerView.vue` also invokes `publishingStore.deletePost(pub.id)` directly from card actions, so
delete currently removes items from local state without backend persistence.

A persistence hardening gap exists in the backend repository path: `EditPublicationHandler` checks
editability in memory, but `R2dbcPublicationRepository.updateEditableDraft()` just rewrites the row
through delete+insert semantics and does not enforce the allowed-status rule at SQL level.

### Affected Areas

- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/PublishingPolicies.kt` —
  source of the core business rule for editable/deletable states.
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingApi.kt` — add
  delete command/result contract if true deletion is introduced.
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingHandlers.kt` —
  edit exists; delete handler would need to mirror workspace/auth checks and coordinate job cleanup.
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/PublishingRepositories.kt` —
  no delete method exists today on `PublicationRepository`.
-

`server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingControllers.kt` —
has edit/cancel endpoints, but no delete endpoint.

-

`server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/R2dbcPublishingRepositories.kt` —
repository currently uses delete+insert for updates; delete flow and optional SQL status guard would
land here.

- `server/smp/src/main/resources/db/changelog/publishing/004-create-publications.yaml` — current
  schema has no DB-level state guard; optional hardening may require a migration or more selective
  SQL updates.
- `apps/web/app/src/stores/publishing.ts` — `deletePost()` and `updatePost()` are local-only;
  backend-backed delete/edit actions belong here.
- `apps/web/app/src/components/PostDetailModal.vue` — currently supports delete and reschedule only;
  likely home for edit UI if keeping detail-modal-driven flows.
- `apps/web/app/src/views/SchedulerView.vue` — card-level delete buttons call local deletion
  directly and would need to use the integrated store action.
- `apps/web/app/src/components/PostDetailModal.test.ts` — existing modal tests cover
  delete/reschedule and will expand for edit affordances and status gating.
- `apps/web/app/src/stores/publishing.test.ts` — existing store tests cover local delete/update and
  backend reschedule; missing backend edit/delete coverage.
- `apps/web/app/e2e/specs/scheduler-post-interaction.spec.ts` and
  `apps/web/app/e2e/pages/post-detail-modal-page.ts` — current delete E2E is local-state-only and no
  edit scenario exists.
- `openspec/specs/publishing/spec.md` — base publishing spec already states editable/cancellable
  pre-delivery behavior and should anchor the change scope.

### Approaches

1. **Soft-delete via existing cancel semantics** — Re-label the UI delete action to call cancel and
   keep records as `CANCELLED`.
    - Pros: Lowest backend effort; aligns with existing command/endpoint/job handling.
    - Cons: Does not satisfy issue scope calling for backend delete support; user-visible behavior
      is not true removal; frontend/local-state expectations stay mismatched.
    - Effort: Low

2. **True unpublished delete + backend-backed edit UI** — Add a dedicated delete
   command/repository/endpoint for `DRAFT`/`QUEUED`/`SCHEDULED`, integrate frontend delete with API,
   and add frontend edit flow that calls the existing edit endpoint.
    - Pros: Matches issue intent; keeps edit and delete semantics explicit; preserves current edit
      backend investment; clean user mental model for unpublished posts.
    - Cons: More moving parts across backend, store, modal/list actions, and tests; deletion
      semantics for related jobs/assets must be designed carefully.
    - Effort: Medium

3. **True delete + SQL-level edit hardening in same change** — Do approach 2 and also add
   persistence-level guards so editable updates cannot bypass domain checks through races or future
   misuse.
    - Pros: Strongest integrity story; addresses the optional hardening while touching the same
      repository code.
    - Cons: Higher risk and scope; current repository rewrite strategy (`DELETE` then `INSERT`)
      makes row-level status guards less straightforward and may force a deeper persistence refactor
      or targeted SQL path.
    - Effort: High

### Recommendation

Recommend **Approach 2** for the main change scope: implement a dedicated backend delete flow for
unpublished publications, wire frontend delete to that API, and add a frontend edit UI/flow that
uses the already-existing backend edit endpoint. This closes the actual product gap without turning
the change into a persistence redesign.

Recommend treating the **SQL-level status guard as out of scope for the initial proposal**, but
capture it as a follow-up hardening item unless implementation reveals a cheap, low-risk path. The
current repository update strategy is broad enough that forcing SQL guards into this change could
expand scope disproportionately.

### Risks

- Delete semantics are not defined yet for linked records (`publication_jobs`,
  `publication_asset_links`, possible audit expectations); physical deletion must avoid orphaning or
  inconsistent worker behavior.
- Frontend currently has optimistic/local fallback behavior; moving delete/edit to backend truth may
  expose unauthenticated/offline assumptions in the store and tests.
- `SchedulerView.vue` triggers delete from multiple card locations, so partial wiring could leave
  mixed local/backend behavior.
- Edit UI shape is not present yet; deciding between inline modal editing vs. reusing composer
  patterns affects scope and test surface.
- SQL-level hardening is non-trivial because `updateEditableDraft()` currently delegates to
  delete+insert rather than a constrained conditional update.

### Ready for Proposal

Yes — enough evidence exists to draft a proposal. The proposal should scope in: backend unpublished
delete support, frontend delete integration, frontend edit UI + API integration, and automated
tests. It should scope out the optional SQL-level edit guard as a follow-up hardening item unless a
minimal implementation path is chosen during design.
