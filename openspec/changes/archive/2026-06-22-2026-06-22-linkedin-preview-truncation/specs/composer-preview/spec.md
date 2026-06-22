# Composer Preview Specification

## Purpose

Define provider-specific compose preview behavior in the Create Post modal, starting with LinkedIn long-text rendering while preserving a stable preview panel for future networks.

## Requirements

### Requirement: Bounded Long-Text Preview

The system MUST render long LinkedIn preview text within a bounded preview region so preview content does not grow without limit.

#### Scenario: Long text is visually clamped

- GIVEN a LinkedIn preview receives text exceeding the supported preview length
- WHEN the preview is rendered in the Create Post modal
- THEN the text MUST be visually truncated within the preview region
- AND the full text MUST remain unchanged in composer state

#### Scenario: Short text remains fully visible

- GIVEN a LinkedIn preview receives text that fits within the supported preview length
- WHEN the preview is rendered
- THEN the text MUST be shown in full
- AND no truncation treatment SHALL be applied

### Requirement: Truncation Affordance Visibility

The system MUST show a passive `...more` affordance only when preview text is truncated.

#### Scenario: Affordance appears for truncated text

- GIVEN a LinkedIn preview text is truncated by the preview bounds
- WHEN the truncated preview is displayed
- THEN the preview MUST display a visible `...more` affordance
- AND the affordance MUST be associated with the truncated text block

#### Scenario: Affordance is hidden for non-truncated text

- GIVEN a LinkedIn preview text is fully visible
- WHEN the preview is displayed
- THEN the preview MUST NOT display the `...more` affordance

### Requirement: Stable Modal Preview Layout

The system MUST preserve a stable Create Post modal layout while preview content changes.

#### Scenario: Long text does not expand the preview indefinitely

- GIVEN a Create Post modal with a LinkedIn preview and very long text
- WHEN the user types or pastes additional text
- THEN the preview column MUST remain visually bounded
- AND the modal SHALL preserve its controlled scrolling behavior

#### Scenario: Empty or edited content keeps the preview shell stable

- GIVEN the preview text changes from empty, short, or long states
- WHEN the modal re-renders the preview
- THEN the preview panel MUST keep a consistent container role in the layout
- AND sibling composer areas MUST remain usable without overflow side effects

### Requirement: Media-Compatible Truncation

The system MUST apply text truncation rules consistently whether the LinkedIn preview includes media or text only.

#### Scenario: Truncated text with image attachment

- GIVEN a LinkedIn preview includes a media attachment and long text
- WHEN the preview is rendered
- THEN the text MUST still obey the bounded truncation behavior
- AND the media preview MUST remain visible within the same preview card

#### Scenario: Truncated text with non-image media state

- GIVEN a LinkedIn preview includes a supported non-image media representation and long text
- WHEN the preview is rendered
- THEN the text truncation MUST remain active
- AND the preview MUST preserve a coherent media-plus-text layout

### Requirement: Provider-Specific Preview Boundary

The system MUST separate shared preview-panel behavior from provider-specific preview rendering so additional networks MAY be added without redefining the modal contract.

#### Scenario: Shared shell delegates provider rendering

- GIVEN the Create Post modal needs to render a social preview
- WHEN a provider-specific preview is selected
- THEN a shared preview container MUST host the preview area
- AND provider-specific rendering rules MUST be owned by a provider-specific component

#### Scenario: Future provider addition does not redefine LinkedIn rules

- GIVEN another social network preview is added later
- WHEN that preview is introduced
- THEN it MAY define its own rendering behavior inside the shared preview contract
- AND LinkedIn truncation behavior MUST remain a provider-owned rule
