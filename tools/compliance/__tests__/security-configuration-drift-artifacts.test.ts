import { describe, it, expect, beforeAll } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import YAML from 'yaml'

const currentDir = dirname(fileURLToPath(import.meta.url))
const repoRoot = resolve(currentDir, '../../..')
const statePath = resolve(
  repoRoot,
  '.agents/automation/state/security-configuration-drift-auditor.yaml',
)
const reportPath = resolve(
  repoRoot,
  '.agents/automation/reports/security-configuration-drift-auditor.md',
)

const ALLOWED_OUTCOMES = [
  'CHANGES_APPLIED',
  'NO_DRIFT_DETECTED',
  'PARTIALLY_COMPLETED',
  'BLOCKED',
]

const ALLOWED_FINDING_STATUSES = [
  'new',
  'unresolved',
  'resolved',
  'blocked',
  'ignored',
]

const ALLOWED_REMEDIATION_STATUSES = [
  'none',
  'proposed',
  'implemented',
  'verified',
]

interface AutomationFinding {
  id: string
  type: string
  status: string
  firstDetected: string
  lastVerified: string
  occurrences: number
  evidence: string
  remediation: {
    status: string
    description: string
    pullRequest: number | null
  }
}

interface AutomationCheck {
  name: string
  status: string
}

interface AutomationState {
  schemaVersion: number
  task: string
  lastExecution: string | null
  outcome?: string
  findings: AutomationFinding[]
  checks: AutomationCheck[]
}

describe('security-configuration-drift automation state file', () => {
  let state: AutomationState

  beforeAll(() => {
    const stateContent = readFileSync(statePath, 'utf-8')
    state = YAML.parse(stateContent) as AutomationState
  })

  it('declares schema version 1 as a number', () => {
    expect(state.schemaVersion).toBe(1)
    expect(typeof state.schemaVersion).toBe('number')
  })

  it('identifies the owning task', () => {
    expect(state.task).toBe('security-configuration-drift-auditor')
  })

  it('records a parseable ISO 8601 lastExecution timestamp', () => {
    expect(state.lastExecution).toBe('2026-07-23T18:45:32Z')
    expect(state.lastExecution).not.toBeNull()
    const parsedDate = new Date(state.lastExecution as string)
    expect(Number.isNaN(parsedDate.getTime())).toBe(false)
  })

  it('reports an outcome allowed by the automation framework contract', () => {
    expect(state.outcome).toBe('PARTIALLY_COMPLETED')
    expect(ALLOWED_OUTCOMES).toContain(state.outcome)
  })

  it('contains exactly one finding describing the actuator/prometheus drift', () => {
    expect(state.findings).toHaveLength(1)
    const [finding] = state.findings
    expect(finding.id).toBe('actuator-prometheus-exposure-drift')
    expect(finding.type).toBe('security-configuration-drift')
    expect(finding.status).toBe('unresolved')
  })

  it('uses a finding status allowed by the automation framework contract', () => {
    for (const finding of state.findings) {
      expect(ALLOWED_FINDING_STATUSES).toContain(finding.status)
    }
  })

  it('records finding lifecycle fields firstDetected, lastVerified, and occurrences', () => {
    const [finding] = state.findings
    expect(finding.firstDetected).toBe('2026-07-23T18:45:32Z')
    expect(finding.lastVerified).toBe('2026-07-23T18:45:32Z')
    expect(finding.occurrences).toBe(1)
  })

  it('provides non-empty, specific evidence explaining the unresolved finding', () => {
    const [finding] = state.findings
    expect(typeof finding.evidence).toBe('string')
    expect(finding.evidence.length).toBeGreaterThan(0)
    expect(finding.evidence).toContain('/actuator/prometheus')
    expect(finding.evidence).toContain('IdentitySecurityConfiguration.kt')
  })

  it('records a remediation with proposed status and a HIGH ambiguous description', () => {
    const [finding] = state.findings
    expect(ALLOWED_REMEDIATION_STATUSES).toContain(finding.remediation.status)
    expect(finding.remediation.status).toBe('proposed')
    expect(finding.remediation.description).toContain('HIGH ambiguous')
    expect(finding.remediation.pullRequest).toBeNull()
  })

  it('has unique finding ids', () => {
    const ids = state.findings.map((finding) => finding.id)
    expect(new Set(ids).size).toBe(ids.length)
  })

  it('lists the expected validation checks, all Not run', () => {
    expect(state.checks).toEqual([
      { name: 'backend-build-check', status: 'Not run' },
      { name: 'backend-test-check', status: 'Not run' },
      { name: 'frontend-biome-check', status: 'Not run' },
    ])
  })

  it('does not regress into an uninitialized/no-op state', () => {
    expect(state.lastExecution).not.toBeNull()
    expect(state.findings.length).toBeGreaterThan(0)
    expect(state.checks.length).toBeGreaterThan(0)
  })
})

describe('security-configuration-drift automation report file', () => {
  let report: string

  beforeAll(() => {
    report = readFileSync(reportPath, 'utf-8')
  })

  it('uses the expected report title', () => {
    expect(report.startsWith('# Security Configuration Drift Audit Report')).toBe(
      true,
    )
  })

  it('includes every mandatory framework section, in order', () => {
    const requiredSections = [
      '## Purpose',
      '## Execution Result',
      '## Scope Inspected',
      '## Changes Applied',
      '## Evidence Table',
      '## Validation Table',
      '## Unresolved Findings',
      '## Blockers',
      '## Automation State',
      '## Risk Assessment',
      '## Human Review Notes',
    ]

    let previousIndex = -1
    for (const section of requiredSections) {
      const index = report.indexOf(section)
      expect(index).toBeGreaterThan(previousIndex)
      previousIndex = index
    }
  })

  it('declares the PARTIALLY_COMPLETED outcome', () => {
    expect(report).toContain('**Outcome:** `PARTIALLY_COMPLETED`')
  })

  it('states that no runtime code modifications were applied', () => {
    expect(report).toContain('No runtime code modifications were applied')
  })

  it('documents no blockers', () => {
    expect(report).toContain('## Blockers\n\n- **None.**')
  })

  it('renders the evidence table row with a HIGH ambiguous risk classification', () => {
    expect(report).toContain(
      '| `docs/monitoring/actuator-security.md`: Only basic health status `/actuator/health` is public. Everything else requires authentication. |',
    )
    expect(report).toContain('HIGH ambiguous')
  })

  it('renders the validation table with all checks as Not run', () => {
    expect(report).toContain(
      '| **Backend Build** | `just backend-build` | Not run | Verification pending. |',
    )
    expect(report).toContain(
      '| **Backend Fast Tests** | `just backend-test-fast` | Not run | Verification pending. |',
    )
    expect(report).toContain(
      '| **Frontend Biome Check** | `just frontend-lint` | Not run | Verification pending. |',
    )
  })

  it('describes the unresolved finding with an id, risk, and remediation status', () => {
    expect(report).toContain('- **Finding ID:** `actuator-prometheus-exposure-drift`')
    expect(report).toContain('HIGH ambiguous')
    expect(report).toContain('**Remediation Status:** `proposed`')
  })

  it('lists both artifact files under Changes Applied', () => {
    expect(report).toContain(
      'State file updated: `.agents/automation/state/security-configuration-drift-auditor.yaml`',
    )
    expect(report).toContain(
      'Report file updated: `.agents/automation/reports/security-configuration-drift-auditor.md`',
    )
  })
})

describe('security-configuration-drift state and report consistency', () => {
  let state: AutomationState
  let report: string

  beforeAll(() => {
    state = YAML.parse(readFileSync(statePath, 'utf-8')) as AutomationState
    report = readFileSync(reportPath, 'utf-8')
  })

  it('mirrors the state schemaVersion, task, and lastExecution in the Automation State section', () => {
    expect(report).toContain(`- **Last Execution:** \`${state.lastExecution}\``)
    expect(report).toContain(`- **Schema Version:** \`${state.schemaVersion}\``)
    expect(report).toContain(`- **Task Identity:** \`${state.task}\``)
  })

  it('references every state finding id somewhere in the report', () => {
    for (const finding of state.findings) {
      expect(report).toContain(finding.id)
    }
  })

  it('does not report a Passed check when the state records all checks as Not run', () => {
    for (const check of state.checks) {
      expect(check.status).toBe('Not run')
    }
    expect(report).not.toContain('| Passed |')
  })
})
