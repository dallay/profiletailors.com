# Publishing Shortlinks Specification

## Purpose

Define observable shortlink integration for creating social publications with links while preserving destination and publication content.

## Requirements

### Requirement: Shortlink Behavior During Publication Creation

The composer MUST make the shortlink behavior explicit when creating a publication containing one or more links. It MUST NOT silently replace a destination or alter the submitted post content. The exact user choice, supported link cardinality, transformation timing, and behavior when shortening is unavailable remain open for design, based on verified Core V1 contracts.

#### Scenario: Publication with a link exposes explicit shortlink behavior

- GIVEN a workspace member creates a publication containing a link
- WHEN the composer presents the publication for creation
- THEN the shortlink behavior and resulting URL MUST be observable before or as part of creation
- AND neither the destination nor post content MUST be silently changed

#### Scenario: Publication without links does not introduce a shortlink

- GIVEN a workspace member creates a publication without a link
- WHEN the publication is created
- THEN no shortlink MUST be added to the publication content

#### Scenario: Shortlink integration cannot produce a usable link

- GIVEN a publication link cannot be shortened or the shortlink service is unavailable
- WHEN the user attempts to create the publication
- THEN the destination MUST NOT be silently replaced by a different URL
- AND the user-visible result and whether publication creation may proceed MUST be defined by design before implementation
