export const BULK_CANONICAL_HEADER = 'bodyText,scheduledFor,timezone,media_urls,hashtags'

export type BulkRowStatus = 'VALID' | 'INVALID'
export type BulkJobStatus = 'SCHEDULING' | 'SCHEDULED' | 'PARTIAL' | 'FAILED'
export type BulkRowScheduleStatus = 'SCHEDULED' | 'FAILED'

export type ImportError = {
  code: string
  message: string
}

export type BulkRowValidation = {
  rowIndex: number
  status: BulkRowStatus
  errors: ImportError[]
  bodyText?: string | null
  scheduledFor?: string | null
  mediaUrls?: string[]
  hasConflict?: boolean
}

export type ValidateBulkResult = {
  rows: BulkRowValidation[]
}

export type BulkRowResult = {
  rowIndex: number
  status: BulkRowScheduleStatus
  errors: ImportError[]
  publicationId?: string | null
}

export type ScheduleBulkResult = {
  jobId: string
  totalRows: number
  scheduledCount: number
  failedCount: number
  rows: BulkRowResult[]
}

export type BulkJobResult = {
  jobId: string
  status: BulkJobStatus
  totalRows: number
  scheduledCount: number
  failedCount: number
  rows: BulkRowResult[]
}

export type BulkTemplate = {
  id: string
  name: string
  description: string
}

export type BulkTemplatesResult = {
  templates: BulkTemplate[]
}

export type ParsedCsvRow = {
  rowIndex: number
  bodyText: string
  scheduledFor: string
  timezone: string
  mediaUrls: string
  hashtags: string
}

export type ParsedCsvResult = {
  header: string[]
  rows: ParsedCsvRow[]
  headerValid: boolean
}
