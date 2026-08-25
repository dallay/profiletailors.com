# App Shell Delta

## ADDED Requirements

### Requirement: Centralized Provider Presentation

The SPA MUST use a typed presentation registry for catalogs and channel fallbacks. It MUST label `LinkedIn`; `PERSONAL_PROFILE` remains account-kind metadata. Known providers MUST have label/icon data; unknown providers MUST use a neutral icon, never another provider's branding.

#### Scenario: Unknown connected provider is neutral
- GIVEN a connected channel has an unrecognized provider
- WHEN the sidebar renders its fallback presentation
- THEN it MUST use the neutral icon and safe label
- AND it MUST NOT render LinkedIn branding

## MODIFIED Requirements

### Requirement: SidebarConnectSection (connect channels + message)

The section MUST render the server catalog separately: hide `HIDDEN`, connect `AVAILABLE`, and render `LOCKED` without a CTA using its reason. Static providers, “More”, and coming-soon controls/messages MUST NOT render. The shell MUST load catalog and channels together initially and on workspace change; stale entries MUST be non-actionable.

(Previously: A static four-provider list, More control, and coming-soon messages drove connection presentation.)

#### Scenario: Available and locked catalog entries render
- GIVEN `AVAILABLE` and `LOCKED` catalog entries
- WHEN the section renders
- THEN only the available entry MUST expose a connect action
- AND the locked entry MUST be non-actionable

#### Scenario: Workspace switch reloads both sources
- GIVEN workspace A data is visible, including a `HIDDEN` entry
- WHEN the active workspace changes to B
- THEN it MUST reload B's catalog and channels
- AND no hidden or actionable A entry MUST remain
