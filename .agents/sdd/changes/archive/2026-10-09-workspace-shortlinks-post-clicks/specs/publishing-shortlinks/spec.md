# Publishing Shortlinks Specification

## Purpose

This archived delta is superseded by the authoritative [publishing-shortlinks specification](../../../../../specs/publishing-shortlinks/spec.md), which owns shortlink behavior.

## Requirements

### Requirement: Automatic Shortlink Replacement at Publication Creation

For shortlink replacement behavior, see the authoritative [publishing-shortlinks specification](../../../../../specs/publishing-shortlinks/spec.md). Deduplication applies only within one post.

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
