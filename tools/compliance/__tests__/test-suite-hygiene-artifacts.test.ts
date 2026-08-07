import { describe, it, expect, beforeAll } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import YAML from 'yaml'
import { z } from 'zod'

/**
 * These tests guard the `test-suite-hygiene` automation artifacts (state YAML +
 * Draft PR report) updated by this change. The artifacts are consumed by
 * autonomous agents (see `.agents/automation/framework.md`) rather than
 * application code, so the tests validate the artifacts' shape and internal
 * consistency directly against the schema/report contract defined by the
 * automation framework.
 */

const __dirname = dirname(fileURLToPath(import.meta.url))
const REPO_ROOT = resolve(__dirname, '../../..')
const STATE_PATH = resolve(REPO_ROOT, '.agents/automation/state/test-suite-hygiene.yaml')
const REPORT_PATH = resolve(REPO_ROOT, '.agents/automation/reports/test-suite-hygiene.md')

// Vocabulary defined in `.agents/automation/framework.md` and
// `.agents/automation/README.md`.
const FINDING_STATUSES = ['new', 'unresolved', 'resolved', 'blocked', 'ignored'] as const
const OUTCOMES = [
  'CHANGES_APPLIED',
  'NO_DRIFT_DETECTED',
  'PARTIALLY_COMPLETED',
  'BLOCKED',
] as const
const CHECK_STATUSES = ['Passed', 'Failed', 'Not run'] as const

const findingSchema = z
  .object({
    id: z.string().min(1),
    title: z.string().min(1),
    status: z.enum(FINDING_STATUSES),
    type: z.string().min(1),
    file: z.string().min(1),
    reproducible: z.boolean(),
    details: z.string().min(1),
    remediation: z.string().min(1).optional(),
  })
  .passthrough()

const checkSchema = z.object({
  name: z.string().min(1),
  status: z.enum(CHECK_STATUSES),
})

const automationStateSchema = z.object({
  schemaVersion: z.number().int().positive(),
  task: z.string().min(1),
  lastExecution: z.string().datetime().nullable(),
  outcome: z.enum(OUTCOMES).optional(),
  findings: z.array(findingSchema),
  checks: z.array(checkSchema),
})

type AutomationState = z.infer<typeof automationStateSchema>

describe('test-suite-hygiene automation state (.agents/automation/state/test-suite-hygiene.yaml)', () => {
  let rawYaml: string
  let state: AutomationState

  beforeAll(() => {
    rawYaml = readFileSync(STATE_PATH, 'utf-8')
    const parsed = YAML.parse(rawYaml)
    const result = automationStateSchema.safeParse(parsed)
    if (!result.success) {
      throw new Error(
        `State file failed schema validation: ${JSON.stringify(result.error.issues)}`,
      )
    }
    state = result.data
  })

  it('is valid, parseable YAML', () => {
    expect(() => YAML.parse(rawYaml)).not.toThrow()
  })

  it('conforms to the automation state schema (schemaVersion, task, lastExecution, outcome, findings, checks)', () => {
    const parsed = YAML.parse(rawYaml)
    const result = automationStateSchema.safeParse(parsed)
    expect(result.success).toBe(true)
  })

  it('declares schema version 1', () => {
    expect(state.schemaVersion).toBe(1)
    expect(typeof state.schemaVersion).toBe('number')
  })

  it('is owned by the test-suite-hygiene task', () => {
    expect(state.task).toBe('test-suite-hygiene')
  })

  it('records a valid, parseable ISO 8601 lastExecution timestamp', () => {
    expect(state.lastExecution).toBe('2026-08-06T17:58:58Z')
    expect(new Date(state.lastExecution as string).toString()).not.toBe('Invalid Date')
  })

  it('records an allowed completion outcome', () => {
    expect(OUTCOMES).toContain(state.outcome)
    expect(state.outcome).toBe('NO_DRIFT_DETECTED')
  })

  it('contains exactly one finding describing the previously resolved GovernanceTakedownView it.todo', () => {
    expect(state.findings).toHaveLength(1)
    const [finding] = state.findings
    expect(finding.id).toBe('governance-takedown-test-todo')
    expect(finding.type).toBe('test-suite-hygiene')
    expect(finding.file).toBe(
      'apps/web/app/src/modules/governance/views/GovernanceTakedownView.test.ts',
    )
  })

  it('marks the finding as resolved, a valid lifecycle status per the automation framework', () => {
    const [finding] = state.findings
    expect(FINDING_STATUSES).toContain(finding.status)
    expect(finding.status).toBe('resolved')
  })

  it('marks the finding as reproducible with non-empty details and remediation notes', () => {
    const [finding] = state.findings
    expect(finding.reproducible).toBe(true)
    expect(finding.details.length).toBeGreaterThan(0)
    expect(finding.details).toContain('it.todo')
    expect(finding.remediation).toBeDefined()
    expect(finding.remediation as string).toContain('interaction test')
  })

  it('has unique finding ids', () => {
    const ids = state.findings.map((finding) => finding.id)
    expect(new Set(ids).size).toBe(ids.length)
  })

  it('records the two expected validation checks, all Passed', () => {
    expect(state.checks).toEqual([
      { name: 'app unit tests', status: 'Passed' },
      { name: 'app linter and formatter', status: 'Passed' },
    ])
    for (const check of state.checks) {
      expect(CHECK_STATUSES).toContain(check.status)
    }
  })

  it('does not regress into an uninitialized/no-op state', () => {
    expect(state.lastExecution).not.toBeNull()
    expect(state.checks.length).toBeGreaterThan(0)
  })

  it('rejects a finding with an invalid lifecycle status (negative case)', () => {
    const invalid = {
      ...state,
      findings: [{ ...state.findings[0], status: 'in-progress' }],
    }
    const result = automationStateSchema.safeParse(invalid)
    expect(result.success).toBe(false)
  })

  it('rejects a check with an invalid status value (negative case)', () => {
    const invalid = {
      ...state,
      checks: [{ name: 'app unit tests', status: 'Skipped' }],
    }
    const result = automationStateSchema.safeParse(invalid)
    expect(result.success).toBe(false)
  })

  it('rejects a state object missing required top-level fields (negative case)', () => {
    const invalid = { schemaVersion: 1, task: 'test-suite-hygiene' }
    const result = automationStateSchema.safeParse(invalid)
    expect(result.success).toBe(false)
  })

  it('rejects an outcome value outside the allowed completion outcomes (negative case)', () => {
    const invalid = { ...state, outcome: 'IN_PROGRESS' }
    const result = automationStateSchema.safeParse(invalid)
    expect(result.success).toBe(false)
  })

  it('accepts a null lastExecution with empty findings/checks arrays (boundary: not-yet-run state shape)', () => {
    const emptyState = {
      schemaVersion: 1,
      task: 'test-suite-hygiene',
      lastExecution: null,
      findings: [],
      checks: [],
    }
    const result = automationStateSchema.safeParse(emptyState)
    expect(result.success).toBe(true)
  })
})

describe('test-suite-hygiene report (.agents/automation/reports/test-suite-hygiene.md)', () => {
  let report: string

  beforeAll(() => {
    report = readFileSync(REPORT_PATH, 'utf-8')
  })

  it('uses the expected report title', () => {
    expect(report.startsWith('# Test Suite Hygiene Auditor Report')).toBe(true)
  })

  it('declares the NO_DRIFT_DETECTED execution result', () => {
    const executionResultSection = report.slice(
      report.indexOf('## Execution Result'),
      report.indexOf('## Scope Inspected'),
    )
    expect(executionResultSection).toContain('NO_DRIFT_DETECTED')
  })

  it('contains every mandatory Draft PR section defined by the automation framework, in order', () => {
    const requiredHeadings = [
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

    const indices = requiredHeadings.map((heading) => report.indexOf(heading))

    requiredHeadings.forEach((heading, i) => {
      expect(indices[i], `expected to find heading "${heading}"`).toBeGreaterThan(-1)
    })

    for (let i = 1; i < indices.length; i++) {
      expect(indices[i]).toBeGreaterThan(indices[i - 1])
    }
  })

  it('cross-references the same finding id recorded in the state file, marked resolved', () => {
    const evidenceSection = report.slice(
      report.indexOf('## Evidence Table'),
      report.indexOf('## Validation Table'),
    )
    expect(evidenceSection).toContain('governance-takedown-test-todo')
    expect(evidenceSection).toContain('resolved')
    expect(evidenceSection).toContain(
      'apps/web/app/src/modules/governance/views/GovernanceTakedownView.test.ts',
    )
  })

  it('cross-references the same lastExecution timestamp, schemaVersion, and task identity as the state file', () => {
    expect(report).toContain('**Last Execution:** `2026-08-06T17:58:58Z`')
    expect(report).toContain('**Schema Version:** `1`')
    expect(report).toContain('**Task Identity:** `test-suite-hygiene`')
  })

  it('renders a Validation Table with all checks Passed', () => {
    const validationSection = report.slice(
      report.indexOf('## Validation Table'),
      report.indexOf('## Unresolved Findings'),
    )
    const passedRows = validationSection.split('\n').filter((line) => line.includes('| Passed |'))
    expect(passedRows.length).toBeGreaterThanOrEqual(3)
    expect(validationSection).not.toContain('| Failed |')
    expect(validationSection).not.toContain('| Not run |')
  })

  it('reports no unresolved findings and no blockers', () => {
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

  it('classifies the risk as LOW and states no production code changes were made', () => {
    const riskSection = report.slice(
      report.indexOf('## Risk Assessment'),
      report.indexOf('## Human Review Notes'),
    )
    expect(riskSection).toContain('LOW RISK')
    expect(riskSection).toContain('No production code changes were made')
  })

  it('includes human review notes confirming no active hygiene issues were detected', () => {
    const notesSection = report.slice(report.indexOf('## Human Review Notes'))
    expect(notesSection).toContain('No active test suite hygiene issues were detected')
  })
})

describe('test-suite-hygiene state and report consistency', () => {
  let state: AutomationState
  let report: string

  beforeAll(() => {
    state = YAML.parse(readFileSync(STATE_PATH, 'utf-8')) as AutomationState
    report = readFileSync(REPORT_PATH, 'utf-8')
  })

  it('mirrors the state schemaVersion, task, and lastExecution in the Automation State section', () => {
    expect(report).toContain(`- **Last Execution:** \`${state.lastExecution}\``)
    expect(report).toContain(`- **Schema Version:** \`${state.schemaVersion}\``)
    expect(report).toContain(`- **Task Identity:** \`${state.task}\``)
  })

  it('declares the same outcome in both the state file and the report', () => {
    const executionResultSection = report.slice(
      report.indexOf('## Execution Result'),
      report.indexOf('## Scope Inspected'),
    )
    expect(executionResultSection).toContain(state.outcome as string)
  })

  it('references every state finding id somewhere in the report', () => {
    for (const finding of state.findings) {
      expect(report).toContain(finding.id)
    }
  })

  it('does not report a Failed or Not run check when the state records all checks as Passed', () => {
    for (const check of state.checks) {
      expect(check.status).toBe('Passed')
    }
    expect(report).not.toContain('| Failed |')
    expect(report).not.toContain('| Not run |')
  })
})