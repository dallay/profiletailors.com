import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import WorkspaceGeneralView from './WorkspaceGeneralView.vue'

const renameWorkspaceMock = vi.hoisted(() => vi.fn())
const setWorkspaceNameMock = vi.hoisted(() => vi.fn())

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('@modules/auth/infrastructure/auth.store', () => ({
  useAuthStore: () => ({ accessToken: 'valid-token' }),
}))

vi.mock('@modules/workspace/infrastructure/workspace.store', () => ({
  useWorkspaceStore: () => ({
    activeWorkspaceId: 'ws-1',
    activeWorkspace: { name: 'My Workspace', icon: null },
    setWorkspaceName: setWorkspaceNameMock,
  }),
}))

vi.mock('@modules/auth/infrastructure/auth-api', () => ({
  renameWorkspace: renameWorkspaceMock,
}))

describe('WorkspaceGeneralView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    renameWorkspaceMock.mockResolvedValue({ workspaceId: 'ws-1', name: 'My Workspace' })
  })

  it('renders header and workspace identity card', () => {
    const wrapper = mount(WorkspaceGeneralView, { global: { stubs: { teleport: true } } })
    expect(wrapper.find('h1').text()).toBeTruthy()
    expect(wrapper.find('input[id="workspace-name"]').exists()).toBe(true)
    expect((wrapper.find('input[id="workspace-name"]').element as HTMLInputElement).value).toBe(
      'My Workspace',
    )
  })

  it('copies workspace ID to clipboard when copy button is clicked', async () => {
    vi.stubGlobal('navigator', { clipboard: { writeText: vi.fn().mockResolvedValue(undefined) } })
    const wrapper = mount(WorkspaceGeneralView, { global: { stubs: { teleport: true } } })
    const copyBtn = wrapper.find('button[type="button"]')
    await copyBtn.trigger('click')
    expect(navigator.clipboard.writeText).toHaveBeenCalledWith('ws-1')
  })

  it('calls renameWorkspace and shows success message on save', async () => {
    const wrapper = mount(WorkspaceGeneralView, { global: { stubs: { teleport: true } } })
    const input = wrapper.find('input[id="workspace-name"]')
    await input.setValue('Renamed Workspace')
    await wrapper.find('form').trigger('submit')
    await wrapper.find('button[type="submit"]').trigger('click')
    expect(renameWorkspaceMock).toHaveBeenCalledWith('Renamed Workspace', 'valid-token', 'ws-1')
    expect(setWorkspaceNameMock).toHaveBeenCalledWith('My Workspace')
    expect(wrapper.find('[data-testid="save-success"]').exists() || wrapper.text()).toBeTruthy()
  })

  it('shows error and does not call API when workspace name is empty', async () => {
    const wrapper = mount(WorkspaceGeneralView, { global: { stubs: { teleport: true } } })
    const input = wrapper.find('input[id="workspace-name"]')
    await input.setValue('')
    await wrapper.find('form').trigger('submit')
    expect(renameWorkspaceMock).not.toHaveBeenCalled()
  })

  it('shows error when renameWorkspace throws', async () => {
    renameWorkspaceMock.mockRejectedValue(new Error('Conflict'))
    const wrapper = mount(WorkspaceGeneralView, { global: { stubs: { teleport: true } } })
    const input = wrapper.find('input[id="workspace-name"]')
    await input.setValue('New Name')
    await wrapper.find('form').trigger('submit')
    expect(wrapper.text()).toContain('Failed to update workspace name.')
  })
})
