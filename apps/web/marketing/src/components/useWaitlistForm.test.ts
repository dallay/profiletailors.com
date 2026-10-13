import { createApp, defineComponent, h, ref, nextTick } from 'vue'
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { flushPromises } from '@vue/test-utils'
import { useWaitlistForm, normalizeWaitlistApiBase } from './useWaitlistForm'

function requireElement<T extends Element>(root: ParentNode, selector: string): T {
  const element = root.querySelector<T>(selector)
  if (!element) throw new Error(`Missing test element: ${selector}`)
  return element
}

describe('useWaitlistForm', () => {
  const originalLocationDescriptor = Object.getOwnPropertyDescriptor(window, 'location')
  let rootEl: HTMLElement
  let formEl: HTMLFormElement
  let emailInput: HTMLInputElement
  let marketingConsent: HTMLInputElement
  let errorEl: HTMLElement
  let successEl: HTMLElement
  let submitBtn: HTMLButtonElement

  beforeEach(() => {
    vi.restoreAllMocks()
    document.body.innerHTML = ''

    rootEl = document.createElement('div')
    rootEl.innerHTML = `
      <form
        data-waitlist-form
        data-waitlist-key="pt-early"
        data-waitlist-source="hero-section"
        data-waitlist-form-id="hero-waitlist"
        data-waitlist-locale="es"
        data-waitlist-api-base="https://api.profiletailors.com/"
        data-waitlist-messages='{"validEmail":"Email inválido","tooManyRequests":"Demasiadas peticiones","genericError":"Error al registrar","success":"Registrado exitosamente"}'
        data-waitlist-share-url="https://profiletailors.com"
        data-waitlist-share-title="Join Waitlist"
        data-waitlist-share-copied="Copiado"
      >
        <input type="email" data-waitlist-email value="" />
        <input type="checkbox" data-waitlist-consent-marketing checked />
        <div data-waitlist-error class="hidden"></div>
        <div data-waitlist-success class="hidden">
          <button type="button" data-waitlist-share>Share</button>
        </div>
        <button type="submit" data-waitlist-submit>Enviar</button>
      </form>
    `
    document.body.appendChild(rootEl)

    formEl = requireElement<HTMLFormElement>(rootEl, '[data-waitlist-form]')
    emailInput = requireElement<HTMLInputElement>(rootEl, '[data-waitlist-email]')
    marketingConsent = requireElement<HTMLInputElement>(rootEl, '[data-waitlist-consent-marketing]')
    errorEl = requireElement<HTMLElement>(rootEl, '[data-waitlist-error]')
    successEl = requireElement<HTMLElement>(rootEl, '[data-waitlist-success]')
    submitBtn = requireElement<HTMLButtonElement>(rootEl, '[data-waitlist-submit]')
  })

  afterEach(() => {
    if (originalLocationDescriptor) {
      Object.defineProperty(window, 'location', originalLocationDescriptor)
    }
    document.body.innerHTML = ''
    vi.restoreAllMocks()
  })

  function mountComposable(elementToPass: HTMLElement | null) {
    const container = document.createElement('div')
    document.body.appendChild(container)

    const TestComp = defineComponent({
      setup() {
        const rootRef = ref<HTMLElement | null>(elementToPass)
        useWaitlistForm(rootRef)
        return () => h('div')
      },
    })

    const app = createApp(TestComp)
    app.mount(container)

    return {
      unmount: () => {
        app.unmount()
        container.remove()
      },
    }
  }

  describe('normalizeWaitlistApiBase', () => {
    it('removes single and trailing slashes correctly', () => {
      expect(normalizeWaitlistApiBase('https://api.example.com/')).toBe('https://api.example.com')
      expect(normalizeWaitlistApiBase('https://api.example.com///')).toBe('https://api.example.com')
      expect(normalizeWaitlistApiBase('')).toBe('')
    })
  })

  describe('Form binding and mounting', () => {
    it('sets data-waitlist-ready on form when mounted and removes on unmount', () => {
      const { unmount } = mountComposable(rootEl)

      expect(formEl.dataset.waitlistReady).toBe('true')

      unmount()
      expect(formEl.dataset.waitlistReady).toBeUndefined()
    })

    it('gracefully handles missing form or missing form inputs', () => {
      const emptyRoot = document.createElement('div')
      emptyRoot.innerHTML = '<form data-waitlist-form></form>'

      const { unmount } = mountComposable(emptyRoot)
      const emptyForm = requireElement<HTMLFormElement>(emptyRoot, 'form')
      expect(emptyForm.dataset.waitlistReady).toBeUndefined()

      unmount()
    })

    it('uses fallback messages when waitlistMessages attribute is invalid JSON', async () => {
      formEl.setAttribute('data-waitlist-messages', 'invalid-json')
      delete formEl.dataset.waitlistApiBase

      const { unmount } = mountComposable(rootEl)

      emailInput.value = 'invalid-email'
      formEl.dispatchEvent(new Event('submit', { cancelable: true, bubbles: true }))

      await nextTick()
      expect(errorEl.textContent).toBe('Please enter a valid email.')

      unmount()
    })
  })

  describe('Form submission handling', () => {
    it('handles invalid email validation', async () => {
      const { unmount } = mountComposable(rootEl)

      emailInput.value = 'invalid-email'
      const focusSpy = vi.spyOn(emailInput, 'focus')

      formEl.dispatchEvent(new Event('submit', { cancelable: true, bubbles: true }))

      await nextTick()
      expect(emailInput.getAttribute('aria-invalid')).toBe('true')
      expect(errorEl.textContent).toBe('Email inválido')
      expect(errorEl.classList.contains('hidden')).toBe(false)
      expect(successEl.classList.contains('hidden')).toBe(true)
      expect(focusSpy).toHaveBeenCalled()

      unmount()
    })

    it('submits successfully with valid payload, UTM params, and handles API success', async () => {
      Object.defineProperty(window, 'location', {
        value: {
          search: '?utm_source=twitter&utm_medium=cpc&utm_campaign=launch',
          pathname: '/es/waitlist',
        },
        writable: true,
        configurable: true,
      })

      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
        new Response(JSON.stringify({ status: 'ok' }), { status: 200 })
      )

      const { unmount } = mountComposable(rootEl)

      emailInput.value = 'test@example.com'
      marketingConsent.checked = true

      formEl.dispatchEvent(new Event('submit', { cancelable: true, bubbles: true }))

      await flushPromises()

      expect(emailInput.hasAttribute('aria-invalid')).toBe(false)
      expect(fetchSpy).toHaveBeenCalledWith(
        'https://api.profiletailors.com/api/waitlists/pt-early/entries',
        expect.objectContaining({
          method: 'POST',
          headers: { 'content-type': 'application/json' },
        })
      )

      const body = fetchSpy.mock.calls[0]?.[1]?.body
      if (typeof body !== 'string') throw new Error('Expected a JSON string request body')
      const requestBody: unknown = JSON.parse(body)
      expect(requestBody).toMatchObject({
        email: 'test@example.com',
        source: 'hero-section',
        formId: 'hero-waitlist',
        locale: 'es',
        consent: {
          earlyAccess: true,
          marketing: true,
        },
        metadata: {
          utm_source: 'twitter',
          utm_medium: 'cpc',
          utm_campaign: 'launch',
          page_path: '/es/waitlist',
        },
      })

      expect(successEl.classList.contains('hidden')).toBe(false)
      expect(errorEl.classList.contains('hidden')).toBe(true)
      expect(submitBtn.disabled).toBe(false)

      unmount()
    })

    it('uses relative path endpoint when apiBase is not configured', async () => {
      delete formEl.dataset.waitlistApiBase
      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
        new Response(JSON.stringify({ status: 'ok' }), { status: 200 })
      )

      const { unmount } = mountComposable(rootEl)

      emailInput.value = 'test@example.com'
      formEl.dispatchEvent(new Event('submit', { cancelable: true, bubbles: true }))

      await flushPromises()

      expect(fetchSpy).toHaveBeenCalledWith(
        '/api/waitlists/pt-early/entries',
        expect.anything()
      )

      unmount()
    })

    it('handles 429 Too Many Requests response', async () => {
      vi.spyOn(globalThis, 'fetch').mockResolvedValue(
        new Response(null, { status: 429 })
      )

      const { unmount } = mountComposable(rootEl)

      emailInput.value = 'test@example.com'
      formEl.dispatchEvent(new Event('submit', { cancelable: true, bubbles: true }))

      await flushPromises()

      expect(errorEl.textContent).toBe('Demasiadas peticiones')
      expect(errorEl.classList.contains('hidden')).toBe(false)
      expect(successEl.classList.contains('hidden')).toBe(true)

      unmount()
    })

    it('handles 400/500 HTTP error responses', async () => {
      vi.spyOn(globalThis, 'fetch').mockResolvedValue(
        new Response('Server error', { status: 500 })
      )

      const { unmount } = mountComposable(rootEl)

      emailInput.value = 'test@example.com'
      formEl.dispatchEvent(new Event('submit', { cancelable: true, bubbles: true }))

      await flushPromises()

      expect(errorEl.textContent).toBe('Error al registrar')
      expect(errorEl.classList.contains('hidden')).toBe(false)
      expect(successEl.classList.contains('hidden')).toBe(true)

      unmount()
    })

    it('handles network error (fetch throws exception)', async () => {
      vi.spyOn(globalThis, 'fetch').mockRejectedValue(new Error('Network error'))

      const { unmount } = mountComposable(rootEl)

      emailInput.value = 'test@example.com'
      formEl.dispatchEvent(new Event('submit', { cancelable: true, bubbles: true }))

      await flushPromises()

      expect(errorEl.textContent).toBe('Error al registrar')
      expect(errorEl.classList.contains('hidden')).toBe(false)
      expect(successEl.classList.contains('hidden')).toBe(true)

      unmount()
    })
  })
})
