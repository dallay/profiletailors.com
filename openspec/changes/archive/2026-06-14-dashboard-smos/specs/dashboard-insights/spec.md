# AI Insights Specification

## Purpose

Surfaces AI-generated recommendations with actionable CTAs — turning raw analytics data into the
user's next move. Each insight card suggests a specific action and links to the relevant tool.

## Data Model

```ts
interface AiInsight {
  id: string
  type: 'recommendation' | 'alert' | 'opportunity' | 'milestone'
  title: string
  description: string
  confidence: number       // 0-100, shown as badge
  priority: 'high' | 'medium' | 'low'
  cta: {
    label: string
    action: 'navigate' | 'open-modal' | 'external'
    target: string         // route path, modal id, or URL
  }
  icon: string             // icon identifier
  category: 'content' | 'scheduling' | 'engagement' | 'growth'
  createdAt: string        // ISO 8601
}

interface InsightsData {
  insights: AiInsight[]
  summary: string          // "3 actions to boost engagement"
  lastGenerated: string    // ISO 8601
}
```

## Requirements

### Requirement: Hero Insight Card

The system SHALL display the highest-priority insight as a hero card spanning the full section width
with prominent styling.

#### Scenario: Hero card renders top insight

- GIVEN the insights list contains 3 items with priorities high, medium, low
- WHEN the section renders
- THEN the high-priority insight renders as the hero card
- AND the hero card uses a distinct background (`bg-bg-surface` with `border-l-4 border-accent`)
- AND the title, description, and CTA button are all visible without scrolling

#### Scenario: Hero card CTA navigates

- GIVEN the hero insight has `cta.action: 'navigate'` and `cta.target: '/scheduler'`
- WHEN the user clicks the CTA button
- THEN the app navigates to the scheduler view
- AND the insight is marked as "seen" in local state

### Requirement: Insight Cards Grid

Below the hero, the system SHALL render remaining insights in a responsive grid.

#### Scenario: Grid shows remaining insights

- GIVEN there are 3 total insights (1 hero + 2 remaining)
- WHEN the section renders below the hero
- THEN 2 insight cards render in a 2-column grid
- AND each card shows icon, title, description, confidence badge, and CTA

#### Scenario: Single remaining insight

- GIVEN there are 2 total insights (1 hero + 1 remaining)
- WHEN the section renders
- THEN the remaining insight spans full width below the hero

### Requirement: Confidence Badge

Each insight SHALL display a confidence percentage as a small badge.

#### Scenario: Confidence badge renders

- GIVEN an insight has `confidence: 87`
- WHEN the card renders
- THEN a badge shows "87%" with monospace font
- AND the badge color corresponds to confidence level: ≥80 green, ≥50 yellow, <50 muted

### Requirement: Insight Type Styling

Each insight type SHALL have distinct visual treatment.

#### Scenario: Alert insight has warning styling

- GIVEN an insight has `type: 'alert'`
- WHEN the card renders
- THEN the card border uses warning color from design tokens
- AND the icon uses the warning color

#### Scenario: Milestone insight has success styling

- GIVEN an insight has `type: 'milestone'`
- WHEN the card renders
- THEN the card border uses success color
- AND a celebratory icon displays

### Requirement: Empty Insights State

When no insights are available, the section SHALL display a constructive empty state.

#### Scenario: No insights available

- GIVEN the insights list is empty
- WHEN the section renders
- THEN a message displays: "No insights yet — connect a platform to get started"
- AND a CTA button links to the channels/connect view
