import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import PostDetailModal from './PostDetailModal.vue'
import type { Publication } from '@/stores/publishing'

// Mutable config — vi.mock captures these by closure, so each test can override before mounting
const storeOverrides = {
  rescheduleResult: undefined as Publication | undefined,
  rescheduleError: undefined as Error | undefined,
  deleteError: undefined as Error | undefined,
  isPublicationEditable: true,
  isPublicationDeletable: true,
}

const mockReschedule = vi.fn()
const mockDelete = vi.fn()

vi.mock('@/stores/publishing', () => ({
  usePublishingStore: () => ({
    reschedulePublication: mockReschedule,
    deletePost: mockDelete,
    isPublicationEditable: (status: string) =>
      storeOverrides.isPublicationEditable?.(status) ?? true,
    isPublicationDeletable: (status: string) =>
      storeOverrides.isPublicationDeletable?.(status) ?? true,
  }),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key, locale: { value: 'en' } }),
}))

vi.mock('@/lib/auth-api', () => ({
  createApiFetch: vi.fn().mockResolvedValue(undefined),
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
    Pencil: stub,
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
    vi.clearAllMocks()
    storeOverrides.rescheduleResult = makePublication()
    storeOverrides.rescheduleError = undefined
    storeOverrides.deleteError = undefined
    storeOverrides.isPublicationEditable = () => true
    storeOverrides.isPublicationDeletable = () => true

    mockReschedule.mockImplementation(async () => {
      if (storeOverrides.rescheduleError) throw storeOverrides.rescheduleError
      return storeOverrides.rescheduleResult!
    })
    mockDelete.mockImplementation(async () => {
      if (storeOverrides.deleteError) throw storeOverrides.deleteError
    })

    setActivePinia(createPinia())
  })

  describe('renders publication details', () => {
    it('displays title and body as read-only text for all publication statuses', async () => {
      const wrapper = mountModal(
        makePublication({ title: 'My Great Post', content: 'Body text here' }),
      )
      await flushPromises()

      // Title and body are displayed as read-only <p> elements
      expect(wrapper.text()).toContain('My Great Post')
      expect(wrapper.text()).toContain('Body text here')
      // No editable input/textarea for title
      expect(wrapper.find('input[placeholder="postDetail.titleLabel"]').exists()).toBe(false)
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

      const deleteButton = wrapper
        .findAll('button')
        .find((button) => button.text().includes('postDetail.delete'))
      expect(deleteButton).toBeDefined()
      await deleteButton!.trigger('click')
      await flushPromises()

      expect(mockDelete).toHaveBeenCalledWith('pub-1')
      expect(wrapper.emitted('deleted')).toEqual([['pub-1']])
      expect(wrapper.emitted('close')).toHaveLength(1)
    })

    it('displays an error message when delete fails', async () => {
      storeOverrides.deleteError = new Error('Network error')
      const wrapper = mountModal(makePublication())

      const deleteBtn = wrapper
        .findAll('button')
        .find((button) => button.text().includes('postDetail.delete'))
      await deleteBtn!.trigger('click')
      await flushPromises()

      expect(wrapper.text()).toContain('Network error')
    })

    it('hides destructive actions for non-deletable statuses', () => {
      storeOverrides.isPublicationDeletable = () => false
      storeOverrides.isPublicationEditable = () => false
      const wrapper = mountModal(makePublication({ status: 'PROCESSING' }))

      expect(wrapper.text()).not.toContain('postDetail.delete')
      expect(wrapper.text()).toContain('postDetail.reschedule')
      expect(wrapper.text()).not.toContain('postDetail.rescheduleConfirm')
    })

    it('isReadOnly hides delete button for published posts', () => {
      storeOverrides.isPublicationDeletable = () => false
      storeOverrides.isPublicationEditable = () => false
      const wrapper = mountModal(makePublication({ status: 'PUBLISHED' }))

      expect(wrapper.text()).not.toContain('postDetail.delete')
      expect(wrapper.text()).toContain('postDetail.readOnlyHint')
    })

    it('prevents double-click by guarding with isDeleting', async () => {
      // Make deletePost never resolve — isDeleting stays true
      mockDelete.mockImplementation(() => new Promise(() => {}))
      const wrapper = mountModal(makePublication())

      const deleteButton = wrapper
        .findAll('button')
        .find((button) => button.text().includes('postDetail.delete'))
      expect(deleteButton).toBeDefined()
      await deleteButton!.trigger('click')
      // Click again while still deleting
      await deleteButton!.trigger('click')

      // deletePost should only be called once
      expect(mockDelete).toHaveBeenCalledTimes(1)
    })

    it('shows delete error and does not close modal on failure', async () => {
      storeOverrides.deleteError = new Error('Delete failed')
      const wrapper = mountModal(makePublication())

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

  describe('edit button', () => {
    it('renders Edit button for editable publication statuses', () => {
      const wrapper = mountModal(makePublication({ status: 'SCHEDULED' }))
      expect(wrapper.text()).toContain('postDetail.edit')
    })

    it('does NOT render Edit button for PUBLISHED posts', () => {
      storeOverrides.isPublicationEditable = () => false
      const wrapper = mountModal(makePublication({ status: 'PUBLISHED' }))
      expect(wrapper.text()).not.toContain('postDetail.edit')
    })

    it('does NOT render Edit button for non-editable statuses (PROCESSING)', () => {
      storeOverrides.isPublicationEditable = () => false
      const wrapper = mountModal(makePublication({ status: 'PROCESSING' }))
      expect(wrapper.text()).not.toContain('postDetail.edit')
    })

    it('clicking Edit emits edit event with the publication', async () => {
      const pub = makePublication()
      const wrapper = mountModal(pub)
      const editButton = wrapper
        .findAll('button')
        .find((button) => button.text().includes('postDetail.edit'))
      expect(editButton).toBeDefined()
      await editButton!.trigger('click')
      expect(wrapper.emitted('edit')).toEqual([[pub]])
    })
  })

  describe('reschedule', () => {
    it('shows Reschedule button when publication has scheduledAt and is not editable or read-only', () => {
      storeOverrides.isPublicationEditable = () => false
      const wrapper = mountModal(
        makePublication({ status: 'PROCESSING', scheduledAt: '2027-07-01T10:00:00Z' }),
      )

      expect(wrapper.text()).toContain('postDetail.reschedule')
    })

    it('hides Reschedule button for published posts', () => {
      storeOverrides.isPublicationEditable = () => false
      const wrapper = mountModal(makePublication({ status: 'PUBLISHED' }))

      expect(wrapper.text()).not.toContain('postDetail.reschedule')
    })

    it('hides Reschedule button when publication has no scheduledAt', () => {
      storeOverrides.isPublicationEditable = () => false
      const wrapper = mountModal(makePublication({ status: 'PROCESSING', scheduledAt: undefined }))

      expect(wrapper.text()).not.toContain('postDetail.reschedule')
    })

    it('opens the reschedule form on button click', async () => {
      storeOverrides.isPublicationEditable = () => false
      const wrapper = mountModal(
        makePublication({ status: 'PROCESSING', scheduledAt: '2027-07-01T10:00:00Z' }),
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
      storeOverrides.rescheduleResult = makePublication({ id: 'pub-2' })
      storeOverrides.isPublicationEditable = () => false
      const wrapper = mountModal(
        makePublication({ id: 'pub-2', status: 'PROCESSING', scheduledAt: '2027-07-01T10:00:00Z' }),
      )

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
      await flushPromises()
      await nextTick()

      expect(mockReschedule).toHaveBeenCalledWith('pub-2', expect.any(String))
      expect(wrapper.emitted('reschedule')).toBeDefined()
      expect(wrapper.emitted('reschedule')![0]).toEqual([
        { id: 'pub-2', scheduledAt: expect.any(String) },
      ])
      expect(wrapper.emitted('close')).toHaveLength(1)
    })

    it('displays error when reschedule fails', async () => {
      storeOverrides.rescheduleError = new Error('Reschedule failed')
      storeOverrides.isPublicationEditable = () => false
      const wrapper = mountModal(
        makePublication({ status: 'PROCESSING', scheduledAt: '2027-07-01T10:00:00Z' }),
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
      await nextTick()
      expect(wrapper.text()).toContain('Reschedule failed')
    })

    it('cancelReschedule hides the reschedule form', async () => {
      storeOverrides.isPublicationEditable = () => false
      const wrapper = mountModal(
        makePublication({ status: 'PROCESSING', scheduledAt: '2027-07-01T10:00:00Z' }),
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
        makePublication({ status: 'PROCESSING', scheduledAt: '2027-07-01T10:00:00Z' }),
      )

      // The reschedule form should not be visible initially
      expect(wrapper.text()).not.toContain('postDetail.rescheduleConfirm')
      expect(wrapper.text()).not.toContain('postDetail.rescheduleCancel')
    })
  })
})
