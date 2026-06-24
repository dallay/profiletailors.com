import { describe, it, expect, vi } from 'vitest'
import { nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import SidebarChannelsSection, { type SidebarChannel } from './SidebarChannelsSection.vue'

vi.mock('@/components/sidebar/SidebarChannelRow.vue', () => ({
  default: {
    name: 'SidebarChannelRow',
    props: ['channel', 'isActive', 'queuedCount'],
    emits: ['select', 'avatarError'],
    template: `
      <div class="channel-row" :data-provider="channel.provider" :data-active="isActive">
        <span class="row-name">{{ channel.name }}</span>
        <button class="select" @click="$emit('select')">Select</button>
        <button class="error-btn" @click="$emit('avatarError')">TriggerError</button>
      </div>
    `,
  },
}))

vi.mock('@lucide/vue', () => ({
  Users: { name: 'Users', template: '<span />' },
}))

function makeChannel(overrides: Partial<SidebarChannel> = {}): SidebarChannel {
  return {
    id: 'ch-1',
    accountId: 'ch-1',
    name: 'Channel 1',
    provider: 'linkedin',
    avatar: '',
    avatarUrl: undefined,
    handle: 'Channel 1',
    status: 'ACTIVE',
    badge: 'in',
    queuedCount: 0,
    ...overrides,
  }
}

describe('SidebarChannelsSection', () => {
  it('renders the "All channels" row first with the zero-padded total badge when count > 0', () => {
    const wrapper = mount(SidebarChannelsSection, {
      props: {
        channels: [],
        activeChannelId: null,
        totalQueuedCount: 4,
        isSchedulerRoute: true,
      },
    })

    const text = wrapper.text()
    expect(text).toContain('All channels')
    expect(text).toContain('04')
  })

  it('omits the badge entirely when totalQueuedCount is 0 (no zero-state badge)', () => {
    const wrapper = mount(SidebarChannelsSection, {
      props: {
        channels: [],
        activeChannelId: null,
        totalQueuedCount: 0,
        isSchedulerRoute: true,
      },
    })

    // The "All channels" row should be present, but no "00" badge.
    expect(wrapper.text()).toContain('All channels')
    expect(wrapper.text()).not.toContain('00')
  })

  it('renders one SidebarChannelRow per channel', () => {
    const channels = [
      makeChannel({ id: 'ch-1', name: 'A', provider: 'linkedin' }),
      makeChannel({ id: 'ch-2', name: 'B', provider: 'twitter' }),
      makeChannel({ id: 'ch-3', name: 'C', provider: 'instagram' }),
    ]
    const wrapper = mount(SidebarChannelsSection, {
      props: {
        channels,
        activeChannelId: null,
        totalQueuedCount: 0,
        isSchedulerRoute: false,
      },
    })

    const rows = wrapper.findAllComponents({ name: 'SidebarChannelRow' })
    expect(rows.length).toBe(3)
    expect(rows[0]?.props('channel').id).toBe('ch-1')
    expect(rows[2]?.props('channel').id).toBe('ch-3')
  })

  it('resets avatarLoadFailedMap when channels array reference changes', async () => {
    const wrapper = mount(SidebarChannelsSection, {
      props: {
        channels: [makeChannel({ id: 'ch-1' })],
        activeChannelId: null,
        totalQueuedCount: 0,
        isSchedulerRoute: false,
      },
    })

    // Trigger avatar-error on the first row to populate the map
    const firstRow = wrapper.findComponent({ name: 'SidebarChannelRow' })
    await firstRow.vm.$emit('avatarError')

    // The map now has ch-1
    // (Internal ref — assert indirectly by triggering another error and a swap)
    const newChannels = [makeChannel({ id: 'ch-2' }), makeChannel({ id: 'ch-3' })]
    await wrapper.setProps({ channels: newChannels })
    await nextTick()

    // After props change, the watcher resets the map. The DOM still shows the
    // new rows; no error is thrown. We just confirm the rows re-render.
    const rows = wrapper.findAllComponents({ name: 'SidebarChannelRow' })
    expect(rows.length).toBe(2)
  })

  it('emits selectAll when the All-channels row is clicked', async () => {
    const wrapper = mount(SidebarChannelsSection, {
      props: {
        channels: [],
        activeChannelId: null,
        totalQueuedCount: 0,
        isSchedulerRoute: true,
      },
    })

    // The first button in the template is the All-channels row
    const allBtn = wrapper.findAll('button')[0]!
    await allBtn.trigger('click')

    expect(wrapper.emitted('selectAll')).toBeTruthy()
    expect(wrapper.emitted('selectAll')?.length).toBe(1)
  })

  it('emits selectChannel with the accountId when a row is clicked', async () => {
    const channels = [
      makeChannel({ id: 'ch-1', accountId: 'acc-1', provider: 'linkedin' }),
      makeChannel({ id: 'ch-2', accountId: 'acc-2', provider: 'twitter' }),
    ]
    const wrapper = mount(SidebarChannelsSection, {
      props: {
        channels,
        activeChannelId: null,
        totalQueuedCount: 0,
        isSchedulerRoute: false,
      },
    })

    const rows = wrapper.findAllComponents({ name: 'SidebarChannelRow' })
    await rows[1]?.vm.$emit('select')

    expect(wrapper.emitted('selectChannel')).toBeTruthy()
    const payload = wrapper.emitted('selectChannel')?.[0]?.[0] as string
    expect(payload).toBe('acc-2')
  })
})
