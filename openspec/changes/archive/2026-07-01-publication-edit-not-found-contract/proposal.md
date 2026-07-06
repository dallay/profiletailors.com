# Proposal: Publication Edit Not-Found Contract

## Intent

Fix the verified edit-publication failure contract. The backend edit handler already treats a
missing publication in the active workspace as `PublicationNotFoundException`, but
`PublishingProblemDetailsHandler` does not map that exception, so Spring falls through to HTTP 500
instead of 404. The codebase also shows a contract ambiguity: authenticated frontend edit flow
assumes every PATCH is update-only, while recent publishing persistence rules distinguish
update-only writes from create/save flows.

## Scope

### In Scope

- Map publication-not-found edit/delete/retry/reschedule failures to HTTP 404.
- Clarify the publishing spec for update-only workspace-scoped publication mutations.
- Add regression coverage for active-workspace misses and preserve existing editable-status rules.

### Out of Scope

- New scheduler UX or composer redesign.
- Reintroducing create-on-save behavior into authenticated PATCH edit flow.

## Capabilities

### New Capabilities

- None

### Modified Capabilities

- `publishing`: define that update-only publication mutations missing the active-workspace row MUST
  return not found, and the HTTP contract MUST expose that as 404 instead of 500.

## Approach

Add explicit problem-details handling for `PublicationNotFoundException` and align tests at
handler/controller level. Update the `publishing` spec language so backend and frontend both
distinguish update-only edit flows from create/save flows, avoiding future ambiguity around
workspace-scoped misses.

## Affected Areas

| Area                                                                                                                  | Impact   | Description                                                            |
|-----------------------------------------------------------------------------------------------------------------------|----------|------------------------------------------------------------------------|
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingProblemDetailsHandler.kt` | Modified | Map publication-not-found exceptions to 404                            |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingHandlers.kt`                      | Modified | Preserve active-workspace not-found semantics as explicit API contract |
| `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/`                                                       | Modified | Add regression coverage for 404 mapping and update-only misses         |
| `openspec/specs/publishing/spec.md`                                                                                   | Modified | Clarify update-only vs create/save workspace-write behavior            |

## Risks

| Risk                                                | Likelihood | Mitigation                                                   |
|-----------------------------------------------------|------------|--------------------------------------------------------------|
| Spec wording drifts from implemented PATCH behavior | Med        | Tie proposal and tests to authenticated PATCH as update-only |
| Broader not-found paths change unintentionally      | Low        | Limit new mapping to publishing not-found exception coverage |

## Rollback Plan

Revert the problem-details mapping, spec delta, and regression tests; this restores previous
behavior while retaining the investigation trail.

## Dependencies

- Existing `publishing` workspace-scoped write rules
- Publishing HTTP problem-details coverage

## Success Criteria

- [ ] Editing a publication absent from the active workspace returns HTTP 404, not 500.
- [ ] Delete/retry/reschedule paths using the same not-found exception follow the same 404 contract.
- [ ] The publishing spec clearly separates update-only edit flow behavior from create/save flow
  behavior.
