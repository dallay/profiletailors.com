import { ref, computed } from 'vue'
import { usePublishingStore } from '@modules/publishing/infrastructure/publishing.store'
import { useWorkspaceStore } from '@modules/workspace/infrastructure/workspace.store'
import type { BulkJobResult, BulkTemplatesResult, ValidateBulkResult, ScheduleBulkResult } from '@modules/publishing/domain/bulk'

export function useBulkImport() {
  const publishing = usePublishingStore()
  const workspace = useWorkspaceStore()

  const isValidating = ref(false)
  const isScheduling = ref(false)
  const isPolling = ref(false)
  const validateResult = ref<ValidateBulkResult | null>(null)
  const scheduleResult = ref<ScheduleBulkResult | null>(null)
  const jobResult = ref<BulkJobResult | null>(null)
  const templates = ref<BulkTemplatesResult | null>(null)
  const error = ref<string | null>(null)

  const hasValidationErrors = computed(() => validateResult.value?.rows.some((r) => r.status === 'INVALID') ?? false)

  const invalidRows = computed(() => validateResult.value?.rows.filter((r) => r.status === 'INVALID') ?? [])

  async function validate(csvText: string): Promise<ValidateBulkResult> {
    isValidating.value = true
    error.value = null
    try {
      const result = await publishing.validateBulk(csvText)
      validateResult.value = result
      return result
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Validate failed'
      throw e
    } finally {
      isValidating.value = false
    }
  }

  async function schedule(csvText: string, csvHash?: string): Promise<ScheduleBulkResult> {
    isScheduling.value = true
    error.value = null
    try {
      const result = await publishing.scheduleBulk(csvText, csvHash)
      scheduleResult.value = result
      return result
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Schedule failed'
      throw e
    } finally {
      isScheduling.value = false
    }
  }

  async function fetchJob(jobId: string): Promise<BulkJobResult> {
    isPolling.value = true
    try {
      const result = await publishing.fetchBulkJob(jobId)
      jobResult.value = result
      return result
    } finally {
      isPolling.value = false
    }
  }

  async function pollJob(jobId: string, intervalMs = 1500, maxAttempts = 20): Promise<BulkJobResult> {
    for (let i = 0; i < maxAttempts; i++) {
      const result = await fetchJob(jobId)
      if (result.status === 'SCHEDULED' || result.status === 'PARTIAL' || result.status === 'FAILED') return result
      await new Promise((r) => setTimeout(r, intervalMs))
    }
    return jobResult.value as BulkJobResult
  }

  async function loadTemplates(): Promise<BulkTemplatesResult> {
    const result = await publishing.fetchBulkTemplates()
    templates.value = result
    return result
  }

  async function downloadTemplateCsv(templateId: string): Promise<string> {
    return publishing.fetchBulkTemplateCsv(templateId)
  }

  function workspaceIdOrThrow(): string {
    const id = workspace.activeWorkspaceId
    if (!id) throw new Error('Select a workspace before bulk import.')
    return id
  }

  return {
    isValidating,
    isScheduling,
    isPolling,
    validateResult,
    scheduleResult,
    jobResult,
    templates,
    error,
    hasValidationErrors,
    invalidRows,
    validate,
    schedule,
    fetchJob,
    pollJob,
    loadTemplates,
    downloadTemplateCsv,
    workspaceIdOrThrow,
  }
}
