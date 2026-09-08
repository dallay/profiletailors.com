import { describe, it, expect, beforeAll } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import YAML from 'yaml'
import { z } from 'zod'

const __dirname = dirname(fileURLToPath(import.meta.url))
const REPO_ROOT = resolve(__dirname, '../../..')
const STATE_PATH = resolve(REPO_ROOT, '.agents/automation/state/test-suite-hygiene.yaml')
const REPORT_PATH = resolve(REPO_ROOT, '.agents/automation/reports/test-suite-hygiene.md')

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
    status: z.enum(FINDING_STATUSES),
    type: z.string().min(1),
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

  it('rejects a finding with an invalid lifecycle status (negative case)', () => {
    const invalid = {
      ...state,
      findings: [{ id: 'x', status: 'in-progress', type: 'test-suite-hygiene' }],
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
  let state: AutomationState

  beforeAll(() => {
    report = readFileSync(REPORT_PATH, 'utf-8')
    state = YAML.parse(readFileSync(STATE_PATH, 'utf-8')) as AutomationState
  })

  it('uses the expected report title', () => {
    expect(report.startsWith('# Test Suite Hygiene Auditor Report')).toBe(true)
  })

  it('states that no automation execution has been recorded yet', () => {
    expect(report).toContain('No automation execution has been recorded yet')
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

  it('cross-references the same lastExecution, schemaVersion, and task identity as the state file', () => {
    expect(report).toContain(`**Last Execution:** \`${state.lastExecution}\``)
    expect(report).toContain(`**Schema Version:** \`${state.schemaVersion}\``)
    expect(report).toContain(`**Task Identity:** \`${state.task}\``)
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
