import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import DirectInvitationsView from '@/views/DirectInvitationsView.vue'
import RevokeInvitationDialog from '@/components/RevokeInvitationDialog.vue'
import { messages } from '@/i18n'
import { useAdminAuthStore } from '@/stores/auth.store'

const request = vi.fn<ReturnType<typeof useAdminAuthStore>['request']>()

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {}, params: {} }),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
}))

function mountView() {
  const i18n = createI18n({
    legacy: false,
    locale: 'en',
    fallbackLocale: 'en',
    messages,
  })
  return mount(DirectInvitationsView, {
    global: {
      plugins: [i18n],
    },
    attachTo: document.body,
  })
}

describe('DirectInvitationsView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    request.mockReset()
    const authStore = useAdminAuthStore()
    authStore.request = request
    authStore.principal = {
      principalId: 'p-1',
      email: 'owner@example.com',
      displayName: null,
      platformRoles: ['PLATFORM_OWNER'],
    }
  })

  it('disables submit when email is empty', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(
      wrapper.get('[data-testid="direct-invitation-submit"]').attributes('disabled'),
    ).toBeDefined()
    wrapper.unmount()
  })

  it('disables submit when email is invalid', async () => {
    const wrapper = mountView()
    await wrapper.get('[data-testid="direct-invitation-email"]').setValue('not-an-email')
    await flushPromises()
    expect(
      wrapper.get('[data-testid="direct-invitation-submit"]').attributes('disabled'),
    ).toBeDefined()
    wrapper.unmount()
  })

  it('requires workspaceId when target is EXISTING_WORKSPACE', async () => {
    const wrapper = mountView()
    await wrapper.get('[data-testid="direct-invitation-email"]').setValue('user@example.com')
    await wrapper.get('[data-testid="direct-invitation-target"]').setValue('EXISTING_WORKSPACE')
    await flushPromises()
    expect(
      wrapper.get('[data-testid="direct-invitation-submit"]').attributes('disabled'),
    ).toBeDefined()
    wrapper.unmount()
  })

  it('enables submit when email is valid and target is NEW_WORKSPACE', async () => {
    const wrapper = mountView()
    await wrapper.get('[data-testid="direct-invitation-email"]').setValue('user@example.com')
    await wrapper.get('[data-testid="direct-invitation-target"]').setValue('NEW_WORKSPACE')
    await flushPromises()
    expect(
      wrapper.get('[data-testid="direct-invitation-submit"]').attributes('disabled'),
    ).toBeUndefined()
    wrapper.unmount()
  })

  it('submits payload to /api/admin/invitations/direct with default EXISTING_WORKSPACE target', async () => {
    request.mockResolvedValue(
      new Response(
        JSON.stringify({
          invitationId: 'inv-1',
          status: 'ACTIVE',
          expiresAt: '2030-01-01T00:00:00Z',
          version: 0,
        }),
        {
          status: 201,
        },
      ),
    )
    const wrapper = mountView()
    await wrapper.get('[data-testid="direct-invitation-email"]').setValue('user@example.com')
    await wrapper.get('[data-testid="direct-invitation-workspace"]').setValue('ws-99')
    await wrapper.get('[data-testid="direct-invitation-submit"]').trigger('click')
    await flushPromises()
    expect(request).toHaveBeenCalledWith(
      '/api/admin/invitations/direct',
      expect.objectContaining({ method: 'POST' }),
    )
    const firstCall = request.mock.calls[0]
    expect(firstCall).toBeDefined()
    const init = firstCall?.[1] as RequestInit
    const body = JSON.parse(init.body as string)
    expect(body).toMatchObject({
      email: 'user@example.com',
      target: 'EXISTING_WORKSPACE',
      workspaceId: 'ws-99',
    })
    const headers = new Headers(init.headers)
    expect(headers.get('Content-Type')).toBe('application/vnd.api.v1+json')
    wrapper.unmount()
  })

  it('resends the created invitation and refreshes its version', async () => {
    request
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            invitationId: 'inv-1',
            status: 'ACTIVE',
            expiresAt: '2030-01-01T00:00:00Z',
            version: 0,
          }),
          { status: 201 },
        ),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            invitationId: 'inv-1',
            status: 'ACTIVE',
            expiresAt: '2030-01-08T00:00:00Z',
            version: 1,
          }),
          { status: 200 },
        ),
      )
    const wrapper = mountView()
    await wrapper.get('[data-testid="direct-invitation-email"]').setValue('user@example.com')
    await wrapper.get('[data-testid="direct-invitation-target"]').setValue('NEW_WORKSPACE')
    await wrapper.get('[data-testid="direct-invitation-submit"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="direct-invitation-resend"]').trigger('click')
    await flushPromises()
    expect(request).toHaveBeenCalledWith(
      '/api/admin/invitations/inv-1/direct-resend',
      expect.objectContaining({ method: 'POST' }),
    )
    const resendInit = request.mock.calls[1]?.[1] as RequestInit
    expect(new Headers(resendInit.headers).get('Content-Type')).toBe('application/vnd.api.v1+json')
    wrapper.unmount()
  })

  it('revokes with the version returned by create instead of a hardcoded zero', async () => {
    request
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            invitationId: 'inv-1',
            status: 'ACTIVE',
            expiresAt: '2030-01-01T00:00:00Z',
            version: 2,
          }),
          { status: 201 },
        ),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ invitationId: 'inv-1' }), { status: 200 }),
      )
    const wrapper = mountView()
    await wrapper.get('[data-testid="direct-invitation-email"]').setValue('user@example.com')
    await wrapper.get('[data-testid="direct-invitation-target"]').setValue('NEW_WORKSPACE')
    await wrapper.get('[data-testid="direct-invitation-submit"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="direct-invitation-revoke"]').trigger('click')
    await flushPromises()
    const dialog = wrapper.findComponent(RevokeInvitationDialog)
    expect(dialog.props('expectedVersion')).toBe(2)
    expect(dialog.props('email')).toBe('user@example.com')
    wrapper.unmount()
  })

  it('renders created invitation without exposing any token', async () => {
    request.mockResolvedValue(
      new Response(
        JSON.stringify({
          invitationId: 'inv-1',
          status: 'ACTIVE',
          expiresAt: '2030-01-01T00:00:00Z',
        }),
        {
          status: 201,
        },
      ),
    )
    const wrapper = mountView()
    await wrapper.get('[data-testid="direct-invitation-email"]').setValue('user@example.com')
    await wrapper.get('[data-testid="direct-invitation-target"]').setValue('NEW_WORKSPACE')
    await wrapper.get('[data-testid="direct-invitation-submit"]').trigger('click')
    await flushPromises()
    const success = wrapper.get('[data-testid="direct-invitation-success"]')
    expect(success.text()).toContain('inv-1')
    expect(success.text()).not.toContain('tok')
    expect(success.text()).not.toMatch(/token/i)
    wrapper.unmount()
  })

  it('renders INVITATION_ALREADY_ACTIVE error from server payload', async () => {
    request.mockResolvedValue(
      new Response(JSON.stringify({ properties: { code: 'INVITATION_ALREADY_ACTIVE' } }), {
        status: 409,
        headers: { 'Content-Type': 'application/vnd.api.v1+json' },
      }),
    )
    const wrapper = mountView()
    await wrapper.get('[data-testid="direct-invitation-email"]').setValue('user@example.com')
    await wrapper.get('[data-testid="direct-invitation-target"]').setValue('NEW_WORKSPACE')
    await wrapper.get('[data-testid="direct-invitation-submit"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('An active invitation already exists')
    wrapper.unmount()
  })
})
