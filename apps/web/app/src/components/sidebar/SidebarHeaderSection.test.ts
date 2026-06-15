import { describe, it, expect, vi } from 'vitest'
import { reactive, nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import SidebarHeaderSection from './SidebarHeaderSection.vue'

// Reactive route object so the composable's watcher can re-evaluate.
const routeState = reactive({ path: '/' })
vi.mock('vue-router', () => ({
  useRoute: () => routeState,
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
}))

vi.mock('@/components/WorkspaceAvatar.vue', () => ({
  default: { name: 'WorkspaceAvatar', template: '<div class="workspace-avatar" />' },
}))

vi.mock('@lucide/vue', () => ({
  ChevronsUpDown: { name: 'ChevronsUpDown', template: '<span />' },
  Plus: { name: 'Plus', template: '<span />' },
}))

const baseWorkspaces = [
  { workspaceId: 'ws-1', name: 'Personal', role: 'OWNER', icon: null },
  { workspaceId: 'ws-2', name: 'Agency', role: 'EDITOR', icon: null },
]

function mountSection(
  active: {
    workspaceId: string
    name: string
    role: string
    icon: string | null
  } | null = baseWorkspaces[0]!,
) {
  return mount(SidebarHeaderSection, {
    props: {
      activeWorkspace: active,
      options: baseWorkspaces,
      isLoading: false,
    },
    attachTo: document.body,
  })
}

describe('SidebarHeaderSection', () => {
  it('closed state shows the trigger only — no panel in the DOM', () => {
    const wrapper = mountSection()
    expect(wrapper.find('#sidebar-workspace-menu').exists()).toBe(false)
    expect(wrapper.find('[role="menu"]').exists()).toBe(false)
    expect(wrapper.find('button[aria-haspopup="menu"]').exists()).toBe(true)
  })

  it('open state shows the panel with role=menu and matching items have role=menuitem', async () => {
    const wrapper = mountSection()
    const trigger = wrapper.find('button[aria-haspopup="menu"]')

    await trigger.trigger('click')
    await nextTick()

    const panel = wrapper.find('#sidebar-workspace-menu')
    expect(panel.exists()).toBe(true)
    expect(panel.attributes('role')).toBe('menu')

    const items = wrapper.findAll('[role="menuitem"]')
    expect(items.length).toBe(baseWorkspaces.length)
  })

  it('selecting a workspace emits select(workspace) and closes the popover', async () => {
    const wrapper = mountSection()
    const trigger = wrapper.find('button[aria-haspopup="menu"]')
    await trigger.trigger('click')
    await nextTick()

    const items = wrapper.findAll('[role="menuitem"]')
    const second = items[1]!
    await second.trigger('click')
    await nextTick()

    expect(wrapper.emitted('select')).toBeTruthy()
    const payload = wrapper.emitted('select')?.[0]?.[0] as { workspaceId: string }
    expect(payload.workspaceId).toBe('ws-2')

    // Popover closed
    expect(wrapper.find('#sidebar-workspace-menu').exists()).toBe(false)
  })

  it('Escape and route change both close the popover', async () => {
    const wrapper = mountSection()
    const trigger = wrapper.find('button[aria-haspopup="menu"]')
    await trigger.trigger('click')
    await nextTick()
    expect(wrapper.find('#sidebar-workspace-menu').exists()).toBe(true)

    // Escape
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await nextTick()
    await nextTick()
    expect(wrapper.find('#sidebar-workspace-menu').exists()).toBe(false)

    // Reopen then route change
    await trigger.trigger('click')
    await nextTick()
    expect(wrapper.find('#sidebar-workspace-menu').exists()).toBe(true)

    routeState.path = '/scheduler'
    await nextTick()
    await nextTick()
    expect(wrapper.find('#sidebar-workspace-menu').exists()).toBe(false)
  })

  it('trigger exposes the full ARIA trio (haspopup, expanded, controls)', () => {
    const wrapper = mountSection()
    const trigger = wrapper.find('button[aria-haspopup="menu"]')
    expect(trigger.attributes('aria-haspopup')).toBe('menu')
    expect(trigger.attributes('aria-expanded')).toBe('false')
    expect(trigger.attributes('aria-controls')).toBe('sidebar-workspace-menu')
  })
})
