import { describe, it, expect, beforeAll } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import YAML from 'yaml'
import { z } from 'zod'

const __dirname = dirname(fileURLToPath(import.meta.url))
const REPO_ROOT = resolve(__dirname, '../../..')
const STATE_PATH = resolve(
  REPO_ROOT,
  '.agents/automation/state/security-configuration-drift-auditor.yaml',
)
const REPORT_PATH = resolve(
  REPO_ROOT,
  '.agents/automation/reports/security-configuration-drift-auditor.md',
)

const FINDING_STATUSES = ['new', 'unresolved', 'resolved', 'blocked', 'ignored'] as const
const REMEDIATION_STATUSES = ['none', 'proposed', 'implemented', 'verified'] as const
const OUTCOMES = [
  'CHANGES_APPLIED',
  'NO_DRIFT_DETECTED',
  'PARTIALLY_COMPLETED',
  'BLOCKED',
] as const
const CHECK_STATUSES = ['Passed', 'Failed', 'Not run'] as const

const remediationSchema = z
  .object({
    status: z.enum(REMEDIATION_STATUSES),
    description: z.string().min(1),
    pullRequest: z.union([z.number().int().positive(), z.null()]),
  })
  .passthrough()

const findingSchema = z
  .object({
    id: z.string().min(1),
    type: z.string().min(1),
    status: z.enum(FINDING_STATUSES),
    firstDetected: z.string().datetime(),
    lastVerified: z.string().datetime(),
    occurrences: z.number().int().positive(),
    evidence: z.string().min(1),
    remediation: remediationSchema,
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

describe('security-configuration-drift automation state (.agents/automation/state/security-configuration-drift-auditor.yaml)', () => {
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
  })

  it('is owned by the security-configuration-drift-auditor task', () => {
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

  it('rejects a finding with an invalid lifecycle status (negative case)', () => {
    const invalid = {
      ...state,
      findings: [
        {
          id: 'x',
          type: 'security-configuration-drift',
          status: 'in-progress',
          firstDetected: '2026-07-23T18:45:32Z',
          lastVerified: '2026-07-23T18:45:32Z',
          occurrences: 1,
          evidence: 'evidence',
          remediation: { status: 'proposed', description: 'desc', pullRequest: null },
        },
      ],
    }
    const result = automationStateSchema.safeParse(invalid)
    expect(result.success).toBe(false)
  })

  it('rejects a finding with an invalid remediation status (negative case)', () => {
    const invalid = {
      ...state,
      findings: [
        {
          id: 'x',
          type: 'security-configuration-drift',
          status: 'unresolved',
          firstDetected: '2026-07-23T18:45:32Z',
          lastVerified: '2026-07-23T18:45:32Z',
          occurrences: 1,
          evidence: 'evidence',
          remediation: { status: 'approved', description: 'desc', pullRequest: null },
        },
      ],
    }
    const result = automationStateSchema.safeParse(invalid)
    expect(result.success).toBe(false)
  })

  it('rejects a check with an invalid status value (negative case)', () => {
    const invalid = {
      ...state,
      checks: [{ name: 'backend-build-check', status: 'Skipped' }],
    }
    const result = automationStateSchema.safeParse(invalid)
    expect(result.success).toBe(false)
  })

  it('rejects a state object missing required top-level fields (negative case)', () => {
    const invalid = { schemaVersion: 1, task: 'security-configuration-drift-auditor' }
    const result = automationStateSchema.safeParse(invalid)
    expect(result.success).toBe(false)
  })

  it('accepts a null lastExecution with empty findings/checks arrays (boundary: not-yet-run state shape)', () => {
    const emptyState = {
      schemaVersion: 1,
      task: 'security-configuration-drift-auditor',
      lastExecution: null,
      findings: [],
      checks: [],
    }
    const result = automationStateSchema.safeParse(emptyState)
    expect(result.success).toBe(true)
  })
})

describe('security-configuration-drift report (.agents/automation/reports/security-configuration-drift-auditor.md)', () => {
  let report: string
  let state: AutomationState

  beforeAll(() => {
    report = readFileSync(REPORT_PATH, 'utf-8')
    state = YAML.parse(readFileSync(STATE_PATH, 'utf-8')) as AutomationState
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
    expect(report).toContain('**Schema Version:** `1`')
    expect(report).toContain('**Task Identity:** `security-configuration-drift-auditor`')
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
