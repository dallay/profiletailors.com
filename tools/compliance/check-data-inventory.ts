#!/usr/bin/env node

import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import YAML from 'yaml'
import { z } from 'zod'

const purposeSchema = z.object({
  purpose: z.string().min(1),
  role: z.enum(['controller', 'processor']),
  legal_basis: z.object({
    type: z.enum([
      'consent', 'contract', 'legal_obligation', 'vital_interest',
      'public_task', 'legitimate_interest', 'controller_instruction',
    ]),
    reference: z.string(),
  }),
})

const recipientSchema = z.object({
  name: z.string(),
  type: z.enum(['controller', 'processor', 'none']),
  location_type: z.enum(['country', 'region', 'unknown']),
  location: z.string(),
  agreement_reference: z.string(),
  activation_status: z.enum(['selected', 'configurable', 'conditional', 'not_selected']).optional(),
  agreement_status: z.enum(['verified', 'unverified', 'not_applicable']).optional(),
})

const evidenceReferenceSchema = z.object({
  path: z.string(),
  reason: z.string(),
})

const processingActivitySchema = z.object({
  id: z.string().regex(/^pa-\d{3}$/),
  name: z.string().min(1),
  purposes: z.array(purposeSchema).min(1),
  personal_data_categories: z.array(z.string()).min(1),
  data_subjects: z.string().optional(),
  recipients: z.array(recipientSchema),
  retention: z.object({
    trigger: z.string(),
    duration: z.string(),
    action: z.enum(['delete', 'anonymize', 'archive']),
    exceptions: z.array(z.string()).optional(),
    control_status: z.enum(['implemented', 'partial', 'not_implemented']).optional(),
    evidence: z.array(z.string()).optional(),
  }),
  security_measures: z.array(z.string()).optional(),
  evidence_references: z.array(evidenceReferenceSchema),
  evidence_status: z.enum(['verified', 'partial', 'not_evidenced']).optional(),
  legal_review_status: z.enum(['pending', 'approved']).optional(),
  notes: z.string().optional(),
})

const inventorySchema = z.object({
  schema_version: z.string().regex(/^\d+\.\d+$/, 'Must be major.minor format (e.g. "1.0")'),
  processing_entity: z.string().min(1),
  DPO_contact: z.string().nullable().optional(),
  status: z.enum(['draft', 'active']).optional(),
  production_release_status: z.enum(['blocked', 'approved']).optional(),
  last_verified_on: z.string().date().optional(),
  processing_activities: z.array(processingActivitySchema).min(1),
})

export type DataInventory = z.infer<typeof inventorySchema>

export interface ValidationResult {
  valid: boolean
  errors: string[]
}

/**
 * Validates a YAML data inventory against the required inventory schema.
 *
 * @param yamlContent - The YAML content to parse and validate
 * @returns A validation result containing any schema or YAML parsing errors
 */
export function validateDataInventory(yamlContent: string): ValidationResult {
  try {
    const parsed = YAML.parse(yamlContent)
    const result = inventorySchema.safeParse(parsed)

    if (result.success) {
      return { valid: true, errors: [] }
    }

    return {
      valid: false,
      errors: result.error.issues.map(
        (issue) => `${issue.path.join('.')}: ${issue.message}`,
      ),
    }
  } catch (error) {
    return {
      valid: false,
      errors: [`YAML parse error: ${(error as Error).message}`],
    }
  }
}

/**
 * Validates the configured data inventory file and reports the result.
 *
 * Exits with status code `1` if the file cannot be read or fails validation.
 */
function main(): void {
  const yamlPath = process.argv[2]
    ? resolve(process.cwd(), process.argv[2])
    : resolve(process.cwd(), 'docs/compliance/data-inventory.yaml')
  let yamlContent: string

  try {
    yamlContent = readFileSync(yamlPath, 'utf-8')
  } catch (error) {
    console.error(`Error reading ${yamlPath}: ${(error as Error).message}`)
    process.exit(1)
  }

  const result = validateDataInventory(yamlContent)

  if (!result.valid) {
    console.error('Data inventory validation FAILED:')
    for (const error of result.errors) {
      console.error(`  - ${error}`)
    }
    process.exit(1)
  }

  console.log('Data inventory validation PASSED')
}

if (process.argv[1] && process.argv[1] === fileURLToPath(import.meta.url)) {
  main()
}
