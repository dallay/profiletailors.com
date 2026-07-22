# Delta for media-library

## ADDED Requirements

### Requirement: Active picker session refresh after upload or import

When an upload or provider import creates or returns persisted assets during an open composer picker
session, the media library MUST refresh or upsert those persisted assets into the active picker
session. Assets successfully uploaded or imported through the active picker session MUST become
staged automatically once they resolve to selectable persisted assets, and they MUST become visible
without requiring the author to reopen the picker.

#### Scenario: Upload adds persisted assets into the active picker session

- GIVEN the composer picker is open
- WHEN an upload completes and yields persisted asset IDs
- THEN those assets MUST appear in the active picker session
- AND the author MUST NOT need to close and reopen the picker to browse them

#### Scenario: Import upserts an existing persisted asset into the active picker session

- GIVEN the composer picker is open
- WHEN a provider import resolves to an existing persisted asset
- THEN the active picker session MUST surface that persisted asset
- AND it MUST remain available for staging in the same session

### Requirement: Picker-facing asset readiness presentation

The media library MUST expose asset state so picker surfaces can distinguish selection readiness.
`READY` assets MUST be selectable. When a `READY` asset has a preview it MUST provide a thumbnail;
when it has no preview, or when preview loading fails, it MUST provide fallback visuals while
remaining selectable. `PROCESSING` assets MUST remain visible with status or placeholder
presentation and MUST NOT be selectable. `FAILED` assets MUST remain visible with failure or
fallback presentation and MUST NOT be selectable.

#### Scenario: READY asset is selectable in picker surfaces

- GIVEN a persisted asset is `READY`
- WHEN a picker surface renders that asset
- THEN the asset MUST be selectable
- AND it MUST provide a thumbnail when one is available

#### Scenario: PROCESSING or FAILED asset remains visible without becoming selectable

- GIVEN a persisted asset is `PROCESSING` or is `FAILED`
- WHEN a picker surface renders that asset
- THEN the asset MUST remain visible with status or fallback presentation
- AND the picker MUST NOT treat it as selectable until it is `READY`

#### Scenario: READY asset without preview remains selectable with fallback

- GIVEN a persisted asset is `READY` but has no preview, or its preview fails to load
- WHEN a picker surface renders that asset
- THEN the asset MUST remain selectable
- AND it MUST render fallback visuals that preserve grid continuity
