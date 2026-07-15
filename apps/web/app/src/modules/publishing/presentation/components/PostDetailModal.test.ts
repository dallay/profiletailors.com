import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import PostDetailModal from './PostDetailModal.vue'
import type { Publication } from '@modules/publishing/infrastructure/publishing.store'

interface StoreOverrides {
  rescheduleResult: Publication | undefined
  rescheduleError: Error | undefined
  deleteError: Error | undefined
  isPublicationEditable: (status: Publication['status']) => boolean
  isPublicationDeletable: (status: Publication['status']) => boolean
}

const { storeOverrides, mockReschedule, mockRetry, mockDelete } = vi.hoisted(() => ({
  storeOverrides: {
    rescheduleResult: undefined as Publication | undefined,
    rescheduleError: undefined as Error | undefined,
    deleteError: undefined as Error | undefined,
    isPublicationEditable: (_status: Publication['status']) => true,
    isPublicationDeletable: (_status: Publication['status']) => true,
  } as StoreOverrides,
  mockReschedule: vi.fn(),
  mockRetry: vi.fn(),
  mockDelete: vi.fn(),
}))

vi.mock('@modules/publishing/infrastructure/publishing.store', () => ({
  usePublishingStore: () => ({
    reschedulePublication: mockReschedule,
    retryPublication: mockRetry,
    deletePost: mockDelete,
    isPublicationEditable: storeOverrides.isPublicationEditable,
    isPublicationDeletable: storeOverrides.isPublicationDeletable,
  }),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key, locale: { value: 'en' } }),
}))

vi.mock('@modules/auth/infrastructure/auth-api', () => ({
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

vi.mock('@shared/lib/provider-styles', () => ({
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

  describe('failure diagnostics', () => {
    const canonicalCases = [
      ['MEDIA_NOT_FOUND', 'mediaNotFound'],
      ['MEDIA_UNAVAILABLE', 'mediaUnavailable'],
      ['ACCOUNT_RECONNECT_REQUIRED', 'accountReconnectRequired'],
      ['ACCOUNT_UNAVAILABLE', 'accountUnavailable'],
      ['PROVIDER_VALIDATION_FAILED', 'providerValidationFailed'],
      ['PROVIDER_UNAVAILABLE', 'providerUnavailable'],
      ['PROVIDER_RATE_LIMITED', 'providerRateLimited'],
      ['PUBLISHING_FAILED', 'publishingFailed'],
    ] as const

    it.each(
      canonicalCases,
    )('renders localized copy for canonical failed code %s', async (code, key) => {
      storeOverrides.isPublicationEditable = () => false
      const wrapper = mountModal(makePublication({ status: 'FAILED', errorCode: code }))
      await flushPromises()

      expect(wrapper.text()).toContain(`postDetail.failure.${key}.label`)
      expect(wrapper.text()).toContain(`postDetail.failure.${key}.explanation`)
      expect(wrapper.text()).toContain(`postDetail.failure.${key}.action`)
      expect(wrapper.text()).toContain('postDetail.retry')
      expect(wrapper.text()).not.toContain(code)
    })

    it.each([
      undefined,
      'UNKNOWN_ERROR_CODE',
      'StorageObjectNotFoundException',
    ])('uses safe fallback for missing, unknown, or historical failed code %s', async (errorCode) => {
      storeOverrides.isPublicationEditable = () => false
      const wrapper = mountModal(makePublication({ status: 'FAILED', errorCode }))
      await flushPromises()

      expect(wrapper.text()).toContain('postDetail.failure.unknown.label')
      expect(wrapper.text()).toContain('postDetail.failure.unknown.explanation')
      expect(wrapper.text()).toContain('postDetail.failure.unknown.action')
      if (errorCode) expect(wrapper.text()).not.toContain(errorCode)
    })

    it('renders canonical reconnect guidance for blocked publications', async () => {
      const wrapper = mountModal(
        makePublication({ status: 'BLOCKED', blockedReason: 'ACCOUNT_RECONNECT_REQUIRED' }),
      )
      await flushPromises()

      expect(wrapper.text()).toContain('postDetail.failure.accountReconnectRequired.label')
      expect(wrapper.text()).toContain('postDetail.failure.accountReconnectRequired.explanation')
      expect(wrapper.text()).toContain('postDetail.failure.accountReconnectRequired.action')
      expect(wrapper.text()).not.toContain('ACCOUNT_RECONNECT_REQUIRED')
    })

    it.each([
      undefined,
      'ReconnectRequiredException: token expired',
      'com.example.StorageObjectNotFoundException',
    ])('uses safe blocked fallback for untrusted blocked reason %s', async (blockedReason) => {
      const wrapper = mountModal(makePublication({ status: 'BLOCKED', blockedReason }))
      await flushPromises()

      expect(wrapper.text()).toContain('postDetail.failure.unknown.label')
      expect(wrapper.text()).toContain('postDetail.failure.unknown.explanation')
      expect(wrapper.text()).toContain('postDetail.failure.unknown.action')
      if (blockedReason) expect(wrapper.text()).not.toContain(blockedReason)
    })

    it('never renders sensitive raw failure diagnostics', async () => {
      const sensitive =
        'com.example.StorageObjectNotFoundException at stack trace https://api.test/path token=secret workspace-123 bucket/key Request failed'
      const wrapper = mountModal(makePublication({ status: 'FAILED', errorCode: sensitive }))
      await flushPromises()

      for (const fragment of [
        'StorageObjectNotFoundException',
        'https://api.test',
        'token=secret',
        'workspace-123',
        'bucket/key',
        'Request failed',
      ]) {
        expect(wrapper.text()).not.toContain(fragment)
      }
      expect(wrapper.text()).toContain('postDetail.failure.unknown.label')
    })

    it('calls retryPublication and emits retried when failed retry succeeds', async () => {
      storeOverrides.isPublicationEditable = () => false
      mockRetry.mockResolvedValue(makePublication({ id: 'pub-1', status: 'QUEUED' }))
      const wrapper = mountModal(makePublication({ status: 'FAILED' }))

      const retryButton = wrapper
        .findAll('button')
        .find((button) => button.text().includes('postDetail.retry'))
      expect(retryButton).toBeDefined()
      await retryButton!.trigger('click')
      await flushPromises()

      expect(mockRetry).toHaveBeenCalledWith('pub-1')
      expect(wrapper.emitted('retried')).toEqual([['pub-1']])
      expect(wrapper.emitted('close')).toHaveLength(1)
    })

    it.each([
      [{ status: 401 }, 'unauthorized'],
      [{ status: 403 }, 'unauthorized'],
      [{ status: 400 }, 'validation'],
      [{ status: 500 }, 'temporarilyUnavailable'],
      [{ errorCode: 'UNEXPECTED_PROVIDER_VALUE', status: 418 }, 'unknown'],
    ] as const)('maps retry action error %o to %s safe copy', async (errorFields, expectedReason) => {
      storeOverrides.isPublicationEditable = () => false
      mockRetry.mockRejectedValueOnce(
        Object.assign(new Error('Request failed: token leaked'), {
          ...errorFields,
          detail: 'raw backend detail',
        }),
      )
      const wrapper = mountModal(makePublication({ status: 'FAILED' }))

      const retryButton = wrapper
        .findAll('button')
        .find((button) => button.text().includes('postDetail.retry'))
      expect(retryButton).toBeDefined()
      await retryButton!.trigger('click')
      await flushPromises()

      expect(wrapper.text()).toContain(`postDetail.actionErrors.reasons.${expectedReason}`)
      expect(wrapper.text()).toContain('postDetail.actionErrors.operations.retry')
      expect(wrapper.text()).not.toContain('Request failed')
      expect(wrapper.text()).not.toContain('raw backend detail')
    })

    it('maps retry network action errors to safe localized copy and clears it when the modal reopens', async () => {
      storeOverrides.isPublicationEditable = () => false
      mockRetry.mockRejectedValueOnce(
        Object.assign(new Error('Request failed: token leaked'), {
          status: undefined,
          detail: 'raw backend detail',
        }),
      )
      const wrapper = mountModal(makePublication({ status: 'FAILED' }))

      const retryButton = wrapper
        .findAll('button')
        .find((button) => button.text().includes('postDetail.retry'))
      expect(retryButton).toBeDefined()
      await retryButton!.trigger('click')
      await flushPromises()
      expect(wrapper.text()).toContain('postDetail.actionErrors.reasons.temporarilyUnavailable')
      expect(wrapper.text()).toContain('postDetail.actionErrors.operations.retry')
      expect(wrapper.text()).not.toContain('Request failed')
      expect(wrapper.text()).not.toContain('raw backend detail')

      await wrapper.setProps({ isOpen: false })
      await nextTick()
      await wrapper.setProps({ isOpen: true })
      await flushPromises()

      expect(wrapper.text()).not.toContain('postDetail.actionErrors.reasons.temporarilyUnavailable')
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

    it('maps delete action errors to safe localized copy', async () => {
      storeOverrides.deleteError = Object.assign(
        new Error('com.example.DeleteException raw detail'),
        { status: 404 },
      )
      const wrapper = mountModal(makePublication())

      const deleteBtn = wrapper
        .findAll('button')
        .find((button) => button.text().includes('postDetail.delete'))
      await deleteBtn!.trigger('click')
      await flushPromises()

      expect(wrapper.text()).toContain('postDetail.actionErrors.reasons.notFound')
      expect(wrapper.text()).toContain('postDetail.actionErrors.operations.delete')
      expect(wrapper.text()).not.toContain('com.example.DeleteException')
      expect(wrapper.text()).not.toContain('raw detail')
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

    it('shows safe delete error and does not close modal on failure', async () => {
      storeOverrides.deleteError = Object.assign(new Error('Delete failed'), { status: 409 })
      const wrapper = mountModal(makePublication())

      const deleteButton = wrapper
        .findAll('button')
        .find((button) => button.text().includes('postDetail.delete'))
      await deleteButton!.trigger('click')
      await flushPromises()

      expect(wrapper.text()).toContain('postDetail.actionErrors.reasons.stateConflict')
      expect(wrapper.text()).not.toContain('Delete failed')
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

    it('maps reschedule action errors to safe localized copy', async () => {
      storeOverrides.rescheduleError = Object.assign(
        new Error('Reschedule failed with /bucket/object'),
        { status: 422 },
      )
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
      expect(wrapper.text()).toContain('postDetail.actionErrors.reasons.validation')
      expect(wrapper.text()).toContain('postDetail.actionErrors.operations.reschedule')
      expect(wrapper.text()).not.toContain('Reschedule failed')
      expect(wrapper.text()).not.toContain('/bucket/object')
    })

    it('uses localized copy for invalid reschedule dates', async () => {
      storeOverrides.isPublicationEditable = () => false
      const wrapper = mountModal(
        makePublication({ status: 'PROCESSING', scheduledAt: '2027-07-01T10:00:00Z' }),
      )

      const rescheduleBtn = wrapper
        .findAll('button')
        .find((b) => b.text().includes('postDetail.reschedule'))
      await rescheduleBtn!.trigger('click')
      await wrapper.find('input#reschedule-datetime').setValue('2020-01-01T10:00')

      const confirmBtn = wrapper
        .findAll('button')
        .find((b) => b.text().includes('postDetail.rescheduleConfirm'))
      await confirmBtn!.trigger('click')

      expect(wrapper.text()).toContain('postDetail.rescheduleInvalidDate')
      expect(wrapper.text()).not.toContain('Please select a valid future date and time.')
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
