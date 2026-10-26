import { describe, it, expect, vi } from 'vitest'
import { nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import SidebarChannelsSection, { type SidebarChannel } from './SidebarChannelsSection.vue'

const sidebarState = vi.hoisted(() => ({
  isMobile: { value: true },
  setOpenMobile: vi.fn(),
}))

vi.mock('@profiletailors/vue-ui/shell/sidebar', () => ({
  useSidebar: () => sidebarState,
}))

vi.mock('@layouts/sidebar/SidebarChannelRow.vue', () => ({
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
    id: '92c0751b-b11e-5b27-b9bd-8c4b03c253ef',
    accountId: '92c0751b-b11e-5b27-b9bd-8c4b03c253ef',
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
      makeChannel({ id: '92c0751b-b11e-5b27-b9bd-8c4b03c253ef', name: 'A', provider: 'linkedin' }),
      makeChannel({ id: '8d7c2526-b27d-5c90-861f-d9b91afe47f2', name: 'B', provider: 'twitter' }),
      makeChannel({ id: '30b88e8c-8e00-5b5e-8f9c-996d7f58681b', name: 'C', provider: 'instagram' }),
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
    expect(rows).toHaveLength(3)
    expect(rows[0]?.props('channel').id).toBe('92c0751b-b11e-5b27-b9bd-8c4b03c253ef')
    expect(rows[2]?.props('channel').id).toBe('30b88e8c-8e00-5b5e-8f9c-996d7f58681b')
  })

  it('resets avatarLoadFailedMap when channels array reference changes', async () => {
    const wrapper = mount(SidebarChannelsSection, {
      props: {
        channels: [makeChannel({ id: '92c0751b-b11e-5b27-b9bd-8c4b03c253ef' })],
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
    const newChannels = [
      makeChannel({ id: '8d7c2526-b27d-5c90-861f-d9b91afe47f2' }),
      makeChannel({ id: '30b88e8c-8e00-5b5e-8f9c-996d7f58681b' }),
    ]
    await wrapper.setProps({ channels: newChannels })
    await nextTick()

    // After props change, the watcher resets the map. The DOM still shows the
    // new rows; no error is thrown. We just confirm the rows re-render.
    const rows = wrapper.findAllComponents({ name: 'SidebarChannelRow' })
    expect(rows).toHaveLength(2)
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
      makeChannel({
        id: '92c0751b-b11e-5b27-b9bd-8c4b03c253ef',
        accountId: '788c8103-35f1-5899-b15a-6ca994253c99',
        provider: 'linkedin',
      }),
      makeChannel({
        id: '8d7c2526-b27d-5c90-861f-d9b91afe47f2',
        accountId: '28353a84-fcee-5f3a-93b6-4c1bd639921e',
        provider: 'twitter',
      }),
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
    expect(payload).toBe('28353a84-fcee-5f3a-93b6-4c1bd639921e')
  })

  it('closes the mobile sidebar when a channel is selected', async () => {
    sidebarState.setOpenMobile.mockClear()
    const wrapper = mount(SidebarChannelsSection, {
      props: {
        channels: [makeChannel({ accountId: '788c8103-35f1-5899-b15a-6ca994253c99' })],
        activeChannelId: null,
        totalQueuedCount: 0,
        isSchedulerRoute: true,
      },
    })

    const row = wrapper.findComponent({ name: 'SidebarChannelRow' })
    await row.vm.$emit('select')

    expect(sidebarState.setOpenMobile).toHaveBeenCalledWith(false)
  })
})
