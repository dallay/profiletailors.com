# Delta for Publishing

## ADDED Requirements

### Requirement: Publication Asset PATCH Tri-State Semantics

The publishing API MUST preserve CREATE asset behavior while giving PATCH publication edits explicit tri-state `assetIds` semantics. For edit requests, absent or `null` `assetIds` MUST preserve the publication's current asset IDs, an empty array MUST clear all current assets, and a non-empty array MUST replace current assets exactly in request order. CREATE semantics MUST remain unchanged: absent/default `assetIds` creates no assets, and provided IDs are used. Workspace-scoped targeting, update-not-found behavior, and the existing #224/#225 edit hardening behavior MUST remain unchanged.

#### Scenario: PATCH assetIds absent preserves current assets

- GIVEN a same-workspace editable publication has asset IDs `[A, B]`
- WHEN PATCH edits text and omits `assetIds`
- THEN the persisted publication MUST keep asset IDs `[A, B]`

#### Scenario: PATCH assetIds null preserves current assets

- GIVEN a same-workspace editable publication has asset IDs `[A, B]`
- WHEN PATCH includes `"assetIds": null`
- THEN the persisted publication MUST keep asset IDs `[A, B]`

#### Scenario: PATCH assetIds empty clears assets

- GIVEN a same-workspace editable publication has asset IDs `[A, B]`
- WHEN PATCH includes `"assetIds": []`
- THEN the persisted publication MUST have no asset IDs

#### Scenario: PATCH assetIds list replaces exactly

- GIVEN a same-workspace editable publication has asset IDs `[A, B]`
- WHEN PATCH includes `"assetIds": ["C", "A"]`
- THEN the persisted publication MUST have asset IDs `[C, A]`

#### Scenario: CREATE asset behavior is unchanged

- GIVEN a valid create request omits `assetIds` or uses the default value
- WHEN the publication is created
- THEN the persisted publication MUST have no asset IDs
- AND a create request with IDs MUST persist those IDs

#### Scenario: Workspace isolation remains enforced

- GIVEN workspace A edits publication `P1` and workspace B owns another `P1`
- WHEN A sends any PATCH `assetIds` shape
- THEN only A's target row MAY change
- AND #224/#225 status and workspace rules MUST remain unchanged

### Requirement: Composer Edit Asset Hydration and Submission

The scheduler composer MUST hydrate resolvable existing asset summaries when opened in edit mode and display previews for those assets. Missing or deleted assets MUST be handled gracefully without crashing the editor and MUST NOT silently clear unrelated valid asset IDs. Saving an edit without asset interaction MUST omit `assetIds` from PATCH. Explicit remove-all MUST send `assetIds: []`. Selecting or replacing assets MUST send the selected asset IDs exactly.

#### Scenario: Edit modal hydrates and previews existing assets

- GIVEN an editable publication has resolvable asset IDs `[A, B]`
- WHEN the edit modal opens
- THEN the composer MUST load summaries for A and B
- AND previews for A and B MUST be displayed

#### Scenario: Missing asset hydration is graceful

- GIVEN an editable publication references valid asset `A` and missing asset `Z`
- WHEN the edit modal hydrates assets
- THEN the editor MUST remain usable and show resolvable asset `A`
- AND it MUST NOT remove `A` or crash because `Z` is missing

#### Scenario: Untouched save omits assetIds

- GIVEN the edit modal opened with existing assets
- WHEN the user saves without touching assets
- THEN the PATCH body MUST omit `assetIds`

#### Scenario: Explicit remove-all sends empty array

- GIVEN the edit modal opened with existing assets
- WHEN the user removes all assets
- THEN the PATCH body MUST include `"assetIds": []`

#### Scenario: Selecting replacement sends selected IDs

- GIVEN the edit modal is open
- WHEN the user selects assets `[C, D]`
- THEN the PATCH body MUST include `"assetIds": ["C", "D"]`

#### Scenario: TDD acceptance coverage exists

- GIVEN backend and frontend regression tests are written first
- WHEN the focused suites run
- THEN they MUST cover all PATCH tri-state cases, CREATE compatibility, hydration, missing assets, untouched save omission, clear-all, replacement, and unchanged workspace/#224/#225 behavior
