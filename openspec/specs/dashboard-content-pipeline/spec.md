# Content Pipeline Specification

## Purpose

Provides a kanban-style view of content flowing through four stages: Ideas → Drafts → Scheduled →
Published. Gives the user a visual pipeline to understand content velocity and bottlenecks.

## Data Model

```ts
type PipelineStage = 'ideas' | 'drafts' | 'scheduled' | 'published'

interface PipelineCard {
  id: string
  title: string
  content: string
  stage: PipelineStage
  platform: string[]
  createdAt: string        // ISO 8601
  scheduledAt?: string     // ISO 8601, only for 'scheduled' stage
  author: {
    name: string
    avatar?: string
  }
  tags: string[]
  priority: 'high' | 'medium' | 'low'
}

interface PipelineColumn {
  stage: PipelineStage
  label: string            // i18n key
  cards: PipelineCard[]
  count: number
}

interface ContentPipelineData {
  columns: PipelineColumn[]
  totalCards: number
  lastUpdated: string
}
```

## Requirements

### Requirement: Kanban Columns

The system SHALL render four columns in horizontal layout: Ideas, Drafts, Scheduled, Published.

#### Scenario: Columns render with counts

- GIVEN the pipeline has 3 ideas, 2 drafts, 5 scheduled, 12 published
- WHEN the section renders
- THEN four columns display horizontally with headers showing stage name and card count
- AND columns are separated by subtle vertical dividers
- AND the total card count appears in the section header

#### Scenario: Column scroll on overflow

- GIVEN a column has more cards than fit vertically
- WHEN the user scrolls within the column
- THEN the column scrolls independently
- AND the column header remains sticky at the top

### Requirement: Pipeline Cards

Each card SHALL display title, content preview, platform badges, and author.

#### Scenario: Card renders fully

- GIVEN a card has title "Launch Announcement", 2 platforms, and an author
- WHEN the card renders
- THEN the title displays in bold
- THEN a content preview shows first 80 characters
- AND platform badges display as small pills
- AND the author name appears at the bottom

#### Scenario: Card with scheduled time

- GIVEN a card is in the "scheduled" stage with `scheduledAt: "2026-06-15T14:00:00Z"`
- WHEN the card renders
- THEN a clock icon and formatted date/time appear below the title
- AND the time uses the user's locale formatting

### Requirement: Card Priority Indicators

Each card SHALL show a priority indicator using color coding.

#### Scenario: High priority card

- GIVEN a card has `priority: 'high'`
- WHEN the card renders
- THEN a left border stripe uses the error/warning color
- AND the priority label shows in monospace font

### Requirement: Empty Column State

Columns with zero cards SHALL display a helpful empty state.

#### Scenario: Empty ideas column

- GIVEN the ideas column has 0 cards
- WHEN the section renders
- THEN the ideas column shows "No ideas yet — add your first one"
- AND an "Add Idea" button appears
- AND the button triggers the content creation flow

### Requirement: Pipeline Summary

The section header SHALL display a pipeline velocity summary.

#### Scenario: Velocity summary

- GIVEN the pipeline has cards distributed across all stages
- WHEN the section renders
- THEN the header shows "X ideas → Y drafts → Z scheduled → W published"
- AND each count uses the column's accent color
