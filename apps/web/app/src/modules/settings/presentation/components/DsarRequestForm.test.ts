import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import DsarRequestForm from './DsarRequestForm.vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

describe('DsarRequestForm', () => {
  const globalStubs = {
    global: {
      stubs: {
        Button: {
          template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
          props: ['disabled', 'type', 'variant'],
        },
        Select: {
          template: '<div><slot /></div>',
          props: ['modelValue'],
        },
        SelectTrigger: { template: '<div><slot /></div>' },
        SelectValue: { template: '<div><slot /></div>' },
        SelectContent: { template: '<div><slot /></div>' },
        SelectItem: { template: '<div><slot /></div>' },
        Dialog: {
          template: '<div v-if="open"><slot /></div>',
          props: ['open'],
        },
        DialogContent: { template: '<div><slot /></div>' },
        DialogHeader: { template: '<div><slot /></div>' },
        DialogTitle: { template: '<h2><slot /></h2>' },
        DialogDescription: { template: '<p><slot /></p>' },
        DialogFooter: { template: '<div><slot /></div>' },
        DialogClose: { template: '<div><slot /></div>' },
      },
    },
  }

  it('submit button is disabled when no request type is selected', () => {
    const wrapper = mount(DsarRequestForm, globalStubs)

    const submitBtn = wrapper.find('[data-testid="dsar-submit"]')
    expect(submitBtn.exists()).toBe(true)
    expect(submitBtn.attributes('disabled')).toBeDefined()
  })

  it('shows correction input fields when requestType is CORRECTION', async () => {
    const wrapper = mount(DsarRequestForm, globalStubs)

    expect(wrapper.find('[data-testid="dsar-correction-email"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="dsar-correction-username"]').exists()).toBe(false)

    // Set requestType to CORRECTION
    wrapper.vm.requestType = 'CORRECTION'
    await wrapper.vm.$nextTick()

    expect(wrapper.find('[data-testid="dsar-correction-email"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="dsar-correction-username"]').exists()).toBe(true)
  })

  it('emits submit event with payload for ACCESS request type', async () => {
    const wrapper = mount(DsarRequestForm, globalStubs)

    wrapper.vm.requestType = 'ACCESS'
    wrapper.vm.notes = 'Need my data'
    await wrapper.vm.$nextTick()

    const submitBtn = wrapper.find('[data-testid="dsar-submit"]')
    expect(submitBtn.attributes('disabled')).toBeUndefined()

    await submitBtn.trigger('click')

    expect(wrapper.emitted('submit')).toBeTruthy()
    expect(wrapper.emitted('submit')?.[0]).toEqual([
      {
        type: 'ACCESS',
        notes: 'Need my data',
      },
    ])
  })

  it('emits submit event with correctionData when requestType is CORRECTION', async () => {
    const wrapper = mount(DsarRequestForm, globalStubs)

    wrapper.vm.requestType = 'CORRECTION'
    wrapper.vm.newEmail = 'new@example.com'
    wrapper.vm.newUsername = 'newuser'
    await wrapper.vm.$nextTick()

    const submitBtn = wrapper.find('[data-testid="dsar-submit"]')
    await submitBtn.trigger('click')

    expect(wrapper.emitted('submit')?.[0]).toEqual([
      {
        type: 'CORRECTION',
        correctionData: {
          newEmail: 'new@example.com',
          newUsername: 'newuser',
        },
      },
    ])
  })

  it('opens confirm deletion modal when requestType is DELETION and triggers submit on confirm', async () => {
    const wrapper = mount(DsarRequestForm, globalStubs)

    wrapper.vm.requestType = 'DELETION'
    await wrapper.vm.$nextTick()

    const submitBtn = wrapper.find('[data-testid="dsar-submit-deletion"]')
    expect(submitBtn.exists()).toBe(true)

    await submitBtn.trigger('click')

    // Modal should be open
    expect(wrapper.vm.showConfirmDeletion).toBe(true)
    expect(wrapper.find('[data-testid="dsar-deletion-confirm-dialog"]').exists()).toBe(true)

    // Click confirm deletion in modal
    const confirmBtn = wrapper.find('[data-testid="dsar-deletion-confirm"]')
    await confirmBtn.trigger('click')

    expect(wrapper.vm.showConfirmDeletion).toBe(false)
    expect(wrapper.emitted('submit')?.[0]).toEqual([
      {
        type: 'DELETION',
      },
    ])
  })
})
