import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import AcceptInvitationRoute from './AcceptInvitationRoute.vue'

const routeQuery = vi.hoisted(() => ({ value: {} as Record<string, unknown> }))

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRoute: () => ({ query: routeQuery.value }),
  }
})

vi.mock('@modules/invitation/presentation/AcceptInvitationView.vue', () => ({
  default: {
    props: ['token'],
    template: '<div data-testid="invitation-view">{{ token }}</div>',
  },
}))

describe('AcceptInvitationRoute', () => {
  beforeEach(() => {
    routeQuery.value = {}
  })

  it('passes string token from query to view', () => {
    routeQuery.value = { token: 'raw-token-123' }
    const wrapper = mount(AcceptInvitationRoute)
    expect(wrapper.get('[data-testid="invitation-view"]').text()).toBe('raw-token-123')
  })

  it('passes empty string when token is missing', () => {
    routeQuery.value = {}
    const wrapper = mount(AcceptInvitationRoute)
    expect(wrapper.get('[data-testid="invitation-view"]').text()).toBe('')
  })

  it('passes empty string when token is not a string', () => {
    routeQuery.value = { token: 123 as unknown as string }
    const wrapper = mount(AcceptInvitationRoute)
    expect(wrapper.get('[data-testid="invitation-view"]').text()).toBe('')
  })

  it('uses first element when token is an array', () => {
    routeQuery.value = { token: ['first', 'second'] }
    const wrapper = mount(AcceptInvitationRoute)
    expect(wrapper.get('[data-testid="invitation-view"]').text()).toBe('first')
  })

  it('passes empty string when token is an empty array', () => {
    routeQuery.value = { token: [] }
    const wrapper = mount(AcceptInvitationRoute)
    expect(wrapper.get('[data-testid="invitation-view"]').text()).toBe('')
  })

  it('handles array with undefined first element', () => {
    routeQuery.value = { token: [undefined as unknown as string] }
    const wrapper = mount(AcceptInvitationRoute)
    expect(wrapper.get('[data-testid="invitation-view"]').text()).toBe('')
  })
})
