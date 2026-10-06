import { mount } from '@vue/test-utils'
import { h } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import DashboardShell from './DashboardShell.vue'

vi.mock('./tooltip', () => ({
  TooltipProvider: { template: '<div data-testid="tooltip-provider"><slot /></div>' },
}))

vi.mock('./sidebar', () => ({
  Sidebar: { template: '<aside data-testid="sidebar"><slot /></aside>' },
  SidebarContent: { template: '<section data-testid="sidebar-content"><slot /></section>' },
  SidebarFooter: { template: '<footer data-testid="sidebar-footer"><slot /></footer>' },
  SidebarHeader: { template: '<header data-testid="sidebar-header"><slot /></header>' },
  SidebarInset: { template: '<main data-testid="sidebar-inset"><slot /></main>' },
  SidebarProvider: { template: '<div data-testid="sidebar-provider"><slot /></div>' },
}))

const nav = {
  groups: [{ key: 'main', label: 'Main', items: [] }],
}

describe('DashboardShell', () => {
  it('provides app-specific slots and sidebar structure', () => {
    const wrapper = mount(DashboardShell, {
      props: { nav },
      slots: {
        header: '<div>Workspace</div>',
        content: '<div>Navigation</div>',
        inset: '<div>Page</div>',
        pwa: '<div>PWA</div>',
        account: '<div>Account</div>',
        footer: '<div>Footer</div>',
      },
    })

    expect(wrapper.get('[data-testid="tooltip-provider"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="sidebar-provider"]').attributes('class')).toContain(
      'font-sans',
    )
    expect(wrapper.get('[data-testid="sidebar-header"]').text()).toBe('Workspace')
    expect(wrapper.get('[data-testid="sidebar-content"]').text()).toBe('Navigation')
    expect(wrapper.get('[data-testid="sidebar-inset"]').text()).toBe('Page')
    expect(wrapper.get('[data-testid="sidebar-footer"]').text()).toContain('PWA')
    expect(wrapper.get('[data-testid="sidebar-footer"]').text()).toContain('Account')
    expect(wrapper.get('[data-testid="sidebar-footer"]').text()).toContain('Footer')
  })

  it('emits signOut and openSettings from account slot callbacks', async () => {
    let accountProps: Record<string, () => void> = {}
    const wrapper = mount(DashboardShell, {
      props: { nav },
      slots: {
        account: (props: Record<string, () => void>) => {
          accountProps = props
          return h('div')
        },
      },
    })

    accountProps.signOut()
    accountProps.openSettings()
    await wrapper.vm.$nextTick()

    expect(wrapper.emitted('signOut')).toHaveLength(1)
    expect(wrapper.emitted('openSettings')).toHaveLength(1)
  })
})
