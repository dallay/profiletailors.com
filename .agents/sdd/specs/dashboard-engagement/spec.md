# Dashboard Engagement Specification

## Purpose

Combines the Inbox Summary (grouped by message type) and the Team Activity feed (recent actions by team members). Gives the user a pulse on engagement and team productivity.

## Data Model

```ts
type InboxType = 'comment' | 'mention' | 'message' | 'lead'

interface InboxItem {
  id: string
  type: InboxType
  platform: Platform
  content: string
  from: string
  createdAt: string
  priority: 'high' | 'normal'
}

interface TeamMember {
  id: string
  name: string
  avatar?: string
  online: boolean
}

interface TeamActivityEvent {
  id: string
  memberId: string
  memberName: string
  action: string
  timestamp: string
}
```

## Requirements

### Requirement: Inbox Summary by Type

The system SHALL render the inbox summary as cards grouped by `InboxItem.type`. Each card exposes the type label, the item count, an icon glyph, and platform badges for the platforms with items of that type.

#### Scenario: Cards render for populated types

- GIVEN the inbox contains items with types `comment`, `mention`, `message`, `lead`
- WHEN `InboxSummary.vue` renders
- THEN a card appears for every type whose count is greater than zero (using `Object.entries(typeCounts).filter(([, count]) => count > 0)`)
- AND each card displays the type icon (`@`, `✉`, `→`, or `…` depending on the type), the count, and a list of platform badges (`linkedin`, `twitter`, `bluesky`, `threads`)
- AND cards use a 4-column responsive grid at the `lg` breakpoint

#### Scenario: Lead type uses warning accent

- GIVEN any inbox item has `type: 'lead'`
- WHEN the corresponding card renders
- THEN the card uses `border-l-[var(--warning-color)]` and the count uses `text-[var(--warning-color)]`

#### Scenario: Empty inbox

- GIVEN the inbox array is empty
- WHEN the section renders
- THEN a centered placeholder appears using i18n key `dashboard.inbox.noItems`

#### Scenario: High-priority indicator

- GIVEN at least one inbox item has `priority: 'high'`
- WHEN the section header renders
- THEN a warning-colored dot appears next to the high-priority count

### Requirement: Team Activity Feed

`TeamActivity.vue` SHALL render the first five events from the props.

#### Scenario: Feed renders up to five actions

- GIVEN the parent passes five or more events
- WHEN the section renders
- THEN only the first five are visible (`events.slice(0, 5)`)
- AND each entry shows an avatar placeholder (`memberName.charAt(0)`), the `memberName`, the textual `action`, and a relative time formatted via `formatRelativeTime`

#### Scenario: Online indicator

- GIVEN a `TeamMember.online === true` matches the event's `memberId`
- WHEN the row renders
- THEN a small success-color dot appears on the avatar
- AND `aria-label` reflects online/offline state via i18n keys `dashboard.teamActivity.online|offline`

#### Scenario: Empty feed

- GIVEN the events array is empty
- WHEN the section renders
- THEN a centered placeholder appears using i18n key `dashboard.teamActivity.noActivity`

### Requirement: Mock Data Source

Inbox items, team members, and team activity events SHALL be sourced from local fixture modules consumed by the dashboard store. There is no backend endpoint in v1.