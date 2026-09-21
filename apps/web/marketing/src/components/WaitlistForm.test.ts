import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it, vi } from 'vitest'
import { bindWaitlistShare, readWaitlistShareAttributes } from './waitlist-share'

const source = readFileSync(resolve(process.cwd(), 'src/components/WaitlistForm.astro'), 'utf8')
const interactiveSource = readFileSync(resolve(process.cwd(), 'src/components/InteractiveWaitlistForm.astro'), 'utf8')
const clientSource = readFileSync(resolve(process.cwd(), 'src/components/useWaitlistForm.ts'), 'utf8')

describe('WaitlistForm accessibility', () => {
  it('binds the email label to a unique form-scoped id', () => {
    expect(source).toContain('formId}-email')
    expect(source).toContain('for={emailId}')
    expect(source).toContain('id={emailId}')
  })

  it('does not render a redundant early-access consent checkbox', () => {
    expect(source).not.toContain('data-waitlist-consent-early')
    expect(clientSource).toContain('earlyAccess: true')
  })

  it('keeps the static form free of page-global behavior', () => {
    expect(source).not.toContain('<script>')
    expect(interactiveSource).toContain('client:load')
    expect(clientSource).toContain('bindWaitlistShare')
    expect(clientSource).toContain('readWaitlistShareAttributes')
    expect(clientSource).not.toContain('document.querySelectorAll')
  })

  it('exposes a placeholder on the email input', () => {
    expect(source).toContain('placeholder={wl.emailInput.placeholder}')
  })

  it('shares with a button instead of an http href', () => {
    expect(source).toContain('data-waitlist-share')
    expect(source).toContain('type="button"')
    expect(source).not.toContain('share.href')
  })

  it('shows the copied result after a successful share click', async () => {
    document.body.innerHTML = `
      <form
        data-waitlist-form
        data-waitlist-share-url="https://profiletailors.com/"
        data-waitlist-share-title="Profile Tailors waitlist"
        data-waitlist-share-copied="Link copied"
      >
        <div data-waitlist-success>
          <button type="button" data-waitlist-share>Share the waitlist</button>
        </div>
      </form>
    `
    const form = document.querySelector('form')
    const share = document.querySelector<HTMLButtonElement>('[data-waitlist-share]')
    if (!form || !share) {
      throw new Error('Waitlist share markup was not found')
    }

    const writeText = vi.fn().mockResolvedValue(undefined)
    bindWaitlistShare(share, readWaitlistShareAttributes(form), { writeText })
    share.click()

    await vi.waitFor(() => {
      expect(share.textContent).toBe('Link copied')
    })
    expect(writeText).toHaveBeenCalledWith('https://profiletailors.com/')
  })
})
