import { describe, it, expect, vi } from 'vitest'
import { defineComponent, h, markRaw } from 'vue'
import { mount, type DOMWrapper } from '@vue/test-utils'
import type { NavGroup } from '@profiletailors/vue-ui/shell/ports'
import SidebarNavSection from './SidebarNavSection.vue'

const sidebar = vi.hoisted(() => ({
  isMobile: { value: true },
  setOpenMobile: vi.fn(),
}))

vi.mock('@profiletailors/vue-ui/shell/sidebar/utils', () => ({
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

const StubIcon = markRaw(defineComponent({ name: 'StubIcon', render: () => h('svg') }))

function makeGroups(): NavGroup[] {
  return [
    {
      key: 'primary',
      label: 'Workspace',
      items: [
        { key: 'dashboard', labelKey: 'nav.dashboard', path: '/', iconComponent: StubIcon },
        {
          key: 'scheduler',
          labelKey: 'nav.scheduler',
          path: '/scheduler',
          iconComponent: StubIcon,
        },
        {
          key: 'analytics',
          labelKey: 'nav.analytics',
          path: '/analytics',
          iconComponent: StubIcon,
          badge: 'Live',
        },
        { key: 'media', labelKey: 'nav.media', path: '/media', iconComponent: StubIcon },
      ],
    },
    {
      key: 'system',
      label: 'System',
      items: [
        { key: 'settings', labelKey: 'nav.settings', path: '/settings', iconComponent: StubIcon },
      ],
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

  it('keeps translated titles on links when labels collapse to icons', () => {
    const wrapper = mount(SidebarNavSection, {
      ...mountOptions,
      props: { groups: makeGroups(), totalQueuedCount: 0 },
    })
    for (const item of makeGroups().flatMap((group) => group.items)) {
      expect(wrapper.get(`a[href="${item.path}"]`).attributes('title')).toBe(item.labelKey)
      expect(wrapper.get(`a[href="${item.path}"] .sr-only`).text()).toBe(item.labelKey)
    }
  })

  it('omits an empty group label while retaining its navigation items', () => {
    const groups = makeGroups()
    groups[0]!.label = ''
    const wrapper = mount(SidebarNavSection, {
      ...mountOptions,
      props: { groups, totalQueuedCount: 0 },
    })
    expect(wrapper.findAll('p').map((label) => label.text())).toEqual(['System'])
    expect(wrapper.findAll('a')).toHaveLength(5)
    expect(wrapper.get('a[href="/"]').text()).toContain('nav.dashboard')
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
    expect(links).toHaveLength(5)

    const schedulerLink = links.find((l: DOMWrapper<Element>) => l.text().includes('nav.scheduler'))
    expect(schedulerLink).toBeTruthy()
    expect(schedulerLink?.attributes('href')).toBe('/scheduler')
  })

  it('closes the mobile sidebar after selecting a navigation item', async () => {
    const groups: NavGroup[] = [
      {
        key: 'primary',
        label: 'Workspace',
        items: [{ key: 'ideas', labelKey: 'nav.ideas', path: '/ideas', iconComponent: StubIcon }],
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
