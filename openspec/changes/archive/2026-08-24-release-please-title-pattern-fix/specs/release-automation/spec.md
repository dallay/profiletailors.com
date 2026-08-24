# Release Automation Specification

## Purpose

Keep the grouped Release Please workflow compatible with Release Please 17.6.0 title parsing without changing its stable review title or package release behavior.

## Requirements

### Requirement: Parser-compatible release PR title pattern

The release automation configuration MUST define the root per-package `pull-request-title-pattern` as `chore${scope}: release${component} ${version}`. The pattern MUST include the `${scope}`, `${component}`, and `${version}` placeholders required to parse historical and future package release PR titles.

#### Scenario: Release Please can parse a package release title

- GIVEN Release Please 17.6.0 loads the repository configuration
- WHEN it builds the matcher for a package release PR title
- THEN the matcher MUST include optional scope, package component, and semantic version tokens
- AND the configuration MUST remain valid JSON

#### Scenario: A grouped release PR remains recognizable

- GIVEN an existing grouped release PR is titled `chore(release): prepare releases`
- WHEN Release Please processes the merged release PR
- THEN the per-package parser configuration MUST provide the placeholders needed for release processing
- AND the grouped title pattern MUST remain `chore(release): prepare releases`

### Requirement: Preserve grouped release and package behavior

The configuration change MUST preserve one grouped release PR, manifest package/version behavior, package components, tags, workflow triggers, permissions, and the pinned Release Please action. It MUST NOT change package versions or release tags.

#### Scenario: Grouped release output remains stable

- GIVEN one or more configured packages require a release
- WHEN the release automation creates the release PR
- THEN it MUST continue to create one grouped release PR
- AND its title MUST be `chore(release): prepare releases`

#### Scenario: Unrelated release configuration is unchanged

- GIVEN the title-pattern compatibility change is applied
- WHEN the focused configuration diff is inspected
- THEN only the root per-package title pattern MUST differ
- AND package definitions, manifest baselines, workflow settings, permissions, and action pin MUST be unchanged
