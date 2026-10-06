import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import MobileOverflowMenu from './MobileOverflowMenu.vue'

vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('@profiletailors/vue-ui/shell/button', () => ({
  Button: { template: '<button><slot /></button>' },
}))
vi.mock('@lucide/vue', () => ({ MoreHorizontal: { template: '<svg />' } }))
vi.mock('@/components/ui/dropdown-menu', () => ({
  DropdownMenu: { template: '<div><slot /></div>' },
  DropdownMenuTrigger: { template: '<div><slot /></div>' },
  DropdownMenuContent: { template: '<div><slot /></div>' },
  DropdownMenuItem: {
    emits: ['select'],
    template: '<button @click="$emit(\'select\')"><slot /></button>',
  },
}))

describe('MobileOverflowMenu', () => {
  it('renders secondary actions and emits selections', async () => {
    const wrapper = mount(MobileOverflowMenu, { global: { mocks: { $t: (key: string) => key } } })

    expect(wrapper.get('[data-testid="mobile-overflow-menu"]').classes()).toEqual(
      expect.arrayContaining(['size-11', 'min-h-11']),
    )
    await wrapper.get('[data-testid="open-bulk-import"]').trigger('click')
    await wrapper.get('[data-testid="start-tour-btn"]').trigger('click')

    expect(wrapper.emitted('openBulkImport')).toHaveLength(1)
    expect(wrapper.emitted('startTour')).toHaveLength(1)
  })
})
