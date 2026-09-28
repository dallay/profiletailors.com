import { afterEach, beforeEach, describe, it, expect } from 'vitest'
import {
  mkdirSync,
  mkdtempSync,
  readFileSync,
  realpathSync,
  rmSync,
  symlinkSync,
  writeFileSync,
} from 'node:fs'
import { tmpdir } from 'node:os'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { validateDataInventory, resolveDataInventoryPath } from '../check-data-inventory.js'

const repositoryRoot = fileURLToPath(new URL('../../../', import.meta.url))
const dataInventoryPath = resolve(repositoryRoot, 'docs/compliance/data-inventory.yaml')

describe('validateDataInventory', () => {
  it('returns valid for a correct minimal inventory', () => {
    const yaml = `
schema_version: "1.0"
processing_entity: Test Entity
processing_activities:
  - id: pa-001
    name: Test activity
    purposes:
      - purpose: Testing
        role: controller
        legal_basis:
          type: contract
          reference: GDPR Art. 6(1)(b)
    personal_data_categories:
      - Email address
    data_subjects: Test users
    recipients:
      - name: Internal
        type: none
        location_type: region
        location: EEA
        agreement_reference: not_applicable
    retention:
      trigger: account_deleted
      duration: P30D
      action: delete
    evidence_references:
      - path: src/test.ts
        reason: Test evidence
`
    const result = validateDataInventory(yaml)
    expect(result.valid).toBe(true)
    expect(result.errors).toHaveLength(0)
  })

  it('returns error for missing required fields', () => {
    const yaml = `
schema_version: "1.0"
processing_entity: Test
processing_activities:
  - id: pa-001
    name: Test
`
    const result = validateDataInventory(yaml)
    expect(result.valid).toBe(false)
    expect(result.errors.length).toBeGreaterThan(0)
  })

  it('returns error for invalid role value', () => {
    const yaml = `
schema_version: "1.0"
processing_entity: Test
processing_activities:
  - id: pa-001
    name: Test
    purposes:
      - purpose: Test
        role: invalid_role
        legal_basis:
          type: contract
          reference: GDPR Art. 6(1)(b)
    personal_data_categories:
      - Email
    data_subjects: Users
    recipients:
      - name: Internal
        type: none
        location_type: region
        location: EEA
        agreement_reference: not_applicable
    retention:
      trigger: account_deleted
      duration: P30D
      action: delete
    evidence_references: []
`
    const result = validateDataInventory(yaml)
    expect(result.valid).toBe(false)
    expect(result.errors.some((e) => e.includes('role'))).toBe(true)
  })

  it('accepts evidence and release-control metadata', () => {
    const yaml = `
schema_version: "2.0"
processing_entity: UNRESOLVED
status: draft
production_release_status: blocked
last_verified_on: 2026-07-17
processing_activities:
  - id: pa-001
    name: Test activity
    purposes:
      - purpose: Testing
        role: controller
        legal_basis:
          type: contract
          reference: Pending legal approval
    personal_data_categories: [Email]
    recipients:
      - name: Candidate provider
        type: processor
        location_type: unknown
        location: Not selected
        agreement_reference: Not executed
        activation_status: not_selected
        agreement_status: unverified
    retention:
      trigger: account_deleted
      duration: Not established
      action: delete
      control_status: not_implemented
      evidence: []
    evidence_references: []
    evidence_status: partial
    legal_review_status: pending
`
    const result = validateDataInventory(yaml)
    expect(result.valid).toBe(true)
    expect(result.errors).toHaveLength(0)
  })

  it('returns error for invalid YAML syntax', () => {
    const result = validateDataInventory('key: [unclosed')
    expect(result.valid).toBe(false)
    expect(result.errors.some((e) => e.includes('YAML parse error'))).toBe(true)
  })

  it('validates actual repo data inventory docs/compliance/data-inventory.yaml successfully', () => {
    const yamlContent = readFileSync(dataInventoryPath, 'utf-8')
    const result = validateDataInventory(yamlContent)
    expect(result.errors).toEqual([])
    expect(result.valid).toBe(true)
  })
})

describe('resolveDataInventoryPath', () => {
  let fixtureRoot: string
  let cwd: string
  let outsideFile: string

  beforeEach(() => {
    fixtureRoot = realpathSync(mkdtempSync(resolve(tmpdir(), 'data-inventory-')))
    cwd = resolve(fixtureRoot, 'workspace')
    mkdirSync(resolve(cwd, 'docs/compliance'), { recursive: true })
    writeFileSync(resolve(cwd, 'docs/compliance/data-inventory.yaml'), '')
    outsideFile = resolve(fixtureRoot, 'outside.yaml')
    writeFileSync(outsideFile, '')
  })

  afterEach(() => {
    rmSync(fixtureRoot, { recursive: true, force: true })
  })

  it('resolves default path when no path argument is provided', () => {
    const resolved = resolveDataInventoryPath(undefined, cwd)
    expect(resolved).toBe(resolve(cwd, 'docs/compliance/data-inventory.yaml'))
  })

  it('resolves valid relative path within cwd', () => {
    const resolved = resolveDataInventoryPath('docs/compliance/data-inventory.yaml', cwd)
    expect(resolved).toBe(resolve(cwd, 'docs/compliance/data-inventory.yaml'))
  })

  it.each(['../outside.yaml', '..'])('rejects traversal outside cwd: %s', (inputPath) => {
    expect(() => resolveDataInventoryPath(inputPath, cwd)).toThrow(
      /resolves outside the allowed root working directory/,
    )
  })

  it('throws error when path traverses outside cwd via absolute path', () => {
    expect(() => resolveDataInventoryPath(outsideFile, cwd)).toThrow(
      /resolves outside the allowed root working directory/,
    )
  })

  it('rejects an in-root symlink to an outside file', () => {
    symlinkSync(outsideFile, resolve(cwd, 'inventory.yaml'), 'file')
    expect(() => resolveDataInventoryPath('inventory.yaml', cwd)).toThrow(
      /resolves outside the allowed root working directory/,
    )
  })

  it('rejects a path through an in-root symlink to an outside directory', () => {
    symlinkSync(fixtureRoot, resolve(cwd, 'outside'), 'dir')
    expect(() => resolveDataInventoryPath('outside/outside.yaml', cwd)).toThrow(
      /resolves outside the allowed root working directory/,
    )
  })

  it('accepts an in-root filename beginning with two dots', () => {
    const target = resolve(cwd, '..inventory.yaml')
    writeFileSync(target, '')
    expect(resolveDataInventoryPath('..inventory.yaml', cwd)).toBe(target)
  })

  it('resolves paths against a canonical working directory', () => {
    const linkedCwd = resolve(fixtureRoot, 'linked-workspace')
    symlinkSync(cwd, linkedCwd, 'dir')
    expect(resolveDataInventoryPath(undefined, linkedCwd)).toBe(
      resolve(cwd, 'docs/compliance/data-inventory.yaml'),
    )
  })

  it('returns the canonical target for an in-root symlink', () => {
    const target = resolve(cwd, 'docs/compliance/data-inventory.yaml')
    symlinkSync(target, resolve(cwd, 'inventory.yaml'), 'file')
    expect(resolveDataInventoryPath('inventory.yaml', cwd)).toBe(target)
  })
})
