import { describe, it, expect } from 'vitest'
import {
  buildWaitlistPayload,
  WAITLIST_PAYLOAD_KEYS,
  type WaitlistFormValues,
  WAITLIST_KEY_DEFAULT,
  WAITLIST_SOURCE_DEFAULT,
  WAITLIST_FORM_ID_DEFAULT,
} from './waitlist-form'

const baseValues: WaitlistFormValues = {
  email: 'user@example.com',
  source: WAITLIST_SOURCE_DEFAULT,
  formId: WAITLIST_FORM_ID_DEFAULT,
  locale: 'en',
  consent: { earlyAccess: true, marketing: false },
  metadata: { utm_source: 'twitter', page_path: '/' },
}

describe('buildWaitlistPayload', (): void => {
  it('emits the documented payload shape for the backend POST contract', (): void => {
    const payload = buildWaitlistPayload(baseValues)

    expect(payload).toEqual({
      email: 'user@example.com',
      source: WAITLIST_SOURCE_DEFAULT,
      formId: WAITLIST_FORM_ID_DEFAULT,
      locale: 'en',
      consent: { earlyAccess: true, marketing: false },
      metadata: { utm_source: 'twitter', page_path: '/' },
    })
  })

  it('keys the payload against the documented contract fields', () => {
    const payload = buildWaitlistPayload(baseValues)
    const keys = Object.keys(payload).sort()
    expect(keys).toEqual([...WAITLIST_PAYLOAD_KEYS].sort())
  })

  it('trims whitespace around the email before submission', () => {
    const payload = buildWaitlistPayload({ ...baseValues, email: '  user@example.com  ' })
    expect(payload.email).toBe('user@example.com')
  })

  it('omits the metadata field when the caller supplies empty metadata', () => {
    const payload = buildWaitlistPayload({ ...baseValues, metadata: {} })
    expect(payload).not.toHaveProperty('metadata')
  })

  it('drops metadata keys outside the backend whitelist', () => {
    const payload = buildWaitlistPayload({
      ...baseValues,
      metadata: { utm_source: 'twitter', random_internal_id: 'x' },
    })
    expect(payload.metadata).toEqual({ utm_source: 'twitter' })
  })

  it('drops marketing=true when the user did not opt in', () => {
    const payload = buildWaitlistPayload({
      ...baseValues,
      consent: { earlyAccess: true, marketing: false },
    })
    expect(payload.consent.marketing).toBe(false)
  })

  it('emits marketing=true when the user opted in', () => {
    const payload = buildWaitlistPayload({
      ...baseValues,
      consent: { earlyAccess: true, marketing: true },
    })
    expect(payload.consent.marketing).toBe(true)
  })

  it('preserves the configured WAITLIST_KEY for the future backend URL helper', () => {
    expect(WAITLIST_KEY_DEFAULT).toBe('profile-tailors-launch')
  })
})
