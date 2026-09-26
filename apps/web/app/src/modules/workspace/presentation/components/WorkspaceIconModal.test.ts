import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import WorkspaceIconModal from './WorkspaceIconModal.vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

describe('WorkspaceIconModal', () => {
  const defaultProps = {
    open: true,
    currentIcon: 'briefcase' as string | null,
    isUpdating: false,
    errorMessage: null as string | null,
  }

  const globalStubs = {
    global: {
      stubs: {
        Dialog: {
          template: '<div v-if="open"><slot /></div>',
          props: ['open'],
        },
        DialogContent: { template: '<div><slot /></div>' },
        DialogTitle: { template: '<h2><slot /></h2>' },
        DialogDescription: { template: '<p><slot /></p>' },
        Button: {
          template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
          props: ['disabled', 'variant', 'size'],
        },
      },
    },
  }

  it('renders modal content when open', () => {
    const wrapper = mount(WorkspaceIconModal, {
      props: defaultProps,
      ...globalStubs,
    })

    expect(wrapper.text()).toContain('workspace.iconModalTitle')
    expect(wrapper.text()).toContain('workspace.iconModalDesc')
  })

  it('allows picking a new icon from curated icons list', async () => {
    const wrapper = mount(WorkspaceIconModal, {
      props: defaultProps,
      ...globalStubs,
    })

    const rocketButton = wrapper.find('button[aria-label="rocket"]')
    expect(rocketButton.exists()).toBe(true)

    await rocketButton.trigger('click')

    expect(rocketButton.attributes('aria-pressed')).toBe('true')
  })

  it('emits select event with localIcon on save confirm click', async () => {
    const wrapper = mount(WorkspaceIconModal, {
      props: defaultProps,
      ...globalStubs,
    })

    const rocketButton = wrapper.find('button[aria-label="rocket"]')
    await rocketButton.trigger('click')

    const buttons = wrapper.findAll('button')
    const saveButton = buttons.find((btn) => btn.text().includes('workspace.save'))
    expect(saveButton).toBeDefined()

    await saveButton?.trigger('click')

    expect(wrapper.emitted('select')).toBeTruthy()
    expect(wrapper.emitted('select')?.[0]).toEqual(['rocket'])
  })

  it('allows clearing the icon when currentIcon is set', async () => {
    const wrapper = mount(WorkspaceIconModal, {
      props: defaultProps,
      ...globalStubs,
    })

    const removeButton = wrapper
      .findAll('button')
      .find((btn) => btn.text().includes('workspace.removeIcon'))
    expect(removeButton).toBeDefined()

    await removeButton?.trigger('click')

    const saveButton = wrapper
      .findAll('button')
      .find((btn) => btn.text().includes('workspace.save'))
    await saveButton?.trigger('click')

    expect(wrapper.emitted('select')?.[0]).toEqual([null])
  })

  it('displays error message when provided', () => {
    const wrapper = mount(WorkspaceIconModal, {
      props: { ...defaultProps, errorMessage: 'Failed to save icon' },
      ...globalStubs,
    })

    expect(wrapper.text()).toContain('Failed to save icon')
  })

  it('disables interactions when isUpdating is true', () => {
    const wrapper = mount(WorkspaceIconModal, {
      props: { ...defaultProps, isUpdating: true },
      ...globalStubs,
    })

    const iconButton = wrapper.find('button[aria-label="briefcase"]')
    expect(iconButton.attributes('disabled')).toBeDefined()
  })

  it('emits update:open false on cancel button click', async () => {
    const wrapper = mount(WorkspaceIconModal, {
      props: defaultProps,
      ...globalStubs,
    })

    const cancelButton = wrapper
      .findAll('button')
      .find((btn) => btn.text().includes('workspace.cancel'))
    await cancelButton?.trigger('click')

    expect(wrapper.emitted('update:open')).toBeTruthy()
    expect(wrapper.emitted('update:open')?.[0]).toEqual([false])
  })
})
