import { describe, it, expect, vi } from 'vitest'
import type { LucideIcon } from '@lucide/vue'
import { defineComponent, h, markRaw } from 'vue'
import { mount, type DOMWrapper } from '@vue/test-utils'
import SidebarNavSection, { type NavGroup } from './SidebarNavSection.vue'

const sidebar = vi.hoisted(() => ({
  isMobile: { value: true },
  setOpenMobile: vi.fn(),
}))

vi.mock('@/components/ui/sidebar/utils', () => ({
  useSidebar: () => sidebar,
}))

const RouterLinkStub = defineComponent({
  name: 'RouterLink',
  props: ['to'],
  template: '<a :href="to"><slot /></a>',
})

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

const StubIcon = markRaw(
  defineComponent({ name: 'StubIcon', render: () => h('svg') }),
) as unknown as LucideIcon

function makeGroups(): NavGroup[] {
  return [
    {
      label: 'Workspace',
      items: [
        { labelKey: 'nav.dashboard', to: '/', icon: StubIcon },
        { labelKey: 'nav.scheduler', to: '/scheduler', icon: StubIcon },
        { labelKey: 'nav.analytics', to: '/analytics', icon: StubIcon, badge: 'Live' },
        { labelKey: 'nav.media', to: '/media', icon: StubIcon },
      ],
    },
    {
      label: 'System',
      items: [{ labelKey: 'nav.settings', to: '/settings', icon: StubIcon }],
    },
  ]
}

const mountOptions = {
  global: {
    stubs: { RouterLink: RouterLinkStub },
  },
}

describe('SidebarNavSection', () => {
  it('renders both group labels and all items', () => {
    const wrapper = mount(SidebarNavSection, {
      ...mountOptions,
      props: { groups: makeGroups(), totalQueuedCount: 0 },
    })

    const text = wrapper.text()
    expect(text).toContain('Workspace')
    expect(text).toContain('System')
    expect(wrapper.findAll('.sr-only').length).toBeGreaterThan(0)
    expect(text).toContain('nav.dashboard')
    expect(text).toContain('nav.scheduler')
    expect(text).toContain('nav.analytics')
    expect(text).toContain('nav.media')
    expect(text).toContain('nav.settings')
  })

  it('zero-pads the Dashboard badge for counts 1–9 and renders raw counts for 10+', () => {
    const groups = makeGroups()

    const w7 = mount(SidebarNavSection, { ...mountOptions, props: { groups, totalQueuedCount: 7 } })
    expect(w7.text()).toContain('07')

    const w12 = mount(SidebarNavSection, {
      ...mountOptions,
      props: { groups, totalQueuedCount: 12 },
    })
    expect(w12.text()).toContain('12')
    expect(w12.text()).not.toContain('012')
  })

  it('omits the Dashboard badge entirely when totalQueuedCount is 0 (no zero-state badge)', () => {
    const groups = makeGroups()
    const wrapper = mount(SidebarNavSection, {
      ...mountOptions,
      props: { groups, totalQueuedCount: 0 },
    })

    // The text should not contain a zero-padded "00" badge for Dashboard
    // (other items like Analytics with badge="Live" still render).
    expect(wrapper.text()).not.toContain('00')
    // But "Live" should still be there (other items pass through).
    expect(wrapper.text()).toContain('Live')
  })

  it('renders RouterLink with correct `to` prop for each navigation item', () => {
    const wrapper = mount(SidebarNavSection, {
      ...mountOptions,
      props: { groups: makeGroups(), totalQueuedCount: 0 },
    })

    const links = wrapper.findAll('a')
    expect(links.length).toBe(5)

    const schedulerLink = links.find((l: DOMWrapper<Element>) => l.text().includes('nav.scheduler'))
    expect(schedulerLink).toBeTruthy()
    expect(schedulerLink?.attributes('href')).toBe('/scheduler')
  })

  it('closes the mobile sidebar after selecting a navigation item', async () => {
    const groups: NavGroup[] = [
      {
        label: 'Workspace',
        items: [{ labelKey: 'nav.ideas', to: '/ideas', icon: StubIcon }],
      },
    ]
    const wrapper = mount(SidebarNavSection, {
      ...mountOptions,
      props: { groups, totalQueuedCount: 0 },
    })

    await wrapper.get('a[href="/ideas"]').trigger('click')

    expect(sidebar.setOpenMobile).toHaveBeenCalledWith(false)
  })
})
