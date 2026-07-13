import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

// Mock vue-i18n to avoid pulling in the full i18n setup just for these tests.
vi.mock('@/i18n', () => {
  return {
    default: {
      global: {
        locale: { value: 'en' },
      },
    },
  }
})

interface Stubs {
  storage: Map<string, string>
  classList: {
    add: ReturnType<typeof vi.fn>
    remove: ReturnType<typeof vi.fn>
    toggle: ReturnType<typeof vi.fn>
  }
  setAttribute: ReturnType<typeof vi.fn>
  localStorageMock: {
    setItem: ReturnType<typeof vi.fn>
    getItem: ReturnType<typeof vi.fn>
  }
}

// Build a global stubs bundle and override document.documentElement + localStorage
// so the store reaches our mocks when it calls into the DOM/storage.
function setupBrowserStubs(initialStored: string | null): Stubs {
  const storage = new Map<string, string>()
  if (initialStored !== null) {
    storage.set('pt_settings_v1', initialStored)
  }

  const classList = {
    add: vi.fn(),
    remove: vi.fn(),
    toggle: vi.fn(),
  }

  const setAttribute = vi.fn()

  const localStorageMock = {
    setItem: vi.fn((key: string, value: string) => {
      storage.set(key, value)
    }),
    getItem: vi.fn((key: string) => storage.get(key) ?? null),
    removeItem: vi.fn((key: string) => {
      storage.delete(key)
    }),
    clear: vi.fn(() => {
      storage.clear()
    }),
    key: vi.fn(),
    length: 0,
  } as unknown as Storage

  vi.stubGlobal('localStorage', localStorageMock)

  // Mutate the real documentElement to expose our mocks. The store calls
  // document.documentElement.classList.toggle(...) and setAttribute(...).
  Object.defineProperty(document.documentElement, 'classList', {
    configurable: true,
    get: () => classList,
  })
  const originalSetAttribute = document.documentElement.setAttribute
  document.documentElement.setAttribute = setAttribute as typeof originalSetAttribute

  return {
    storage,
    classList,
    setAttribute,
    localStorageMock: localStorageMock as unknown as Stubs['localStorageMock'],
  }
}

describe('settings store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.unstubAllGlobals()
  })

  it('defaults to dark + en when no persisted state exists', async () => {
    const stubs = setupBrowserStubs(null)
    const { useSettingsStore } = await import('@modules/settings/infrastructure/settings.store')
    const store = useSettingsStore()

    expect(store.currentTheme).toBe('dark')
    expect(store.currentLocale).toBe('en')
    // Defaults should be applied to the DOM.
    expect(stubs.classList.toggle).toHaveBeenCalledWith('dark', true)
    expect(stubs.setAttribute).toHaveBeenCalledWith('lang', 'en')
  })

  it('hydrates from localStorage on init', async () => {
    const stubs = setupBrowserStubs(JSON.stringify({ locale: 'es', theme: 'light' }))
    const { useSettingsStore } = await import('@modules/settings/infrastructure/settings.store')
    const store = useSettingsStore()

    expect(store.currentTheme).toBe('light')
    expect(store.currentLocale).toBe('es')
    expect(stubs.classList.toggle).toHaveBeenCalledWith('light', true)
    expect(stubs.classList.toggle).toHaveBeenCalledWith('dark', false)
  })

  it('falls back to defaults when stored value is malformed', async () => {
    setupBrowserStubs('not-json')
    const { useSettingsStore } = await import('@modules/settings/infrastructure/settings.store')
    const store = useSettingsStore()

    expect(store.currentTheme).toBe('dark')
    expect(store.currentLocale).toBe('en')
  })

  it('falls back to defaults when stored values are out of range', async () => {
    setupBrowserStubs(JSON.stringify({ locale: 'fr', theme: 'neon' }))
    const { useSettingsStore } = await import('@modules/settings/infrastructure/settings.store')
    const store = useSettingsStore()

    expect(store.currentTheme).toBe('dark')
    expect(store.currentLocale).toBe('en')
  })

  it('setTheme updates state, DOM, and storage', async () => {
    const stubs = setupBrowserStubs(null)
    const { useSettingsStore } = await import('@modules/settings/infrastructure/settings.store')
    const store = useSettingsStore()

    // Capture the call count before mutating, then verify that
    // setTheme triggers a fresh persistence write.
    const callsBefore = stubs.localStorageMock.setItem.mock.calls.length
    stubs.classList.toggle.mockClear()

    store.setTheme('light')

    expect(store.currentTheme).toBe('light')
    expect(stubs.classList.toggle).toHaveBeenCalledWith('light', true)
    expect(stubs.classList.toggle).toHaveBeenCalledWith('dark', false)
    // A new write must have happened with the new theme value.
    expect(stubs.localStorageMock.setItem.mock.calls.length).toBeGreaterThan(callsBefore)
    const lastCall = stubs.localStorageMock.setItem.mock.calls.at(-1)
    expect(lastCall?.[0]).toBe('pt_settings_v1')
    expect(JSON.parse(String(lastCall?.[1]))).toEqual({ locale: 'en', theme: 'light' })
  })

  it('toggleTheme switches between dark and light', async () => {
    setupBrowserStubs(null)
    const { useSettingsStore } = await import('@modules/settings/infrastructure/settings.store')
    const store = useSettingsStore()

    expect(store.currentTheme).toBe('dark')
    store.toggleTheme()
    expect(store.currentTheme).toBe('light')
    store.toggleTheme()
    expect(store.currentTheme).toBe('dark')
  })

  it('setLocale updates state and the lang attribute', async () => {
    const stubs = setupBrowserStubs(null)
    const { useSettingsStore } = await import('@modules/settings/infrastructure/settings.store')
    const store = useSettingsStore()

    stubs.setAttribute.mockClear()
    store.setLocale('es')

    expect(store.currentLocale).toBe('es')
    expect(stubs.setAttribute).toHaveBeenCalledWith('lang', 'es')
  })

  it('toggleLocale switches between en and es', async () => {
    setupBrowserStubs(null)
    const { useSettingsStore } = await import('@modules/settings/infrastructure/settings.store')
    const store = useSettingsStore()

    expect(store.currentLocale).toBe('en')
    store.toggleLocale()
    expect(store.currentLocale).toBe('es')
    store.toggleLocale()
    expect(store.currentLocale).toBe('en')
  })
})
