import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it, vi } from 'vitest'
import AdminLayout from '@/layouts/AdminLayout.vue'
import { messages } from '@/i18n'
import { NAV_REGISTRY } from '@/router/nav-registry'
import { useAdminAuthStore, type PlatformRole } from '@/stores/auth.store'

function resolveLabel(root: unknown, labelKey: string): unknown {
  let current: unknown = root
  for (const part of labelKey.split('.')) {
    if (typeof current !== 'object' || current === null) return undefined
    current = (current as Record<string, unknown>)[part]
  }
  return current
}

describe('NAV_REGISTRY', () => {
  it('exposes key, routeName, path, permission and status for every entry', () => {
    expect(NAV_REGISTRY.length).toBeGreaterThan(0)
    for (const entry of NAV_REGISTRY) {
      expect(typeof entry.key).toBe('string')
      expect(typeof entry.routeName).toBe('string')
      expect(typeof entry.path).toBe('string')
      expect(typeof entry.permission).toBe('string')
      expect(typeof entry.labelKey).toBe('string')
      expect(typeof entry.icon).toBe('string')
      expect(['live', 'planned']).toContain(entry.status)
    }
  })

  it('lists direct-invitations as a live entry gated by platform.invitations.read', () => {
    const entry = NAV_REGISTRY.find((candidate) => candidate.key === 'direct-invitations')
    expect(entry).toBeDefined()
    expect(entry).toMatchObject({
      routeName: 'direct-invitations',
      path: 'direct-invitations',
      permission: 'platform.invitations.read',
      status: 'live',
    })
  })

  it('covers every shell area with one entry per area', () => {
    const keys = NAV_REGISTRY.map((entry) => entry.key)
    for (const area of [
      'overview',
      'users',
      'waitlist',
      'direct-invitations',
      'notifications',
      'governance',
      'configuration',
      'audit',
    ]) {
      expect(keys).toContain(area)
    }
  })

  it('marks overview as planned', () => {
    expect(NAV_REGISTRY.find((entry) => entry.key === 'overview')?.status).toBe('planned')
  })

  it('marks governance as live with correct permission', () => {
    const entry = NAV_REGISTRY.find((candidate) => candidate.key === 'governance')
    expect(entry).toBeDefined()
    expect(entry).toMatchObject({
      routeName: 'governance',
      path: 'governance',
      permission: 'platform.governance.read',
      status: 'live',
    })
  })

  it('marks notifications as live with correct permission', () => {
    const entry = NAV_REGISTRY.find((candidate) => candidate.key === 'notifications')
    expect(entry).toBeDefined()
    expect(entry?.status).toBe('live')
    expect(entry?.permission).toBe('platform.notifications.read')
  })

  it('keeps dashboard, waitlist, users, direct-invitations, audit, governance and configuration live', () => {
    for (const key of [
      'dashboard',
      'waitlist',
      'users',
      'direct-invitations',
      'audit',
      'governance',
      'configuration',
    ]) {
      expect(NAV_REGISTRY.find((entry) => entry.key === key)?.status).toBe('live')
    }
  })

  it('lists configuration as a live entry gated by platform.configuration.read', () => {
    const entry = NAV_REGISTRY.find((candidate) => candidate.key === 'configuration')
    expect(entry).toBeDefined()
    expect(entry).toMatchObject({
      routeName: 'configuration',
      path: 'configuration',
      permission: 'platform.configuration.read',
      status: 'live',
    })
  })

  it('gates every planned entry by an existing platform permission', () => {
    const knownPermissions = new Set([
      'platform.dashboard.read',
      'platform.waitlist.read',
      'platform.users.read',
      'platform.audit.read',
      'platform.operators.read',
      'platform.invitations.read',
    ])
    for (const entry of NAV_REGISTRY.filter((candidate) => candidate.status === 'planned')) {
      expect(knownPermissions.has(entry.permission)).toBe(true)
    }
  })

  it('provides EN and ES labels for every entry', () => {
    for (const entry of NAV_REGISTRY) {
      expect(typeof resolveLabel(messages.en, entry.labelKey)).toBe('string')
      expect(typeof resolveLabel(messages.es, entry.labelKey)).toBe('string')
    }
  })

  it('provides a planned-area message in EN and ES', () => {
    expect(typeof resolveLabel(messages.en, 'planned.message')).toBe('string')
    expect(typeof resolveLabel(messages.es, 'planned.message')).toBe('string')
  })
})

describe('AdminLayout nav filtering', () => {
  async function mountLayout(platformRoles: PlatformRole[], attachToDocument = false) {
    const pinia = createPinia()
    setActivePinia(pinia)
    const authStore = useAdminAuthStore()
    authStore.principal = {
      principalId: 'p-1',
      email: 'admin@example.com',
      displayName: null,
      platformRoles,
    }
    const i18n = createI18n({
      legacy: false,
      locale: 'en',
      fallbackLocale: 'en',
      messages,
    })
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        {
          path: '/login',
          name: 'login',
          component: { template: '<div>Sign in</div>' },
        },
        ...NAV_REGISTRY.map((entry) => ({
          path: `/${entry.path}`,
          name: entry.routeName,
          component: { template: '<div />' },
        })),
      ],
    })
    const wrapper = mount(AdminLayout, {
      attachTo: attachToDocument ? document.body : undefined,
      global: {
        plugins: [pinia, i18n, router],
      },
    })
    await router.isReady()
    await flushPromises()
    return wrapper
  }

  it('opens and closes the shared mobile navigation from the accessible trigger', async () => {
    const originalMatchMedia = window.matchMedia
    window.matchMedia = vi.fn().mockReturnValue({
      matches: true,
      media: '(max-width: 767px)',
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }) as unknown as typeof window.matchMedia

    try {
      const wrapper = await mountLayout(['AUDITOR'], true)
      const openButton = wrapper.get('button[aria-label="Open navigation"]')
      await openButton.trigger('click')
      await flushPromises()

      const mobileNavigation = document.body.querySelector('[role="dialog"]')
      expect(mobileNavigation).not.toBeNull()
      const closeButton = mobileNavigation?.querySelector<HTMLButtonElement>(
        '[data-slot="sheet-close"]',
      )
      expect(closeButton).not.toBeNull()
      closeButton?.click()
      await flushPromises()
      expect(document.body.querySelector('[role="dialog"]')).toBeNull()

      wrapper.unmount()
    } finally {
      window.matchMedia = originalMatchMedia
    }
  })

  it('shows the build source link for a principal with admin access', async () => {
    const wrapper = await mountLayout(['PLATFORM_OWNER'])
    const sourceLink = wrapper.get(
      'a[href^="https://github.com/dallay/profiletailors.com/commit/"]',
    )
    expect(sourceLink.text()).toBe('Source code')
    expect(sourceLink.attributes('href')).toMatch(
      /^https:\/\/github\.com\/dallay\/profiletailors\.com\/commit\/[0-9a-f]{7}$/,
    )
    wrapper.unmount()
  })

  it('shows direct-invitations for a principal holding platform.invitations.read', async () => {
    const wrapper = await mountLayout(['PLATFORM_OWNER'])
    expect(wrapper.text()).toContain('Direct Invitations')
    wrapper.unmount()
  })

  it('returns to sign-in when the admin session expires', async () => {
    const wrapper = await mountLayout(['PLATFORM_OWNER'])

    useAdminAuthStore().clearSession()
    await flushPromises()

    expect(wrapper.text()).toContain('Sign in')
    wrapper.unmount()
  })

  it('hides direct-invitations for a principal missing platform.invitations.read', async () => {
    const wrapper = await mountLayout(['SUPPORT_AGENT'])
    expect(wrapper.text()).not.toContain('Direct Invitations')
    expect(wrapper.text()).toContain('Waitlist')
    expect(wrapper.text()).toContain('Users')
    wrapper.unmount()
  })

  it('hides every entry whose permission the principal lacks', async () => {
    const wrapper = await mountLayout(['AUDITOR'])
    expect(wrapper.text()).toContain('Dashboard')
    expect(wrapper.text()).toContain('Audit')
    expect(wrapper.text()).not.toContain('Direct Invitations')
    wrapper.unmount()
  })
})
