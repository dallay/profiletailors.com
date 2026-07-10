# Dashboard Layout Specification

## Purpose

Defines the responsive grid system that composes all 11 dashboard sections into a cohesive command
center. Replaces the current monolithic HomeView with a section-per-component architecture.

## Requirements

### Requirement: Section Composition

The system SHALL render dashboard sections as independent components composed by
`DashboardLayout.vue`. Each section component receives data via props and emits events — no section
references another section's state.

#### Scenario: Desktop layout renders all sections

- GIVEN the user is on a viewport ≥ 1024px
- WHEN the dashboard loads
- THEN sections render in a 12-column CSS grid
- AND Executive Overview spans 12 columns (full width)
- AND AI Insights spans 12 columns (full width)
- AND Growth Score spans 4 columns
- AND Content Performance spans 4 columns
- AND Cross-Channel Analytics spans 4 columns

#### Scenario: Tablet layout stacks sections

- GIVEN the user is on a viewport between 768px and 1023px
- WHEN the dashboard loads
- THEN sections render in a 2-column grid
- AND Executive Overview and AI Insights span 2 columns each
- AND remaining sections span 1 column each

#### Scenario: Mobile layout single column

- GIVEN the user is on a viewport < 768px
- WHEN the dashboard loads
- THEN all sections render in a single column
- AND each section takes full width

### Requirement: Section Header Pattern

Every dashboard section SHALL display a consistent header with title, optional subtitle, and
optional action link.

#### Scenario: Section renders header

- GIVEN a section has a title key `dashboard.{section}.title`
- WHEN the section mounts
- THEN a header renders with the translated title
- AND the header uses `font-mono text-xs font-bold tracking-widest uppercase` styling
- AND a separator line renders below the header

### Requirement: Section Loading States

Each section component SHALL support independent loading, empty, and error states without affecting
other sections.

#### Scenario: Section shows skeleton while loading

- GIVEN the section data is being fetched
- WHEN the section renders
- THEN a skeleton placeholder displays matching the section's content shape
- AND the skeleton uses `animate-pulse` with `bg-bg-surface` color

#### Scenario: Section shows empty state

- GIVEN the section has no data
- WHEN the section renders
- THEN an empty state message displays using the section's i18n empty key
- AND the empty state includes a relevant illustration or icon

### Requirement: Welcome Header

The dashboard SHALL display a personalized welcome header above all sections.

#### Scenario: Welcome header shows user name

- GIVEN the user is authenticated
- WHEN the dashboard loads
- THEN "Welcome back, {name}" renders using `auth.displayName`
- AND a subtitle renders with the dashboard subtitle translation

### Requirement: Responsive Breakpoints

The layout SHALL use these exact breakpoints matching the existing design system:

- Mobile: < 768px (1 column)
- Tablet: ≥ 768px (2 columns)
- Desktop: ≥ 1024px (12-column grid)

#### Scenario: Resize triggers layout change

- GIVEN the user resizes from desktop to mobile
- WHEN the viewport crosses 768px
- THEN sections reflow from multi-column to single-column
- AND no horizontal scroll is introduced
