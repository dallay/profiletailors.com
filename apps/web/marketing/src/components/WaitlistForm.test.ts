import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

function renderWaitlistFormMarkup(): HTMLFormElement {
  const componentPath = resolve(process.cwd(), 'src/components/WaitlistForm.astro')
  const source = readFileSync(componentPath, 'utf8')
  const formMarkup = source.match(/<form[\s\S]*?<\/form>/)?.[0]

  if (!formMarkup) {
    throw new Error('Waitlist form markup was not found')
  }

  document.body.innerHTML = formMarkup

  const form = document.querySelector<HTMLFormElement>('[data-waitlist-form]')
  if (!form) {
    throw new Error('Waitlist form did not render')
  }

  return form
}

describe('WaitlistForm accessibility', () => {
  it('associates the visible email label with the email input', () => {
    const form = renderWaitlistFormMarkup()
    const emailInput = form.querySelector<HTMLInputElement>('[data-waitlist-email]')
    const emailLabel = form.querySelector<HTMLLabelElement>('label[for="waitlist-email"]')

    expect(emailInput).not.toBeNull()
    expect(emailLabel).not.toBeNull()
    expect(emailLabel?.htmlFor).toBe(emailInput?.id)
    expect(emailLabel?.control).toBe(emailInput)
  })

  it('does not associate the email label with another waitlist control', () => {
    const form = renderWaitlistFormMarkup()
    const emailLabel = form.querySelector<HTMLLabelElement>('label[for="waitlist-email"]')
    const earlyAccessConsent = form.querySelector<HTMLInputElement>(
      '[data-waitlist-consent-early]',
    )

    expect(emailLabel?.control).not.toBe(earlyAccessConsent)
    expect(emailLabel?.htmlFor).not.toBe(earlyAccessConsent?.id)
  })
})
