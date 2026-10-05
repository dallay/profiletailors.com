# AI Insights Specification

## Purpose

Surfaces AI-generated recommendations with textual CTAs. The component supports a single hero card plus a grid of remaining insights, with priority-driven border accents.

## Data Model

```ts
type InsightType = 'recommendation' | 'alert' | 'opportunity'
type InsightPriority = 'high' | 'medium' | 'low'

interface AiInsight {
  id: string
  type: InsightType
  title: string
  description: string
  actionLabel: string
  actionTarget?: string
  priority: InsightPriority
  createdAt: string
  dismissed: boolean
}
```

## Requirements

### Requirement: Hero Insight Card

The system SHALL render the first insight (index 0) as a hero card. The hero card uses `border-l-2` with a priority-driven color (`high` → error, `medium` → warning, `low` → secondary). The card also surfaces the type badge and an optional "high priority" badge when applicable.

#### Scenario: Hero card renders the first insight

- GIVEN the insights array has at least one element
- WHEN `AiInsightsHero.vue` renders
- THEN the first insight appears inside a card with `border-l-2` and the matching priority accent
- AND the type badge text comes from `dashboard.insights.{type}`
- AND a "Dismiss" action emits `dismiss(id)`

### Requirement: List Grid Below the Hero

Remaining insights (slice(1)) SHALL render in a 1- or 2-column responsive grid depending on viewport width.

#### Scenario: Grid shows remaining insights

- GIVEN the insights array has 3 entries
- WHEN the section renders
- THEN the hero shows insight 1 and the grid shows insights 2 and 3 in a 2-column responsive grid
- AND each grid card uses the same priority-driven border accent as the hero

#### Scenario: Empty insights list

- GIVEN the insights array is empty
- WHEN the section renders
- THEN a centered placeholder appears using i18n key `dashboard.insights.empty`

### Requirement: Action Button

Each insight SHALL render an action button labeled `insight.actionLabel`. The component emits `action(insight)`; routing or modal behaviour is owned by the consumer.

#### Scenario: Action button is wired

- GIVEN the user clicks the hero's action button
- WHEN the click handler fires
- THEN `action` is emitted with the full insight object
- AND the consumer decides whether to navigate, open a modal, or do nothing

### Requirement: In-Memory Dismissal

The insights store SHALL persist `dismissed` state in memory only. There is no localStorage persistence in v1; dismissing an insight hides it until the page is reloaded.

#### Scenario: Dismiss hides the insight until reload

- GIVEN the user clicks "Dismiss"
- THEN `useInsightsStore().dismiss(id)` sets `dismissed = true` on the matching insight
- AND the reactive `activeInsights` getter filters out dismissed entries
- AND reloading the page resets the dismissal state to the fixture defaults

### Requirement: Mock Data Source

The insights store SHALL seed insights from `mock-data/insights.ts`. There is no backend endpoint in v1.