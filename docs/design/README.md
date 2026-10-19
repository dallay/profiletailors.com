# Design Sources

## Overview

Binary Figma sources for visual design. Rendered tokens and rules live in `.agents/DESIGN.md`; these files are the editable sources.

## Usage

- [Composer interaction definition](./composer-interaction.md) — workflow stages, editing and preview contexts, variant rules, and mapping to the current modal. Behavior only, no visual redesign.
- [Composer interaction prototype](./composer-prototype.html) — isolated Spanish Compose → Customize → Review flow with per-account variants, local-only image previews, and simulated confirmation, inside a faithful replica of the `CreatePostModal` shell. Open directly in a browser; no server, publishing, or persistence. Fonts reference existing local repository assets.
- [Shared Vue UI shell](./shared-vue-ui-shell.md) — reusable dashboard/admin navigation frame, package boundaries, slots, and adoption guidance
- `profiletailors.fig` — Profile Tailors brand system (71 KB)
- `shadcn-ui 2025 kit.fig` — component kit reference (8.2 MB)
- Open with Figma or OpenPencil; do not hand-edit binaries.

## Troubleshooting

- If tokens drift, `DESIGN.md` wins for implementation until a new `.fig` export lands.
- Keep marketing dark-first Nothing-inspired language; do not invent a parallel palette.

## References

- [Design System](../../.agents/DESIGN.md)
- [Docs index](../../README.md)
