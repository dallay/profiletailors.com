# Composer Media Picker Specification

## Purpose

Define an accessible, localized composer modal shell for browsing reusable workspace media without owning asset retrieval or draft attachment.

## Requirements

### Requirement: Picker opening and dismissal

The composer MUST expose a control that opens the media picker while preserving current composer state. The picker MUST support dismissal through its close control, standard dialog keyboard behavior, and a close interaction emitted to the parent.

#### Scenario: Open picker without losing composer state

- GIVEN an author has entered content in the composer
- WHEN the author activates the media picker trigger
- THEN the picker MUST open
- AND the existing composer content MUST remain unchanged

#### Scenario: Dismiss picker with keyboard

- GIVEN the picker is open
- WHEN the author presses the standard dialog dismissal key
- THEN the picker MUST close
- AND focus MUST return to the triggering control

### Requirement: Accessible localized shell

The picker MUST present localized header text, search and filter controls, an asset-grid region, and accessible names for interactive controls in supported locales.

#### Scenario: Render localized controls

- GIVEN the application locale is English or Spanish
- WHEN the picker opens
- THEN all user-facing picker text MUST use that locale
- AND search, filter, close, and asset-grid regions MUST have accessible names

#### Scenario: Operate controls by keyboard

- GIVEN the picker is open
- WHEN the author navigates using only the keyboard
- THEN each enabled interactive control MUST be reachable and operable
- AND focus MUST remain within the modal until dismissal

### Requirement: Deterministic presentation states

The picker MUST render distinct loading, empty, error, ready, and disabled states from parent-provided inputs. Disabled controls MUST NOT emit search or filter interactions.

#### Scenario: Render loading and empty states

- GIVEN the parent reports loading or an empty asset collection
- WHEN the picker renders
- THEN it MUST show the corresponding localized state
- AND it MUST NOT show an actionable asset grid for that state

#### Scenario: Render error and disabled states

- GIVEN the parent provides an error or disabled state
- WHEN the picker renders
- THEN it MUST show the corresponding localized state
- AND disabled controls MUST NOT emit search or filter interactions

### Requirement: Parent-owned interaction contract

The picker MUST accept typed presentation inputs and MUST emit typed search, filter, and close interactions. It MUST NOT fetch, mutate, upload, delete, persist, or attach media assets.

#### Scenario: Emit search and filter interactions

- GIVEN the picker is ready and enabled
- WHEN the author changes the search query or filter
- THEN the picker MUST emit the corresponding typed interaction
- AND it MUST NOT directly access a media store or API

#### Scenario: Preserve shell-only scope

- GIVEN reusable assets are displayed by parent-provided data
- WHEN the author interacts with the shell
- THEN the picker MUST NOT persist asset selection or alter a draft
- AND it MUST NOT offer multi-selection, upload, deletion, or provider import

### Requirement: Asset region presentation

The picker MUST provide a dedicated asset-grid region for parent-provided media items and MAY render a non-interactive ready state when assets are available but selection behavior is not yet implemented.

#### Scenario: Render ready asset region

- GIVEN the parent provides one or more reusable assets
- WHEN the picker renders in a ready state
- THEN the asset-grid region MUST be visible
- AND each rendered asset MUST remain within shell-only presentation scope

#### Scenario: Render ready state without attachment behavior

- GIVEN the picker shows available assets before attachment support exists
- WHEN the author views the ready state
- THEN the picker MAY present assets without attach actions
- AND composer draft content MUST remain unchanged

### Requirement: Testable shell behavior

The system MUST define the shell behavior so focused component tests can verify open and close interactions, accessibility labels, state rendering, and emitted interactions.

#### Scenario: Verify accessible open and close behavior

- GIVEN the shell is exercised in a component test
- WHEN the test opens and dismisses the picker
- THEN the observable dialog and focus behavior MUST be assertable
- AND the close interaction MUST be observable

#### Scenario: Verify state and interaction emissions

- GIVEN the shell is exercised with loading, empty, error, disabled, and ready inputs
- WHEN the test triggers supported user interactions
- THEN each state-specific rendering MUST be assertable
- AND each supported emitted interaction MUST be observable
