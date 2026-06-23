# Delta for Publishing

## Overview

This delta extends publishing so unpublished publications can be deleted through a workspace-scoped backend flow and edited through the existing backend PATCH flow. Edit and delete MUST be allowed only for publications in `DRAFT`, `QUEUED`, or `SCHEDULED`. They MUST NOT be allowed for `PROCESSING`, `PUBLISHED`, `BLOCKED`, `FAILED`, or `CANCELLED`.

## Changes

### Requirement: Delete unpublished publications

The system MUST provide a workspace-scoped delete flow for publications in `DRAFT`, `QUEUED`, or `SCHEDULED`.

Backend deletion MUST use `DeletePublicationCommand` with only `publicationId`. `DeletePublicationHandler` MUST fetch by `workspaceId + publicationId`, return `404` when not found, call `PublicationLifecyclePolicy.requireDeletable()`, cancel any pending jobs for the publication, delete related `publication_asset_links`, hard-delete the publication row, and complete with `204 No Content`. `requireDeletable()` SHALL enforce the same allowed statuses as `requireEditable()` for this change. If deletion is rejected by lifecycle state, the API MUST return `409 Conflict` with a machine-readable reason.

### Requirement: Persist publication edits from the SPA

The SPA MUST persist publication edits through the existing backend PATCH contract instead of local-only state mutation.

`updatePost(id, updates)` MUST call `PATCH /api/publishing/publications/{id}`. On success, the store MUST replace the local publication with the response payload. On error, the store MUST preserve local state and surface the failure to the caller. Existing template guards are sufficient for hiding edit actions on published posts, and backend lifecycle enforcement remains authoritative.

### Requirement: Persist publication deletes from the SPA

The SPA MUST persist publication deletes through the backend DELETE contract instead of local-only removal.

`deletePost(id)` MUST call `DELETE /api/publishing/publications/{id}` through authenticated fetch. On successful 2xx completion, the store MUST remove the publication from local state. On 4xx failure, the store MUST keep the publication visible and surface the error. `SchedulerView.vue` and `PostDetailModal.vue` MUST route delete actions through this store behavior.

## Scenarios

### Backend delete scenarios

#### Scenario: Delete allowed for unpublished publication
- GIVEN a publication in `DRAFT`, `QUEUED`, or `SCHEDULED` within the active workspace
- WHEN the client sends `DELETE /api/publishing/publications/{publicationId}`
- THEN the system MUST cancel pending jobs, delete asset links, hard-delete the publication, and return `204`

#### Scenario: Delete rejected for non-deletable status
- GIVEN a publication in `PROCESSING`, `PUBLISHED`, `BLOCKED`, `FAILED`, or `CANCELLED`
- WHEN the client sends `DELETE /api/publishing/publications/{publicationId}`
- THEN the system MUST reject the request with `409 Conflict`
- AND the response MUST include the lifecycle rejection reason

#### Scenario: Delete rejected across workspace boundary
- GIVEN a publication exists in another workspace
- WHEN a workspace-scoped delete request is sent for that publication id
- THEN the system MUST behave as not found and return `404`

### Frontend state scenarios

#### Scenario: Delete success removes local publication
- GIVEN the store contains a publication visible in scheduler or modal UI
- WHEN `deletePost(id)` receives a successful DELETE response
- THEN the store MUST remove that publication from local state

#### Scenario: Delete failure preserves local publication
- GIVEN the store contains a publication visible in scheduler or modal UI
- WHEN `deletePost(id)` receives a `4xx` error response
- THEN the store MUST keep the publication in local state
- AND it MUST surface the error to the UI

#### Scenario: Edit success updates local publication
- GIVEN the store contains an editable publication
- WHEN `updatePost(id, updates)` receives a successful PATCH response
- THEN the store MUST replace the local publication with the response payload

#### Scenario: Edit failure preserves local publication
- GIVEN the store contains an editable publication
- WHEN `updatePost(id, updates)` receives an error response
- THEN the store MUST keep the existing local publication unchanged
- AND it MUST surface the error to the UI

## API Contract

- `DELETE /api/publishing/publications/{publicationId}`
  - `204 No Content` — publication deleted
  - `404 Not Found` — publication absent in active workspace
  - `409 Conflict` — publication not deletable in current lifecycle status
- `PATCH /api/publishing/publications/{publicationId}`
  - Existing response contract remains unchanged and MUST be used by the SPA as the source of truth

## Error Handling

- Delete and edit authorization/scoping MUST remain workspace-scoped.
- Lifecycle rejection for delete MUST map to `409`, not silent local removal.
- Frontend MUST NOT optimistically remove or overwrite state after a failed delete or edit response.
- Backend NOT FOUND responses MUST NOT reveal whether the publication exists in another workspace.
