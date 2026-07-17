import { describe, it, expect } from 'vitest'
import { validateDataInventory } from '../check-data-inventory.js'

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
})
