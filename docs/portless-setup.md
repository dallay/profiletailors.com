# Portless — Local Development URLs

**Date:** 2026-06-12
**Status:** Active

## Overview

[Portless](https://portless.sh) provides stable, named `.localhost` HTTPS URLs for local
development servers — eliminating port conflicts and making URLs predictable for CORS, OAuth
redirects, API clients, and tooling.

Instead of remembering `http://localhost:4321` (Astro) and `http://localhost:5173` (Vite), you get
consistent HTTPS URLs:

| Project           | URL                                |
| ----------------- | ---------------------------------- |
| Marketing (Astro) | `https://profiletailors.localhost` |
| App (Vue 3)       | `https://pt-app.localhost`         |

Portless auto-discovers packages through `pnpm-workspace.yaml` and respects per-package
`"portless"` config in each `package.json`. Name resolution uses the closest config, with this
precedence: CLI flags > `package.json` > `portless.json` > defaults.

## Changes

### 2026-06-12 — Portless installed and configured

- Installed portless globally (`npm install -g portless` v0.14.0)
- Created root `portless.json` with explicit app name map for both frontend projects
- Updated `apps/web/app/package.json`: added `"portless"` config block (`name: pt-app`, `script:
  dev:app`), changed dev script to run through portless
- `apps/web/marketing/package.json` was already configured — no changes needed

## Usage

### First-time setup

Start the portless proxy (requires sudo for port 443):

```bash
portless proxy start
```

This creates a loopback network interface and installs a local TLS certificate authority. After
the initial setup, the proxy runs in the background.

Optionally, install it as a persistent launchd service (survives reboots):

```bash
portless service install
```

### Daily workflow

Run any frontend project with `pnpm dev` — portless transparently redirects to the underlying dev
server:

```bash
# Marketing site (Astro 7)
cd apps/web/marketing
pnpm dev
# → https://profiletailors.localhost

# App (Vue 3 + Vite 8)
cd apps/web/app
pnpm dev
# → https://pt-app.localhost
```

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
    "apps/web/marketing": { "name": "profiletailors" }
  }
}
```

### Useful commands

| Command                      | Description                                     |
| ---------------------------- | ----------------------------------------------- |
| `portless proxy start`       | Start the HTTPS proxy (sudo for port 443)       |
| `portless proxy stop`        | Stop the proxy                                  |
| `portless service install`   | Install as launchd service (auto-start on boot) |
| `portless service uninstall` | Remove launchd service                          |
| `portless list`              | Show active routes                              |
| `portless get <name>`        | Resolve a named app URL                         |
| `portless clean`             | Clean local TLS state                           |

## Troubleshooting

### Proxy won't start — permission denied

Portless needs sudo access for port 443. Run `portless proxy start` in a terminal where you can
enter your password. If sudo is not available, portless falls back to a high port (e.g. `:1355`).

### Certificate warnings

Portless installs a local CA and generates certificates for `.localhost` domains. If you see
browser warnings, the CA certificate may not be trusted. Re-run:

```bash
portless clean
portless proxy start
```

### Port conflicts

If something else is already on port 443, stop the other service or configure portless to use an
alternative port via `portless.json`.

## References

- [Portless](https://portless.sh)
- [Monorepo structure](../README.md)
- [pnpm workspaces](https://pnpm.io/workspaces)
