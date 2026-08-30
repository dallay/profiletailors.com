# Idea Composer Specification

## Purpose

Single IdeaComposerModal (create|edit) with shared validation, chips/links, delete confirm, and handoff prefill to Publishing composer; legacy POST /convert kept additive. AC 11–33, 38–42.

## Requirements

### Requirement: Unified Entry Points

System MUST use one composer for create|edit sharing form/validation (AC 15). Top Add→create first lane (AC 12); lane +→create preselected (AC 13); card→edit populated (AC 11). Quick Capture/Sheet primary UX removed (AC 14).

#### Scenario: Top Add (AC 12)
- GIVEN on `/ideas`
- WHEN top Add Idea
- THEN mode=create first lane selected

#### Scenario: Lane Add (AC 13)
- GIVEN In Progress lane
- WHEN lane Add
- THEN mode=create preselected In Progress

#### Scenario: Edit card (AC 11)
- GIVEN idea exists
- WHEN card clicked
- THEN same composer mode=edit populated

### Requirement: Validation and Column

Title MUST be required; empty blocks submit. Notes (AC 17) and column editable and persist. Save MUST block duplicate and show states. (AC 16–18,22)

#### Scenario: Title required (AC 16)
- GIVEN empty title
- WHEN save
- THEN blocked, error, no call

#### Scenario: Duplicate guard (AC 22)
- GIVEN save inflight
- WHEN second click
- THEN disabled until settled

#### Scenario: Column change (AC 18)
- GIVEN edit Raw
- WHEN change to Done and save
- THEN PATCH persists Done

### Requirement: Tags and Links Structured UX

Tags MUST use chips not CSV; trim/dedupe. Links MUST use structured add/remove with https validation. (AC 19,20)

#### Scenario: Tags dedupe (AC 19)
- GIVEN input " kotlin , KOTLIN , testing "
- WHEN saved
- THEN persisted [kotlin,testing]

#### Scenario: Links validate (AC 20)
- GIVEN add https://example.com label Spec
- WHEN saved
- THEN count+1; invalid url rejected

### Requirement: Delete Confirmation

Delete MUST need confirm; cancel preserves; confirm calls DELETE. (AC 21)

#### Scenario: Delete confirm (AC 21)
- GIVEN edit open
- WHEN Delete→Confirm
- THEN removed; cancel keeps

### Requirement: Handoff Prefill

Unsaved Create Post MUST first POST idea to establish id. Prefill is `title+"\n\n"+notes+tags as #tags` deduping hashtags. Opens real Publishing composer. No media. (AC 23,24,25,41)

#### Scenario: Persist before handoff (AC 23)
- GIVEN new unsaved idea
- WHEN Create Post
- THEN POST /ideas then Publishing composer opens prefilled, no publication yet

#### Scenario: Dedup hashtags (AC 25)
- GIVEN notes #kafka and tags kafka
- WHEN prefill built
- THEN single #kafka

### Requirement: Publishing Authority

Publishing composer owns channel/media/AI/hashtags/schedule. Ideas MUST NOT auto-pick first active channel. (AC 26–28)

#### Scenario: No auto channel (AC 28)
- GIVEN 3 active channels
- WHEN handoff composer shown
- THEN no auto-select

### Requirement: Publication Guards

No publication until Publishing submit (AC 29). Cancel keeps null (AC 30). Success associates via PATCH convertedToPublicationId (AC 31). No auto delete/move (AC 32,33).

#### Scenario: Cancel not converted (AC 30)
- GIVEN handoff I1
- WHEN cancel
- THEN convertedToPublicationId null

#### Scenario: Associate (AC 31)
- GIVEN submit returns P9
- WHEN associate
- THEN I1 convertedToPublicationId=P9 stays in lane

#### Scenario: No auto move/delete (AC 32,33)
- GIVEN associated
- WHEN reload
- THEN same lane

### Requirement: Legacy Convert Kept

POST /api/ideas/{id}/convert MUST stay 200 creating NOW via first active channel; handoff never calls it; docs mark legacy. (additive)

#### Scenario: Legacy 200
- GIVEN idea+active channel
- WHEN POST /convert
- THEN 200; handoff not using it

### Requirement: E2E i18n a11y

Critical create/edit/move/handoff MUST have Playwright (AC 39) and Vitest for composer (AC 38); en/es locales no fixed-width; dialog traps focus, Escape confirms if dirty, keyboard DnD; tests updated not bypassed (AC 40); no branding (AC 42).

#### Scenario: A11y i18n
- GIVEN ES keyboard only
- WHEN navigating composer
- THEN focus trapped, operable, Escape confirms if dirty, no competitor strings
