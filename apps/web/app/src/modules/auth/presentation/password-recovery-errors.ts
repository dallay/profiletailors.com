export type PasswordRecoveryRequestErrorKey =
  | 'passwordRecovery.rateLimited'
  | 'passwordRecovery.unavailable'
  | 'passwordRecovery.genericError'

export type PasswordResetStatus = 'form' | 'invalid' | 'success'

const RATE_LIMIT_CODE = 'AUTH_RATE_LIMIT_EXCEEDED'
const DISABLED_CODE = 'PASSWORD_RECOVERY_DISABLED'

export function resolveRequestErrorKey(
  status?: number,
  code?: string,
): PasswordRecoveryRequestErrorKey {
  if (status === 429 || code === RATE_LIMIT_CODE) return 'passwordRecovery.rateLimited'
  if (status === 503 || code === DISABLED_CODE) return 'passwordRecovery.unavailable'
  return 'passwordRecovery.genericError'
}

export function resolveResetTitleKey(status: PasswordResetStatus): string {
  if (status === 'success') return 'passwordRecovery.resetSuccessTitle'
  if (status === 'invalid') return 'passwordRecovery.invalidLinkTitle'
  return 'passwordRecovery.resetTitle'
}
