# Delta for Publishing

## ADDED Requirements

### Requirement: Authenticated Create Reconciliation

After an authenticated create succeeds, the client MUST replace any optimistic publication identity
and fields with the returned backend publication. The store MUST use the returned `publicationId`,
`status`, `scheduleMode`, `scheduledFor`, `nextSlotAfter`, and `socialAccountId` as authoritative
values and MUST NOT retain a synthetic local ID.

#### Scenario: Standard create adopts server truth

- GIVEN authenticated creation has an optimistic local publication
- WHEN the backend returns a successful `PublicationResult`
- THEN the store MUST identify the publication by the returned `publicationId`
- AND MUST store the returned status, schedule, and social-account fields

#### Scenario: Freshly created publication is edited

- GIVEN an authenticated create succeeded and the publication was reconciled
- WHEN the user reopens it and saves an edit
- THEN PATCH MUST target the returned backend `publicationId`
- AND a successful response MUST replace local state with server truth

#### Scenario: PATCH target is absent in the workspace

- GIVEN the current workspace has no publication matching the PATCH identifier
- WHEN an authenticated edit is submitted
- THEN the backend MUST return 404
- AND publications in every other workspace MUST remain unchanged

### Requirement: Reconciled Composer Edit State

The edit composer MUST initialize schedule controls from authoritative reconciled fields. For `NOW`
and `NEXT_SLOT`, it MUST NOT prefill stale or invalid custom date/time values. Existing assets MUST
remain hydrated, previewed, and preserved when media is untouched. The explicit PATCH asset
semantics established by #223 MUST remain unchanged.

#### Scenario: NOW creation reopens without stale custom schedule

- GIVEN a created publication is reconciled with `scheduleMode = NOW`
- WHEN the edit composer opens
- THEN it MUST select NOW
- AND MUST NOT prefill a custom scheduled date/time from optimistic state

#### Scenario: NEXT_SLOT creation reopens without stale custom schedule

- GIVEN a created publication is reconciled with `scheduleMode = NEXT_SLOT`
- WHEN the edit composer opens
- THEN it MUST select NEXT_SLOT and use backend scheduling fields
- AND MUST NOT prefill invalid custom schedule data

#### Scenario: Untouched existing media is preserved

- GIVEN a reconciled publication has resolvable existing assets
- WHEN the user edits non-media fields and saves
- THEN the assets MUST remain hydrated and previewed
- AND PATCH MUST omit `assetIds`, preserving persisted assets

#### Scenario: Explicit media clear or replacement remains supported

- GIVEN the edit composer contains existing assets
- WHEN the user explicitly clears all assets or selects replacements
- THEN PATCH MUST send `assetIds: []` for clear or the exact selected IDs for replacement
