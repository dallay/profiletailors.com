# Dashboard Engagement Specification

## Purpose

Combines Inbox Summary (unread messages, comments, mentions across platforms) and Team Activity (
recent actions by team members). Gives the user a pulse on engagement and team productivity.

## Data Model

```ts
interface InboxMetric {
  platform: 'linkedin' | 'twitter' | 'instagram' | 'facebook'
  unreadMessages: number
  pendingComments: number
  mentions: number
  total: number
  lastChecked: string      // ISO 8601
}

interface InboxSummary {
  metrics: InboxMetric[]
  totalUnread: number
  hasNewActivity: boolean
}

interface TeamAction {
  id: string
  member: {
    name: string
    avatar?: string
    role: 'admin' | 'editor' | 'viewer'
  }
  action: 'created' | 'scheduled' | 'published' | 'edited' | 'commented'
  target: string           // post title or description
  timestamp: string        // ISO 8601
  platform?: string
}

interface TeamActivity {
  actions: TeamAction[]
  onlineMembers: number
  totalMembers: number
}
```

## Requirements

### Requirement: Inbox Metric Cards

The system SHALL render per-platform inbox metrics as compact stat cards.

#### Scenario: Metrics render for connected platforms

- GIVEN 2 platforms have inbox data (LinkedIn: 12 unread, Twitter: 3 unread)
- WHEN the section renders
- THEN each platform renders a card with platform icon, unread count, and breakdown
- AND cards are ordered by unread count descending
- AND the total unread count appears in the section header

#### Scenario: Platform with zero unread

- GIVEN Instagram has 0 unread items
- WHEN the section renders
- THEN the Instagram card shows "0" with muted styling
- AND the card is visually de-emphasized

### Requirement: New Activity Indicator

The inbox section SHALL show a notification dot when new activity exists.

#### Scenario: New activity indicator

- GIVEN `hasNewActivity: true`
- WHEN the section renders
- THEN a pulsing dot appears next to the "Inbox" label
- AND the dot uses the accent color

### Requirement: Team Activity Feed

The system SHALL render the 5 most recent team actions as a feed.

#### Scenario: Activity feed renders

- GIVEN 5 recent team actions exist
- WHEN the section renders
- THEN each action shows member avatar, name, action verb, target, and relative time
- AND actions use icons: ✏️ created, 📅 scheduled, 🚀 published, 🔍 edited, 💬 commented
- AND the feed is ordered by timestamp descending

#### Scenario: Relative time formatting

- GIVEN an action occurred 3 hours ago
- WHEN the feed renders
- THEN the timestamp shows "3h ago"
- AND actions within the last minute show "just now"

### Requirement: Online Members Indicator

The team section SHALL show how many team members are currently active.

#### Scenario: Online indicator

- GIVEN `onlineMembers: 3` and `totalMembers: 8`
- WHEN the section renders
- THEN a label shows "3 of 8 online"
- AND online members have a green dot indicator
