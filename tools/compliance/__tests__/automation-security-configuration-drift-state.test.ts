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

  it('records a valid ISO 8601 lastExecution timestamp', () => {
    expect(state.lastExecution).toBe('2026-08-22T22:47:58Z')
    expect(new Date(state.lastExecution as string).toString()).not.toBe('Invalid Date')
  })

  it('records an allowed completion outcome', () => {
    expect(OUTCOMES).toContain(state.outcome)
    expect(state.outcome).toBe('PARTIALLY_COMPLETED')
  })

  it('contains exactly one finding describing the actuator/prometheus drift', () => {
    expect(state.findings).toHaveLength(1)
    const [finding] = state.findings
    expect(finding.id).toBe('actuator-prometheus-exposure-drift')
    expect(finding.type).toBe('security-configuration-drift')
  })

  it('marks the finding as unresolved, a valid lifecycle status per the automation framework', () => {
    const [finding] = state.findings
    expect(FINDING_STATUSES).toContain(finding.status)
    expect(finding.status).toBe('unresolved')
  })

  it('records finding lifecycle fields firstDetected, lastVerified, and occurrences', () => {
    const [finding] = state.findings
    expect(finding.firstDetected).toBe('2026-07-23T18:45:32Z')
    expect(finding.lastVerified).toBe('2026-07-23T18:45:32Z')
    expect(finding.occurrences).toBe(1)
  })

  it('provides non-empty evidence referencing the affected configuration file and endpoint', () => {
    const [finding] = state.findings
    expect(finding.evidence.length).toBeGreaterThan(0)
    expect(finding.evidence).toContain('IdentitySecurityConfiguration.kt')
    expect(finding.evidence).toContain('/actuator/prometheus')
  })

  it('records a remediation with proposed status and a description explaining the HIGH ambiguous classification', () => {
    const [finding] = state.findings
    expect(REMEDIATION_STATUSES).toContain(finding.remediation.status)
    expect(finding.remediation.status).toBe('proposed')
    expect(finding.remediation.description.length).toBeGreaterThan(0)
    expect(finding.remediation.description).toContain('HIGH ambiguous')
    expect(finding.remediation.pullRequest).toBeNull()
  })

  it('records the three expected validation checks with their statuses', () => {
    expect(state.checks).toHaveLength(3)
    const names = state.checks.map((check) => check.name)
    expect(names).toEqual(['backend-build-check', 'backend-test-check', 'frontend-biome-check'])
    for (const check of state.checks) {
      expect(CHECK_STATUSES).toContain(check.status)
    }
  })

  it('rejects a finding with an invalid lifecycle status (negative case)', () => {
    const invalid = {
      ...state,
      findings: [{ ...state.findings[0], status: 'in-progress' }],
    }
    const result = automationStateSchema.safeParse(invalid)
    expect(result.success).toBe(false)
  })

  it('rejects a finding with an invalid remediation status (negative case)', () => {
    const invalid = {
      ...state,
      findings: [{ ...state.findings[0], remediation: { ...state.findings[0].remediation, status: 'approved' } }],
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

  it('replaces the placeholder "no execution recorded" content from before the audit ran', () => {
    expect(report).not.toContain('No automation execution has been recorded yet')
  })

  it('declares the audit outcome consistently with the state file', () => {
    expect(report).toContain('**Outcome:** `PARTIALLY_COMPLETED`')
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

  it('cross-references the same finding id recorded in the state file', () => {
    expect(report).toContain('**Finding ID:** `actuator-prometheus-exposure-drift`')
  })

  it('cross-references the same lastExecution timestamp, schemaVersion, and task identity as the state file', () => {
    expect(report).toContain(`**Last Execution:** \`${state.lastExecution}\``)
    expect(report).toContain('**Schema Version:** `1`')
    expect(report).toContain('**Task Identity:** `security-configuration-drift-auditor`')
  })

  it('renders an Evidence Table row classifying the drift as HIGH ambiguous and Unresolved', () => {
    const evidenceSection = report.slice(
      report.indexOf('## Evidence Table'),
      report.indexOf('## Validation Table'),
    )
    expect(evidenceSection).toContain('HIGH ambiguous')
    expect(evidenceSection).toContain('Unresolved')
    expect(evidenceSection).toContain('/actuator/prometheus')
  })

  it('renders a Validation Table with checks matching the state file checks', () => {
    const validationSection = report.slice(
      report.indexOf('## Validation Table'),
      report.indexOf('## Unresolved Findings'),
    )
    const rows = validationSection.split('\n').filter((line) => line.trim().startsWith('| **'))
    expect(rows).toHaveLength(state.checks.length)
    state.checks.forEach((check, i) => {
      expect(rows[i]).toContain(`| ${check.status} |`)
    })
  })

  it('reports no blockers for this partially-completed audit', () => {
    const blockersSection = report.slice(
      report.indexOf('## Blockers'),
      report.indexOf('## Automation State'),
    )
    expect(blockersSection).toContain('None')
  })

  it('includes human review notes prompting verification before remediation', () => {
    const notesSection = report.slice(report.indexOf('## Human Review Notes'))
    expect(notesSection).toContain('Review Prometheus Scraper Authentication')
    expect(notesSection).toContain('Port Separation Enforcement')
  })

  it('documents the remediation status as proposed in the Unresolved Findings section', () => {
    const findingsSection = report.slice(
      report.indexOf('## Unresolved Findings'),
      report.indexOf('## Blockers'),
    )
    expect(findingsSection).toContain('**Remediation Status:** `proposed`')
    expect(findingsSection).toContain('HIGH ambiguous')
  })
})
