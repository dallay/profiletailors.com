# Composer Media Picker — Delta for Media Provider (Unsplash)

## Purpose

Add an optional provider tab to the existing shell so users can import stock imagery from a
configured provider without the picker fetching or mutating anything itself.

## Added Requirements

### Requirement: Provider tab is shell-only and parent-owned

The composer media picker MUST accept an optional `provider: "unsplash" | null` prop and
MUST emit a `provider-import` interaction. The shell MUST NOT call any HTTP endpoint
directly; data fetching MUST happen in a parent-owned panel.

#### Scenario: Provider tab is conditional

- GIVEN a parent passes `provider="unsplash"`
- WHEN the picker renders
- THEN a provider tab MUST be visible
- AND when `provider` is `null` or omitted the tab MUST NOT render

#### Scenario: Parent owns search and import interactions

- GIVEN a provider tab is rendered
- WHEN the author types a query and presses Enter
- THEN the picker MUST emit `provider-search` with the typed payload
- AND the picker MUST NOT call any HTTP endpoint directly

#### Scenario: Importing a result emits provider-import

- GIVEN a provider result is displayed
- WHEN the author clicks "Import"
- THEN the picker MUST emit `provider-import` with `{ externalId }`
- AND the picker MUST NOT directly call the import API
