# Growth Score Specification

## Purpose

Delivers a single composite metric (0–100) that summarizes overall social media growth health, with
a breakdown of contributing factors and the top opportunity to improve the score.

## Data Model

```ts
interface GrowthScore {
  score: number            // 0-100
  label: string            // "Growth Score"
  trend: 'improving' | 'declining' | 'stable'
  delta: number            // change from previous period
  factors: ScoreFactor[]
  topOpportunity: Opportunity
  lastCalculated: string   // ISO 8601
}

interface ScoreFactor {
  name: string             // "Engagement Rate"
  value: number            // 0-100 contribution
  weight: number           // percentage weight in total
  status: 'strong' | 'moderate' | 'weak'
}

interface Opportunity {
  title: string
  description: string
  potentialImpact: number  // estimated score increase
  cta: {
    label: string
    target: string
  }
}
```

## Requirements

### Requirement: Score Display

The system SHALL render the growth score as a large circular gauge with the numeric score centered
inside.

#### Scenario: Score renders correctly

- GIVEN the growth score is 84
- WHEN the section renders
- THEN a circular gauge displays "84" in the center at 5xl font
- AND the gauge arc fills to 84% using the accent color
- AND a "/100" label renders below the number

#### Scenario: Score trend indicator

- GIVEN the score has `trend: 'improving'` and `delta: +5`
- WHEN the section renders
- THEN an upward arrow with "+5" renders below the score
- AND the indicator uses the success color

### Requirement: Factor Breakdown

The system SHALL display contributing factors as horizontal progress bars below the gauge.

#### Scenario: Factors render as bars

- GIVEN the score has 4 factors: Engagement Rate, Content Quality, Posting Consistency, Audience
  Growth
- WHEN the section renders
- THEN each factor renders with name, percentage bar, and weight label
- AND bars are ordered by weight descending
- AND bar fill uses status-based color: strong=success, moderate=warning, weak=error

### Requirement: Top Opportunity Card

The system SHALL render the top opportunity as a distinct card below the factors.

#### Scenario: Opportunity card renders

- GIVEN `topOpportunity` has `title: "Post 3x more on LinkedIn"`, `potentialImpact: 8`
- WHEN the section renders
- THEN a card displays the opportunity title and description
- AND a badge shows "+8 potential" in accent color
- AND a CTA button links to the target

#### Scenario: No opportunity available

- GIVEN `topOpportunity` is null
- WHEN the section renders
- THEN the opportunity card area is hidden
- AND a "Score is strong — keep it up" message displays instead

### Requirement: Score Loading State

The growth score SHALL show a skeleton gauge while loading.

#### Scenario: Loading state

- GIVEN the growth score data is being fetched
- WHEN the section renders
- THEN a circular skeleton pulse animation displays
- AND factor bars show as animated rectangles
