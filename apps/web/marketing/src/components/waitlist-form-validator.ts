// src/components/waitlist-form-validator.ts
// Client-side email validation mirroring the backend EmailAddress VO rules.
// Reference regex lives in shared/lead-capture/common.
const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function isValidEmail(value: string): boolean {
  const trimmed = value.trim()
  if (trimmed.length === 0) return false
  if (trimmed.length > 320) return false
  return EMAIL_REGEX.test(trimmed)
}
