# Delta for media-provider-unsplash

## MODIFIED Requirements

### Requirement: Composer picker exposes a provider tab

The parent MUST supply `provider="unsplash"` only when the provider is configured and the feature flag is enabled. When that prop is present, the composer media picker MUST render an Unsplash provider tab alongside Library; otherwise it MUST hide the tab. The tab MUST remain shell-only: no fetching, no persistence, and no asset mutation inside the picker shell. All provider-specific calls MUST happen in a parent-owned panel that emits `provider-search` and `provider-import`. Provider import MUST behave as an action within the picker flow, not as a browsable source replacement, and MUST keep the picker open so the author can continue staged multi-selection.
(Previously: The picker exposed a provider tab and parent-owned provider events, but import behavior did not explicitly preserve the open picker session or distinguish provider browsing from action semantics.)

#### Scenario: Provider tab appears only when enabled

- GIVEN a parent component passes `provider="unsplash"` and the feature flag is enabled
- WHEN the picker renders
- THEN an Unsplash tab MUST be visible alongside Library
- AND when the provider is unavailable the Unsplash tab MUST NOT render

#### Scenario: Importing a result keeps multi-selection active

- GIVEN a provider-search result is displayed by the parent panel
- WHEN the author clicks Import
- THEN the picker MUST emit `provider-import` with `{ externalId }`
- AND the picker MUST remain open so the imported asset can join the staged selection
