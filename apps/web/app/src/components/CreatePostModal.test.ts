import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { usePublishingStore } from '@/stores/publishing'
import CreatePostModalComponent from './CreatePostModal.vue'

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

vi.mock('@/lib/auth-api', () => ({
  createApiFetch: () =>
    async function apiFetch<T>() { return {} as T },
  refreshSession: vi.fn().mockResolvedValue(null),
  getCurrentUserProfile: vi.fn().mockResolvedValue(null),
  login: vi.fn(),
  register: vi.fn(),
  logoutSession: vi.fn(),
}))

vi.mock('@/components/ui/button', () => ({
  Button: { template: '<button class="ui-button"><slot /></button>' },
}))

vi.mock('@lucide/vue', () => {
  const stub = { template: '<svg />' }
  return {
    Image: stub, Calendar: stub, Check: stub, ChevronDown: stub,
    FileImage: stub, Hash: stub, Paperclip: stub, Smile: stub,
    Sparkles: stub, X: stub,
  }
})

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeChannel(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    accountId: id,
    name: `Channel ${id}`,
    provider: 'linkedin' as const,
    avatar: '',
    avatarUrl: undefined as string | undefined,
    handle: 'urn:li:person:test',
    status: 'ACTIVE' as const,
    ...overrides,
  }
}

function mountModal(channels: ReturnType<typeof makeChannel>[]) {
  const store = usePublishingStore()
  store.channels = channels

  return mount(CreatePostModalComponent, {
    props: { isOpen: true },
    global: {
      mocks: { $t: mockT },
    },
  })
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('CreatePostModal.vue — avatar rendering', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    document.body.innerHTML = ''
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('renders <img> when channel has a valid avatarUrl', () => {
    const wrapper = mountModal([
      makeChannel('ch-1', { avatarUrl: 'https://example.com/avatar.jpg' }),
    ])

    // Teleported content is rendered into document.body
    const body = document.body.innerHTML
    expect(body).toContain('https://example.com/avatar.jpg')

    // Also check via wrapper.html() which includes teleported content
    const allHtml = wrapper.html() + body
    expect(allHtml).toContain('Channel ch-1 avatar')
  })

  it('renders fallback badge when avatarUrl is null/undefined', () => {
    const wrapper = mountModal([
      makeChannel('ch-2', { avatarUrl: undefined }),
    ])

    const body = document.body.innerHTML
    // For a linkedin channel, the fallback badge shows "in"
    expect(body).toContain('in')

    // No img with a src attribute in teleported content
    expect(body).not.toContain('src="undefined"')
  })

  it('shows fallback badge when avatar image fails to load', async () => {
    const wrapper = mountModal([
      makeChannel('ch-3', { avatarUrl: 'https://example.com/broken.jpg' }),
    ])

    const body = document.body.innerHTML
    expect(body).toContain('https://example.com/broken.jpg')

    // Find the img in teleported body content and trigger error
    const img = document.body.querySelector('img[src="https://example.com/broken.jpg"]')
    expect(img).toBeTruthy()

    // Dispatch error event
    img!.dispatchEvent(new Event('error'))

    await wrapper.vm.$nextTick()

    const bodyAfterError = document.body.innerHTML
    // After error, fallback badge "in" should be shown
    expect(bodyAfterError).toContain('in')
  })
})
