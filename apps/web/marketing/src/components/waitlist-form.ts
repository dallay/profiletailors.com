// src/components/waitlist-form.ts
// Pure builder for the lead-capture waitlist POST payload.
// Kept framework-free so Vitest can exercise it under jsdom.

export const WAITLIST_KEY_DEFAULT = 'profile-tailors-launch'
export const WAITLIST_SOURCE_DEFAULT = 'marketing-site'
export const WAITLIST_FORM_ID_DEFAULT = 'waitlist-hero'

export const WAITLIST_PAYLOAD_KEYS: readonly string[] = [
  'email',
  'source',
  'formId',
  'locale',
  'consent',
  'metadata',
] as const

export const WAITLIST_METADATA_WHITELIST: readonly string[] = [
  'utm_source',
  'utm_medium',
  'utm_campaign',
  'utm_content',
  'utm_term',
  'referrer',
  'page_path',
  'user_agent_family',
  'consent_version',
] as const

export interface WaitlistFormValues {
  email: string
  source?: string
  formId?: string
  locale?: string
  consent: { earlyAccess: boolean; marketing: boolean }
  metadata?: Record<string, string>
}

export interface WaitlistPayload {
  email: string
  source: string
  formId: string
  locale: string
  consent: { earlyAccess: boolean; marketing: boolean }
  metadata?: Record<string, string>
}

export function buildWaitlistPayload(values: WaitlistFormValues): WaitlistPayload {
  const trimmedEmail = values.email.trim()

  const basePayload: WaitlistPayload = {
    email: trimmedEmail,
    source: values.source ?? WAITLIST_SOURCE_DEFAULT,
    formId: values.formId ?? WAITLIST_FORM_ID_DEFAULT,
    locale: values.locale ?? 'en',
    consent: {
      earlyAccess: values.consent.earlyAccess,
      marketing: values.consent.marketing,
    },
  }

  const filteredMetadata = filterMetadata(values.metadata)
  if (filteredMetadata) {
    basePayload.metadata = filteredMetadata
  }
  return basePayload
}

function filterMetadata(metadata: Record<string, string> | undefined): Record<string, string> | undefined {
  if (!metadata) return undefined
  const filtered: Record<string, string> = {}
  let count = 0
  for (const key of WAITLIST_METADATA_WHITELIST) {
    const value = metadata[key]
    if (value === undefined) continue
    filtered[key] = value
    count += 1
  }
  return count === 0 ? undefined : filtered
}
