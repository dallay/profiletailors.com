# Portless — Local Development URLs

**Date:** 2026-08-22
**Status:** Active

## Overview

[Portless](https://portless.sh) provides stable, named `.localhost` HTTPS URLs for local
development servers — eliminating port conflicts and making URLs predictable for CORS, OAuth
redirects, API clients, and tooling.

Instead of remembering fixed Vite and Astro ports, you get consistent HTTPS URLs:

| Project           | URL                                |
|-------------------|------------------------------------|
| Marketing (Astro) | `https://profiletailors.localhost` |
| App (Vue 3)       | `https://pt-app.localhost`         |
| Admin (Vue 3)     | `https://pt-admin.localhost`       |

Portless auto-discovers packages through `pnpm-workspace.yaml` and respects per-package
`"portless"` config in each `package.json`. Name resolution uses the closest config, with this
precedence: CLI flags > `package.json` > `portless.json` > defaults.

## Changes

### 2026-08-22 — Portless installed and configured

- Added Portless `0.15.5` as a workspace development dependency so every worktree uses the same
  version through the lockfile.
- Added explicit app name resolution for the marketing, dashboard, and admin workspaces.
- Portless detects linked Git worktrees and prefixes the branch name to each hostname.
- The worktree-aware launchers use the same URLs and avoid global process termination.

## Usage

### First-time setup

Start the portless proxy (requires sudo for port 443):

```bash
pnpm exec portless proxy start
```

This creates a loopback network interface and installs a local TLS certificate authority. After
the initial setup, the proxy runs in the background.

Optionally, install it as a persistent launchd service (survives reboots):

```bash
pnpm exec portless service install
```

### Daily workflow

Run the repository command hub or any frontend project with `pnpm dev` — Portless transparently
redirects to the underlying dev server:

```bash
# All frontend apps
just dev-frontend
# → https://profiletailors.localhost
# → https://pt-app.localhost

# A single package
cd apps/web/app
pnpm dev
# → https://pt-app.localhost
```

In a linked worktree on branch `fix-ui`, the same commands expose
`https://fix-ui.pt-app.localhost` and `https://fix-ui.profiletailors.localhost`. The main
worktree keeps the unprefixed URLs.

### How it works

Each project's `package.json` declares its portless config:

- `apps/web/marketing/package.json`:

  ```json
  {
    "portless": {
      "name": "profiletailors",
      "script": "dev:app"
    },
    "scripts": {
      "dev": "portless",
      "dev:app": "astro dev"
    }
  }
  ```

- `apps/web/app/package.json`:

  ```json
  {
    "portless": {
      "name": "pt-app",
      "script": "dev:app"
    },
    "scripts": {
      "dev": "portless",
      "dev:app": "vite"
    }
  }
  ```

The root `portless.json` provides additional name resolution for the monorepo:

```json
{
  "apps": {
    "apps/web/app": { "name": "pt-app" },
    "apps/web/marketing": { "name": "profiletailors" },
    "apps/web/admin": { "name": "pt-admin" }
  }
}
```

### Useful commands

| Command                              | Description                                     |
|--------------------------------------|-------------------------------------------------|
| `pnpm exec portless proxy start`     | Start the HTTPS proxy (sudo for port 443)       |
| `pnpm exec portless proxy stop`      | Stop the proxy                                  |
| `pnpm exec portless service install` | Install as launchd service (auto-start on boot) |
| `pnpm exec portless list`            | Show active routes                              |
| `pnpm exec portless get <name>`      | Resolve a named app URL                         |
| `pnpm exec portless doctor`          | Check proxy, routes, and DNS                    |
| `pnpm exec portless clean`           | Clean local TLS state                           |

## Troubleshooting

### Proxy won't start — permission denied

Portless needs sudo access for port 443. Run `pnpm exec portless proxy start` in a terminal where
you can
enter your password. If sudo is not available, portless falls back to a high port (e.g. `:1355`).

### Certificate warnings

Portless installs a local CA and generates certificates for `.localhost` domains. If you see
browser warnings, the CA certificate may not be trusted. Re-run:

```bash
pnpm exec portless clean
pnpm exec portless proxy start
```

### Port conflicts

If something else is already on port 443, stop the other service or configure portless to use an
alternative port via `portless.json`.

## References

- [Portless](https://portless.sh)
- [Monorepo structure](../README.md)
- [pnpm workspaces](https://pnpm.io/workspaces)
