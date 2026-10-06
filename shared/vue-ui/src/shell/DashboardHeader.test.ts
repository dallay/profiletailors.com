import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import DashboardHeader from './DashboardHeader.vue'

vi.mock('./sidebar', () => ({
  SidebarTrigger: {
    props: ['ariaLabel'],
    template: '<button data-testid="sidebar-trigger" :aria-label="ariaLabel" />',
  },
}))

describe('DashboardHeader', () => {
  it('renders title, navigation trigger, data-tour and actions', () => {
    const wrapper = mount(DashboardHeader, {
      props: {
        eyebrow: 'Workspace',
        title: 'Analytics',
        toggleLabel: 'Open navigation',
        titleDataTour: 'section-title',
      },
      slots: { actions: '<button>Help</button>' },
    })

    expect(wrapper.get('header').text()).toContain('Workspace')
    expect(wrapper.get('h1').text()).toBe('Analytics')
    expect(wrapper.get('[data-testid="sidebar-trigger"]').attributes('aria-label')).toBe(
      'Open navigation',
    )
    expect(wrapper.get('div[data-tour="section-title"]').exists()).toBe(true)
    expect(wrapper.get('header').text()).toContain('Help')
  })

  it('omits the data-tour attribute when no title tour is configured', () => {
    const wrapper = mount(DashboardHeader, {
      props: { eyebrow: 'Workspace', title: 'Analytics', toggleLabel: 'Open navigation' },
    })

    expect(wrapper.get('h1').attributes('data-tour')).toBeUndefined()
  })
})
