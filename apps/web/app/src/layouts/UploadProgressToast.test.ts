import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import UploadProgressToast from './UploadProgressToast.vue'
import { useMediaStore, type UploadItem } from '@modules/media/infrastructure/media.store'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key, locale: { value: 'en' } }),
}))

vi.mock('@lucide/vue', () => {
  const stub = { template: '<svg />' }
  return {
    Upload: stub,
    CheckCircle: stub,
    AlertCircle: stub,
    XCircle: stub,
    ChevronDown: stub,
    ChevronUp: stub,
    X: stub,
  }
})

vi.mock('@modules/auth/infrastructure/auth-api', () => ({
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

function makeFile(name = 'photo.jpg'): File {
  return new File(['fake-content'], name, { type: 'image/jpeg' })
}

function makeUploadItem(overrides: Partial<UploadItem> = {}): UploadItem {
  return {
    tempKey: 'temp-1',
    assetId: 'asset-1',
    file: makeFile(),
    progress: 50,
    status: 'uploading',
    ...overrides,
  }
}

function mountToast() {
  return mount(UploadProgressToast, {
    global: {
      stubs: { Teleport: true },
    },
  })
}

const wrappers: ReturnType<typeof mount>[] = []

describe('UploadProgressToast', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    // Clear any leftover DOM elements from previous stubbed Teleports
    document.body.innerHTML = ''
  })

  afterEach(() => {
    wrappers.forEach((w) => w.unmount())
    wrappers.length = 0
  })

  it('is hidden when there are no uploads', () => {
    const wrapper = mountToast()
    wrappers.push(wrapper)

    expect(wrapper.find('output').exists()).toBe(false)
  })

  it('shows when there are active uploads', async () => {
    const store = useMediaStore()
    store.uploadList.push(makeUploadItem())

    const wrapper = mountToast()
    wrappers.push(wrapper)

    await flushPromises()
    // With stubbed Teleport, content renders inside the wrapper
    expect(wrapper.find('output').exists()).toBe(true)
    expect(wrapper.text()).toContain('media.uploadProgress.uploading')
  })

  it('shows done state when all uploads complete', async () => {
    const store = useMediaStore()
    store.uploadList.push(makeUploadItem({ status: 'done', progress: 100 }))

    const wrapper = mountToast()
    wrappers.push(wrapper)

    await flushPromises()
    expect(wrapper.text()).toContain('media.uploadProgress.uploaded')
  })

  it('shows partial state when some uploads fail', async () => {
    const store = useMediaStore()
    store.uploadList.push(makeUploadItem({ tempKey: 't1', status: 'done', progress: 100 }))
    store.uploadList.push(makeUploadItem({ tempKey: 't2', status: 'failed', progress: 50 }))

    const wrapper = mountToast()
    wrappers.push(wrapper)

    await flushPromises()
    expect(wrapper.text()).toContain('media.uploadProgress.partial')
  })

  it('shows failed state when all uploads fail', async () => {
    const store = useMediaStore()
    store.uploadList.push(makeUploadItem({ tempKey: 't1', status: 'failed' }))
    store.uploadList.push(makeUploadItem({ tempKey: 't2', status: 'conflict' }))

    const wrapper = mountToast()
    wrappers.push(wrapper)

    await flushPromises()
    expect(wrapper.text()).toContain('media.uploadProgress.failed')
  })

  it('expands and collapses detail on header click', async () => {
    const store = useMediaStore()
    store.uploadList.push(makeUploadItem())

    const wrapper = mountToast()
    wrappers.push(wrapper)
    await flushPromises()

    // Initially collapsed — no detail shown
    expect(wrapper.text()).not.toContain('photo.jpg')

    // Click header to expand
    const header = wrapper.find('button')
    await header.trigger('click')

    expect(wrapper.text()).toContain('photo.jpg')

    // Click again to collapse
    await header.trigger('click')
    expect(wrapper.text()).not.toContain('photo.jpg')
  })

  it('dismiss button clears uploads', async () => {
    const store = useMediaStore()
    store.uploadList.push(makeUploadItem())
    vi.spyOn(store, 'clearUploads')

    const wrapper = mountToast()
    wrappers.push(wrapper)
    await flushPromises()

    const dismissBtn = wrapper.find('[aria-label="Dismiss"]')
    await dismissBtn.trigger('click')

    expect(store.clearUploads).toHaveBeenCalled()
  })

  it('shows progress percentage for uploading items', async () => {
    const store = useMediaStore()
    store.uploadList.push(makeUploadItem({ progress: 42 }))

    const wrapper = mountToast()
    wrappers.push(wrapper)
    await flushPromises()

    // Expand to see detail
    const header = wrapper.find('button')
    await header.trigger('click')

    expect(wrapper.text()).toContain('42%')
  })

  it('shows error detail for failed uploads', async () => {
    const store = useMediaStore()
    store.uploadList.push(
      makeUploadItem({
        status: 'failed',
        errorTitle: 'Upload failed',
        errorDetail: 'Connection timed out',
      }),
    )

    const wrapper = mountToast()
    wrappers.push(wrapper)
    await flushPromises()

    const header = wrapper.find('button')
    await header.trigger('click')

    // errorTitle is shown as visible text; errorDetail is in the title attribute
    expect(wrapper.text()).toContain('Upload failed')
    const errorLine = wrapper.find('[title="Connection timed out"]')
    expect(errorLine.exists()).toBe(true)
  })
})
