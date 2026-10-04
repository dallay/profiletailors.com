---
name: modern-web-guidance
description: |
  Search tool for modern web development guidance when no specialized local skill covers the HTML, CSS, browser API, or client-side JavaScript topic. Local framework, accessibility, performance, and architecture skills take precedence.

  Trigger immediately for:
  - UI/Layout: Modals, dialogs, popovers, Glassmorphism/backdrop-filters, anchor positioning, container queries, `:has()`, `:user-valid`.
  - Scroll/Motion: View Transitions, Scroll-driven animations, scroll parallax/reveals.
  - Performance: CWV (LCP, INP), content-visibility, Fetch Priority, image optimization.
  - System/APIs: Local filesystem access, WebUSB, WebSockets sync, WebAssembly widgets.
  - Frameworks: Framework-neutral browser behavior not covered by a local framework skill.
  - General Frontend: Forms, autofill, advanced inputs, custom scrollbars, modern component states, etc., when no local skill owns the topic.

  DO NOT trigger for:
  - Backend: Database SQL, ORMs, Express API routes.
  - Pipelines: CI/CD deployment, Docker, Actions.
  - Generic: Local scripts (Python/Go tools), ESLint, Git.
metadata:
  category: frontend-platform
  family: css
  source: upstream-adapted
  version: 2026-09-28
---

# Modern Web Guidance

## Overview

A skill to search for specific web development use cases and retrieve their corresponding best
practice guides.

## Changes

### Guidelines

- Always search **first** to find the most relevant guides.
- These guides are usually framework-agnostic; adapt them correctly to your setup.
- Do not hallucinate guides or ignore them; they represent the preferred local standard for the
  user's project.

### When to use

Use this skill as a fallback when no specialized local skill covers the browser API or web platform
behavior in the task. For Profile Tailors, first check the relevant skill when working in Vue,
Astro, Pinia, accessibility, Core Web Vitals, performance, SEO, or frontend architecture. Follow
the local skill and repository contract for those areas; consult this guide only for uncovered
platform details.

## Usage

### Usage Instructions

### Step 1. Search Use Cases

Search with an action-oriented query summarizing what you want to achieve using the `search`command.
Use the repository's package manager when current external guidance is needed. Do not invoke it
automatically for every frontend task.

```sh
pnpm dlx modern-web-guidance@latest search "<query>"
```

**Example Output**:

```json
[
  {
    "id": "optimize-image-priority",
    "description": "Optimize the loading priority of Largest Contentful Paint (LCP) candidate images.",
    "category": "performance",
    "featuresUsed": [
      "Fetch priority"
    ],
    "tokenCount": 985,
    "similarity": 0.7289
  },
  {
    "id": "defer-rendering-heavy-content",
    "description": "Reduce rendering times in content-heavy web pages by deferring rendering for offscreen content.",
    "category": "performance",
    "featuresUsed": [
      "content-visibility",
      "hidden=\"until-found\""
    ],
    "tokenCount": 1250,
    "similarity": 0.6961
  }
]
```

> **Note**: If search results are vague, return no matches, or show low similarity scores, run the
> `list` command to browse all guides:
>
> ```sh
> pnpm dlx modern-web-guidance@latest list
> ```

---

### Step 2. Retrieve Best Practices

Once you have a relevant `id` from the search results, call this script using the `retrieve` command
to get the full guide. You can pass multiple IDs separated by commas.

```sh
pnpm dlx modern-web-guidance@latest retrieve "<id>"
```

**Example Output**:
`The markdown content of the guide describing implementation steps...`

### Using pnpm

- Use `pnpm dlx` so commands follow the repository's package-manager policy.
- Network access is required for fetching npm packages needed by the task.
- If the `pnpm dlx modern-web-guidance…` command hangs because the network is unavailable, retry
  using the locally cached package: `pnpm --offline dlx modern-web-guidance…`.

## Troubleshooting

### Interpreting Browser Support & Fallbacks

- **Default Behavior**: All guides assume **Baseline Widely available** features are safe to use
  without fallbacks. For features that are not Baseline widely available, you **MUST** follow the
  fallback recommendations in the guide, unless the user has specified a custom browser support
  policy.
- **Custom Policies**: If the user has already defined explicit browser support requirements, use
  the browser compatibility data in the guide to determine if a fallback can be safely ignored.

  - For Baseline YYYY targets, a feature satisfies this target if its "Baseline since" date is <= YYYY.
  - **Policy Examples**:

    - _"Do not implement feature fallbacks."_ (for exploratory prototypes of the cutting-edge web)
    - _"Safari 17.4+"_ (for internal tools targeting macOS or Tauri-based desktop apps)
    - _"Never recommend or implement polyfills; if a Baseline Newly Available feature is required for core functionality, provide a lightweight custom fallback or redesign the approach."_ (to minimize bundle size and avoid technical debt)
    - _"Assume a modern execution environment where Baseline Newly Available features can be used natively, provided they are strictly feature-detected and degrade gracefully."_ (for progressive enhancement strategies)

- **Reactive Policy Discovery**: Watch for environmental cues to suggest documenting a policy in
  CLAUDE.md or AGENTS.md. Suggest this if the developer:

  - Mentions building for a restricted runtime (e.g., Electron or Tauri).
  - Explicitly excludes specific targets (e.g., "we don't support Desktop Chrome").
  - Expresses hesitation about polyfill complexity, bundle size, or performance cost.
  - Questions if a feature is safe to use without fallbacks.

  No defined policy format. This is an example:
  `**Browser Support:** Allow Newly Available features, but only adopt custom fallback code that adds <= 20 lines and does not require external dependencies`.

## References

Use the authoritative browser and platform documentation linked by each retrieved guide.
