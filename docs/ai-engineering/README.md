# AI-Assisted Engineering

> How ProfileTailors uses AI agents with AgentSync and OpenSpec for structured, spec-driven development.

---

## Overview

ProfileTailors is built using **Agent Harness** — a personal framework for AI-assisted engineering that provides:

- **AgentSync**: Multi-agent instruction synchronization
- **OpenSpec**: Spec-driven development with phase DAG
- **Skills System**: 66 domain-specific skills
- **MCP Integration**: Linear, Playwright, and more

This is not a demonstration or exercise. This is how ProfileTailors is actually built.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    GitHub Copilot (VS Code)                  │
├─────────────────────────────────────────────────────────────┤
│  .github/copilot-instructions.md  ← Synced via AgentSync   │
│  .github/prompts/                   ← Reusable commands     │
│  MCP Servers (Linear, Playwright) ← External integration    │
│  .opencode/skills/               ← Synced via AgentSync   │
└─────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────┐
│                    Agent Harness (OpenCode)                  │
├─────────────────────────────────────────────────────────────┤
│  .agents/AGENTS.md              ← Canonical instructions    │
│  .agents/skills/                ← 66 top-level skills       │
│  .agents/agents/                 ← 23 maintenance agents     │
│  .agents/agentsync.toml         ← Sync configuration        │
└─────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────┐
│                       OpenSpec SDD                          │
├─────────────────────────────────────────────────────────────┤
│  Proposal → Spec → Design → Tasks → Apply → Verify → QA   │
│                          ↓                                   │
│                       Archive                                │
└─────────────────────────────────────────────────────────────┘
```

---

## Quick Navigation

| Document | Purpose |
|----------|---------|
| [ASSESSMENT.md](./ASSESSMENT.md) | Configuration compliance matrix |
| [CHANGES.md](./CHANGES.md) | Evidence from completed changes |
| [EFICIENCIA.md](./EFICIENCIA.md) | Efficiency measurement methodology |
| [TOOLING.md](./TOOLING.md) | Tool selection decisions |
| [SCORECARD.md](./SCORECARD.md) | Final evaluation scorecard |
| [reviews/](./reviews/) | Review evidence |

---

## Core Components

### AGENTS.md

Canonical instructions for all AI agents. Synchronized to:

- Root `AGENTS.md` (symlink)
- `.claude/`
- `.codex/`
- `.gemini/`
- `.opencode/`

### Skills (66 total)

| Area | Examples |
|------|----------|
| Architecture and backend | `architecture-governance`, `hexagonal-architecture`, `ddd-architecture`, `spring-boot` |
| Design | `impeccable`, `nothing-design`, and GoF patterns |
| Frontend | `vue`, `astrolicious-astro`, `pinia`, `frontend-architecture` |
| Languages and testing | `typescript`, `kotlin`, `zod-4`, `playwright`, `vitest` |
| Tools | `gradle`, `pnpm`, `docker-expert` |

### OpenSpec

Spec-driven development with defined phases:

```
init → explore → propose → [spec + design] → tasks → apply → verify → qa → archive
```

### MCP Servers

Configured in `.mcp.json` and `.vscode/mcp.json`:

- **Linear**: Issue tracking
- **Playwright**: E2E testing
- **Chrome DevTools**: Browser automation
- **CodeGraph**: Code intelligence
- **Socket**: Security scanning
- **GitHub Grep**: Code search
- **shadcn-vue**: UI components

---

## Reference Changes

Three completed changes demonstrating the workflow:

| Change | Linear | Status | Evidence |
|--------|--------|--------|----------|
| Invitation Registration Flow | DALLAY-567 | ✅ Archived | `.agents/sdd/archive/2026-09-20-*/` |
| Direct Invitation FK Fix | — | 🔄 Near-complete | `.agents/sdd/changes/hotfix-*/` |
| Private Beta Launch | DALLAY-555/557 | ⚠️ QA blocked | `.agents/sdd/changes/private-*/` |

See [CHANGES.md](./CHANGES.md) for details.

---

## Review Pattern

Structured 4R review (Risk, Readability, Reliability, Resilience):

1. **Agent reviews** against specification
2. **Reports findings** with file:line
3. **Human evaluates** findings
4. **Human decides** on corrections
5. **Corrections implemented**

Example review: [4R Tenancy Review](../reviews/4r-review-tenancy-remediation.md)

---

## Commands

### Review Prompt

```bash
# In Copilot Chat, reference:
@.github/prompts/review.prompt.md
```

### OpenSpec Commands

```bash
# List available SDD commands
just -l | grep sdd

# Initialize, propose, apply, verify, archive
sdd-init <name>
sdd-propose
sdd-apply
sdd-verify
sdd-qa
sdd-archive
```

---

## Setup

```bash
# Install dependencies
just install

# Setup hooks and AgentSync
just hooks-install

# Verify setup
just -l
```

### Linear MCP Setup

```bash
# Get API key from https://linear.app/settings/api
export LINEAR_API_KEY="lin_api_xxxxx"
```

---

## Related

- [Agent Harness](https://github.com/yacosta738/agent-harness) — Framework powering this setup
- [OpenSpec](../../.agents/sdd/) — Spec-driven development system
- [Architecture](../architecture/) — System architecture
- [Skills](../../.agents/skills/) — Domain skills
