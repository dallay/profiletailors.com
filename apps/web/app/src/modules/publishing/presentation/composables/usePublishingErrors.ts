import { computed, toValue, type MaybeRefOrGetter } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ApiError } from '@modules/auth/infrastructure/auth-api'
import type { Publication } from '@modules/publishing/infrastructure/publishing.store'

const FAILURE_COPY_KEYS = {
  MEDIA_NOT_FOUND: 'mediaNotFound',
  MEDIA_UNAVAILABLE: 'mediaUnavailable',
  PROVIDER_VALIDATION_FAILED: 'providerValidationFailed',
  PROVIDER_RATE_LIMITED: 'providerRateLimited',
  PROVIDER_UNAVAILABLE: 'providerUnavailable',
  ACCOUNT_RECONNECT_REQUIRED: 'accountReconnectRequired',
  ACCOUNT_UNAVAILABLE: 'accountUnavailable',
  PUBLISHING_FAILED: 'publishingFailed',
} as const

const FAILURE_COPY_I18N_KEYS = {
  mediaNotFound: {
    label: 'postDetail.failure.mediaNotFound.label',
    explanation: 'postDetail.failure.mediaNotFound.explanation',
    action: 'postDetail.failure.mediaNotFound.action',
  },
  mediaUnavailable: {
    label: 'postDetail.failure.mediaUnavailable.label',
    explanation: 'postDetail.failure.mediaUnavailable.explanation',
    action: 'postDetail.failure.mediaUnavailable.action',
  },
  providerValidationFailed: {
    label: 'postDetail.failure.providerValidationFailed.label',
    explanation: 'postDetail.failure.providerValidationFailed.explanation',
    action: 'postDetail.failure.providerValidationFailed.action',
  },
  providerRateLimited: {
    label: 'postDetail.failure.providerRateLimited.label',
    explanation: 'postDetail.failure.providerRateLimited.explanation',
    action: 'postDetail.failure.providerRateLimited.action',
  },
  providerUnavailable: {
    label: 'postDetail.failure.providerUnavailable.label',
    explanation: 'postDetail.failure.providerUnavailable.explanation',
    action: 'postDetail.failure.providerUnavailable.action',
  },
  accountReconnectRequired: {
    label: 'postDetail.failure.accountReconnectRequired.label',
    explanation: 'postDetail.failure.accountReconnectRequired.explanation',
    action: 'postDetail.failure.accountReconnectRequired.action',
  },
  accountUnavailable: {
    label: 'postDetail.failure.accountUnavailable.label',
    explanation: 'postDetail.failure.accountUnavailable.explanation',
    action: 'postDetail.failure.accountUnavailable.action',
  },
  publishingFailed: {
    label: 'postDetail.failure.publishingFailed.label',
    explanation: 'postDetail.failure.publishingFailed.explanation',
    action: 'postDetail.failure.publishingFailed.action',
  },
  unknown: {
    label: 'postDetail.failure.unknown.label',
    explanation: 'postDetail.failure.unknown.explanation',
    action: 'postDetail.failure.unknown.action',
  },
} as const

const ACTION_REASON_KEYS = {
  unauthorized: 'postDetail.actionErrors.reasons.unauthorized',
  notFound: 'postDetail.actionErrors.reasons.notFound',
  stateConflict: 'postDetail.actionErrors.reasons.stateConflict',
  validation: 'postDetail.actionErrors.reasons.validation',
  temporarilyUnavailable: 'postDetail.actionErrors.reasons.temporarilyUnavailable',
  unknown: 'postDetail.actionErrors.reasons.unknown',
} as const

const ACTION_OPERATION_KEYS = {
  retry: 'postDetail.actionErrors.operations.retry',
  delete: 'postDetail.actionErrors.operations.delete',
  reschedule: 'postDetail.actionErrors.operations.reschedule',
} as const

type FailureCopyKey = keyof typeof FAILURE_COPY_I18N_KEYS
type ActionOperation = keyof typeof ACTION_OPERATION_KEYS
type ActionReason = keyof typeof ACTION_REASON_KEYS

function mapFailureCopyKey(value: string | undefined): FailureCopyKey {
  if (!value || !Object.hasOwn(FAILURE_COPY_KEYS, value)) return 'unknown'
  return FAILURE_COPY_KEYS[value as keyof typeof FAILURE_COPY_KEYS]
}

function isApiError(error: unknown): error is Error & ApiError {
  return typeof error === 'object' && error !== null
}

function mapActionReason(error: unknown): ActionReason {
  if (!isApiError(error)) return 'unknown'
  const code = error.errorCode ?? error.code
  if (code === 'UNAUTHORIZED' || code === 'FORBIDDEN') return 'unauthorized'
  if (code === 'NOT_FOUND') return 'notFound'
  if (code === 'STATE_CONFLICT' || code === 'CONFLICT') return 'stateConflict'
  if (code === 'VALIDATION_FAILED' || code === 'VALIDATION_ERROR') return 'validation'
  if (code === 'RATE_LIMITED' || code === 'SERVICE_UNAVAILABLE') return 'temporarilyUnavailable'
  if (error instanceof Error && error.status === undefined) return 'temporarilyUnavailable'
  switch (error.status) {
    case 401:
    case 403:
      return 'unauthorized'
    case 404:
      return 'notFound'
    case 409:
      return 'stateConflict'
    case 400:
    case 422:
      return 'validation'
    case 429:
      return 'temporarilyUnavailable'
    default:
      return typeof error.status === 'number' && error.status >= 500
        ? 'temporarilyUnavailable'
        : 'unknown'
  }
}

export function usePublishingErrors(publication: MaybeRefOrGetter<Publication | null>) {
  const { t } = useI18n()
  const failureCopy = computed(() => {
    const currentPublication = toValue(publication)
    if (!currentPublication) return null
    if (currentPublication.status === 'BLOCKED') {
      return FAILURE_COPY_I18N_KEYS[mapFailureCopyKey(currentPublication.blockedReason)]
    }
    if (currentPublication.status === 'FAILED') {
      return FAILURE_COPY_I18N_KEYS[mapFailureCopyKey(currentPublication.errorCode)]
    }
    return null
  })

  function actionErrorMessage(error: unknown, operation: ActionOperation): string {
    const reason = mapActionReason(error)
    return t('postDetail.actionErrors.message', {
      reason: t(ACTION_REASON_KEYS[reason]),
      operation: t(ACTION_OPERATION_KEYS[operation]),
    })
  }

  return { failureCopy, actionErrorMessage }
}
