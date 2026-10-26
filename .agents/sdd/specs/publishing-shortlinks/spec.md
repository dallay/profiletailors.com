# Publishing Shortlinks Specification

## Purpose

Define automatic shortlink integration for creating social publications with links while preserving destination, post content, and the user-visible result of the transformation.

## Requirements

### Requirement: Internal UUID Identifiers in Shortlinks

Shortlink flows MUST accept and transmit internal owner identifiers as canonical unprefixed UUIDs.
Invalid identifiers MUST produce a clear validation error rather than an opaque parsing exception.
The design MUST correct the root cause of prefixed workspace `ws-...` values reaching shortlinks;
an isolated parser hotfix does not satisfy this requirement.

#### Scenario: Canonical UUID owner

- GIVEN an authenticated request with a valid internal UUID owner
- WHEN a shortlink is created or queried
- THEN the flow MUST preserve the same canonical UUID across internal boundaries

#### Scenario: Prefixed or invalid owner

- GIVEN an internal owner identifier with a prefix or invalid UUID syntax
- WHEN the shortlink flow validates the identifier
- THEN it MUST reject the request with an identifiable format error
- AND it MUST NOT create or query a shortlink

#### Scenario: Root cause corrected

- GIVEN a workspace reaches shortlinks through the authentication context
- WHEN the integrated flow executes
- THEN the workspace MUST arrive as a canonical internal UUID
- AND an integration test MUST cover the complete path, not only the parser

### Requirement: Shortlinks Compatibility During Transition

Transitional reads and writes MUST follow the conditional coordinated stopped-writer cutover in
the [publishing identifier migration contract](../publishing/spec.md#requirement-lossless-identifier-migration-and-conditional-recovery).
Legacy consumers use legacy formats before conversion; coordinated consumers use raw UUIDs within
v1 afterward. Legacy writers MUST NOT write to the converted schema. No rolling compatibility,
maintenance window or production approval is assumed. If the coordinated conditions cannot be met,
release MUST remain blocked pending a separate plan, not an invented fallback implementation.

#### Scenario: Selected rollout

- GIVEN shortlink clients or data use different representations before transition
- WHEN the rollout plan is prepared
- THEN the design MUST specify accepted formats at each stage and compatibility verification
- AND it MUST define recovery without data loss or ambiguous ownership
- AND recovery MUST account for new UUID-only writes rather than promise automatic reversal

### Requirement: Automatic Shortlink Replacement at Publication Creation

The composer MUST automatically shorten every distinct destination URL found in the post content at the moment the user submits the publication. Deduplication is scoped to that single post and does not imply deduplication across publications. The composer MUST NOT require an opt-in, a manual preview, or a manual decision step (such as `Use shortlink` / `Keep original URL`); shortening is an implementation detail of the submit action. The composer MUST shorten each distinct URL exactly once and MUST replace every occurrence of that URL in the content with the same shortlink. The composer MUST submit the publication with the already-shortened content so the post persisted in the system carries the shortlinks.

#### Scenario: Publication with a single distinct link shortens automatically at submit

- GIVEN a workspace member submits a publication containing a single distinct URL
- WHEN the user clicks `Schedule Now`, `Schedule Post`, or otherwise submits the publication
- THEN the composer MUST request a shortlink for that URL via `POST /api/v1/links`
- AND the composer MUST replace the URL in the post content with the returned shortlink before sending `POST /api/publishing/publications`
- AND the publication persisted in the system MUST contain the shortlink, not the original URL

#### Scenario: Publication with the same link repeated uses the same shortlink for every occurrence

- GIVEN a workspace member submits a publication whose content contains the same URL more than once
- WHEN the user submits the publication
- THEN the composer MUST shorten that URL exactly once
- AND the composer MUST replace every occurrence of that URL in the post content with the same shortlink
- AND duplicate shortlinks for that destination MUST NOT be stored for this post

#### Scenario: Publication with multiple distinct links shortens every distinct link

- GIVEN a workspace member submits a publication whose content contains two or more distinct URLs
- WHEN the user submits the publication
- THEN the composer MUST shorten every distinct URL
- AND the composer MUST replace every occurrence of each URL in the post content with its corresponding shortlink
- AND the publication persisted in the system MUST contain one shortlink per distinct original URL

#### Scenario: Publication without links is submitted unchanged

- GIVEN a workspace member submits a publication whose content contains no URL
- WHEN the user submits the publication
- THEN the composer MUST NOT call the shortlink service
- AND the publication MUST be persisted with the original content, byte-for-byte

#### Scenario: Shortlink service failure for a single URL is best-effort and the publication still submits

- GIVEN a workspace member submits a publication whose content contains one or more URLs
- AND at least one URL cannot be shortened (network error, 5xx, timeout, or non-201 response)
- WHEN the user submits the publication
- THEN for each URL that fails to shorten, the composer MUST leave the original URL in the post content
- AND for each URL that succeeds, the composer MUST replace the original URL with the shortlink
- AND the publication MUST still be submitted and persisted
- AND the composer MUST surface a non-blocking inline warning identifying the URLs that could not be shortened

#### Scenario: Live preview reflects the post content as it will be submitted

- GIVEN a workspace member is composing a post with one or more URLs
- WHEN the composer renders the live preview
- THEN the preview MUST show the current post content (which may include shortlinks only after the user submits)
- AND the composer MUST NOT introduce a separate preview shortlink step before submit

#### Scenario: Shortlink service is not invoked when editing an existing publication

- GIVEN a workspace member opens the composer to edit an existing publication
- WHEN the user edits and submits the publication
- THEN the composer MUST NOT call the shortlink service
- AND the composer MUST NOT alter the existing post content for shortlink purposes
- AND the existing shortlinks already present in the content MUST remain unchanged
