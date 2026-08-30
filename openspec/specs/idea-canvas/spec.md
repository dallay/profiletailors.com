# Idea Canvas Specification

## Purpose

Kanban workspace for ideas: fixed lanes own overflow, cards surface title/excerpt/tags/links, DnD with rollback, column config, workspace isolation. AC 1–10, 34–37.

## Requirements

### Requirement: Board Workspace Rendering

System MUST render `/ideas` as horizontal lanes not isolated cards. Lanes MUST be 280–320px fixed. Board MUST own horizontal scroll. (AC 1,2)

#### Scenario: Lanes with counts (AC 1,2)
- GIVEN columns Raw/In Progress/Done with ideas
- WHEN user opens `/ideas`
- THEN each renders as lane with name/count and board scrolls horizontally when overflow

#### Scenario: Loading skeleton (AC 1)
- GIVEN board loading
- WHEN view renders
- THEN skeleton lanes occupy board and shell not replaced by full-page spinner

### Requirement: Lane Overflow Ownership

Lane overflow MUST be contained in board/lane scroll, not shell. Header MUST stay visible. Empty lane MUST show "No ideas yet" + "+ Add idea" without dominance. (AC 3)

#### Scenario: Long lane contained (AC 3)
- GIVEN lane has 30 ideas
- WHEN displayed
- THEN lane/board scrolls independently and shell height bounded

#### Scenario: Empty lane (AC 3)
- GIVEN lane has 0 ideas
- WHEN rendered
- THEN shows empty state and add action

### Requirement: Card Hierarchy

Card MUST show title primary; bounded excerpt only if notes exist (clamped 2–3 lines); bounded tags with +N overflow; links count only if present; converted badge secondary. (AC 7–10)

#### Scenario: Full card (AC 7–10)
- GIVEN idea has notes, 5 tags, 2 links
- WHEN rendered
- THEN title primary, excerpt clamped, 3 tags as `#tag +2`, `🔗 2` shown

#### Scenario: Minimal card (AC 7,8,10)
- GIVEN idea has title only
- WHEN rendered
- THEN no excerpt or link count placeholder

### Requirement: Drag-and-Drop Reorder and Cross-Column

System MUST support reorder within column and move across columns via pointer and keyboard, with insertion feedback; cards remain clickable while draggable. (AC 4,5)

#### Scenario: Reorder (AC 4)
- GIVEN Raw has [A,B,C]
- WHEN B dropped between A and C
- THEN board optimistically shows reorder and PATCH /move persists it

#### Scenario: Cross-column (AC 5)
- GIVEN idea in Raw
- WHEN dropped in In Progress at index 1
- THEN appears at target index and persists

### Requirement: Optimistic Move with Rollback

Move MUST optimistically update then PATCH `/api/ideas/{id}/move`. On failure MUST rollback and toast error. (AC 6)

#### Scenario: Rollback (AC 6)
- GIVEN optimistic move to Done
- WHEN PATCH fails
- THEN card returns to source and error shown

### Requirement: Column Management

Columns MUST support add/rename/reorder/delete per existing semantics; minimum one required; orphans remap to fallback (minBy order). (AC 34,35)

#### Scenario: CRUD (AC 34)
- GIVEN rename Raw→Inbox and reorder
- WHEN PUT /columns succeeds
- THEN board reflects new order and orphans remapped

#### Scenario: Last-column guard (AC 35)
- GIVEN 1 column
- WHEN delete attempted
- THEN rejected

### Requirement: Workspace Scoping and Authorization

All reads/writes MUST scope by `X-Workspace-Id` and auth; MUST preserve authorization; MUST NOT leak across workspaces. (AC 36,37)

#### Scenario: Isolation (AC 36,37)
- GIVEN A owns I1
- WHEN B requests I1
- THEN 404 and A unchanged

### Requirement: Tests and Contracts

Presentation MUST have Vitest coverage; critical paths Playwright; existing tests updated not bypassed; no media FK without contract; no competitor branding. (AC 38,40,41,42)

#### Scenario: Coverage (AC 38,40,41)
- GIVEN refactor to IdeaBoard/Lane/Card/useIdeaDragAndDrop
- WHEN suites run
- THEN Vitest covers rendering/rollback and no asset FK exists
