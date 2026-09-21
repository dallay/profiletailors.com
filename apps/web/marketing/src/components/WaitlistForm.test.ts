import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const source = readFileSync(resolve(process.cwd(), 'src/components/WaitlistForm.astro'), 'utf8')

describe('WaitlistForm accessibility', () => {
  it('binds the email label to a unique form-scoped id', () => {
    expect(source).toContain('formId}-email')
    expect(source).toContain('for={emailId}')
    expect(source).toContain('id={emailId}')
  })

  it('does not render a redundant early-access consent checkbox', () => {
    expect(source).not.toContain('data-waitlist-consent-early')
    expect(source).toContain('earlyAccess: true')
  })

  it('exposes a placeholder on the email input', () => {
    expect(source).toContain('placeholder={wl.emailInput.placeholder}')
  })

  it('shares with a button instead of an http href', () => {
    expect(source).toContain('data-waitlist-share')
    expect(source).toContain('type="button"')
    expect(source).not.toContain('share.href')
  })
})
