import { describe, it, expect, vi } from 'vitest'
import { defineComponent, h, markRaw } from 'vue'
import { mount } from '@vue/test-utils'
import SidebarNavSection, { type NavGroup } from './SidebarNavSection.vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

const StubIcon = markRaw(defineComponent({ name: 'StubIcon', render: () => h('svg') }))

function makeGroups(): NavGroup[] {
  return [
    {
      label: 'Workspace',
      items: [
        { labelKey: 'nav.dashboard', to: '/', icon: StubIcon },
        { labelKey: 'nav.scheduler', to: '/scheduler', icon: StubIcon },
        { labelKey: 'nav.analytics', to: '/analytics', icon: StubIcon, badge: 'Live' },
      ],
    },
    {
      label: 'System',
      items: [{ labelKey: 'nav.settings', to: '/settings', icon: StubIcon }],
    },
  ]
}

describe('SidebarNavSection', () => {
  it('renders both group labels and all items', () => {
    const wrapper = mount(SidebarNavSection, {
      props: { groups: makeGroups(), totalQueuedCount: 0 },
    })

    const text = wrapper.text()
    expect(text).toContain('Workspace')
    expect(text).toContain('System')
    expect(text).toContain('nav.dashboard')
    expect(text).toContain('nav.scheduler')
    expect(text).toContain('nav.analytics')
    expect(text).toContain('nav.settings')
  })

  it('zero-pads the Dashboard badge for counts 0–9 and renders raw counts for 10+', () => {
    const groups = makeGroups()

    const w7 = mount(SidebarNavSection, { props: { groups, totalQueuedCount: 7 } })
    expect(w7.text()).toContain('07')

    const w12 = mount(SidebarNavSection, { props: { groups, totalQueuedCount: 12 } })
    expect(w12.text()).toContain('12')
    expect(w12.text()).not.toContain('012')
  })

  it('emits navigate with the item `to` on click', async () => {
    const wrapper = mount(SidebarNavSection, {
      props: { groups: makeGroups(), totalQueuedCount: 0 },
    })

    // The second item is scheduler — find the button with that label
    const schedulerBtn = wrapper.findAll('button').find((b) => b.text().includes('nav.scheduler'))
    expect(schedulerBtn).toBeTruthy()
    await schedulerBtn?.trigger('click')

    expect(wrapper.emitted('navigate')).toBeTruthy()
    expect(wrapper.emitted('navigate')?.[0]).toEqual(['/scheduler'])
  })
})
