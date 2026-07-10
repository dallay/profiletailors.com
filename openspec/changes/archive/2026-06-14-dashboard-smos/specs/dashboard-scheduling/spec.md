# Dashboard Scheduling Specification

## Purpose

Combines two scheduling widgets: Best Posting Times (heatmap showing optimal publish windows) and
Upcoming Schedule (list of next scheduled posts with calendar link). Helps the user decide when to
publish and see what's coming.

## Data Model

```ts
interface TimeSlot {
  day: number              // 0=Sunday, 6=Saturday
  hour: number             // 0-23
  score: number            // 0-100 engagement probability
  label?: string           // "Peak", "Good", "Avoid"
}

interface BestPostingTimes {
  timezone: string
  slots: TimeSlot[]
  bestDay: string          // "Tuesday"
  bestHour: string         // "10:00 AM"
  reason: string           // "Your audience is most active on Tuesday mornings"
  lastAnalyzed: string     // ISO 8601
}

interface ScheduledPost {
  id: string
  title: string
  content: string
  platforms: string[]
  scheduledAt: string      // ISO 8601
  status: 'queued' | 'processing' | 'published' | 'failed'
}

interface UpcomingSchedule {
  posts: ScheduledPost[]
  nextPost?: ScheduledPost
  totalCount: number
}
```

## Requirements

### Requirement: Time Grid Heatmap

The system SHALL render a 7×24 heatmap grid showing engagement probability by day and hour.

#### Scenario: Heatmap renders with color intensity

- GIVEN the heatmap has 168 time slots (7 days × 24 hours)
- WHEN the section renders
- THEN each cell renders as a small colored rectangle
- AND cell color maps to score: 80-100 darkest, 60-79 medium, 40-59 light, <40 lightest
- AND day labels appear on the left (Mon–Sun)
- AND hour labels appear on top (6AM, 9AM, 12PM, 3PM, 6PM, 9PM)

#### Scenario: Hover shows slot details

- GIVEN the user hovers over a cell at Tuesday 10AM with score 92
- WHEN the hover occurs
- THEN a tooltip shows "Tuesday 10:00 AM — Score: 92"
- AND the tooltip uses the existing Tooltip component

### Requirement: Best Time Recommendation

The section SHALL highlight the top recommended posting time below the heatmap.

#### Scenario: Recommendation displays

- GIVEN `bestDay: "Tuesday"` and `bestHour: "10:00 AM"`
- WHEN the section renders
- THEN a recommendation badge shows "Best time: Tuesday at 10:00 AM"
- AND the badge uses accent color styling
- AND the reason text appears below in secondary text color

### Requirement: Upcoming Schedule List

The system SHALL display the next 5 scheduled posts in a compact list.

#### Scenario: Schedule list renders

- GIVEN 5 upcoming posts are scheduled
- WHEN the section renders
- THEN each post shows title, platform icons, and scheduled time
- AND posts are ordered by scheduledAt ascending
- AND the next post (soonest) has a "Next" indicator

#### Scenario: No upcoming posts

- GIVEN no posts are scheduled
- WHEN the section renders
- THEN an empty state shows "Nothing scheduled — create your first post"
- AND a CTA button links to the composer

### Requirement: Calendar CTA

The section SHALL include a "View Full Calendar" link.

#### Scenario: Calendar link navigates

- GIVEN the user clicks "View Full Calendar"
- WHEN the click occurs
- THEN the app navigates to the SchedulerView
- AND the calendar opens to the current week

### Requirement: Timezone Awareness

The heatmap SHALL display in the user's configured timezone.

#### Scenario: Timezone display

- GIVEN the user's timezone is "Europe/Madrid"
- WHEN the heatmap renders
- THEN all hour labels show Madrid time
- AND the timezone label displays below the section title
