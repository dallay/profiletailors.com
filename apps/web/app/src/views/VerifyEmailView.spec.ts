import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { reactive } from 'vue'
import VerifyEmailView from './VerifyEmailView.vue'

const routeQuery = vi.hoisted(() => ({ value: {} as Record<string, unknown> }))
const verifyEmail = vi.hoisted(() => vi.fn())
const authState = reactive({
  isAuthenticated: false,
})

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: routeQuery.value }),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    verifyEmail,
    get isAuthenticated() {
      return authState.isAuthenticated
    },
  }),
}))

function mountVerifyEmailView() {
  return mount(VerifyEmailView, {
    global: {
      mocks: {
        $t: (key: string) => key,
      },
      stubs: {
        Loader2: true,
        CheckCircle2: true,
        TriangleAlert: true,
        Button: {
          props: ['as', 'href', 'variant', 'type'],
          template:
            '<a v-if="as === `a`" :href="href"><slot /></a><button v-else :type="type"><slot /></button>',
        },
      },
    },
  })
}

describe('VerifyEmailView', () => {
  beforeEach(() => {
    routeQuery.value = {}
    authState.isAuthenticated = false
    verifyEmail.mockReset()
  })

  it('shows a loading state while verifying a token', async () => {
    routeQuery.value = { token: 'token-123' }
    verifyEmail.mockImplementation(() => new Promise(() => {}))

    const wrapper = mountVerifyEmailView()
    await Promise.resolve()

    expect(verifyEmail).toHaveBeenCalledWith('token-123')
    expect(wrapper.text()).toContain('verifyEmail.loadingTitle')
    expect(wrapper.text()).toContain('verifyEmail.loadingMessage')
  })

  it('shows a missing-token state without calling the API when no token is present', async () => {
    const wrapper = mountVerifyEmailView()
    await flushPromises()

    expect(verifyEmail).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('verifyEmail.missingTokenTitle')
    expect(wrapper.text()).toContain('verifyEmail.missingTokenMessage')
  })

  it('shows an invalid-link state when backend verification fails with an invalid token', async () => {
    routeQuery.value = { token: 'bad-token' }
    verifyEmail.mockRejectedValue(
      Object.assign(new Error('Invalid token'), {
        detail: 'Invalid verification token.',
        status: 400,
      }),
    )

    const wrapper = mountVerifyEmailView()
    await flushPromises()

    expect(wrapper.text()).toContain('verifyEmail.invalidTitle')
    expect(wrapper.text()).toContain('Invalid verification token.')
    expect(wrapper.text()).not.toContain('verifyEmail.successTitle')
  })

  it('shows an expired-link state when backend verification fails with an expired token', async () => {
    routeQuery.value = { token: 'expired-token' }
    verifyEmail.mockRejectedValue(
      Object.assign(new Error('Expired token'), {
        detail: 'Verification token has expired.',
        status: 400,
      }),
    )

    const wrapper = mountVerifyEmailView()
    await flushPromises()

    expect(wrapper.text()).toContain('verifyEmail.expiredTitle')
    expect(wrapper.text()).toContain('Verification token has expired.')
  })

  it('falls back to i18n key when error detail is present but not a string', async () => {
    routeQuery.value = { token: 'bad-token' }
    verifyEmail.mockRejectedValue(
      Object.assign(new Error('Boom'), {
        // detail is present but not a string — should fall through to i18n key
        detail: 123,
        status: 400,
      }),
    )

    const wrapper = mountVerifyEmailView()
    await flushPromises()

    // Should show invalid state with i18n fallback message, not the number 123
    expect(wrapper.text()).toContain('verifyEmail.invalidTitle')
    expect(wrapper.text()).not.toContain('123')
  })

  it('handles token as an array (Vue Router array query) by taking the first element', async () => {
    // Vue Router can return arrays for repeated query params
    routeQuery.value = { token: ['array-token'] } as unknown as Record<string, unknown>
    verifyEmail.mockResolvedValue(undefined)

    const wrapper = mountVerifyEmailView()
    await flushPromises()

    expect(verifyEmail).toHaveBeenCalledWith('array-token')
    expect(wrapper.text()).toContain('verifyEmail.successTitle')
  })

  it('shows a success confirmation with a guest-safe CTA instead of auto-redirecting', async () => {
    routeQuery.value = { token: 'good-token' }
    authState.isAuthenticated = false
    verifyEmail.mockResolvedValue(undefined)

    const wrapper = mountVerifyEmailView()
    await flushPromises()

    expect(wrapper.text()).toContain('verifyEmail.successTitle')
    expect(wrapper.text()).toContain('verifyEmail.successMessage')
    expect(wrapper.text()).toContain('verifyEmail.signInCta')
    expect(wrapper.find('a[href="/login"]').exists()).toBe(true)
  })
})
