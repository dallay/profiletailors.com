# Mock Data Strategy Specification

## Purpose

Defines the typed mock fixture system that powers all 11 dashboard sections during Phase 1. Ensures mock data matches future API contract interfaces so swapping to real data requires zero component changes.

## Requirements

### Requirement: Typed Fixtures Per Section

Each section SHALL have a corresponding TypeScript fixture file that exports a typed mock dataset matching the section's data model interface.

#### Scenario: Fixture type matches interface

- GIVEN the `dashboard-overview` spec defines `OverviewData` interface
- WHEN `mockData/overview.ts` exports its fixture
- THEN the export is typed as `OverviewData`
- AND the fixture compiles without TypeScript errors

#### Scenario: Fixture provides realistic data

- GIVEN the `dashboard-growth-score` fixture exports a score
- WHEN the fixture value is inspected
- THEN `score` is between 0 and 100
- AND `factors` array has at least 3 entries
- AND `topOpportunity.potentialImpact` is between 1 and 20

### Requirement: Fixture Directory Structure

Mock data SHALL live in `src/lib/mockData/` with one file per section.

```
src/lib/mockData/
├── overview.ts          // OverviewData
├── insights.ts          // InsightsData
├── growthScore.ts       // GrowthScore
├── analytics.ts         // AnalyticsData
├── contentPipeline.ts   // ContentPipelineData
├── scheduling.ts        // BestPostingTimes + UpcomingSchedule
├── engagement.ts        // InboxSummary + TeamActivity
├── index.ts             // barrel export
└── types.ts             // shared mock types if needed
```

#### Scenario: Barrel export works

- GIVEN all fixture files exist
- WHEN `import { mockOverview } from '@/lib/mockData'`
- THEN the import resolves without errors
- AND the imported value is the correct typed fixture

### Requirement: Realistic Distribution

Mock data SHALL represent realistic social media metrics for a mid-size B2B account.

#### Scenario: Engagement rates are realistic

- GIVEN mock analytics data is loaded
- WHEN engagement rates are inspected
- THEN LinkedIn rates are between 2% and 8%
- AND Twitter rates are between 0.5% and 3%
- AND no rate exceeds 15% (unrealistic for organic)

#### Scenario: Audience numbers are plausible

- GIVEN mock growth data is loaded
- WHEN follower counts are inspected
- THEN total followers are between 1,000 and 100,000
- AND daily growth is between -50 and +200

### Requirement: Fixture Randomization

Fixtures SHALL use a seeded random helper for minor value variations so repeated loads feel realistic without being identical.

#### Scenario: Fixture varies on reload

- GIVEN the mock data module is imported twice
- WHEN values are compared
- THEN core structure is identical
- BUT minor numeric values (sparkline points) may vary within ±10%

### Requirement: Zero Backend Dependency

Mock data SHALL be importable without any network requests, API calls, or environment variables.

#### Scenario: Mock data loads offline

- GIVEN the app is loaded with network disabled
- WHEN dashboard sections render
- THEN all sections display with mock data
- AND no console errors related to missing data
