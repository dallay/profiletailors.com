import { beforeEach, describe, expect, it, vi } from 'vitest'
import { reactive } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import AcceptInvitationView from './AcceptInvitationView.vue'

const accept = vi.hoisted(() => vi.fn())
const hydrateSession = vi.hoisted(() => vi.fn())
const routerReplace = vi.hoisted(() => vi.fn())

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

const capabilitiesState = reactive({
  resolved: true,
  invitationAcceptanceEnabled: true,
})
const load = vi.hoisted(() => vi.fn())

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

describe('AcceptInvitationView', () => {
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
  })

  it('renders the unavailable state when invitation acceptance capability is disabled', () => {
    capabilitiesState.invitationAcceptanceEnabled = false
    const wrapper = mount(AcceptInvitationView, {
      props: { token: 'raw-token' },
      global: { stubs: { RouterLink: true } },
    })
    expect(wrapper.find('form').exists()).toBe(false)
    expect(wrapper.text()).toContain('invitation.unavailableTitle')
  })

  it('submits the token from props and exposes the resolved workspace id', async () => {
    accept.mockImplementation(async () => {
      state.workspaceId = 'ws-abc'
      state.membershipStatus = 'ACTIVE'
      return {
        workspaceId: 'ws-abc',
        membershipStatus: 'ACTIVE',
        errorCode: null,
        errorStatus: null,
      }
    })
    const wrapper = mount(AcceptInvitationView, {
      props: { token: 'raw-token' },
      global: { stubs: { RouterLink: true, RouterView: true } },
    })

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()
    await flushPromises()

    expect(accept).toHaveBeenCalledWith('raw-token')
    expect(state.workspaceId).toBe('ws-abc')
  })

  it('hydrates the auth session and redirects to dashboard after successful acceptance', async () => {
    accept.mockImplementation(async () => {
      state.workspaceId = 'ws-abc'
      state.membershipStatus = 'ACTIVE'
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

    expect(hydrateSession).toHaveBeenCalledOnce()
    expect(routerReplace).toHaveBeenCalledWith('/')
  })

  it('redirects to login when the session was not established by the cookie', async () => {
    accept.mockImplementation(async () => {
      state.workspaceId = 'ws-abc'
      state.membershipStatus = 'ACTIVE'
      return {
        workspaceId: 'ws-abc',
        membershipStatus: 'ACTIVE',
        errorCode: null,
        errorStatus: null,
      }
    })
    hydrateSession.mockImplementation(async () => {
      authState.hydrated = false
    })

    const wrapper = mount(AcceptInvitationView, {
      props: { token: 'raw-token' },
      global: { stubs: { RouterLink: true, RouterView: true } },
    })

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()
    await flushPromises()

    expect(hydrateSession).toHaveBeenCalledOnce()
    expect(routerReplace).toHaveBeenCalledWith(
      expect.objectContaining({
        path: '/login',
        query: expect.objectContaining({
          redirect: expect.stringContaining('/invitations/accept?token='),
        }),
      }),
    )
  })

  it('shows the canonical not-acceptable copy when the backend rejects with that code', async () => {
    accept.mockImplementation(async () => {
      state.errorCode = 'INVITATION_NOT_ACCEPTABLE'
      state.errorStatus = 400
      return {
        workspaceId: null,
        membershipStatus: null,
        errorCode: 'INVITATION_NOT_ACCEPTABLE',
        errorStatus: 400,
      }
    })
    const wrapper = mount(AcceptInvitationView, {
      props: { token: 'raw-token' },
      global: { stubs: { RouterLink: true, RouterView: true } },
    })

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()
    await flushPromises()

    expect(wrapper.text()).toContain('invitation.errors.notAcceptable')
  })

  it('falls back to the safe generic copy on unexpected failures', async () => {
    accept.mockImplementation(async () => {
      state.errorCode = 'INTERNAL_ERROR'
      state.errorStatus = 500
      return {
        workspaceId: null,
        membershipStatus: null,
        errorCode: 'INTERNAL_ERROR',
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

  it('treats an empty token as a validation failure without invoking the store', () => {
    const wrapper = mount(AcceptInvitationView, {
      props: { token: '' },
      global: { stubs: { RouterLink: true, RouterView: true } },
    })

    expect(wrapper.find('form').exists()).toBe(false)
    expect(wrapper.text()).toContain('invitation.errors.missingToken')
    expect(accept).not.toHaveBeenCalled()
  })

  it('never renders or echoes the raw token in DOM attributes or visible text', async () => {
    accept.mockImplementation(async () => {
      state.workspaceId = 'ws-abc'
      state.membershipStatus = 'ACTIVE'
      return {
        workspaceId: 'ws-abc',
        membershipStatus: 'ACTIVE',
        errorCode: null,
        errorStatus: null,
      }
    })
    const wrapper = mount(AcceptInvitationView, {
      props: { token: 'super-secret-raw-token' },
      global: { stubs: { RouterLink: true, RouterView: true } },
    })

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()
    await flushPromises()

    const html = wrapper.html()
    expect(html).not.toContain('super-secret-raw-token')
    expect(wrapper.text()).not.toContain('super-secret-raw-token')
  })
})
