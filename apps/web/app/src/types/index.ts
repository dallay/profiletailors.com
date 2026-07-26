/**
 * Centralized type definitions for the frontend application.
 * Re-export common types from modules and shared utilities.
 */

// Shared composable types
export type { ValidationRule } from '@shared/composables'

// Common utility types
export type Nullable<T> = T | null
export type Optional<T> = T | undefined
export type AsyncFunction<T = void> = () => Promise<T>
export type EventHandler<T = Event> = (event: T) => void

// Form and validation types
export interface FormState<T extends Record<string, unknown>> {
  values: T
  errors: Record<keyof T, string | undefined>
  touched: Set<keyof T>
  isDirty: boolean
  isSubmitting: boolean
}

export interface PaginationState {
  currentPage: number
  pageSize: number
  totalItems: number
  totalPages: number
}

// API response types
export interface PaginatedResponse<T> {
  items: T[]
  pagination: PaginationState
}
