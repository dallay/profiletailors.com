import { defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ConfirmDialog from './ConfirmDialog.vue'

const DialogPair = defineComponent({
  components: { ConfirmDialog },
  template: `
    <div>
      <ConfirmDialog :open="true" title="First" description="First description" confirm-text="Confirm" />
      <ConfirmDialog :open="true" title="Second" description="Second description" confirm-text="Confirm" />
    </div>
  `,
})

function mountPair() {
  return mount(DialogPair, { global: { mocks: { $t: (key: string) => key } } })
}

describe('ConfirmDialog', () => {
  it('uses instance-scoped accessible title and description IDs', async () => {
    const wrapper = mountPair()
    await flushPromises()
    const dialogs = wrapper.findAll('dialog')
    const titleIds = dialogs.map((dialog) => dialog.find('h2').attributes('id'))
    const descriptionIds = dialogs.map((dialog) => dialog.find('p').attributes('id'))

    expect(new Set(titleIds).size).toBe(2)
    expect(new Set(descriptionIds).size).toBe(2)
    dialogs.forEach((dialog, index) => {
      expect(dialog.attributes('aria-labelledby')).toBe(titleIds[index])
      expect(dialog.attributes('aria-describedby')).toBe(descriptionIds[index])
    })
  })

  it('prevents Escape dismissal while busy and allows it otherwise', async () => {
    const wrapper = mount(ConfirmDialog, {
      props: {
        open: true,
        title: 'Confirm',
        description: 'Continue?',
        confirmText: 'Continue',
        busy: true,
      },
      global: { mocks: { $t: (key: string) => key } },
    })
    await flushPromises()
    const dialog = wrapper.get('dialog').element
    const busyCancel = new Event('cancel', { cancelable: true })
    dialog.dispatchEvent(busyCancel)
    expect(busyCancel.defaultPrevented).toBe(true)

    await wrapper.setProps({ busy: false })
    const idleCancel = new Event('cancel', { cancelable: true })
    dialog.dispatchEvent(idleCancel)
    expect(idleCancel.defaultPrevented).toBe(false)
  })
})
