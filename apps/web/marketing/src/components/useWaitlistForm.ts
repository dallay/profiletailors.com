import { onBeforeUnmount, onMounted, type Ref } from 'vue'
import { buildWaitlistPayload, WAITLIST_KEY_DEFAULT } from './waitlist-form'
import { isValidEmail } from './waitlist-form-validator'
import { bindWaitlistShare, readWaitlistShareAttributes } from './waitlist-share'

const SUCCESS_HIDDEN = 'hidden'

interface WaitlistMessages {
  validEmail: string
  tooManyRequests: string
  genericError: string
  success: string
}

function captureUtm(): Record<string, string> {
  const params = new URLSearchParams(window.location.search)
  const utmSource = params.get('utm_source')
  const utmMedium = params.get('utm_medium')
  const utmCampaign = params.get('utm_campaign')
  const metadata: Record<string, string> = {}
  if (utmSource) metadata.utm_source = utmSource
  if (utmMedium) metadata.utm_medium = utmMedium
  if (utmCampaign) metadata.utm_campaign = utmCampaign
  const path = window.location.pathname
  if (path) metadata.page_path = path
  return metadata
}

function readMessages(form: HTMLFormElement): WaitlistMessages {
  const fallback: WaitlistMessages = {
    validEmail: 'Please enter a valid email.',
    tooManyRequests: 'Too many requests. Try again in a minute.',
    genericError: 'Could not register you. Please try again.',
    success: "You're on the list.",
  }
  try {
    const raw = form.dataset.waitlistMessages
    return raw ? (JSON.parse(raw) as WaitlistMessages) : fallback
  } catch {
    return fallback
  }
}

function bindWaitlistForm(form: HTMLFormElement): (() => void) | undefined {
  const emailInput = form.querySelector<HTMLInputElement>('[data-waitlist-email]')
  const marketingInput = form.querySelector<HTMLInputElement>('[data-waitlist-consent-marketing]')
  const errorEl = form.querySelector<HTMLElement>('[data-waitlist-error]')
  const successEl = form.querySelector<HTMLElement>('[data-waitlist-success]')
  const submit = form.querySelector<HTMLButtonElement>('[data-waitlist-submit]')
  const share = form.querySelector<HTMLButtonElement>('[data-waitlist-share]')
  if (!emailInput || !marketingInput || !errorEl || !successEl || !submit) return

  const unbindShare = share
    ? bindWaitlistShare(share, readWaitlistShareAttributes(form))
    : undefined
  const waitlistKey = form.dataset.waitlistKey ?? WAITLIST_KEY_DEFAULT
  const source = form.dataset.waitlistSource ?? 'marketing-site'
  const formId = form.dataset.waitlistFormId ?? 'waitlist-hero'
  const locale = form.dataset.waitlistLocale ?? 'en'
  const messages = readMessages(form)

  const handleSubmit = async (event: SubmitEvent) => {
    event.preventDefault()
    const email = emailInput.value ?? ''
    if (!isValidEmail(email)) {
      emailInput.setAttribute('aria-invalid', 'true')
      errorEl.textContent = messages.validEmail
      errorEl.classList.remove(SUCCESS_HIDDEN)
      successEl.classList.add(SUCCESS_HIDDEN)
      emailInput.focus()
      return
    }

    emailInput.removeAttribute('aria-invalid')
    errorEl.classList.add(SUCCESS_HIDDEN)
    submit.disabled = true
    const payload = buildWaitlistPayload({
      email,
      source,
      formId,
      locale,
      consent: {
        earlyAccess: true,
        marketing: marketingInput.checked,
      },
      metadata: captureUtm(),
    })

    const apiBase = form.dataset.waitlistApiBase ?? ''
    const endpoint = apiBase
      ? `${apiBase.replace(/\/+$/, '')}/api/waitlists/${encodeURIComponent(waitlistKey)}/entries`
      : `/api/waitlists/${encodeURIComponent(waitlistKey)}/entries`
    try {
      const response = await fetch(endpoint, {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify(payload),
      })
      if (response.status === 429) {
        errorEl.textContent = messages.tooManyRequests
        errorEl.classList.remove(SUCCESS_HIDDEN)
        successEl.classList.add(SUCCESS_HIDDEN)
        return
      }
      if (!response.ok && response.status >= 400) {
        errorEl.textContent = messages.genericError
        errorEl.classList.remove(SUCCESS_HIDDEN)
        successEl.classList.add(SUCCESS_HIDDEN)
        return
      }
      successEl.classList.remove(SUCCESS_HIDDEN)
      errorEl.classList.add(SUCCESS_HIDDEN)
      form.reset()
    } catch {
      errorEl.textContent = messages.genericError
      errorEl.classList.remove(SUCCESS_HIDDEN)
      successEl.classList.add(SUCCESS_HIDDEN)
    } finally {
      submit.disabled = false
    }
  }

  form.addEventListener('submit', handleSubmit)
  return () => {
    form.removeEventListener('submit', handleSubmit)
    unbindShare?.()
  }
}

export function useWaitlistForm(root: Readonly<Ref<HTMLElement | null>>): void {
  let unbind: (() => void) | undefined

  onMounted(() => {
    const form = root.value?.querySelector<HTMLFormElement>('[data-waitlist-form]')
    if (form) unbind = bindWaitlistForm(form)
  })
  onBeforeUnmount(() => unbind?.())
}
