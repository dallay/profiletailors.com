# Content Pipeline Specification

## Purpose

Provides a kanban-style view of content flowing through four stages. The component supports keyboard-accessible move buttons and pointer-based drag-and-drop via `@atlaskit/pragmatic-drag-and-drop`.

## Data Model

```ts
type Platform = 'linkedin' | 'twitter' | 'bluesky' | 'threads'

interface PipelineCard {
  id: string
  title: string
  content: string
  platform: Platform                // one platform per card
  scheduledFor?: string
  author: string
  thumbnail?: string
  tags: string[]
}

interface PipelineColumn {
  id: string                        // stable identifier used for DnD
  title: string                     // i18n key
  cards: PipelineCard[]
}
```

## Requirements

### Requirement: Kanban Columns

The system SHALL render columns as a horizontal flex row that snaps to a four-column grid on `lg`. Each column shows the localized title and the card count.

#### Scenario: Columns render with counts

- GIVEN the store exposes four columns
- WHEN `ContentPipeline.vue` renders
- THEN each column appears with the localized title from `t(column.title)`
- AND the count badge shows `column.cards.length`
- AND each column has a `data-dnd-column` attribute set to the column id for drag-and-drop targeting

### Requirement: Pipeline Cards

Each card SHALL display its title, a platform badge, the author, and up to three tags. Cards have a drag handle region (`data-dnd-draggable`).

#### Scenario: Card renders fully

- GIVEN a card with title, single `platform`, `author`, and tags
- WHEN the card renders
- THEN the title appears with `line-clamp-2`
- AND the platform badge uses `platformBadgeColor[platform]`
- AND the author label appears next to the first three tags
- AND cards apply `cursor-grab` with `active:cursor-grabbing`

### Requirement: Move Buttons

Each card SHALL render two icon buttons (`Move left`, `Move right`) that emit `moveCard(cardId, fromColumnId, toColumnId, toIndex?)`. The left button is disabled on the first column; the right button is disabled on the last column.

#### Scenario: Move buttons disabled at column boundaries

- GIVEN a card lives in the first column
- WHEN the card renders
- THEN the left button is disabled
- AND clicking the right button emits `moveCard` targeting the following column

### Requirement: Drag-and-Drop

The component SHALL register Pragmatic drag-and-drop handlers (`monitorForElements`, `draggable`, `dropTargetForElements`) on every column and card. Drop targets emit `moveCard` with the computed drop index, normalizing same-column reorderings.

#### Scenario: Drag-and-drop registers and tears down

- GIVEN the columns prop changes
- WHEN the watch handler runs
- THEN existing handlers are cleaned up and new ones are registered after `nextTick`
- AND on unmount (`onBeforeUnmount`) all handlers are cleaned up

#### Scenario: Card drop recomputes target index

- GIVEN a card is dragged over another card in the same column
- WHEN the drop fires
- THEN `handleDrop` emits `moveCard` with the normalized target index, treating drops below the card's vertical midpoint as "insert after"

### Requirement: Mock Data Source

The pipeline store SHALL expose columns sourced from `mock-data/content-pipeline.ts`. There is no backend endpoint in v1. The store surface is `useContentPipelineStore().columns`.