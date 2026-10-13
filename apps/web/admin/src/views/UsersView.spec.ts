import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import UsersView from './UsersView.vue'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'

const mockUsersResult = {
  items: [
    {
      principalId: 'user-1',
      email: 'john@example.com',
      displayIdentity: 'John Doe',
      principalType: 'USER',
      accountState: 'ACTIVE',
      emailStatus: 'VERIFIED',
      createdAt: '2024-01-01T00:00:00Z',
      lastAuthenticatedAt: '2024-06-01T00:00:00Z',
      platformRoles: ['ROLE_ADMIN'],
    },
    {
      principalId: 'user-2',
      email: null,
      displayIdentity: null,
      principalType: 'SYSTEM',
      accountState: 'DISABLED',
      emailStatus: null,
      createdAt: '2024-02-01T00:00:00Z',
      lastAuthenticatedAt: null,
      platformRoles: [],
    },
  ],
  page: 0,
  size: 25,
  totalItems: 2,
  totalPages: 1,
  hasPrevious: false,
  hasNext: false,
}

const mockRequest = vi.fn()
vi.mock('@/stores/auth.store', () => ({
  useAdminAuthStore: () => ({ request: mockRequest }),
}))

async function createView() {
  setActivePinia(createPinia())
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/users', name: 'users', component: UsersView },
      { path: '/users/:principalId', name: 'user-detail', component: { template: '<div />' } },
    ],
  })
  const i18n = createI18n({
    legacy: false,
    locale: 'en',
    messages: {
      en: {
        users: {
          title: 'Users',
          displayName: 'Display Name',
          principalType: 'Principal Type',
          accountState: 'Account State',
          empty: 'No users found',
          accountStates: {
            active: 'Active',
            disabled: 'Disabled',
          },
        },
        waitlist: {
          filters: {
            search: 'Search users...',
          },
        },
        common: {
          loading: 'Loading...',
          error: 'An error occurred',
          email: 'Email',
          createdAt: 'Created At',
        },
      },
    },
  })

  await router.push({ name: 'users' })
  const wrapper = mount(UsersView, {
    global: {
      plugins: [router, i18n],
      stubs: {
        EmptyState: { template: '<div class="empty-state"><slot /></div>' },
        PaginationControls: {
          props: ['page', 'totalPages', 'hasPrevious', 'hasNext'],
          template: `
            <div class="pagination">
              <button class="prev" :disabled="!hasPrevious" @click="$emit('previous')">Prev</button>
              <button class="next" :disabled="!hasNext" @click="$emit('next')">Next</button>
            </div>
          `,
        },
      },
    },
  })
  return { wrapper, router }
}

describe('UsersView', () => {
  beforeEach(() => {
    mockRequest.mockReset()
    vi.useFakeTimers()
  })

  it('renders user list successfully', async () => {
    mockRequest.mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve(mockUsersResult),
    })

    const { wrapper } = await createView()
    await flushPromises()

    expect(wrapper.text()).toContain('Users')
    expect(wrapper.text()).toContain('john@example.com')
    expect(wrapper.text()).toContain('John Doe')
    expect(wrapper.text()).toContain('Active')
    // Fallback display
    expect(wrapper.text()).toContain('user-2')
    expect(wrapper.text()).toContain('—')
    expect(wrapper.text()).toContain('Disabled')
  })

  it('shows empty state when no users exist', async () => {
    mockRequest.mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({ ...mockUsersResult, items: [] }),
    })

    const { wrapper } = await createView()
    await flushPromises()

    expect(wrapper.find('.empty-state').text()).toBe('No users found')
  })

  it('shows error message on request failure', async () => {
    mockRequest.mockResolvedValueOnce({ ok: false, status: 500 })

    const { wrapper } = await createView()
    await flushPromises()

    expect(wrapper.find('[role="alert"]').text()).toBe('An error occurred')
  })

  it('filters users by search input with debounce', async () => {
    mockRequest
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(mockUsersResult) })
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(mockUsersResult) })

    const { wrapper } = await createView()
    await flushPromises()

    const searchInput = wrapper.find('input[type="search"]')
    await searchInput.setValue('john')

    // Before debounce timer fires
    expect(mockRequest).toHaveBeenCalledTimes(1)

    // Fast-forward debounce timer (300ms)
    vi.advanceTimersByTime(300)
    await flushPromises()

    expect(mockRequest).toHaveBeenCalledTimes(2)
    const secondCall = mockRequest.mock.calls[1]
    expect(secondCall).toBeDefined()
    expect(secondCall?.[0]).toContain('email=john')
  })

  it('supports pagination next and previous triggers', async () => {
    const paginatedResult = {
      ...mockUsersResult,
      page: 0,
      totalPages: 2,
      hasNext: true,
      hasPrevious: false,
    }

    mockRequest
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(paginatedResult) })
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve({ ...paginatedResult, page: 1, hasPrevious: true, hasNext: false }) })

    const { wrapper } = await createView()
    await flushPromises()

    const nextBtn = wrapper.find('button.next')
    await nextBtn.trigger('click')
    await flushPromises()

    expect(mockRequest).toHaveBeenCalledTimes(2)
    const secondCall = mockRequest.mock.calls[1]
    expect(secondCall).toBeDefined()
    expect(secondCall?.[0]).toContain('page=1')
  })
})
