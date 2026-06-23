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
    vi.spyOn(publishingStore, 'deletePost').mockResolvedValue(undefined)
    vi.spyOn(publishingStore, 'reschedulePublication').mockResolvedValue(makePublication())
    vi.spyOn(publishingStore, 'updatePost').mockResolvedValue(makePublication())
  })

  describe('renders publication details', () => {
    it('displays editable title and body values for editable publications', async () => {
      const wrapper = mountModal(
        makePublication({ title: 'My Great Post', content: 'Body text here' }),
      )
      await flushPromises()

      const titleInput = wrapper.find('input[placeholder="postDetail.titleLabel"]')
      const textarea = wrapper.find('textarea')

      expect((titleInput.element as HTMLInputElement).value).toBe('My Great Post')
      expect((textarea.element as HTMLTextAreaElement).value).toBe('Body text here')
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

    it('falls back to raw status string for unknown status in label', () => {
      const wrapper = mountModal(
        makePublication({ status: 'UNKNOWN_STATUS' as Publication['status'] }),
      )
      expect(wrapper.text()).toContain('UNKNOWN_STATUS')
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

    it('hides destructive actions for non-deletable statuses', () => {
      const wrapper = mountModal(makePublication({ status: 'PROCESSING' }))

      expect(wrapper.text()).not.toContain('postDetail.delete')
      expect(wrapper.text()).toContain('postDetail.reschedule')
      expect(wrapper.text()).not.toContain('postDetail.rescheduleConfirm')
    })

    it('isReadOnly hides delete button for published posts', () => {
      const wrapper = mountModal(makePublication({ status: 'PUBLISHED' }))

      expect(wrapper.text()).not.toContain('postDetail.delete')
      expect(wrapper.text()).toContain('postDetail.readOnlyHint')
    })

    it('prevents double-click by guarding with isDeleting', async () => {
      const wrapper = mountModal(makePublication())
      const publishingStore = usePublishingStore()
      // Make deletePost never resolve — isDeleting stays true
      vi.spyOn(publishingStore, 'deletePost').mockReturnValue(new Promise(() => {}))

      const deleteButton = wrapper
        .findAll('button')
        .find((button) => button.text().includes('postDetail.delete'))
      expect(deleteButton).toBeDefined()
      await deleteButton!.trigger('click')
      // Click again while still deleting
      await deleteButton!.trigger('click')

      // deletePost should only be called once
      expect(publishingStore.deletePost).toHaveBeenCalledTimes(1)
    })

    it('shows delete error and does not close modal on failure', async () => {
      const wrapper = mountModal(makePublication())
      const publishingStore = usePublishingStore()
      vi.spyOn(publishingStore, 'deletePost').mockRejectedValue(new Error('Delete failed'))

      const deleteButton = wrapper
        .findAll('button')
        .find((button) => button.text().includes('postDetail.delete'))
      await deleteButton!.trigger('click')
      await flushPromises()

      expect(wrapper.text()).toContain('Delete failed')
      // Modal should still be open (close not emitted on failure)
      expect(wrapper.emitted('close')).toBeUndefined()
    })
  })

  describe('save publication', () => {
    it('calls updatePost and closes modal on successful save', async () => {
      const wrapper = mountModal(makePublication())
      const publishingStore = usePublishingStore()

      const titleInput = wrapper.find('input[placeholder="postDetail.titleLabel"]')
      await titleInput.setValue('Updated title')
      const textarea = wrapper.find('textarea')
      await textarea.setValue('Updated content')

      const saveButton = wrapper
        .findAll('button')
        .find((button) => button.text().includes('postDetail.save'))
      await saveButton!.trigger('click')
      await flushPromises()

      expect(publishingStore.updatePost).toHaveBeenCalledWith(
        'pub-1',
        expect.objectContaining({ title: 'Updated title', content: 'Updated content' }),
      )
      expect(wrapper.emitted('close')).toHaveLength(1)
    })

    it('shows save error when update fails', async () => {
      const wrapper = mountModal(makePublication())
      const publishingStore = usePublishingStore()
      vi.spyOn(publishingStore, 'updatePost').mockRejectedValue(new Error('Save failed'))

      const saveButton = wrapper
        .findAll('button')
        .find((button) => button.text().includes('postDetail.save'))
      await saveButton!.trigger('click')
      await flushPromises()

      expect(wrapper.text()).toContain('Save failed')
    })
  })

  describe('reschedule', () => {
    it('shows Reschedule button when publication has scheduledAt and is not editable or read-only', () => {
      const wrapper = mountModal(
        makePublication({ status: 'PROCESSING', scheduledAt: '2026-07-01T10:00:00Z' }),
      )

      expect(wrapper.text()).toContain('postDetail.reschedule')
    })

    it('hides Reschedule button for published posts', () => {
      const wrapper = mountModal(makePublication({ status: 'PUBLISHED' }))

      expect(wrapper.text()).not.toContain('postDetail.reschedule')
    })

    it('hides Reschedule button when publication has no scheduledAt', () => {
      const wrapper = mountModal(makePublication({ status: 'PROCESSING', scheduledAt: undefined }))

      expect(wrapper.text()).not.toContain('postDetail.reschedule')
    })

    it('opens the reschedule form on button click', async () => {
      const wrapper = mountModal(
        makePublication({ status: 'PROCESSING', scheduledAt: '2026-07-01T10:00:00Z' }),
      )

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
        makePublication({ id: 'pub-2', status: 'PROCESSING', scheduledAt: '2026-07-01T10:00:00Z' }),
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
      const wrapper = mountModal(
        makePublication({ status: 'PROCESSING', scheduledAt: '2026-07-01T10:00:00Z' }),
      )
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
      const wrapper = mountModal(
        makePublication({ status: 'PROCESSING', scheduledAt: '2026-07-01T10:00:00Z' }),
      )

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

    it('reschedule form is hidden by default', () => {
      const wrapper = mountModal(
        makePublication({ status: 'PROCESSING', scheduledAt: '2026-07-01T10:00:00Z' }),
      )

      // The reschedule form should not be visible initially
      expect(wrapper.text()).not.toContain('postDetail.rescheduleConfirm')
      expect(wrapper.text()).not.toContain('postDetail.rescheduleCancel')
    })
  })
})
