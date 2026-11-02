import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { messages } from '@/i18n'
import BootstrapView from '@/views/BootstrapView.vue'

describe('BootstrapView', () => {
  beforeEach(() => {
    window.history.replaceState(null, '', '/bootstrap#one-use-token')
  })

  it('clears the invitation fragment before fetching Terms requirements', async () => {
    const sequence: string[] = []
    const originalReplaceState = window.history.replaceState.bind(window.history)
    const historyReplace = vi
      .spyOn(window.history, 'replaceState')
      .mockImplementation((...args) => {
        sequence.push('fragment-clear')
        return originalReplaceState(...args)
      })
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        sequence.push('fetch')
        return Response.json({ currentTermsVersion: 'terms-v1.0.0' })
      }),
    )

    const wrapper = mountBootstrap()
    await flushPromises()

    expect(sequence.indexOf('fragment-clear')).toBeLessThan(sequence.indexOf('fetch'))
    expect(window.location.hash).toBe('')
    expect(wrapper.text()).toContain('terms-v1.0.0')
    historyReplace.mockRestore()
  })

  it('requires Terms acceptance and submits only the invitation fields', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(Response.json({ currentTermsVersion: 'terms-v1.0.0' }))
      .mockResolvedValueOnce(new Response(null, { status: 202 }))
    vi.stubGlobal('fetch', fetchMock)
    const wrapper = mountBootstrap()
    await flushPromises()

    await wrapper.get('[data-testid="bootstrap-password"]').setValue('a-strong-password')
    await wrapper.get('[data-testid="bootstrap-form"]').trigger('submit')
    await flushPromises()
    expect(fetchMock).toHaveBeenCalledTimes(1)

    await wrapper.get('[data-testid="bootstrap-terms-accepted"]').setValue(true)
    await wrapper.get('[data-testid="bootstrap-form"]').trigger('submit')
    await flushPromises()

    const requestInit = fetchMock.mock.calls[1]?.[1]
    expect(JSON.parse(String(requestInit?.body))).toEqual({
      invitationToken: 'one-use-token',
      password: 'a-strong-password',
      acceptedTermsVersion: 'terms-v1.0.0',
    })
    expect(JSON.stringify(requestInit?.body)).not.toContain('age')
    expect(wrapper.find('[data-testid="bootstrap-age-confirmation"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('Check your email')
  })

  it('shows the same unavailable message for unusable invitation credentials', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(Response.json({ currentTermsVersion: 'terms-v1.0.0' }))
      .mockResolvedValueOnce(
        Response.json(
          { code: 'BOOTSTRAP_UNAVAILABLE' },
          { status: 400, statusText: 'Bad Request' },
        ),
      )
    vi.stubGlobal('fetch', fetchMock)
    const wrapper = mountBootstrap()
    await flushPromises()

    await wrapper.get('[data-testid="bootstrap-password"]').setValue('a-strong-password')
    await wrapper.get('[data-testid="bootstrap-terms-accepted"]').setValue(true)
    await wrapper.get('[data-testid="bootstrap-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[data-testid="bootstrap-error"]').text()).toContain(
      'Invitation unavailable',
    )
  })
})

function mountBootstrap() {
  return mount(BootstrapView, {
    global: {
      plugins: [createI18n({ legacy: false, locale: 'en', messages })],
    },
  })
}
