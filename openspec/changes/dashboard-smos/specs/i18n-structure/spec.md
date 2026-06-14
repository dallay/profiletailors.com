# i18n Structure Specification

## Purpose

Defines the translation key organization for all 11 dashboard sections. Every user-visible string MUST live in the i18n message object — no hardcoded text in components.

## Requirements

### Requirement: Namespace Per Section

Each dashboard section SHALL have its own i18n namespace under `dashboard.{section}`.

#### Scenario: Key structure

- GIVEN the dashboard overview section
- WHEN translation keys are defined
- THEN they live under `dashboard.overview.*`
- AND the root keys are: `title`, `subtitle`, `empty`, `error`
- AND card-specific keys are nested: `dashboard.overview.cards.{cardId}.*`

#### Scenario: All sections follow pattern

- GIVEN all 11 sections have i18n keys
- WHEN the message object is inspected
- THEN these namespaces exist: `overview`, `insights`, `growthScore`, `analytics`, `contentPipeline`, `scheduling`, `engagement`
- AND shared keys live under `dashboard.layout.*`

### Requirement: Complete EN/ES Coverage

Every i18n key SHALL have both English and Spanish translations.

#### Scenario: EN translation exists

- GIVEN a key `dashboard.overview.title`
- WHEN the EN message object is inspected
- THEN a non-empty string value exists

#### Scenario: ES translation exists

- GIVEN a key `dashboard.overview.title`
- WHEN the ES message object is inspected
- THEN a non-empty string value exists
- AND the ES string is different from EN (not a copy-paste)

### Requirement: Key Naming Convention

Keys SHALL use camelCase and be descriptive enough to understand context without code.

#### Scenario: Good key names

- GIVEN a section needs to label "Last 30 days"
- WHEN the key is named
- THEN it uses `dashboard.overview.period.last30Days`
- AND NOT `dashboard.overview.p3` or `dashboard.overview.text1`

### Requirement: Parameterized Strings

Translation strings with dynamic values SHALL use `{variable}` placeholders.

#### Scenario: Parameterized welcome

- GIVEN the key `dashboard.welcome` has value "Welcome back, {name}"
- WHEN `$t('dashboard.welcome', { name: 'Yuniel' })` is called
- THEN the output is "Welcome back, Yuniel"

### Requirement: i18n Key Inventory

The following key groups SHALL be defined (target: ~120 keys total):

```
dashboard.layout.*
  title, subtitle

dashboard.overview.*
  title, subtitle, lastUpdated, period.7d, period.30d, period.90d
  cards.scheduled, cards.platforms, cards.audience, cards.engagement
  empty

dashboard.insights.*
  title, subtitle, heroBadge, confidence, empty, emptyCta
  types.recommendation, types.alert, types.opportunity, types.milestone

dashboard.growthScore.*
  title, subtitle, potential, factors, opportunity, empty

dashboard.analytics.*
  title, subtitle, topPost, noPosts, platformFilter, allPlatforms
  growth.chartTitle, growth.noData

dashboard.contentPipeline.*
  title, subtitle, stages.ideas, stages.drafts, stages.scheduled, stages.published
  emptyColumn, addIdea, velocity

dashboard.scheduling.*
  title, subtitle, heatmap.bestTime, heatmap.reason, heatmap.timezone
  upcoming.title, upcoming.next, upcoming.empty, upcoming.viewCalendar

dashboard.engagement.*
  inbox.title, inbox.unread, inbox.newActivity, inbox.empty
  team.title, team.online, team.feed, team.empty
  actions.created, actions.scheduled, actions.published, actions.edited, actions.commented
```

#### Scenario: Key count check

- GIVEN all key groups are defined
- WHEN the total key count is tallied
- THEN it is between 100 and 140 keys
- AND no key is duplicated across namespaces
