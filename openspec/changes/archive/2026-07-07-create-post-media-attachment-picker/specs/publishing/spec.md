# Delta for publishing

## MODIFIED Requirements

### Requirement: Composer Media Selection Uses Reusable Workspace Assets

The SPA composer MUST support selecting media from persisted workspace assets, including newly
uploaded assets, previously uploaded assets from the same workspace, and feature-flagged provider
imports that resolve to persisted assets.

The composer flow MUST distinguish transient `pickerSelectionIds`, draft-level `draftAttachmentIds`,
and persisted publication `assetIds`. Opening the picker MUST stage the current draft attachments
for replace-set editing. Upload or provider import that creates or resolves persisted assets MUST
refresh them into the active picker session and MUST auto-stage the resulting asset IDs once they
resolve to selectable persisted assets. The draft MUST change only when the user explicitly applies
the picker result. Publication submission MUST continue using persisted `assetIds` derived from the
confirmed draft attachment set.
(Previously: The composer supported persisted asset reuse, but did not define staged picker
selection, draft replacement semantics, or same-session upload/import auto-staging.)

#### Scenario: Upload or import stages persisted assets before draft commit

- GIVEN the picker is open in the composer
- WHEN upload or provider import yields persisted asset IDs
- THEN those asset IDs MUST become available in the active picker session
- AND newly created assets MUST become staged selections automatically once they resolve to
  selectable persisted assets
- AND the draft attachment set MUST remain unchanged until apply

#### Scenario: Applying the picker updates draft attachments but not publication persistence

- GIVEN staged `pickerSelectionIds` differ from `draftAttachmentIds`
- WHEN the user confirms the picker
- THEN `draftAttachmentIds` MUST be replaced by the staged selection
- AND persisted publication `assetIds` MUST change only when the draft is later saved or published

## ADDED Requirements

### Requirement: Multi-channel attachment limit enforcement

The composer and publishing flow MUST enforce an effective attachment limit equal to the minimum
`maxAttachments` across all currently selected target channels. If a later channel change makes the
current draft attachments invalid, the system MUST preserve the attachments in the draft, surface
the invalid state, and block publish or schedule actions until the author resolves the mismatch.

#### Scenario: Effective limit uses the strictest selected channel

- GIVEN the author selects multiple target channels with different attachment limits
- WHEN the composer evaluates attachment capacity
- THEN the effective limit MUST equal the minimum channel `maxAttachments`
- AND the picker or draft flow MUST prevent confirming more attachments than that limit

#### Scenario: Channel change invalidates existing attachments without auto-removal

- GIVEN the draft currently has attachments within the prior limit
- WHEN the selected channels change and the effective limit becomes lower than the current
  attachment count
- THEN the system MUST keep the existing draft attachments
- AND it MUST surface an invalid state and block publish or schedule until resolved
