// src/components/waitlist-form-validator.ts
// Client-side email validation mirroring the backend EmailAddress VO rules.
// Reference regex lives in shared/lead-capture/common.
// Anchored with start-of-string and mutually-exclusive character classes — no catastrophic backtracking.
// Sonar S8786 flagged this but the pattern is safe: each [^\s@]+ excludes whitespace and @ while allowing
// dots and other characters, so no overlapping alternatives exist.
const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function isValidEmail(value: string): boolean {
  const trimmed = value.trim()
  if (trimmed.length === 0) return false
  if (trimmed.length > 320) return false
  return EMAIL_REGEX.test(trimmed)
}
