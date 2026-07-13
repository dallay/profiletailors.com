import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ConflictBadge from './ConflictBadge.vue'
import CalendarCell from './CalendarCell.vue'
import type { Publication, ActivityEntry } from '@/stores/publishing'

// ---------------------------------------------------------------------------
// Mock dependencies shared across component tests
// ---------------------------------------------------------------------------

// Mock vue-i18n to provide a locale value
vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    locale: { value: 'en' },
  }),
}))

// Mock auth-api required by publishing store
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

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makePublication(overrides: Partial<Publication> = {}): Publication {
  return {
    id: 'pub-test-1',
    content: 'Test post content',
    title: 'Test Title',
    channels: ['linkedin'],
    scheduledAt: '2026-06-15T20:00:00Z',
    status: 'SCHEDULED',
    priority: false,
    ...overrides,
  }
}

function makeActivity(overrides: Partial<ActivityEntry> = {}): ActivityEntry {
  return {
    date: '2026-06-15',
    density: 'MEDIUM',
    count: 4,
    ...overrides,
  }
}

// ---------------------------------------------------------------------------
// ConflictBadge Tests
// ---------------------------------------------------------------------------

describe('ConflictBadge', () => {
  it('renders dot variant with correct class', () => {
    const wrapper = mount(ConflictBadge, {
      props: { variant: 'dot' },
    })
    const badge = wrapper.find('output')
    expect(badge.classes()).toContain('rounded-full')
    // Dot variant should be small with no text content
    expect(badge.text()).toBe('')
  })

  it('renders badge variant with exclamation mark', () => {
    const wrapper = mount(ConflictBadge, {
      props: { variant: 'badge' },
    })
    expect(wrapper.text()).toContain('!')
  })

  it('renders inline variant with "Conflict" label', () => {
    const wrapper = mount(ConflictBadge, {
      props: { variant: 'inline' },
    })
    expect(wrapper.text()).toContain('Conflict')
  })

  it('defaults to badge variant', () => {
    const wrapper = mount(ConflictBadge)
    expect(wrapper.text()).toContain('!')
  })

  it('applies custom reason as title attribute', () => {
    const wrapper = mount(ConflictBadge, {
      props: { variant: 'badge', reason: 'Custom conflict reason' },
    })
    expect(wrapper.find('output').attributes('title')).toBe('Custom conflict reason')
  })

  it('has accessible role and aria-label', () => {
    const wrapper = mount(ConflictBadge, {
      props: { variant: 'badge' },
    })
    // <output> has implicit status role — no explicit role attribute needed
    expect(wrapper.find('output').attributes('aria-label')).toBe('Conflict')
  })
})

// ---------------------------------------------------------------------------
// CalendarCell Tests
// ---------------------------------------------------------------------------

describe('CalendarCell', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders the day number', () => {
    const date = new Date(2026, 5, 15) // June 15, 2026
    const wrapper = mount(CalendarCell, {
      props: {
        date,
        isCurrentMonth: true,
        isToday: false,
        publications: [],
      },
    })
    expect(wrapper.text()).toContain('15')
  })

  it('highlights today with special styling', () => {
    const date = new Date(2026, 5, 15)
    const wrapper = mount(CalendarCell, {
      props: {
        date,
        isCurrentMonth: true,
        isToday: true,
        publications: [],
      },
    })
    // The day number span should have the today highlight class
    const daySpan = wrapper.find('span')
    expect(daySpan.classes()).toContain('bg-text-display')
    expect(daySpan.classes()).toContain('text-bg-primary')
  })

  it('dims out-of-month days', () => {
    const date = new Date(2026, 4, 31) // May 31, previous month
    const wrapper = mount(CalendarCell, {
      props: {
        date,
        isCurrentMonth: false,
        isToday: false,
        publications: [],
      },
    })
    // Root div should have bg-bg-surface/30 for out-of-month
    expect(wrapper.classes()).toContain('bg-bg-surface/30')
  })

  it('renders publication snippets', () => {
    const pubs = [
      makePublication({ id: 'p1', title: 'First Post' }),
      makePublication({ id: 'p2', title: 'Second Post' }),
    ]
    const wrapper = mount(CalendarCell, {
      props: {
        date: new Date(2026, 5, 15),
        isCurrentMonth: true,
        isToday: false,
        publications: pubs,
      },
    })
    expect(wrapper.text()).toContain('First Post')
    expect(wrapper.text()).toContain('Second Post')
  })

  it('truncates publications beyond maxVisible', () => {
    const pubs = [
      makePublication({ id: 'p1', title: 'Post 1' }),
      makePublication({ id: 'p2', title: 'Post 2' }),
      makePublication({ id: 'p3', title: 'Post 3' }),
      makePublication({ id: 'p4', title: 'Post 4' }),
    ]
    const wrapper = mount(CalendarCell, {
      props: {
        date: new Date(2026, 5, 15),
        isCurrentMonth: true,
        isToday: false,
        publications: pubs,
        maxVisible: 3,
      },
    })
    expect(wrapper.text()).toContain('+1 more')
    // Should show first 3 but not the 4th
    expect(wrapper.text()).toContain('Post 1')
    expect(wrapper.text()).not.toContain('Post 4')
  })

  it('shows activity dot when activity entry is provided', () => {
    const activity = makeActivity({ density: 'MEDIUM' })
    const wrapper = mount(CalendarCell, {
      props: {
        date: new Date(2026, 5, 15),
        isCurrentMonth: true,
        isToday: false,
        publications: [],
        activityEntry: activity,
      },
    })
    // Should render the activity dot div with orange color for MEDIUM
    const dots = wrapper.findAll('.rounded-full.bg-orange-400')
    expect(dots.length).toBeGreaterThanOrEqual(1)
  })

  it('hides activity dot for out-of-month cells', () => {
    const activity = makeActivity({ density: 'MEDIUM' })
    const wrapper = mount(CalendarCell, {
      props: {
        date: new Date(2026, 4, 31),
        isCurrentMonth: false,
        isToday: false,
        publications: [],
        activityEntry: activity,
      },
    })
    // Activity dot should not render when isCurrentMonth is false
    const dots = wrapper.findAll('.bg-orange-400')
    expect(dots).toHaveLength(0)
  })

  it('shows correct density colors', () => {
    // LIGHT = yellow
    const lightWrapper = mount(CalendarCell, {
      props: {
        date: new Date(2026, 5, 15),
        isCurrentMonth: true,
        isToday: false,
        publications: [],
        activityEntry: makeActivity({ density: 'LIGHT' }),
      },
    })
    expect(lightWrapper.findAll('.bg-yellow-400').length).toBeGreaterThanOrEqual(1)

    // HIGH = green
    const highWrapper = mount(CalendarCell, {
      props: {
        date: new Date(2026, 5, 15),
        isCurrentMonth: true,
        isToday: false,
        publications: [],
        activityEntry: makeActivity({ density: 'HIGH' }),
      },
    })
    expect(highWrapper.findAll('.bg-green-500').length).toBeGreaterThanOrEqual(1)
  })

  it('renders conflict badge on publication with hasConflict', () => {
    const pubs = [makePublication({ hasConflict: true })]
    const wrapper = mount(CalendarCell, {
      props: {
        date: new Date(2026, 5, 15),
        isCurrentMonth: true,
        isToday: false,
        publications: pubs,
      },
    })
    // Conflict badge uses bg-error class
    const conflictBadge = wrapper.findComponent(ConflictBadge)
    expect(conflictBadge.exists()).toBe(true)
  })

  it('renders thumbnail image when publication has one', () => {
    const pubs = [
      makePublication({
        id: 'pub-with-thumb',
        thumbnail: 'https://example.com/image.jpg',
      }),
    ]
    const wrapper = mount(CalendarCell, {
      props: {
        date: new Date(2026, 5, 15),
        isCurrentMonth: true,
        isToday: false,
        publications: pubs,
      },
    })
    const img = wrapper.find('img')
    expect(img.exists()).toBe(true)
    expect(img.attributes('src')).toBe('https://example.com/image.jpg')
  })

  it('keeps text visible beside the thumbnail in month view', () => {
    const pubs = [
      makePublication({
        id: 'pub-with-thumb-and-text',
        title: 'Visible Title',
        thumbnail: 'https://example.com/image.jpg',
      }),
    ]
    const wrapper = mount(CalendarCell, {
      props: {
        date: new Date(2026, 5, 15),
        isCurrentMonth: true,
        isToday: false,
        publications: pubs,
      },
    })
    const row = wrapper
      .findAll('button')
      .find((b) => b.classes().some((c) => c.includes('flex-row')))
    expect(row?.exists()).toBe(true)
    expect(wrapper.text()).toContain('Visible Title')
    expect(row?.find('img').exists()).toBe(true)
  })

  it('emits click-day when current-month cell is clicked', async () => {
    const date = new Date(2026, 5, 15)
    const wrapper = mount(CalendarCell, {
      props: {
        date,
        isCurrentMonth: true,
        isToday: false,
        publications: [],
      },
    })
    await wrapper.trigger('click')
    expect(wrapper.emitted('click-day')).toBeTruthy()
    const emittedDate = wrapper.emitted('click-day')?.[0]?.[0] as Date
    expect(emittedDate.getDate()).toBe(15)
  })
})
