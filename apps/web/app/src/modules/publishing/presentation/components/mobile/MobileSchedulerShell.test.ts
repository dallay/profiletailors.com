import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import MobileSchedulerShell from './MobileSchedulerShell.vue'

vi.mock('vue-i18n', () => ({
  createI18n: () => ({ global: { locale: { value: 'en' } } }),
  useI18n: () => ({ t: (key: string) => key }),
}))
vi.mock('@/components/ui/button', () => ({ Button: { template: '<button><slot /></button>' } }))
vi.mock('@lucide/vue', () => {
  const icon = { template: '<svg />' }
  return {
    CalendarDays: icon,
    ChevronLeft: icon,
    ChevronRight: icon,
    Filter: icon,
    Plus: icon,
    MoreHorizontal: icon,
  }
})
vi.mock('@/components/ui/sheet', () => ({
  Sheet: { template: '<div><slot /></div>' },
  SheetClose: { template: '<div><slot /></div>' },
  SheetContent: { template: '<section><slot /></section>' },
  SheetFooter: { template: '<footer><slot /></footer>' },
  SheetHeader: { template: '<header><slot /></header>' },
  SheetTitle: { template: '<h2><slot /></h2>' },
}))
vi.mock('@/components/ui/dropdown-menu', () => ({
  DropdownMenu: { template: '<div><slot /></div>' },
  DropdownMenuTrigger: { template: '<div><slot /></div>' },
  DropdownMenuContent: { template: '<div><slot /></div>' },
  DropdownMenuItem: { template: '<div><slot /></div>' },
}))

describe('MobileSchedulerShell', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  const baseProps = {
    title: 'Scheduler',
    view: 'week' as const,
    periodLabel: 'Jun 15 – 21, 2026',
    days: [],
    hourSlots: [],
    status: 'all' as const,
    timezone: 'UTC',
    channelIds: [],
    q: '',
    publicationsForSlot: () => [],
    isToday: () => false,
    formatDayName: () => 'Monday',
    isPastSlot: () => false,
    hasNoChannels: false,
    now: new Date('2026-06-15T09:30:00'),
    formatCurrentTime: (date: Date) =>
      date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
  }

  it('composes the mobile shell and delegates agenda content to its slot', () => {
    const wrapper = mount(MobileSchedulerShell, {
      props: { ...baseProps, view: 'agenda' },
      slots: { agendaSlot: '<p data-testid="agenda-content">Agenda content</p>' },
      global: { mocks: { $t: (key: string) => key } },
    })

    expect(wrapper.get('[data-testid="scheduler-mobile-shell"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="agenda-content"]').text()).toBe('Agenda content')
    expect(wrapper.find('[data-testid="scheduler-timeline-viewport"]').exists()).toBe(false)
  })
})
