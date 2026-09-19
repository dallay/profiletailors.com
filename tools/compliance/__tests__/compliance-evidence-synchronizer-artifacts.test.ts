import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import YAML from 'yaml'
import { z } from 'zod'

const automationStateSchema = z.object({
  schemaVersion: z.number(),
  task: z.string(),
  lastExecution: z.string().nullable(),
  outcome: z.enum(['CHANGES_APPLIED', 'NO_DRIFT_DETECTED', 'PARTIALLY_COMPLETED', 'BLOCKED']).optional(),
  findings: z.array(z.any()),
  checks: z.array(z.any()),
})

describe('compliance-evidence-synchronizer state and report artifacts', () => {
  const statePath = resolve(
    process.cwd(),
    '../../.agents/automation/state/compliance-evidence-synchronizer.yaml'
  )
  const reportPath = resolve(
    process.cwd(),
    '../../.agents/automation/reports/compliance-evidence-synchronizer.md'
  )

  it('state conforms to schema', () => {
    const content = readFileSync(statePath, 'utf-8')
    const parsed = YAML.parse(content)
    const result = automationStateSchema.safeParse(parsed)
    expect(result.success).toBe(true)
    if (result.success) {
      expect(result.data.task).toBe('compliance-evidence-synchronizer')
      expect(result.data.schemaVersion).toBe(1)
      expect(result.data.outcome).toBe('CHANGES_APPLIED')
    }
  })

  it('report contains framework sections and matches state', () => {
    const reportContent = readFileSync(reportPath, 'utf-8')
    expect(reportContent).toContain('# Legal and Compliance Evidence Synchronizer Report')
    expect(reportContent).toContain('## Execution Result')
    expect(reportContent).toContain('## Scope Inspected')
    expect(reportContent).toContain('## Changes Applied')
    expect(reportContent).toContain('## Evidence Table')
    expect(reportContent).toContain('## Validation Table')
    expect(reportContent).toContain('## Unresolved Findings')
    expect(reportContent).toContain('## Blockers')
    expect(reportContent).toContain('## Automation State')
    expect(reportContent).toContain('## Risk Assessment')
    expect(reportContent).toContain('## Human Review Notes')
    expect(reportContent).toContain('`CHANGES_APPLIED`')
  })
})
