import { mount, flushPromises } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import { createI18n } from 'vue-i18n'
import { messages } from '@/i18n'
import RevokeInvitationDialog from '@/components/RevokeInvitationDialog.vue'

const noopProvider = defineComponent({
  setup(_, { slots }) {
    return () => h('div', slots.default?.())
  },
})

const i18n = createI18n({
  legacy: false,
  locale: 'en',
  fallbackLocale: 'en',
  messages,
})

type DialogProps = {
  open: boolean
  invitationId: string
  email: string
  expectedVersion: number | undefined
  pending: boolean
  error: string | null
}

function mountDialog(overrides: Partial<DialogProps>) {
  const props: DialogProps = {
    open: true,
    invitationId: 'inv-1',
    email: 'user@example.com',
    expectedVersion: 3,
    pending: false,
    error: null,
    ...overrides,
  }
  return mount(RevokeInvitationDialog, {
    props,
    global: {
      plugins: [i18n],
      stubs: {
        Teleport: noopProvider,
      },
    },
    attachTo: document.body,
  })
}

describe('RevokeInvitationDialog', () => {
  it('renders nothing when open is false', () => {
    const wrapper = mountDialog({ open: false })
    expect(wrapper.text()).toBe('')
    wrapper.unmount()
  })

  it('emits close when cancel button is clicked', async () => {
    const wrapper = mountDialog({ expectedVersion: 3 })
    await wrapper.get('[data-testid="revoke-dialog-cancel"]').trigger('click')
    expect(wrapper.emitted('close')).toBeTruthy()
    expect(wrapper.emitted('confirm')).toBeFalsy()
    wrapper.unmount()
  })

  it('emits confirm with expectedVersion when confirm button is clicked', async () => {
    const wrapper = mountDialog({ expectedVersion: 7 })
    await wrapper.get('[data-testid="revoke-dialog-confirm"]').trigger('click')
    await nextTick()
    expect(wrapper.emitted('confirm')).toBeTruthy()
    expect(wrapper.emitted('confirm')?.[0]).toEqual([7])
    wrapper.unmount()
  })

  it('disables both buttons while pending', () => {
    const wrapper = mountDialog({ expectedVersion: 7, pending: true })
    expect(
      wrapper.get('[data-testid="revoke-dialog-confirm"]').attributes('disabled'),
    ).toBeDefined()
    expect(wrapper.get('[data-testid="revoke-dialog-cancel"]').attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })

  it('renders the error message when provided', () => {
    const wrapper = mountDialog({ expectedVersion: 7, error: 'Conflict: stale version.' })
    expect(wrapper.text()).toContain('Conflict: stale version.')
    wrapper.unmount()
  })

  it('falls back to expectedVersion 0 when not provided', async () => {
    const wrapper = mountDialog({ expectedVersion: undefined })
    await wrapper.get('[data-testid="revoke-dialog-confirm"]').trigger('click')
    await flushPromises()
    expect(wrapper.emitted('confirm')?.[0]).toEqual([0])
    wrapper.unmount()
  })
})
