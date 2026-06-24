# Spec: Edit Publication via Composer

## Scenario 1: User edits a scheduled publication

**Given** a user is viewing a scheduled publication in `PostDetailModal`
**And** the publication status is `DRAFT`, `PENDING`, or `SCHEDULED`
**When** the user clicks the "Edit" button
**Then** `PostDetailModal` closes
**And** `CreatePostModal` opens in edit mode
**And** all form fields are pre-filled with the publication data:
  - Content text
  - Schedule date and time
  - Schedule mode (NOW, SCHEDULED_AT, NEXT_SLOT)
  - Priority
  - Media assets
  - Channel (read-only)

## Scenario 2: User edits a published post

**Given** a user is viewing a published post in `PostDetailModal`
**And** the publication status is `PUBLISHED`
**When** the user views the modal
**Then** the "Edit" button is NOT rendered
**And** the post is displayed in read-only mode

## Scenario 3: User saves edit changes

**Given** `CreatePostModal` is open in edit mode
**And** the user has modified the content, schedule, priority, or media
**When** the user clicks "Save Changes"
**Then** `publishingStore.updatePost()` is called with the updated data
**And** the `updated` event is emitted
**And** `CreatePostModal` closes
**And** the calendar refreshes to reflect the changes

## Scenario 4: Channel is locked in edit mode

**Given** `CreatePostModal` is open in edit mode
**When** the user views the channel selector
**Then** the current channel is pre-selected and disabled
**And** the user cannot change the channel

## Scenario 5: Create Another toggle hidden in edit mode

**Given** `CreatePostModal` is open in edit mode
**When** the user views the form
**Then** the "Create Another" toggle is NOT rendered
