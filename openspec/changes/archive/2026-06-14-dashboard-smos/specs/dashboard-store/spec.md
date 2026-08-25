# Dashboard Store Specification

## Purpose

Defines the Pinia store architecture for dashboard state — holding KPI data, insights, pipeline
state, and the global period selector. Stores use mock data in Phase 1 and are typed for real API
swap later.

## Requirements

### Requirement: Analytics Store

A Pinia store `useAnalyticsStore` SHALL hold KPI cards, platform metrics, audience growth, and the
global period selection.

#### Scenario: Store initializes with mock data

- GIVEN the analytics store is created
- WHEN the store initializes
- THEN `cards` contains 4+ KPI card objects matching `KpiCard` interface
- AND `platforms` contains platform metric objects
- AND `audienceGrowth` contains time-series data
- AND `period` defaults to `'30d'`

#### Scenario: Period change updates all views

- GIVEN the user calls `setPeriod('90d')`
- WHEN the period updates
- THEN `period` changes to `'90d'`
- AND a loading state is set for all dependent data
- AND after mock data refresh, cards, platforms, and growth data reflect 90-day values

#### Scenario: Loading state management

- GIVEN data is being fetched (or simulated fetch)
- WHEN `isLoading` is true
- THEN section components show skeleton states
- AND after data loads, `isLoading` becomes false

### Requirement: Insights Store

A Pinia store `useInsightsStore` SHALL hold AI recommendation data and track which insights have
been seen.

#### Scenario: Store provides insights

- GIVEN the insights store is initialized
- WHEN the store loads
- THEN `insights` array contains typed `AiInsight` objects
- AND `summary` contains a human-readable summary string
- AND the highest-priority insight is computable via a getter

#### Scenario: Mark insight as seen

- GIVEN the user interacts with an insight CTA
- WHEN `markSeen(insightId)` is called
- THEN the insight is added to `seenIds` Set
- AND the hero card rotates to the next unseen high-priority insight

### Requirement: Content Pipeline Store

A Pinia store `useContentPipelineStore` SHALL hold kanban board state with columns and cards.

#### Scenario: Store provides pipeline data

- GIVEN the pipeline store initializes
- WHEN data loads
- THEN `columns` contains 4 pipeline columns with typed cards
- AND `getColumnByStage('ideas')` returns the ideas column
- AND `getCardCount()` returns total cards across all columns

#### Scenario: Card count is reactive

- GIVEN the pipeline has 3 ideas, 2 drafts, 5 scheduled, 12 published
- WHEN `totalCards` is accessed
- THEN it returns 22
- AND it updates reactively if cards move between columns

### Requirement: Dashboard Period Sync

All dashboard stores SHALL share the same period state via the analytics store.

#### Scenario: Single source of truth

- GIVEN the analytics store has `period: '30d'`
- WHEN insights, pipeline, or engagement stores need period context
- THEN they read from `useAnalyticsStore().period`
- AND no store maintains its own period state

### Requirement: Store Loading Pipeline

Stores SHALL implement a coordinated loading sequence to avoid waterfall requests.

#### Scenario: Parallel initial load

- GIVEN the dashboard mounts
- WHEN stores initialize
- THEN analytics, insights, and pipeline stores load in parallel
- AND a composite `isReady` flag becomes true only when all stores finish loading
- AND the dashboard layout waits for `isReady` before rendering sections
