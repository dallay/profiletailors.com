import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { usePublishingStore } from '@/stores/publishing'
import { getProviderBadge } from '@/lib/provider-styles'
import AppComponent from './App.vue'

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

const mockT = (key: string) => key

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: mockT, locale: { value: 'en' } }),
  createI18n: () => ({ global: { locale: { value: 'en' } } }),
}))

vi.mock('@/i18n', () => ({
  default: { global: { locale: { value: 'en' } } },
}))

vi.mock('vue-router', () => ({
  RouterLink: { template: '<a><slot /></a>' },
  RouterView: { template: '<div class="router-view"><slot /></div>' },
  useRoute: () => ({ name: 'dashboard', path: '/' }),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
}))

vi.mock('@/lib/auth-api', () => ({
  createApiFetch: () =>
    async function apiFetch<T>() {
      return {} as T
    },
  refreshSession: vi.fn().mockResolvedValue(null),
  getCurrentUserProfile: vi.fn().mockResolvedValue(null),
  login: vi.fn(),
  register: vi.fn(),
  logoutSession: vi.fn(),
  // Latent gap surfaced by the app-shell refactor: SidebarChannelRow calls
  // proxyImageUrl(channel.avatarUrl) and the original mock did not export it.
  proxyImageUrl: (url: string) => url,
}))

vi.mock('@/components/ui/tooltip', () => ({
  TooltipProvider: { template: '<div><slot /></div>' },
}))

vi.mock('@/components/ui/sidebar', () => ({
  Sidebar: { template: '<div class="sidebar"><slot /></div>' },
  SidebarContent: { template: '<div class="sidebar-content"><slot /></div>' },
  SidebarFooter: { template: '<div class="sidebar-footer"><slot /></div>' },
  SidebarGroup: { template: '<div class="sidebar-group"><slot /></div>' },
  SidebarGroupLabel: { template: '<div class="sidebar-group-label"><slot /></div>' },
  SidebarHeader: { template: '<div class="sidebar-header"><slot /></div>' },
  SidebarInset: { template: '<div class="sidebar-inset"><slot /></div>' },
  SidebarMenu: { template: '<div class="sidebar-menu"><slot /></div>' },
  SidebarMenuButton: {
    props: ['asChild', 'isActive', 'tooltip'],
    template: '<button class="sidebar-menu-button"><slot /></button>',
  },
  SidebarMenuItem: { template: '<div class="sidebar-menu-item"><slot /></div>' },
  SidebarProvider: { template: '<div class="sidebar-provider"><slot /></div>' },
  SidebarRail: { template: '<div />' },
  SidebarTrigger: { template: '<button class="sidebar-trigger" />' },
}))

vi.mock('@/components/ThemeToggle.vue', () => ({
  default: { template: '<div class="theme-toggle" />' },
}))

vi.mock('@lucide/vue', () => {
  const stub = { template: '<span />' }
  return {
    BarChart3: stub,
    CalendarDays: stub,
    ChevronsUpDown: stub,
    LayoutGrid: stub,
    LogOut: stub,
    PanelLeft: stub,
    Plus: stub,
    Settings: stub,
    Users: stub,
  }
})

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

interface TestChannel {
  id: string
  accountId: string
  name: string
  provider: 'linkedin'
  avatar: string
  avatarUrl?: string
  handle: string
  status: 'ACTIVE' | 'INACTIVE'
}

function makeChannel(id: string, overrides: Partial<Omit<TestChannel, 'id'>> = {}): TestChannel {
  return {
    id,
    accountId: id,
    name: `Channel ${id}`,
    provider: 'linkedin',
    avatar: '',
    avatarUrl: undefined,
    handle: `Channel ${id}`,
    status: 'ACTIVE',
    ...overrides,
  }
}

function mountApp(channels: TestChannel[]) {
  const store = usePublishingStore()
  store.channels = channels

  return mount(AppComponent, {
    global: {
      mocks: { $t: mockT },
    },
  })
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('App.vue — avatar rendering', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders <img> when channel has a valid avatarUrl', () => {
    const wrapper = mountApp([makeChannel('ch-1', { avatarUrl: 'https://example.com/avatar.jpg' })])

    const imgs = wrapper.findAll('img')
    const avatarImg = imgs.find((img) => img.attributes('src') === 'https://example.com/avatar.jpg')
    expect(avatarImg).toBeTruthy()
    expect(avatarImg?.attributes('alt')).toBe('Channel ch-1 avatar')
  })

  it('renders fallback badge when avatarUrl is null/undefined', () => {
    const wrapper = mountApp([makeChannel('ch-2', { avatarUrl: undefined })])

    const badgeText = getProviderBadge('linkedin')
    expect(wrapper.text()).toContain(badgeText)

    // No img with a src attribute should be rendered
    const imgs = wrapper.findAll('img[src]')
    expect(imgs.length).toBe(0)
  })

  it('shows fallback badge when avatar image fails to load', async () => {
    const wrapper = mountApp([makeChannel('ch-3', { avatarUrl: 'https://example.com/broken.jpg' })])

    const img = wrapper.find('img[src="https://example.com/broken.jpg"]')
    expect(img.exists()).toBe(true)

    await img.trigger('error')

    // After error, the img should be gone and fallback badge shown
    const imgAfterError = wrapper.find('img[src="https://example.com/broken.jpg"]')
    expect(imgAfterError.exists()).toBe(false)

    const badgeText = getProviderBadge('linkedin')
    expect(wrapper.text()).toContain(badgeText)
  })
})
