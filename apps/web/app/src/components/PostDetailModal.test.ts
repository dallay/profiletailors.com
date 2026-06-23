import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import PostDetailModal from './PostDetailModal.vue'
import { usePublishingStore } from '@/stores/publishing'
import type { Publication } from '@/stores/publishing'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key, locale: { value: 'en' } }),
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
}))

vi.mock('@lucide/vue', () => {
  const stub = { template: '<svg />' }
  return {
    CalendarClock: stub,
    ExternalLink: stub,
    Trash2: stub,
    X: stub,
    AlertTriangle: stub,
    CheckCircle2: stub,
    Clock: stub,
  }
})

vi.mock('@/lib/provider-styles', () => ({
  getProviderColor: () => 'blue',
  getProviderBadge: () => 'LI',
}))

function makePublication(overrides: Partial<Publication> = {}): Publication {
  return {
    id: 'pub-1',
    content: 'Test post body text',
    title: 'Test Title',
    channels: ['linkedin'],
    scheduledAt: '2026-06-15T20:00:00Z',
    status: 'SCHEDULED',
    priority: false,
    externalPublicationId: 'urn:li:share:123456789',
    ...overrides,
  }
}

function mountModal(publication: Publication | null, isOpen = true) {
  return mount(PostDetailModal, {
    props: { isOpen, publication },
    global: {
      mocks: { $t: (key: string) => key },
      stubs: { Teleport: true }, // stub Teleport to body in jsdom
    },
  })
}

describe('PostDetailModal', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    const publishingStore = usePublishingStore()
    vi.spyOn(publishingStore, 'deletePost').mockResolvedValue()
    vi.spyOn(publishingStore, 'reschedulePublication').mockResolvedValue()
  })

  describe('renders publication details', () => {
    it('displays title and body content', () => {
      const wrapper = mountModal(
        makePublication({ title: 'My Great Post', content: 'Body text here' }),
      )

      expect(wrapper.text()).toContain('My Great Post')
      expect(wrapper.text()).toContain('Body text here')
    })

    it('displays the thumbnail image when present', () => {
      const wrapper = mountModal(makePublication({ thumbnail: 'https://example.com/hero.jpg' }))

      const img = wrapper.find('img')
      expect(img.exists()).toBe(true)
      expect(img.attributes('src')).toBe('https://example.com/hero.jpg')
    })

    it('does not render when isOpen is false', () => {
      const wrapper = mountModal(makePublication(), false)

      expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    })
  })

  describe('status tone', () => {
    const cases: Array<[Publication['status'], string]> = [
      ['PUBLISHED', 'text-success'],
      ['BLOCKED', 'text-error'],
      ['FAILED', 'text-error'],
      ['CANCELLED', 'text-text-secondary'],
      ['PROCESSING', 'text-warning'],
      ['QUEUED', 'text-warning'],
      ['SCHEDULED', 'text-warning'],
    ]

    it.each(cases)('applies %s tone for status %s', (status, expectedClass) => {
      const wrapper = mountModal(makePublication({ status }))
      expect(wrapper.html()).toContain(expectedClass)
    })
  })

  describe('viewPostUrl', () => {
    it('prefers publicUrl from the backend', () => {
      const wrapper = mountModal(makePublication({ publicUrl: 'https://linkedin.com/post/abc123' }))

      const link = wrapper.find('a[href="https://linkedin.com/post/abc123"]')
      expect(link.exists()).toBe(true)
    })

    it('builds LinkedIn share URL from urn:li: prefix', () => {
      const wrapper = mountModal(makePublication({ externalPublicationId: 'urn:li:share:999' }))

      const link = wrapper.find('a[href*="linkedin.com/feed/update/"]')
      expect(link.exists()).toBe(true)
      expect(link.attributes('href')).toContain(encodeURIComponent('urn:li:share:999'))
    })

    it('disables the view button when no URL is available', () => {
      const wrapper = mountModal(
        makePublication({ publicUrl: undefined, externalPublicationId: undefined }),
      )

      const disabledButton = wrapper.find('button[disabled]')
      expect(disabledButton.exists()).toBe(true)
    })
  })

  describe('deletePublication', () => {
    it('emits deleted with publication id and closes modal on success', async () => {
      const pub = makePublication()
      const wrapper = mountModal(pub)
      const publishingStore = usePublishingStore()

      const deleteButton = wrapper
        .findAll('button')
        .find((button) => button.text().includes('postDetail.delete'))
      expect(deleteButton).toBeDefined()
      await deleteButton!.trigger('click')
      await flushPromises()

      expect(publishingStore.deletePost).toHaveBeenCalledWith('pub-1')
      expect(wrapper.emitted('deleted')).toEqual([['pub-1']])
      expect(wrapper.emitted('close')).toHaveLength(1)
    })

    it('displays an error message when delete fails', async () => {
      const wrapper = mountModal(makePublication())
      const publishingStore = usePublishingStore()
      vi.spyOn(publishingStore, 'deletePost').mockRejectedValue(new Error('Network error'))

      const deleteBtn = wrapper
        .findAll('button')
        .find((button) => button.text().includes('postDetail.delete'))
      await deleteBtn!.trigger('click')
      await flushPromises()

      expect(wrapper.text()).toContain('Network error')
    })

    it('isReadOnly hides delete button for published posts', () => {
      const wrapper = mountModal(makePublication({ status: 'PUBLISHED' }))

      expect(wrapper.text()).not.toContain('postDetail.delete')
      expect(wrapper.text()).toContain('postDetail.readOnlyHint')
    })
  })

  describe('reschedule', () => {
    it('shows Reschedule button when publication has scheduledAt and is not read-only', () => {
      const wrapper = mountModal(makePublication({ scheduledAt: '2026-07-01T10:00:00Z' }))

      expect(wrapper.text()).toContain('postDetail.reschedule')
    })

    it('hides Reschedule button for published posts', () => {
      const wrapper = mountModal(makePublication({ status: 'PUBLISHED' }))

      expect(wrapper.text()).not.toContain('postDetail.reschedule')
    })

    it('hides Reschedule button when publication has no scheduledAt', () => {
      const wrapper = mountModal(makePublication({ scheduledAt: undefined }))

      expect(wrapper.text()).not.toContain('postDetail.reschedule')
    })

    it('opens the reschedule form on button click', async () => {
      const wrapper = mountModal(makePublication())

      const rescheduleBtn = wrapper
        .findAll('button')
        .find((b) => b.text().includes('postDetail.reschedule'))
      expect(rescheduleBtn).toBeDefined()
      await rescheduleBtn!.trigger('click')

      // The reschedule input and confirm/cancel buttons should appear
      expect(wrapper.text()).toContain('postDetail.scheduledFor')
      expect(wrapper.text()).toContain('postDetail.rescheduleConfirm')
      expect(wrapper.text()).toContain('postDetail.rescheduleCancel')
    })

    it('calls reschedulePublication and emits reschedule on confirm', async () => {
      const wrapper = mountModal(
        makePublication({ id: 'pub-2', scheduledAt: '2026-07-01T10:00:00Z' }),
      )
      const publishingStore = usePublishingStore()

      // Open reschedule
      const rescheduleBtn = wrapper
        .findAll('button')
        .find((b) => b.text().includes('postDetail.reschedule'))
      await rescheduleBtn!.trigger('click')

      // Click confirm
      const confirmBtn = wrapper
        .findAll('button')
        .find((b) => b.text().includes('postDetail.rescheduleConfirm'))
      await confirmBtn!.trigger('click')

      expect(publishingStore.reschedulePublication).toHaveBeenCalledWith(
        'pub-2',
        expect.any(String),
      )
      expect(wrapper.emitted('reschedule')).toBeDefined()
      expect(wrapper.emitted('reschedule')![0]).toEqual([
        { id: 'pub-2', scheduledAt: expect.any(String) },
      ])
      expect(wrapper.emitted('close')).toHaveLength(1)
    })

    it('displays error when reschedule fails', async () => {
      const wrapper = mountModal(makePublication({ scheduledAt: '2026-07-01T10:00:00Z' }))
      const publishingStore = usePublishingStore()
      vi.spyOn(publishingStore, 'reschedulePublication').mockRejectedValue(
        new Error('Reschedule failed'),
      )

      const rescheduleBtn = wrapper
        .findAll('button')
        .find((b) => b.text().includes('postDetail.reschedule'))
      await rescheduleBtn!.trigger('click')

      const confirmBtn = wrapper
        .findAll('button')
        .find((b) => b.text().includes('postDetail.rescheduleConfirm'))
      await confirmBtn!.trigger('click')

      await flushPromises()
      expect(wrapper.text()).toContain('Reschedule failed')
    })

    it('cancelReschedule hides the reschedule form', async () => {
      const wrapper = mountModal(makePublication())

      // Open reschedule
      const rescheduleBtn = wrapper
        .findAll('button')
        .find((b) => b.text().includes('postDetail.reschedule'))
      await rescheduleBtn!.trigger('click')
      expect(wrapper.text()).toContain('postDetail.rescheduleConfirm')

      // Cancel
      const cancelBtn = wrapper
        .findAll('button')
        .find((b) => b.text().includes('postDetail.rescheduleCancel'))
      await cancelBtn!.trigger('click')

      expect(wrapper.text()).not.toContain('postDetail.rescheduleConfirm')
      expect(wrapper.text()).not.toContain('postDetail.rescheduleCancel')
    })
  })
})
