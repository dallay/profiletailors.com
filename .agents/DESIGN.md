---
name: Profile Tailors
version: "1.1"
description: Social media management platform marketing site. Nothing-inspired, monochrome, typographically driven. Dark-first with equal-rigor light mode.

colors:
  # Background hierarchy
  primary: "#0a0a0a"
  secondary: "#111111"
  surface: "#1a1a1a"

  # Text hierarchy (dark mode)
  display: "#ffffff"
  body: "#e5e5e5"
  secondary-text: "#a3a3a3"
  muted: "#525252"

  # UI chrome
  border: "#262626"
  border-visible: "#333333"
  accent: "#ffffff"

  # Status
  success: "#4A9E5C"
  warning: "#D4A843"
  error: "#D71921"
  info: "#a3a3a3"

typography:
  display-hero:
    fontFamily: "Doto"
    fontSize: 72px
    fontWeight: 400
    lineHeight: 1.0
    letterSpacing: -0.03em
  display-xl:
    fontFamily: "Space Mono"
    fontSize: 48px
    fontWeight: 400
    lineHeight: 1.05
    letterSpacing: -0.02em
  display-lg:
    fontFamily: "Space Grotesk"
    fontSize: 36px
    fontWeight: 300
    lineHeight: 1.1
    letterSpacing: -0.02em
  heading:
    fontFamily: "Space Grotesk"
    fontSize: 24px
    fontWeight: 500
    lineHeight: 1.2
    letterSpacing: -0.01em
  subheading:
    fontFamily: "Space Grotesk"
    fontSize: 18px
    fontWeight: 400
    lineHeight: 1.3
    letterSpacing: 0
  body:
    fontFamily: "Space Grotesk"
    fontSize: 16px
    fontWeight: 400
    lineHeight: 1.5
    letterSpacing: 0
  body-sm:
    fontFamily: "Space Grotesk"
    fontSize: 14px
    fontWeight: 400
    lineHeight: 1.5
    letterSpacing: 0.01em
  caption:
    fontFamily: "Space Mono"
    fontSize: 12px
    fontWeight: 400
    lineHeight: 1.4
    letterSpacing: 0.04em
  label:
    fontFamily: "Space Mono"
    fontSize: 11px
    fontWeight: 700
    lineHeight: 1.2
    letterSpacing: 0.08em

rounded:
  none: 0px
  sm: 4px
  md: 8px
  lg: 12px
  xl: 16px
  full: 999px

spacing:
  2xs: 2px
  xs: 4px
  sm: 8px
  md: 16px
  lg: 24px
  xl: 32px
  2xl: 48px
  3xl: 64px
  4xl: 96px

components:
  button-primary:
    backgroundColor: "{colors.display}"
    textColor: "{colors.primary}"
    typography: "{typography.label}"
    rounded: "{rounded.full}"
    padding: 12px 24px
  button-secondary:
    backgroundColor: transparent
    textColor: "{colors.body}"
    typography: "{typography.label}"
    rounded: "{rounded.full}"
    padding: 12px 24px
  button-ghost:
    backgroundColor: transparent
    textColor: "{colors.secondary-text}"
    typography: "{typography.label}"
    rounded: "{rounded.none}"
    padding: 12px 16px
  input-default:
    backgroundColor: transparent
    textColor: "{colors.body}"
    typography: "{typography.body}"
    rounded: "{rounded.none}"
    padding: 12px 0
  card-surface:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.xl}"
    padding: 24px
  tag-pill:
    backgroundColor: transparent
    textColor: "{colors.secondary-text}"
    typography: "{typography.caption}"
    rounded: "{rounded.full}"
    padding: 4px 12px
---

# Profile Tailors — Design System

## Overview

Nothing-inspired monochromatic UI for a social media management platform. The visual language
borrows from Swiss typography, Braun/Teenage Engineering industrial design, and high-end editorial
print. Every element earns its pixel — structure is ornament, type does the heavy lifting.

**Core principle:** Subtract, don't add. Color is an event, not a default.

## Philosophy

1. **Three-layer hierarchy per screen:** Primary (the ONE thing), Secondary (context), Tertiary (
   metadata/nav)
2. **Font discipline:** Doto (hero only) + Space Grotesk (body/UI) + Space Mono (labels/data). Max 3
   sizes, 2 weights.
3. **Spacing as meaning:** Tight (4–8px) = grouped, Medium (16px) = same group different items,
   Wide (32–48px) = new section, Vast (64–96px) = hero breathing room
4. **Both modes are first-class:** Dark: OLED black (#000000). Light: warm off-white (#F5F5F5).
   Neither is "derived."

## Color System

The system follows a semantic scale where the step encodes intent:

- `100` Default background (`primary`)
- `200` Secondary background / Hover background (`secondary`)
- `300` Surface background / Active background (`surface`)
- `400` Default border (`border`)
- `500` Hover border
- `600` Active border (`border-visible`)
- `700` Muted text / Disabled / High-contrast fill (`muted`)
- `800` Secondary text / Solid fill hover (`secondary-text`)
- `900` Primary text (`body`)
- `1000` High-contrast Display text (`display`)

### Dark Mode (default)

- **Primary (#0a0a0a):** OLED black — the canvas
- **Secondary (#111111):** Slightly elevated surfaces
- **Surface (#1a1a1a):** Cards, raised elements
- **Display (#ffffff):** Hero headlines, key numbers
- **Body (#e5e5e5):** Body copy, primary content
- **Secondary-text (#a3a3a3):** Labels, captions, metadata
- **Muted (#525252):** Disabled, decorative
- **Border (#262626):** Subtle dividers, wireframe feel
- **Border-visible (#333333):** Intentional separators
- **Accent (#ffffff):** White-on-black UI accent, not decoration

### Light Mode

Same role tokens with adjusted values:

- Primary: `#F5F5F0` (warm off-white)
- Surface: `#E0E0DA`
- Display: `#000000`
- Body: `#1A1A1A`
- Secondary-text: `#525252`
- Border: `#D4D4CE`

**Accent, status colors, and font choices remain identical across modes.**

### Status Colors (identical both modes)

- **Success (#4A9E5C):** Confirmed, completed, connected
- **Warning (#D4A843):** Caution, pending, degraded
- **Error (#D71921):** Urgent, destructive — an interrupt, not decoration
- **Info (#a3a3a3):** Neutral metadata, timestamps

**Data status rule:** Apply color to the VALUE, not the label or row background. Labels stay
`--text-secondary`.

## Typography

### Font Stack

| Role              | Font            | Fallback                    | Weights            |
|-------------------|-----------------|-----------------------------|--------------------|
| **Display**       | `Doto`          | `Space Mono, monospace`     | 400–700            |
| **Body / UI**     | `Space Grotesk` | `DM Sans, system-ui`        | 300, 400, 500, 700 |
| **Labels / Data** | `Space Mono`    | `JetBrains Mono, monospace` | 400, 700           |

### Type Rules

- **Doto:** Hero numbers and time displays only. 48px minimum. Never for body.
- **Labels:** Always Space Mono, ALL CAPS, 0.08em letter-spacing. Instrument panel aesthetic.
- **Data/Numbers:** Always Space Mono. Units as label size, slightly raised.
- **Maximum hierarchy levels:** 4 (display > heading > label > body). If reaching for a fifth, solve
  with spacing instead.

### Spacing Between Elements

- **Same element, same line:** 4–8px (icon + label, number + unit)
- **Related but separate:** 16px (list items, form fields)
- **Section break:** 32–48px
- **Major division:** 64–96px

## Layout

### Grid & Rhythm

- Base unit: 8px
- Section jumps: 32px, 64px, 96px
- Max content width: 1200px
- No card grids for the hero — large type dominates
- Asymmetric layouts > centered symmetry

### Container Strategy (lightest that works)

1. **Spacing alone** (proximity groups items)
2. A single border line
3. A subtle `--border` outline
4. A `--background-surface` background (cards)

### Responsive Breakpoints

- Mobile: < 768px — single column, tighter spacing
- Tablet: 768px–1024px — 2-column where needed
- Desktop: > 1024px — full layout, asymmetric compositions

## Shapes & Radii

| Token  | Value | Use                      |
|--------|-------|--------------------------|
| `none` | 0px   | Technical, data displays |
| `sm`   | 4px   | Compact UI, inputs       |
| `md`   | 8px   | General components       |
| `lg`   | 12px  | Cards, widgets           |
| `xl`   | 16px  | Large cards, modals      |
| `full` | 999px | Buttons, pills, tags     |

**Rules:** No border-radius > 16px on cards. Buttons are pill (999px) or technical (0px). No
shadows, no blur — flat surfaces, border separation.

## Motion

Use motion only when it clarifies a change, never for decoration. Snappiness is preferred.

- **Snappy (0ms):** Best for most state changes (hover, active).
- **Fast (150ms):** For micro-interactions and state changes.
- **Standard (200ms):** For popovers, tooltips, and small element entries.
- **Overlay (300ms):** For modals, dialogs, and large transitions.
- **Easing:** `cubic-bezier(0.175, 0.885, 0.32, 1.1)` for physical entry, or
  `cubic-bezier(0.25, 0.1, 0.25, 1)` for subtle ease-out.
- **Preferred:** Opacity over position (fade, don't slide).
- **Reduced motion:** Respect `prefers-reduced-motion`. Disable all animations when set.

## Iconography

- Monoline, 1.5px stroke, no fill
- 24x24 base, 20x20 live area
- Round caps/joins
- Color inherits text color
- Max 5–6 strokes per icon
- **Preferred:** Lucide (thin), Phosphor (thin)

## Components

### Buttons

| Variant   | Background       | Border                       | Text                   | Radius |
|-----------|------------------|------------------------------|------------------------|--------|
| Primary   | `--text-display` | none                         | `--background-primary` | `full` |
| Secondary | transparent      | `1px solid --border-visible` | `--text-body`          | `full` |
| Ghost     | transparent      | none                         | `--text-secondary`     | `none` |

**States:**

- **Hover:** Background/border steps up the scale (e.g., `100` -> `200`, `400` -> `500`).
- **Active:** Background/border steps up again (e.g., `200` -> `300`, `500` -> `600`).
- **Disabled:** `gray-100` fill (light) or `gray-200` (dark), `gray-700` text, `not-allowed` cursor.
- **Focus:** Two-layer ring: 2px gap in surface color, then 2px `accent` ring (
  `box-shadow: 0 0 0 2px var(--background-primary), 0 0 0 4px var(--accent-color)`).

All buttons: Space Mono, ALL CAPS, letter-spacing 0.06em, padding 12px 24px, min-height 44px.

### Cards

- Background: `--background-surface`
- Border: `1px solid --border` or none
- Radius: 12–16px
- Padding: 16–24px
- No shadows. Hierachy through tonal surfaces.

### Inputs

- Underline style: `1px solid --border-visible` bottom
- Label above: Space Mono, ALL CAPS, `--text-secondary`
- Focus: border → `--text-body`
- Error: border → `--error`, message below in `--error`

### Navigation

- Desktop: horizontal text bar, Space Mono ALL CAPS
- Active: `--text-display` + underline or dot
- Inactive: `--text-muted`
- Format: `[ HOME ] GALLERY INFO` or `HOME | GALLERY | INFO`

### Tags / Chips

- Border: `1px solid --border-visible`, no fill
- Text: Space Mono, `--caption`, ALL CAPS
- Radius: `full` (pill) or `sm` (technical)
- Padding: 4px 12px

### Overlays

- No shadows. Layering through background contrast and subtle translucent borders.
- Modal: backdrop `rgba(0,0,0,0.8)`, dialog `--background-surface` + border + 16px radius.
- Toast: Inline status text at bottom or corner: `[Saved]`, `[Error: ...]`. Drop trailing periods.

## Voice & Content

Keep copy precise and free of filler. Copy IS design.

- **Capitalization:** Title Case for labels, buttons, titles, and tabs. Sentence case for body,
  helper text, and toasts.
- **Action Naming:** Use [Verb] + [Noun] (e.g., `Deploy Project`, `Delete Member`). Never just
  `Confirm` or `OK`.
- **Error Messages:** State what happened + what to do next.
  `Build failed. Bundle exceeds 50 MB. Reduce it or raise the limit.`
- **Toasts:** Name the specific change, drop trailing period, avoid "successfully".
  `Project deleted`, not `Successfully deleted project.`
- **In-progress:** Use present participle with ellipsis. `Deploying...`, `Saving...`.
- **Numbers:** Use numerals (`3 projects`).
- **Style:** Use curly quotes and the ellipsis character (\u2026); skip "please" and marketing
  superlatives.

## Do's and Don'ts

### Do

- Use the semantic scale to rank information: `1000` for display text, `900` for primary/body text,
  `800` for secondary text, `700` for muted/disabled text.
- Maintain WCAG AA contrast (4.5:1).
- Show the focus ring on every interactive element at `:focus-visible`.
- Apply typography tokens strictly; don't set manual font sizes or weights.
- Pair color status with an icon or text label; don't rely on color alone.

### Don't

- Use gradients, shadows, or blurs.
- Mix rounded and sharp corners in the same view.
- Use more than two font weights in one view.
- Use `background-200` for general fills; it's for subtle separation or hovers only.
- Swap `gray-*` for `background-*`; they are separate scales.

## Bilingual Content Model

All user-facing strings live in a single locale object — never hardcoded inline:

```ts
const content = {
  en: {nav, hero, platforms, features, audiences, finalCta, footer},
  es: {nav, hero, platforms, features, audiences, finalCta, footer},
}
```

- Default locale: English
- Language switch updates copy AND `lang` attribute
- Spanish copy is 20–30% longer — never use fixed-width containers
- EN/ES switcher lives in the header

## Dot-Matrix Motif

Use for:

- Hero typography (Doto)
- Decorative grid backgrounds
- Dot-grid data visualization
- Loading indicators
- Empty state illustrations

```css
.dot-grid {
  background-image: radial-gradient(circle, var(--border-visible) 1px, transparent 1px);
  background-size: 16px 16px;
}
```

Dots: 1–2px, uniform 12–16px grid. Opacity 0.1–0.2 for backgrounds, full for data. Never as
container border or button style.

## Accessibility Baseline

- Readable contrast for all text and interactive states
- Visible focus state on every interactive element
- Minimum 44x44px touch target for controls
- Reduced-motion support
- Semantic labels and accessible names for icon-only buttons
- **Do not communicate status with color alone** — pair with text, value, icon, pattern, or label

## Tech Stack

- **Framework:** Astro 6, static-first, no SSR
- **Package manager:** pnpm
- **CSS:** Tailwind CSS v4 with `@theme` custom properties
- **Fonts:** Google Fonts (Doto, Space Grotesk, Space Mono)
- **Dev server:** `localhost:4321`
- **Build output:** `./dist/`

## File Structure

```text
apps/web/marketing/
├── src/
│   ├── styles/
│   │   └── global.css      # CSS custom properties (tokens live here)
│   ├── components/         # Astro components
│   ├── layouts/            # Layout wrappers
│   ├── pages/              # Routes
│   └── i18n/               # Locale objects (en/es)
├── public/                 # Static assets
├── astro.config.mjs
└── package.json
```

## Quick Reference

| Token                  | Dark      | Light     |
|------------------------|-----------|-----------|
| `--background-primary` | `#0a0a0a` | `#F5F5F0` |
| `--background-surface` | `#1a1a1a` | `#E0E0DA` |
| `--text-display`       | `#ffffff` | `#000000` |
| `--text-body`          | `#e5e5e5` | `#1a1a1a` |
| `--text-secondary`     | `#a3a3a3` | `#525252` |
| `--border`             | `#262626` | `#D4D4CE` |
| `--border-visible`     | `#333333` | `#CCCCCC` |

| Font          | Use                       |
|---------------|---------------------------|
| Doto          | Hero numbers only (48px+) |
| Space Grotesk | Body, headings, UI        |
| Space Mono    | Labels, data, ALL CAPS    |
