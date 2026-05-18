# Profile Tailors

<div align="center">

![Profile Tailors Logo](shared/assets/profiletailors-logotype-light.svg)

**Schedule smarter. Post everywhere.**

Public-facing marketing site for the Profile Tailors social media management platform.

[![License](https://img.shields.io/github/license/dallay/profiletailors.com?style=for-the-badge&color=2d3748)](LICENSE)
[![CI](https://img.shields.io/github/actions/workflow/status/dallay/profiletailors.com/release-please.yml?style=for-the-badge&color=2d3748&label=CI)](https://github.com/dallay/profiletailors.com/actions)
[![Astro](https://img.shields.io/badge/Astro-6.3.3-2d3748?style=for-the-badge&logo=astro&logoColor=ffffff)](https://astro.build)
[![Node.js](https://img.shields.io/badge/Node.js-22.12%2B-2d3748?style=for-the-badge&logo=node.js&logoColor=5fa04e)](https://nodejs.org)
[![pnpm](https://img.shields.io/badge/pnpm-10.x-2d3748?style=for-the-badge&logo=pnpm&logoColor=f69220)](https://pnpm.io)

</div>

---

## Overview

**Profile Tailors** is a social media management product for planning, scheduling, and publishing content across multiple platforms.

This repository currently focuses on the **marketing site** and **early-access waitlist** experience. The active app lives in `apps/web/marketing/` and is built as a lightweight, static-first Astro site.

### What ships in this repo today

- **English landing page** at `/`
- **Spanish landing page** at `/es/`
- **Client-side waitlist flow** for early access
- **Dark-first, typography-led design system**
- **Shared brand assets** served from `shared/assets/`

> The product name is **Profile Tailors**. `profiletailors.com` is the repository/domain name.

---

## Tech Stack

| Category | Technology |
| --- | --- |
| Frontend | Astro 6, Tailwind CSS 4, TypeScript |
| Rendering model | Static-first, no SSR |
| i18n | Astro i18n routing (`en`, `es`) |
| Icons | `@dallay/astro-icon`, Iconify |
| Package manager | pnpm |
| Workspace tooling | Bazel, Lefthook |
| CI/CD | GitHub Actions, Release Please |

---

## Repository Layout

```text
profiletailors.com/
├── apps/
│   └── web/
│       └── marketing/            # Active Astro marketing site
│           ├── public/           # Static files served as-is
│           ├── src/
│           │   ├── components/   # UI building blocks
│           │   ├── i18n/         # EN/ES locale content
│           │   ├── layouts/      # Shared page shells
│           │   ├── pages/        # Routes (`/` and `/es/`)
│           │   ├── scripts/      # Client-side behavior
│           │   └── styles/       # Global styles and tokens
│           ├── astro.config.mjs
│           └── package.json
├── shared/
│   └── assets/                   # Shared logos, icons, and web assets
├── .agents/                      # Agent tooling config and skills
├── .github/workflows/            # CI and automation
├── docs/security/                # Security-related documentation space
├── tmp/                          # Research notes and temporary planning docs
├── CONTRIBUTING.md
├── CLA.md
├── LICENSE
└── README.md
```

---

## Getting Started

### Prerequisites

| Requirement | Version |
| --- | --- |
| Node.js | `>= 22.12.0` |
| pnpm | `>= 10` |

### Install and run locally

```bash
git clone https://github.com/dallay/profiletailors.com.git
cd profiletailors.com/apps/web/marketing
pnpm install
pnpm dev
```

The site will be available at [http://localhost:4321](http://localhost:4321).

### Common commands

Run these from `apps/web/marketing/`:

| Command | What it does |
| --- | --- |
| `pnpm install` | Install dependencies |
| `pnpm dev` | Start the Astro dev server |
| `pnpm build` | Build the production site into `dist/` |
| `pnpm preview` | Preview the production build locally |
| `pnpm check` | Run Astro type/content checks |
| `pnpm lint` | Lint the app source |

---

## Development Notes

- The active app is `apps/web/marketing/`.
- The site uses Astro's built-in locale routing with **English as the default locale** and **Spanish under `/es/`**.
- User-facing copy is maintained in locale files under `apps/web/marketing/src/i18n/`.
- Shared web assets are sourced from `shared/assets/` and exposed by the app config during development and build.
- The current waitlist flow is **client-side only**.

---

## Contributing

Contributions are welcome. Before opening a pull request:

1. Read [`CONTRIBUTING.md`](CONTRIBUTING.md).
2. Open an issue or discussion first for non-trivial changes.
3. Sign the [`CLA.md`](CLA.md) when prompted on your first PR.
4. Verify your changes locally from `apps/web/marketing/`.

We use [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/):

```text
feat(scope): short description
fix(scope): short description
docs(scope): short description
chore(scope): short description
```

Examples:

```text
feat(marketing): refine hero waitlist flow
fix(i18n): correct spanish locale switch label
docs(readme): refresh repository onboarding
```

---

## Security

If you discover a security issue, **do not open a public issue**.

Contact: **security@profiletailors.com**

---

## Support

- **Discussions:** https://github.com/dallay/profiletailors.com/discussions
- **Issues:** https://github.com/dallay/profiletailors.com/issues
- **Email:** **dev@profiletailors.com**

---

## License

This project is licensed under the **GNU Affero General Public License v3.0 (AGPL-3.0)**.

See [`LICENSE`](LICENSE) for the full text.

---

<div align="center">

Built by the **Profile Tailors** team.

</div>