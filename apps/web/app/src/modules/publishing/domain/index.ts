/**
 * Domain layer barrel
 *
 * Pure domain types and business logic — NO framework dependencies.
 */

export type { Channel, ChannelProvider } from './channel'
export type {
  BulkJobResult,
  BulkJobStatus,
  BulkRowResult,
  BulkRowScheduleStatus,
  BulkRowStatus,
  BulkRowValidation,
  BulkTemplate,
  BulkTemplatesResult,
  ImportError,
  ParsedCsvResult,
  ParsedCsvRow,
  ScheduleBulkResult,
  ValidateBulkResult,
} from './bulk'
export { BULK_CANONICAL_HEADER } from './bulk'
