import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import TeamActivity from './TeamActivity.vue'
import type { TeamActivityEvent, TeamMember } from '@modules/dashboard/domain/dashboard.types'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

const RouterLinkStub = {
  template: '<a><slot /></a>',
}

function makeEvent(id: string, overrides: Partial<TeamActivityEvent> = {}): TeamActivityEvent {
  return {
    id,
    memberId: 'm1',
    memberName: 'Yuniel',
    action: 'Scheduled "Kotlin coroutines" for LinkedIn',
    timestamp: '2026-06-12T08:00:00Z',
    ...overrides,
  }
}

function makeMember(id: string, overrides: Partial<TeamMember> = {}): TeamMember {
  return {
    id,
    name: 'Yuniel',
    online: true,
    ...overrides,
  }
}

describe('TeamActivity', () => {
  it('renders the title and subtitle', () => {
    const wrapper = mount(TeamActivity, {
      props: {
        events: [makeEvent('e1')],
        members: [makeMember('m1')],
      },
      global: { stubs: { 'router-link': RouterLinkStub } },
    })
    expect(wrapper.text()).toContain('dashboard.teamActivity.title')
    expect(wrapper.text()).toContain('dashboard.teamActivity.subtitle')
  })

  it('renders event member names and actions', () => {
    const wrapper = mount(TeamActivity, {
      props: {
        events: [makeEvent('e1', { memberName: 'Yuniel', action: 'Published a post' })],
        members: [makeMember('m1', { name: 'Yuniel' })],
      },
      global: { stubs: { 'router-link': RouterLinkStub } },
    })
    expect(wrapper.text()).toContain('Yuniel')
    expect(wrapper.text()).toContain('Published a post')
  })

  it('renders at most 5 events', () => {
    const events = Array.from({ length: 8 }, (_, i) =>
      makeEvent(`e${i}`, { memberName: `User${i}`, action: 'Action' }),
    )
    const members = Array.from({ length: 8 }, (_, i) => makeMember(`m${i}`, { name: `User${i}` }))
    const wrapper = mount(TeamActivity, {
      props: { events, members },
      global: { stubs: { 'router-link': RouterLinkStub } },
    })
    // Should show 5 events, but the "View All" link should still appear
    const eventElements = wrapper.findAll('.rounded-lg.bg-\\[var\\(--background-surface\\)\\]')
    expect(eventElements.length).toBeLessThanOrEqual(5)
  })

  it('renders online indicator for online members', () => {
    const wrapper = mount(TeamActivity, {
      props: {
        events: [makeEvent('e1', { memberId: 'm1' })],
        members: [makeMember('m1', { online: true })],
      },
      global: { stubs: { 'router-link': RouterLinkStub } },
    })
    // Online indicator is a green dot
    const dot = wrapper.find('.rounded-full.bg-\\[var\\(--success-color\\)\\]')
    expect(dot.exists()).toBe(true)
  })

  it('shows empty state when no events', () => {
    const wrapper = mount(TeamActivity, {
      props: { events: [], members: [makeMember('m1')] },
      global: { stubs: { 'router-link': RouterLinkStub } },
    })
    expect(wrapper.text()).toContain('dashboard.teamActivity.noActivity')
  })

  it('renders View All router link when events exist', () => {
    const wrapper = mount(TeamActivity, {
      props: {
        events: [makeEvent('e1')],
        members: [makeMember('m1')],
      },
      global: { stubs: { 'router-link': RouterLinkStub } },
    })
    expect(wrapper.text()).toContain('dashboard.viewAll')
  })
})
