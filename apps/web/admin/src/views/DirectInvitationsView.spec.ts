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

function listPageResponse(items: unknown[] = []) {
  return new Response(
    JSON.stringify({
      items,
      page: 0,
      size: 25,
      totalElements: items.length,
      totalPages: 1,
      hasNext: false,
      hasPrevious: false,
    }),
    { status: 200 },
  )
}

function listRowResponse(overrides: Record<string, unknown> = {}) {
  return {
    invitationId: 'inv-1',
    email: 'ops@example.com',
    target: 'EXISTING_WORKSPACE',
    workspaceId: 'ws-1',
    status: 'ACTIVE',
    expiresAt: '2030-01-01T00:00:00Z',
    version: 2,
    ...overrides,
  }
}

function postCalls() {
  return request.mock.calls.filter(
    (call) => (call[1] as RequestInit | undefined)?.method === 'POST',
  )
}

function listCalls() {
  return request.mock.calls.filter(
    (call) =>
      typeof call[0] === 'string' &&
      call[0].startsWith('/api/admin/invitations/direct?') &&
      (call[1] as RequestInit | undefined)?.method !== 'POST',
  )
}

describe('DirectInvitationsView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    request.mockReset()
    request.mockResolvedValue(listPageResponse())
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
    request.mockResolvedValueOnce(listPageResponse()).mockResolvedValue(
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
    const firstPost = postCalls()[0]
    expect(firstPost).toBeDefined()
    const init = firstPost?.[1] as RequestInit
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
      .mockResolvedValueOnce(listPageResponse())
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
    const resendCall = postCalls().find(
      (call) => call[0] === '/api/admin/invitations/inv-1/direct-resend',
    )
    expect(resendCall).toBeDefined()
    const resendInit = resendCall?.[1] as RequestInit
    expect(new Headers(resendInit.headers).get('Content-Type')).toBe('application/vnd.api.v1+json')
    wrapper.unmount()
  })

  it('revokes with the version returned by create instead of a hardcoded zero', async () => {
    request
      .mockResolvedValueOnce(listPageResponse())
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
    request.mockResolvedValueOnce(listPageResponse()).mockResolvedValue(
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
    request.mockResolvedValueOnce(listPageResponse()).mockResolvedValue(
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

  it('loads and renders direct invitations in a table', async () => {
    request.mockResolvedValueOnce(
      listPageResponse([
        listRowResponse(),
        listRowResponse({ invitationId: 'inv-2', email: 'second@example.com' }),
      ]),
    )
    const wrapper = mountView()
    await flushPromises()
    expect(listCalls()[0]?.[0]).toContain('/api/admin/invitations/direct?page=0&size=25')
    const table = wrapper.get('[data-testid="direct-invitations-table"]')
    expect(table.text()).toContain('ops@example.com')
    expect(table.text()).toContain('second@example.com')
    expect(table.text()).toContain('EXISTING_WORKSPACE')
    expect(table.text()).toContain('ACTIVE')
    expect(wrapper.findAll('[data-testid="direct-invitation-row-resend"]').length).toBe(2)
    expect(wrapper.findAll('[data-testid="direct-invitation-row-revoke"]').length).toBe(2)
    wrapper.unmount()
  })

  it('re-queries page 0 when status filter or email search changes', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(listCalls().length).toBe(1)
    await wrapper.get('[data-testid="direct-invitations-status-filter"]').setValue('ACTIVE')
    await flushPromises()
    expect(listCalls().length).toBe(2)
    expect(listCalls()[1]?.[0]).toContain('status=ACTIVE')
    expect(listCalls()[1]?.[0]).toContain('page=0')
    await wrapper.get('[data-testid="direct-invitations-search"]').setValue('ops@')
    await flushPromises()
    expect(listCalls().length).toBe(3)
    expect(listCalls()[2]?.[0]).toContain('email=ops%40')
    expect(listCalls()[2]?.[0]).toContain('page=0')
    wrapper.unmount()
  })

  it('shows an empty state when the list has no rows', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.find('[data-testid="direct-invitations-empty"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="direct-invitations-table"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('shows a loading state while the list is fetching', async () => {
    let resolveList!: (res: Response) => void
    request.mockImplementationOnce(
      () =>
        new Promise<Response>((resolve) => {
          resolveList = resolve
        }),
    )
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.find('[data-testid="direct-invitations-loading"]').exists()).toBe(true)
    resolveList(listPageResponse())
    await flushPromises()
    expect(wrapper.find('[data-testid="direct-invitations-loading"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('shows an error state without stale rows when the list fetch fails', async () => {
    request.mockReset()
    request.mockRejectedValueOnce(new Error('network down'))
    const authStore = useAdminAuthStore()
    authStore.request = request
    authStore.principal = {
      principalId: 'p-1',
      email: 'owner@example.com',
      displayName: null,
      platformRoles: ['PLATFORM_OWNER'],
    }
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.find('[data-testid="direct-invitations-error"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="direct-invitations-table"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('resends a row invitation and refreshes the list', async () => {
    request
      .mockResolvedValueOnce(listPageResponse([listRowResponse()]))
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            invitationId: 'inv-1',
            status: 'ACTIVE',
            expiresAt: '2030-01-08T00:00:00Z',
            version: 3,
          }),
          { status: 200 },
        ),
      )
      .mockResolvedValue(listPageResponse([listRowResponse({ version: 3 })]))
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[data-testid="direct-invitation-row-resend"]').trigger('click')
    await flushPromises()
    expect(request).toHaveBeenCalledWith(
      '/api/admin/invitations/inv-1/direct-resend',
      expect.objectContaining({ method: 'POST' }),
    )
    expect(listCalls().length).toBe(2)
    wrapper.unmount()
  })

  it('revokes a row with its expected version and refreshes the list', async () => {
    request
      .mockResolvedValueOnce(listPageResponse([listRowResponse()]))
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ invitationId: 'inv-1' }), { status: 200 }),
      )
      .mockResolvedValue(listPageResponse([]))
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[data-testid="direct-invitation-row-revoke"]').trigger('click')
    await flushPromises()
    const dialog = wrapper.findComponent(RevokeInvitationDialog)
    expect(dialog.props('expectedVersion')).toBe(2)
    expect(dialog.props('email')).toBe('ops@example.com')
    await dialog.vm.$emit('confirm', 2)
    await flushPromises()
    const revokeCall = postCalls().find(
      (call) => call[0] === '/api/admin/invitations/inv-1/direct-revoke',
    )
    expect(revokeCall).toBeDefined()
    const revokeInit = revokeCall?.[1] as RequestInit
    expect(JSON.parse(revokeInit.body as string)).toMatchObject({ expectedVersion: 2 })
    expect(listCalls().length).toBe(2)
    wrapper.unmount()
  })

  it('issues zero list requests when the operator cannot read', async () => {
    const authStore = useAdminAuthStore()
    authStore.principal = {
      principalId: 'p-9',
      email: 'auditor@example.com',
      displayName: null,
      platformRoles: ['AUDITOR'],
    }
    const wrapper = mountView()
    await flushPromises()
    expect(listCalls().length).toBe(0)
    expect(wrapper.find('[data-testid="direct-invitations-list"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('platform role')
    wrapper.unmount()
  })
})
