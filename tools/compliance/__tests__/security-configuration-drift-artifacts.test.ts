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

interface AutomationCheck {
  name: string
  status: string
}

interface AutomationState {
  schemaVersion: number
  task: string
  lastExecution: string | null
  outcome?: string
  findings: unknown[]
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

  it('records null lastExecution in the not-yet-run baseline state', () => {
    expect(state.lastExecution).toBeNull()
  })

  it('has empty findings and checks arrays in the not-yet-run baseline state', () => {
    expect(state.findings).toEqual([])
    expect(state.checks).toEqual([])
  })

  it('does not declare an outcome in the not-yet-run baseline state', () => {
    expect(state.outcome).toBeUndefined()
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

  it('states that no automation execution has been recorded yet', () => {
    expect(report).toContain('No automation execution has been recorded yet')
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

  it('reports no unresolved findings and no blockers in the baseline state', () => {
    const unresolvedSection = report.slice(
      report.indexOf('## Unresolved Findings'),
      report.indexOf('## Blockers'),
    )
    const blockersSection = report.slice(
      report.indexOf('## Blockers'),
      report.indexOf('## Automation State'),
    )
    expect(unresolvedSection).toContain('None')
    expect(blockersSection).toContain('None')
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

  it('reports validation check statuses consistent with the state file', () => {
    const passedChecks = state.checks.filter((check) => check.status === 'Passed')
    const failedChecks = state.checks.filter((check) => check.status === 'Failed')
    const notRunChecks = state.checks.filter((check) => check.status === 'Not run')
    for (const check of passedChecks) {
      expect(report).toContain('| Passed |')
    }
    for (const check of failedChecks) {
      expect(report).toContain('| Failed |')
    }
    expect(report).toContain(`| Not run |`)
    expect(passedChecks.length + failedChecks.length + notRunChecks.length).toBe(
      state.checks.length,
    )
  })
})
