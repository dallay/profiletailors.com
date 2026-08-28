import { beforeEach, describe, expect, it, vi } from 'vitest'
import { reactive } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import AcceptInvitationView from './AcceptInvitationView.vue'

const accept = vi.hoisted(() => vi.fn())
const hydrateSession = vi.hoisted(() => vi.fn())
const routerReplace = vi.hoisted(() => vi.fn())
const load = vi.hoisted(() => vi.fn())

const authState = reactive({
  hydrated: false,
})

const state = reactive({
  pending: false,
  workspaceId: null as string | null,
  membershipStatus: null as string | null,
  errorCode: null as string | null,
  errorStatus: null as number | null,
})

const capabilitiesState = reactive({
  resolved: true,
  invitationAcceptanceEnabled: true,
})

vi.mock('@modules/invitation/infrastructure/accept-invitation.store', () => ({
  useAcceptInvitationStore: () => ({
    get pending() {
      return state.pending
    },
    get hasAccepted() {
      return state.workspaceId !== null
    },
    get workspaceId() {
      return state.workspaceId
    },
    get membershipStatus() {
      return state.membershipStatus
    },
    get errorCode() {
      return state.errorCode
    },
    get errorStatus() {
      return state.errorStatus
    },
    accept,
    reset: () => {
      state.pending = false
      state.workspaceId = null
      state.membershipStatus = null
      state.errorCode = null
      state.errorStatus = null
    },
  }),
}))

vi.mock('@modules/auth/infrastructure/public-capabilities.store', () => ({
  usePublicCapabilitiesStore: () => ({
    load,
    get resolved() {
      return capabilitiesState.resolved
    },
    get invitationAcceptanceEnabled() {
      return capabilitiesState.invitationAcceptanceEnabled
    },
  }),
}))

vi.mock('@modules/auth/infrastructure/auth.store', () => ({
  useAuthStore: () => ({
    hydrateSession,
    get isAuthenticated() {
      return authState.hydrated
    },
  }),
}))

vi.mock('vue-router', async (importOriginal) => ({
  ...(await importOriginal<typeof import('vue-router')>()),
  useRoute: () => ({
    path: '/invitations/accept',
    query: { token: 'raw-token' },
    fullPath: '/invitations/accept?token=raw-token',
  }),
  useRouter: () => ({ replace: routerReplace }),
}))

vi.mock('vue-i18n', async (importOriginal) => ({
  ...(await importOriginal<typeof import('vue-i18n')>()),
  useI18n: () => ({ locale: { value: 'en' }, t: (key: string) => key }),
}))

describe('AcceptInvitationView branch coverage', () => {
  beforeEach(() => {
    state.pending = false
    state.workspaceId = null
    state.membershipStatus = null
    state.errorCode = null
    state.errorStatus = null
    capabilitiesState.resolved = true
    capabilitiesState.invitationAcceptanceEnabled = true
    authState.hydrated = false
    accept.mockReset()
    hydrateSession.mockReset()
    routerReplace.mockReset()
    load.mockReset()
    document.title = ''
  })

  it('calls capabilities.load on mount', async () => {
    mount(AcceptInvitationView, {
      props: { token: 'raw-token' },
      global: { stubs: { RouterLink: true, RouterView: true } },
    })
    await flushPromises()
    expect(load).toHaveBeenCalledOnce()
  })

  it('shows checking availability when capabilities not resolved', () => {
    capabilitiesState.resolved = false
    const wrapper = mount(AcceptInvitationView, {
      props: { token: 'raw-token' },
      global: { stubs: { RouterLink: true } },
    })
    expect(wrapper.text()).toContain('invitation.checkingAvailability')
    expect(wrapper.find('[role="status"]').exists()).toBe(true)
  })

  it('shows notFound canonical copy', async () => {
    accept.mockImplementation(async () => {
      state.errorCode = 'INVITATION_NOT_FOUND'
      state.errorStatus = 404
      return {
        workspaceId: null,
        membershipStatus: null,
        errorCode: 'INVITATION_NOT_FOUND',
        errorStatus: 404,
      }
    })
    const wrapper = mount(AcceptInvitationView, {
      props: { token: 'raw-token' },
      global: { stubs: { RouterLink: true, RouterView: true } },
    })
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()
    await flushPromises()
    expect(wrapper.text()).toContain('invitation.errors.notFound')
  })

  it('redirects unauthenticated invitees to registration with the token', async () => {
    accept.mockImplementation(async () => {
      state.errorCode = 'INVITATION_REQUIRES_LOGIN'
      state.errorStatus = 401
      return {
        workspaceId: null,
        membershipStatus: null,
        errorCode: 'INVITATION_REQUIRES_LOGIN',
        errorStatus: 401,
      }
    })
    const wrapper = mount(AcceptInvitationView, {
      props: { token: 'raw-token' },
      global: { stubs: { RouterLink: true, RouterView: true } },
    })
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()
    await flushPromises()
    expect(routerReplace).toHaveBeenCalledWith({
      name: 'register',
      query: { invitationToken: 'raw-token' },
    })
  })

  it('shows rateLimited canonical copy', async () => {
    accept.mockImplementation(async () => {
      state.errorCode = 'INVITATION_RATE_LIMITED'
      state.errorStatus = 429
      return {
        workspaceId: null,
        membershipStatus: null,
        errorCode: 'INVITATION_RATE_LIMITED',
        errorStatus: 429,
      }
    })
    const wrapper = mount(AcceptInvitationView, {
      props: { token: 'raw-token' },
      global: { stubs: { RouterLink: true, RouterView: true } },
    })
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()
    await flushPromises()
    expect(wrapper.text()).toContain('invitation.errors.rateLimited')
  })

  it('shows missingToken canonical copy via store error', async () => {
    accept.mockImplementation(async () => {
      state.errorCode = 'MISSING_TOKEN'
      state.errorStatus = 0
      return {
        workspaceId: null,
        membershipStatus: null,
        errorCode: 'MISSING_TOKEN',
        errorStatus: 0,
      }
    })
    const wrapper = mount(AcceptInvitationView, {
      props: { token: 'raw-token' },
      global: { stubs: { RouterLink: true, RouterView: true } },
    })
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()
    await flushPromises()
    expect(wrapper.text()).toContain('invitation.errors.missingToken')
  })

  it('shows generic copy for unknown error code', async () => {
    accept.mockImplementation(async () => {
      state.errorCode = 'UNKNOWN_CODE'
      state.errorStatus = 500
      return {
        workspaceId: null,
        membershipStatus: null,
        errorCode: 'UNKNOWN_CODE',
        errorStatus: 500,
      }
    })
    const wrapper = mount(AcceptInvitationView, {
      props: { token: 'raw-token' },
      global: { stubs: { RouterLink: true, RouterView: true } },
    })
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()
    await flushPromises()
    expect(wrapper.text()).toContain('invitation.errors.generic')
  })

  it('shows redirecting state when accepted and redirecting', async () => {
    state.workspaceId = 'ws-abc'
    state.membershipStatus = 'ACTIVE'
    accept.mockImplementation(async () => {
      state.workspaceId = 'ws-abc'
      return {
        workspaceId: 'ws-abc',
        membershipStatus: 'ACTIVE',
        errorCode: null,
        errorStatus: null,
      }
    })
    hydrateSession.mockImplementation(async () => {
      authState.hydrated = true
    })
    const wrapper = mount(AcceptInvitationView, {
      props: { token: 'raw-token' },
      global: { stubs: { RouterLink: true, RouterView: true } },
    })
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()
    await flushPromises()
    expect(wrapper.text()).toContain('invitation.redirecting')
    expect(wrapper.find('[role="status"][aria-live="polite"]').exists()).toBe(true)
  })

  it('sets document title when redirecting', async () => {
    accept.mockImplementation(async () => {
      state.workspaceId = 'ws-abc'
      return {
        workspaceId: 'ws-abc',
        membershipStatus: 'ACTIVE',
        errorCode: null,
        errorStatus: null,
      }
    })
    hydrateSession.mockResolvedValue(undefined)
    const wrapper = mount(AcceptInvitationView, {
      props: { token: 'raw-token' },
      global: { stubs: { RouterLink: true, RouterView: true } },
    })
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()
    await flushPromises()
    expect(document.title).toBe('invitation.redirecting')
  })

  it('prevents double submit when already submitted', async () => {
    let resolvePending!: (value: unknown) => void
    let callCount = 0
    accept.mockImplementation(() => {
      callCount += 1
      return new Promise((resolve) => {
        resolvePending = resolve
      }) as unknown as Promise<{
        workspaceId: string | null
        membershipStatus: string | null
        errorCode: string | null
        errorStatus: number | null
      }>
    })
    const wrapper = mount(AcceptInvitationView, {
      props: { token: 'raw-token' },
      global: { stubs: { RouterLink: true, RouterView: true } },
    })
    await wrapper.find('form').trigger('submit.prevent')
    await wrapper.find('form').trigger('submit.prevent')
    expect(callCount).toBe(1)
    resolvePending({
      workspaceId: 'ws-abc',
      membershipStatus: 'ACTIVE',
      errorCode: null,
      errorStatus: null,
    })
    state.workspaceId = 'ws-abc'
    await flushPromises()
    await flushPromises()
  })

  it('prevents submit when store pending', async () => {
    state.pending = true
    const wrapper = mount(AcceptInvitationView, {
      props: { token: 'raw-token' },
      global: { stubs: { RouterLink: true, RouterView: true } },
    })
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()
    expect(accept).not.toHaveBeenCalled()
  })

  it('treats whitespace token as missing', () => {
    const wrapper = mount(AcceptInvitationView, {
      props: { token: '   ' },
      global: { stubs: { RouterLink: true, RouterView: true } },
    })
    expect(wrapper.find('form').exists()).toBe(false)
    expect(wrapper.text()).toContain('invitation.errors.missingToken')
  })

  it('swallows hydrateSession errors and still redirects to login when not authenticated', async () => {
    accept.mockImplementation(async () => {
      state.workspaceId = 'ws-abc'
      return {
        workspaceId: 'ws-abc',
        membershipStatus: 'ACTIVE',
        errorCode: null,
        errorStatus: null,
      }
    })
    hydrateSession.mockRejectedValue(new Error('hydrate failed'))
    const wrapper = mount(AcceptInvitationView, {
      props: { token: 'raw-token' },
      global: { stubs: { RouterLink: true, RouterView: true } },
    })
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()
    await flushPromises()
    expect(hydrateSession).toHaveBeenCalledOnce()
    expect(routerReplace).toHaveBeenCalledWith(expect.objectContaining({ path: '/login' }))
  })

  it('disables submit button when pending', async () => {
    state.pending = true
    const wrapper = mount(AcceptInvitationView, {
      props: { token: 'raw-token' },
      global: { stubs: { RouterLink: true, RouterView: true } },
    })
    const button = wrapper.find('button[type="submit"]')
    expect(button.attributes('disabled')).toBeDefined()
    expect(button.text()).toBe('invitation.submitting')
  })

  it('enables submit button when not pending', () => {
    state.pending = false
    const wrapper = mount(AcceptInvitationView, {
      props: { token: 'raw-token' },
      global: { stubs: { RouterLink: true, RouterView: true } },
    })
    const button = wrapper.find('button[type="submit"]')
    expect(button.attributes('disabled')).toBeUndefined()
    expect(button.text()).toBe('invitation.submit')
  })
})
