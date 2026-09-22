# AI-Assisted Engineering — Tool Selection Decisions

> This document explains why ProfileTailors uses specific tools and the engineering rationale behind each choice.

> Built with [Agent Harness](https://github.com/yacosta738/agent-harness) — a personal framework for AI-assisted engineering.

---

## OpenSpec / SDD

### Problem Being Solved

ProfileTailors needs a spec-driven workflow: ticket → spec → implementation → review → archive.

### Solution Used by ProfileTailors

**OpenSpec** is a spec-driven development (SDD) system with:
- Phase DAG: `init → explore → propose → [spec + design] → tasks → apply → verify → qa → archive`
- `state.yaml` tracking for every change
- `config.yaml` defining testing capabilities and quality gates
- Delta specs and main specs separation
- Phase ownership with explicit deliverables

**Files**:
- `openspec/config.yaml` — configuration
- `openspec/README.md` — lifecycle documentation
- `openspec/changes/<name>/` — change artifacts
- `openspec/archive/<date-name>/` — completed changes

### Alternative Solutions

Generic "spec-driven workflow" or Jira-based specs.

### Why ProfileTailors Uses This Solution

1. **Lightweight**: No external dependency on project management tools
2. **Tractable**: File-based artifacts are easy to review, diff, and version
3. **Git-native**: Artifacts live alongside code, making history traceable
4. **Agent-readable**: LLMs can parse YAML and Markdown without API integration
5. **Flexible**: Adapts to hotfixes (minimal), large features (full), and documentation changes (none)

### Trade-offs

| OpenSpec | Jira-based |
|----------|-----------|
| ✅ No external dependency | ❌ Requires Jira access |
| ✅ Version-controlled specs | ❌ Specs can drift from code |
| ✅ Works offline | ❌ Requires connectivity |
| ❌ Manual state tracking | ✅ Automatic state sync |
| ❌ Duplicate of Linear issues | ✅ Single source of truth |

### Setup Cost

- Near zero: Just create directories and YAML files
- Skill cost: None (skills don't need to know OpenSpec specifics)

### When I Would Choose the Alternative

- **Jira-based specs**: If the team is fully integrated with Jira and needs real-time visibility across non-technical stakeholders
- **GitHub Projects**: If the team prefers Kanban-style tracking

---

## Agent Skills

### Problem Being Solved

ProfileTailors needs 3-4 reusable skills for common tasks.

### Solution Used by ProfileTailors

**Hierarchical Skills System**:

```
.agents/skills/              # Canonical source (67 skills)
├── architecture-governance/  # ARCH-001..005 ownership
├── backend-platform/        # Kotlin, Spring Boot, hexagonal, DDD
├── design-pattern/          # GoF patterns (adapter, builder, etc.)
├── frontend-platform/      # Vue, Astro, Pinia, accessibility
├── impeccable/              # Design/impeccable design reviews
├── languages-typing/        # TypeScript, Kotlin, Zod
├── testing/                # Playwright, Vitest
└── tools/                  # Gradle, pnpm, Docker, etc.
```

**AgentSync**: `.agents/agentsync.toml` synchronizes skills to:
- `.claude/skills/` (Claude Code)
- `.codex/skills/` (GitHub Copilot)
- `.gemini/skills/` (Gemini)
- `.opencode/skills/` → symlinked to `.agents/skills/`

### Alternative Solutions

Single-level prompt files or basic instruction files.

### Why ProfileTailors Uses This Solution

1. **Domain separation**: Skills trigger based on context, not manual invocation
2. **Discovery**: Auto-generated `skill-registry.md` lets agents find relevant skills
3. **Consistency**: Skills ensure pattern adherence (hexagonal, DDD, testing)
4. **Amortization**: One skill definition serves all agents
5. **Quality gates**: Architecture rules are enforceable via Konsist/Spring Modulith tests

### Trade-offs

| Skills System | Simple Prompts |
|---------------|----------------|
| ✅ Context-aware auto-loading | ❌ Requires manual skill selection |
| ✅ Version-controlled patterns | ❌ Patterns can drift |
| ✅ Executable quality checks | ❌ Manual enforcement |
| ❌ Learning curve | ✅ Simpler to understand |
| ❌ More files to maintain | ✅ Single file per concern |

### Setup Cost

- AgentSync handles propagation automatically
- `just hooks-install` sets up the sync

### When I Would Choose Simple Prompts

- For one-off tasks that don't need pattern enforcement
- For very small teams without established conventions

---

## Linear MCP (instead of Jira MCP)

### Problem Being Solved

ProfileTailors uses Jira as an example. The workflow requires reading tickets and updating status.

### Solution Used by ProfileTailors

**Linear MCP** configured in `.mcp.json`:

```json
{
  "mcpServers": {
    "linear": {
      "command": "npx",
      "args": ["-y", "mcp-remote", "https://mcp.linear.app/mcp"]
    }
  }
}
```

**Features**:
- Read/write issues
- List projects, teams, cycles
- Create comments and attachments
- Search and filter

### Alternative Solutions

**Jira MCP** — native Jira integration.

### Why ProfileTailors Uses Linear

1. **Modern API**: Linear has excellent MCP support with predictable behavior
2. **Git-native workflow**: Linear's Git integration maps naturally to code review
3. **Developer-first**: Built for engineering teams, not enterprise managers
4. **CLI-friendly**: `linear` tool in MCP enables scripting
5. **Already adopted**: The team already uses Linear for project management

### Trade-offs

| Linear | Jira |
|--------|------|
| ✅ Modern, developer-friendly API | ❌ Legacy REST complexity |
| ✅ Excellent MCP support | ❌ Requires Jira Cloud subscription |
| ✅ Git-native integrations | ✅ Enterprise-grade features |
| ❌ Smaller ecosystem | ✅ Better for large enterprises |
| ❌ Less customizable | ✅ Highly configurable workflows |

### Setup Cost

1. Get Linear API key: `https://linear.app/settings/api`
2. Set environment variable: `LINEAR_API_KEY`
3. Test with `linear_get_workspace`

### When I Would Choose Jira

- If the organization requires enterprise-grade access controls
- If compliance requires audit trails beyond Linear's capabilities
- If integrating with Atlassian ecosystem (Confluence, Service Management)

---

## GitHub Copilot Instructions

### Problem Being Solved

ProfileTailors needs Copilot-compatible instructions for AI-assisted development.

### Solution Used by ProfileTailors

**Three-layer instruction system**:

1. **`.github/copilot-instructions.md`** — Synced from AGENTS.md, core operating rules
2. **`.github/commands/`** — Specific command prompts (Playwright test generation, healing, planning)
3. **`.agents/AGENTS.md`** — Canonical source, synced via AgentSync

### Alternative Solutions

Single `.github/copilot-instructions.md` with all instructions inline.

### Why ProfileTailors Uses This Solution

1. **Separation of concerns**: General rules vs. specific commands
2. **DRY**: AGENTS.md is canonical; all agents derive from it
3. **Agent-specific**: Different agents may need different prompts
4. **Maintainability**: Changes to one file don't affect others

### Trade-offs

| Three-Layer | Single File |
|-------------|-------------|
| ✅ Better organization | ✅ Simpler to understand |
| ✅ Agent-specific customization | ❌ One-size-fits-all |
| ❌ More complex setup | ✅ Zero configuration |
| ❌ Sync maintenance required | ✅ No sync issues |

### Setup Cost

Zero additional cost. AgentSync handles sync automatically.

### When I Would Choose Single File

- For small teams without established patterns
- For simple projects without complex conventions

---

## Review Workflow

### Problem Being Solved

ProfileTailors requires a non-mutating review command that reads specs and reports findings.

### Solution Used by ProfileTailors

**Four-R Review Pattern**:

`docs/reviews/4r-review-tenancy-remediation.md` documents a comprehensive review covering:
- **Risk**: Security and architectural risks
- **Readability**: Code clarity and documentation
- **Reliability**: Error handling and edge cases
- **Resilience**: Concurrency, transactions, rollback

**Agent-based review** via maintenance agents:
- `adr-consistency-auditor.md`
- `api-contract-drift-auditor.md`
- `security-configuration-drift-auditor.md`
- `suppression-auditor.md`

### Alternative Solutions

Generic review prompt without specific criteria.

### Why ProfileTailors Uses This Solution

1. **Structured**: Four dimensions ensure comprehensive coverage
2. **Verifiable**: Each dimension has specific checks
3. **Reportable**: Findings are categorized and actionable
4. **Amortized**: Agents can run reviews autonomously

### Trade-offs

| Structured Review | Generic Review |
|-------------------|----------------|
| ✅ Comprehensive coverage | ✅ Faster to execute |
| ✅ Consistent across changes | ❌ May miss dimensions |
| ❌ More time per review | ✅ No training needed |
| ❌ Requires domain knowledge | ✅ Works for any code |

### Setup Cost

- Review prompts in `.github/prompts/`
- Existing review evidence in `docs/reviews/`

### When I Would Choose Generic Review

- For quick exploratory reviews
- For non-critical changes

---

## AgentSync

### Problem Being Solved

Multiple AI agents need consistent instructions and skills.

### Solution Used by ProfileTailors

**AgentSync** (`.agents/agentsync.toml`) automatically:
- Syncs `AGENTS.md` to root, `.claude/`, `.codex/`, `.gemini/`
- Propagates skills to agent-specific directories
- Manages MCP configuration across tools
- Handles Git symlinks

### Alternative Solutions

Manual sync or per-agent configuration.

### Why ProfileTailors Uses AgentSync

1. **Single source of truth**: One edit propagates everywhere
2. **Automated**: No manual sync steps
3. **Versioned**: Git history tracks changes
4. **Testable**: Config changes can be validated

### Trade-offs

| AgentSync | Manual Sync |
|-----------|-------------|
| ✅ Automatic propagation | ✅ Simpler to understand |
| ✅ Consistent | ❌ Human error prone |
| ❌ Additional dependency | ✅ No dependency |
| ❌ Config complexity | ✅ Zero config |

### Setup Cost

`just hooks-install` sets up the Git hooks for auto-sync.

### When I Would Choose Manual Sync

- For very small teams with few agents
- For organizations with strict change control requirements

---

## Measurement Mechanism

### Problem Being Solved

ProfileTailors needs efficiency measurement comparing structured vs. unstructured workflows.

### Solution Used by ProfileTailors

**Documentation-based measurement**:

- `EFICIENCIA.md` documents methodology
- Explicit baseline definition
- Token/call/duration tracking (manual for Copilot)
- Honest analysis of trade-offs

### Alternative Solutions

Automated metrics collection.

### Why ProfileTailors Uses Documentation

1. **Honesty**: Cannot measure what isn't measurable without explicit setup
2. **Reproducibility**: Anyone can repeat the documented procedure
3. **Transparency**: Clear what requires manual vs. automated measurement

### Trade-offs

| Documentation | Automated Metrics |
|---------------|-------------------|
| ✅ Always possible | ❌ Requires debug logs |
| ✅ Reproducible | ❌ Environment-dependent |
| ❌ Manual effort | ✅ Real-time tracking |
| ❌ May miss details | ✅ Comprehensive |

### Setup Cost

Documentation only — no technical setup required.

### When I Would Choose Automated Metrics

- For teams with existing observability infrastructure
- For continuous efficiency monitoring

---

## Summary: Why ProfileTailors' Approach is Equivalent or Better

| Concept | ProfileTailors Implementation | Justification |
|-----------------|------------------------------|---------------|
| OpenSpec | Real OpenSpec SDD workflow | More structured than generic "spec-driven" |
| 3-4 Skills | 67 skills + hierarchical system | More comprehensive, not less |
| Prompts | Three-layer instruction system | More maintainable, agent-aware |
| Jira MCP | Linear MCP | Explicitly acceptable per exercise |
| Toy project | Three bounded production changes | More realistic evidence |
| Generic review | Four-R structured review | More comprehensive |
| Manual measurement | Documented methodology | Honest and reproducible |
