# E2E Test Plan — Scheduler Posts

> Last updated: 2026-06-17
> Stack: Playwright + Vitest (or standalone)
> Base URL: `http://localhost:5173` (Vite dev server)
> Auth: `dev@profiletailors.com` / `TEST_PASSWORD_S3cr3tP@ssw0rd*123`
> Backend: Spring Boot on `localhost:8080`

---

## 1. Scope

End-to-end testing of the **scheduler posts** feature across the Profile Tailors SMP application.
Covers:

- Authentication and session management
- Calendar views (month, week, day) and list view
- Creating posts via the composer modal with all schedule modes
- Post card rendering, interaction, and read-only detail modal
- Channel filtering and post-type filtering
- Past/future slot behavior (disabled vs read-only)
- Drag-and-drop rescheduling
- Backend integration (WireMock LinkedIn mock)
- Theme and locale persistence

---

## 2. Environment

| Component                | URL / Port              |
|--------------------------|-------------------------|
| Frontend (Vite)          | `http://localhost:5173` |
| Backend (Spring Boot)    | `http://localhost:8080` |
| WireMock (LinkedIn mock) | `http://localhost:8089` |
| PostgreSQL               | `localhost:5432`        |

**Prerequisites before running:**

```bash
docker compose up -d linkedin-wiremock postgresql
./gradlew :server:smp:bootRun --args='--spring.profiles.active=dev'
pnpm --filter app dev
```

---

## 3. Test Cases

### TC-01: Login and Session

| Step | Action                                                                  | Expected                                |
|------|-------------------------------------------------------------------------|-----------------------------------------|
| 1    | Navigate to `http://localhost:5173`                                     | Login page renders                      |
| 2    | Verify heading: "Build your publishing system without dashboard chaos." | Heading visible                         |
| 3    | Fill email: `dev@profiletailors.com`                                    | Input accepts text                      |
| 4    | Fill password: `TEST_PASSWORD_S3cr3tP@ssw0rd*123`                       | Input accepts text                      |
| 5    | Click "Sign in" button                                                  | Redirects to `/` (Dashboard)            |
| 6    | Verify heading: "Welcome back, Dev User"                                | User is logged in                       |
| 7    | Refresh the page                                                        | User stays logged in (session persists) |

**Tags:** `@auth @smoke`

---

### TC-02: Navigate to Scheduler

| Step | Action                                                    | Expected                                     |
|------|-----------------------------------------------------------|----------------------------------------------|
| 1    | From Dashboard, click "Scheduler" in sidebar              | Scheduler view loads                         |
| 2    | Verify heading: "Scheduler"                               | Correct page                                 |
| 3    | Verify week view is default                               | Week grid renders with 24h slots (12AM–11PM) |
| 4    | Verify day names in header (Mon–Sun)                      | Days of current week shown                   |
| 5    | Verify time labels on left axis (12 AM, 1 AM, ..., 11 PM) | 24 hourly slots visible                      |

**Tags:** `@navigation @scheduler`

---

### TC-03: Calendar View Switching

| Step | Action                                      | Expected                          |
|------|---------------------------------------------|-----------------------------------|
| 1    | Click "Calendar" (month toggle)             | Month grid renders (6×7 cells)    |
| 2    | Verify day numbers are shown in each cell   | Numbers visible                   |
| 3    | Click "Week"                                | Week view renders with time slots |
| 4    | Click "Day"                                 | Day view renders for today        |
| 5    | Verify "TODAY" navigation button is present | Button visible                    |

**Tags:** `@navigation @scheduler @views`

---

### TC-04: Navigate to Past/Future Months

| Step | Action                                                  | Expected                      |
|------|---------------------------------------------------------|-------------------------------|
| 1    | In month view, click forward arrow                      | Next month renders            |
| 2    | Click backward arrow                                    | Previous month renders        |
| 3    | Click "TODAY"                                           | Returns to current month/week |
| 4    | Verify past month cells have diagonal stripe background | Past cells styled as disabled |
| 5    | Verify past month cells show existing posts (read-only) | Posts visible and readable    |

**Tags:** `@navigation @scheduler @past`

---

### TC-05: Create Post — Now Mode

| Step | Action                                                   | Expected                                      |
|------|----------------------------------------------------------|-----------------------------------------------|
| 1    | Click "NEW POST" button                                  | Create Post modal opens                       |
| 2    | Verify modal heading: "CREATE POST"                      | Modal visible                                 |
| 3    | Verify schedule mode: "NOW" is selected by default       | Active tab highlighted                        |
| 4    | Type text in textarea: "E2E test post via Now mode"      | Text appears in textarea and LinkedIn preview |
| 5    | Select a LinkedIn channel from the channel chips         | Channel chip is highlighted                   |
| 6    | Verify button label: "Schedule Now"                      | Button text correct                           |
| 7    | Click "Schedule Now"                                     | Modal closes                                  |
| 8    | Verify the post appears in the calendar/list             | New post card visible                         |
| 9    | Wait 30 seconds, verify post status changes to PUBLISHED | Status badge updates                          |

**Tags:** `@creation @now @e2e`

---

### TC-06: Create Post — Next Schedule Mode

| Step | Action                                                               | Expected                     |
|------|----------------------------------------------------------------------|------------------------------|
| 1    | Click "NEW POST"                                                     | Modal opens                  |
| 2    | Click "NEXT SCHEDULE" tab                                            | Tab is active                |
| 3    | Verify helper text: "Publishes in the next available schedule slot." | Text visible                 |
| 4    | Type text: "E2E test via Next Schedule"                              | Text in textarea             |
| 5    | Select a channel                                                     | Channel highlighted          |
| 6    | Verify button label: "Next Schedule"                                 | Button text correct          |
| 7    | Click "Next Schedule"                                                | Modal closes                 |
| 8    | Verify post appears with status SCHEDULED                            | Status badge shows SCHEDULED |

**Tags:** `@creation @next-schedule @e2e`

---

### TC-07: Create Post — Pick Date Mode

| Step | Action                                               | Expected                         |
|------|------------------------------------------------------|----------------------------------|
| 1    | Click "NEW POST"                                     | Modal opens                      |
| 2    | Click "PICK DATE" tab                                | Tab is active                    |
| 3    | Verify helper text: "Publishes on [date] at [time]." | Text visible                     |
| 4    | Click the date button (e.g., "Wed, Jun 17, 2026")    | Calendar popover opens           |
| 5    | Select a future date on the calendar                 | Date is selected, popover closes |
| 6    | Verify button label changes to "Schedule Post"       | Button text correct              |
| 7    | Type text: "E2E test via Pick Date"                  | Text in textarea                 |
| 8    | Select a channel                                     | Channel highlighted              |
| 9    | Click "Schedule Post"                                | Modal closes                     |
| 10   | Verify post appears in calendar on the selected date | Post card visible                |

**Tags:** `@creation @pick-date @e2e`

---

### TC-08: Create Post — Validation Errors

| Step | Action                                       | Expected        |
|------|----------------------------------------------|-----------------|
| 1    | Click "NEW POST"                             | Modal opens     |
| 2    | Leave textarea empty                         | No text entered |
| 3    | Verify "Schedule Now" button is disabled     | Button disabled |
| 4    | Click "PICK DATE"                            | Tab active      |
| 5    | Click "Schedule Post" without selecting date | Button disabled |
| 6    | Close modal                                  | Modal closes    |

**Tags:** `@creation @validation`

---

### TC-09: Create Post — Media Upload

| Step | Action                                               | Expected              |
|------|------------------------------------------------------|-----------------------|
| 1    | Click "NEW POST"                                     | Modal opens           |
| 2    | Verify "Media Attachment (Max 10MB)" section visible | Section visible       |
| 3    | Click "select a file" link                           | File picker opens     |
| 4    | Upload an image file (< 10MB)                        | Image preview appears |
| 5    | Verify delete button (X) on the preview              | Delete button visible |
| 6    | Click delete button                                  | Preview removed       |

**Tags:** `@creation @media`

---

### TC-10: Create Post — Priority Queue & Create Another

| Step | Action                          | Expected                         |
|------|---------------------------------|----------------------------------|
| 1    | Click "NEW POST"                | Modal opens                      |
| 2    | Check "Priority Queue" checkbox | Checkbox checked                 |
| 3    | Check "Create Another" checkbox | Checkbox checked                 |
| 4    | Fill text and select channel    | Content ready                    |
| 5    | Click "Schedule Now"            | Post submitted, modal stays open |
| 6    | Verify textarea is cleared      | Ready for next post              |
| 7    | Click "CANCEL"                  | Modal closes                     |

**Tags:** `@creation @priority @ux`

---

### TC-11: Post Cards — Click to View Detail Modal

| Step | Action                                          | Expected                |
|------|-------------------------------------------------|-------------------------|
| 1    | In week/day view, find a post card              | Post card visible       |
| 2    | Click on the post card                          | Post Detail modal opens |
| 3    | Verify modal heading: "Post Details"            | Modal visible           |
| 4    | Verify title and body text are displayed        | Content correct         |
| 5    | Verify "Scheduled For" date/time                | Metadata shown          |
| 6    | If post is PUBLISHED: verify "Read Only" badge  | Badge visible           |
| 7    | If post is PUBLISHED: verify "View Post" button | Button visible          |
| 8    | Click "Close" button                            | Modal closes            |

**Tags:** `@post-detail @read-only`

---

### TC-12: Post Detail Modal — View Post Link

| Step | Action                                          | Expected                        |
|------|-------------------------------------------------|---------------------------------|
| 1    | Click on a PUBLISHED post card                  | Post Detail modal opens         |
| 2    | Verify "View Post" button is enabled            | Button clickable                |
| 3    | Click "View Post"                               | New tab opens with LinkedIn URL |
| 4    | Verify URL contains `linkedin.com/feed/update/` | Correct LinkedIn share URL      |

**Tags:** `@post-detail @external-link`

---

### TC-13: Post Cards — Delete

| Step | Action                                        | Expected                           |
|------|-----------------------------------------------|------------------------------------|
| 1    | Hover over a post card                        | Delete button (trash icon) appears |
| 2    | Click the delete button                       | Post is removed                    |
| 3    | Verify post no longer appears in the calendar | Card gone                          |

**Tags:** `@post-delete`

---

### TC-14: Add Post Button (+) in Calendar Cells

| Step | Action                                                | Expected                |
|------|-------------------------------------------------------|-------------------------|
| 1    | In week view, hover over an empty enabled slot        | + button appears        |
| 2    | Click the + button                                    | Create Post modal opens |
| 3    | Verify the date/time is pre-filled to the slot's time | Correct date            |
| 4    | Close modal                                           | Modal closes            |
| 5    | In month view, hover over an empty current-month cell | + button appears        |
| 6    | Click the + button                                    | Create Post modal opens |
| 7    | Verify the date is pre-filled to the cell's date      | Correct date            |

**Tags:** `@scheduler @ux @add-button`

---

### TC-15: Past Slots — Read-Only Posts

| Step | Action                                              | Expected                          |
|------|-----------------------------------------------------|-----------------------------------|
| 1    | Navigate to a past week in the scheduler            | Past week visible                 |
| 2    | Verify past cells have diagonal stripe overlay      | Disabled styling                  |
| 3    | Verify posts are visible and readable in past cells | Content legible                   |
| 4    | Hover over a past post card                         | Card is interactive (hover state) |
| 5    | Click on a past post card                           | Detail modal opens                |
| 6    | Verify detail modal shows full content              | Content readable                  |
| 7    | Verify post status (PUBLISHED, FAILED, etc.)        | Status badge correct              |
| 8    | Close modal                                         | Modal closes                      |

**Tags:** `@past @read-only`

---

### TC-16: Past Slots — Cannot Create or Drop

| Step | Action                                        | Expected                        |
|------|-----------------------------------------------|---------------------------------|
| 1    | In week view, hover over a past slot          | Cursor is `not-allowed`         |
| 2    | Click on a past slot                          | No modal opens (blocked)        |
| 3    | Verify + button does NOT appear in past cells | Button hidden                   |
| 4    | Verify past cells have `aria-disabled="true"` | Accessibility attribute present |

**Tags:** `@past @disabled @a11y`

---

### TC-17: Channel Filtering

| Step | Action                                            | Expected                  |
|------|---------------------------------------------------|---------------------------|
| 1    | In sidebar, click "IN LinkedIn profile"           | Only LinkedIn posts shown |
| 2    | Verify all visible post cards have LinkedIn badge | Filter applied            |
| 3    | Click "All channels"                              | All posts shown again     |
| 4    | Verify mixed channel posts are visible            | Filter cleared            |

**Tags:** `@filtering @channels`

---

### TC-18: Post Type Filtering

| Step | Action                                       | Expected                   |
|------|----------------------------------------------|----------------------------|
| 1    | Select "📁 All Posts" in the filter dropdown | All posts visible          |
| 2    | Select "⏳ Queued"                            | Only queued posts shown    |
| 3    | Select "✅ Published"                         | Only published posts shown |
| 4    | Select "🚫 Cancelled"                        | Only cancelled posts shown |
| 5    | Select "📁 All Posts" again                  | All posts restored         |

**Tags:** `@filtering @post-type`

---

### TC-19: Day View — All Posts for a Day

| Step | Action                                   | Expected                 |
|------|------------------------------------------|--------------------------|
| 1    | Switch to "Day" view                     | Today's day view renders |
| 2    | Verify "All day" section with date label | Section visible          |
| 3    | Verify all posts for today are listed    | Posts visible            |
| 4    | Click on a post card                     | Detail modal opens       |
| 5    | Close modal                              | Modal closes             |

**Tags:** `@day-view @posts`

---

### TC-20: List View — All Posts

| Step | Action                                                             | Expected              |
|------|--------------------------------------------------------------------|-----------------------|
| 1    | Click "List" view toggle                                           | List view renders     |
| 2    | Verify each row shows: date, status badge, content, channel badges | Row structure correct |
| 3    | Verify published posts show green "PUBLISHED" badge                | Status color correct  |
| 4    | Verify queued posts show default badge                             | Status color correct  |
| 5    | Click on a post row                                                | Detail modal opens    |

**Tags:** `@list-view @posts`

---

### TC-21: Theme Persistence (Dark/Light)

| Step | Action                                                                    | Expected              |
|------|---------------------------------------------------------------------------|-----------------------|
| 1    | Open Settings                                                             | Settings page renders |
| 2    | Toggle theme to "Light"                                                   | Light theme applied   |
| 3    | Verify `localStorage.getItem('pt_settings_v1')` contains `theme: "light"` | Persisted             |
| 4    | Refresh the page                                                          | Light theme persists  |
| 5    | Toggle theme back to "Dark"                                               | Dark theme applied    |
| 6    | Refresh the page                                                          | Dark theme persists   |

**Tags:** `@settings @theme @persistence`

---

### TC-22: Locale Persistence (EN/ES)

| Step | Action                                                                  | Expected                   |
|------|-------------------------------------------------------------------------|----------------------------|
| 1    | Open Settings                                                           | Settings page renders      |
| 2    | Switch language to Spanish                                              | UI text changes to Spanish |
| 3    | Verify `localStorage.getItem('pt_settings_v1')` contains `locale: "es"` | Persisted                  |
| 4    | Refresh the page                                                        | Spanish locale persists    |
| 5    | Verify sidebar labels are in Spanish                                    | Labels correct             |
| 6    | Switch back to English                                                  | UI text changes to English |
| 7    | Refresh the page                                                        | English locale persists    |

**Tags:** `@settings @locale @persistence`

---

### TC-23: Drag-and-Drop Reschedule

| Step | Action                                   | Expected                  |
|------|------------------------------------------|---------------------------|
| 1    | In week view, find a post card           | Post card visible         |
| 2    | Drag the post card to a future slot      | Slot highlights on hover  |
| 3    | Drop the post in the new slot            | Post moves to new slot    |
| 4    | Verify post appears in the new time slot | Rescheduled               |
| 5    | Try dragging to a past slot              | Slot does not accept drop |

**Tags:** `@scheduler @drag-drop @reschedule`

---

### TC-24: Calendar + Button — Create in Specific Slot

| Step | Action                                                    | Expected                |
|------|-----------------------------------------------------------|-------------------------|
| 1    | In week view, hover over a future slot (e.g., 2 PM today) | + button appears        |
| 2    | Click the + button                                        | Create Post modal opens |
| 3    | Verify pre-filled date/time matches the slot              | Correct slot            |
| 4    | Fill text: "Slot-specific post"                           | Text entered            |
| 5    | Select channel and click "Schedule Post"                  | Post created            |
| 6    | Verify post appears in the exact slot clicked             | Slot-specific post      |

**Tags:** `@scheduler @create @slot`

---

### TC-25: WireMock Integration — Publish to Mock

| Step | Action                                                | Expected                    |
|------|-------------------------------------------------------|-----------------------------|
| 1    | Set env: `SMP_PUBLISHING_WORKER_ENABLED=true`         | Worker active               |
| 2    | Set env: `SMP_PUBLISHING_WORKER_POLL_INTERVAL=PT5S`   | Fast polling                |
| 3    | Create a post with "NOW" mode                         | Post created                |
| 4    | Wait up to 10 seconds                                 | Worker processes job        |
| 5    | Verify post status changes to PUBLISHED               | Published                   |
| 6    | Verify `externalPublicationId` is a local WireMock ID | Mock ID returned            |
| 7    | Click on the post, click "View Post"                  | LinkedIn URL opens (mocked) |
| 8    | Query `curl http://localhost:8089/__admin/requests`   | Request recorded            |

**Tags:** `@backend @wiremock @integration`

---

### TC-26: WireMock — Rate Limit Handling

| Step | Action                                                        | Expected                |
|------|---------------------------------------------------------------|-------------------------|
| 1    | Create a post with text containing `LINKEDIN_RATE_LIMIT_TEST` | Post created            |
| 2    | Wait for worker to process                                    | Worker attempts publish |
| 3    | Verify post status remains QUEUED/SCHEDULED (retryable)       | Not FAILED              |
| 4    | Wait 30 seconds for retry                                     | Retry attempted         |

**Tags:** `@backend @wiremock @retry`

---

### TC-27: WireMock — Validation Error Handling

| Step | Action                                                        | Expected                |
|------|---------------------------------------------------------------|-------------------------|
| 1    | Create a post with text containing `LINKEDIN_VALIDATION_TEST` | Post created            |
| 2    | Wait for worker to process                                    | Worker attempts publish |
| 3    | Verify post status changes to FAILED                          | Terminal failure        |
| 4    | Verify error details are visible in the post card             | Error shown             |

**Tags:** `@backend @wiremock @error`

---

### TC-28: Responsive — Mobile Viewport

| Step | Action                                      | Expected                    |
|------|---------------------------------------------|-----------------------------|
| 1    | Set viewport to 375×812 (iPhone)            | Mobile viewport             |
| 2    | Navigate to Scheduler                       | Scheduler renders           |
| 3    | Verify week view is horizontally scrollable | Scrollable grid             |
| 4    | Verify time labels are still visible        | Labels not truncated        |
| 5    | Open Create Post modal                      | Modal renders on mobile     |
| 6    | Verify all controls are accessible          | No overflow/hidden elements |

**Tags:** `@responsive @mobile`

---

## 4. Test Data Requirements

| Item             | Value                              | Notes                    |
|------------------|------------------------------------|--------------------------|
| User             | `dev@profiletailors.com`           | Pre-seeded via Liquibase |
| Password         | `TEST_PASSWORD_S3cr3tP@ssw0rd*123` | Dev seed user            |
| Workspace        | `dev-workspace-001`                | Pre-seeded               |
| LinkedIn account | Connected via mock                 | Pre-seeded in dev DB     |
| Test images      | `test/fixtures/sample.jpg`         | < 10MB JPEG              |

---

## 5. Test Isolation

- **Each test** should clean up created posts after execution.
- Use a shared `beforeAll` hook to login and save auth state.
- Use `test.describe.serial` for tests that depend on order (e.g., TC-25 depends on TC-05).
- Use `storageState` to persist login across tests.

---

## 6. Reporting

- Screenshots on failure: `toHaveScreenshot()` or manual `page.screenshot()`
- Video recording: `--video on` for CI runs
- Trace: `--trace on` for debugging failures

---

## 7. Priority Matrix

| Priority          | Tests                                                  | Rationale                                          |
|-------------------|--------------------------------------------------------|----------------------------------------------------|
| **P0 (Critical)** | TC-01, TC-02, TC-05, TC-06, TC-07, TC-11, TC-16, TC-25 | Core flows: auth, navigate, create, read, publish  |
| **P1 (High)**     | TC-03, TC-04, TC-08, TC-13, TC-14, TC-17, TC-18, TC-23 | Important flows: views, validation, delete, filter |
| **P2 (Medium)**   | TC-09, TC-10, TC-12, TC-15, TC-19, TC-20, TC-24        | Secondary flows: media, priority, detail, day view |
| **P3 (Low)**      | TC-21, TC-22, TC-26, TC-27, TC-28                      | Settings persistence, error handling, responsive   |

---

## 8. CI Integration

```yaml

# Example GitHub Actions step
- name: Run E2E Tests
  run: |
    docker compose up -d linkedin-wiremock postgresql
    ./gradlew :server:smp:bootRun &
    pnpm --filter app dev &
    npx playwright test tests/e2e/scheduler/
  env:
    SMP_PUBLISHING_WORKER_ENABLED: "true"
    SMP_PUBLISHING_WORKER_POLL_INTERVAL: "PT5S"
    SMP_LINKEDIN_PUBLISHING_API_BASE_URL: "http://localhost:8089"
```
