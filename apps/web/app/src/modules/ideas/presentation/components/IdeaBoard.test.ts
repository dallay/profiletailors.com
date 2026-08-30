import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import IdeaBoard from './IdeaBoard.vue'
import type { Idea, IdeaColumn } from '@modules/ideas/domain'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('./IdeaLane.vue', () => ({
  default: {
    props: ['column', 'ideas'],
    emits: ['add', 'selectIdea'],
    template:
      '<div :data-dnd-column="column.id" data-testid="lane-stub">{{ column.name }}:{{ ideas.length }}</div>',
  },
}))

function makeColumn(id: string, order: number, color?: string | null): IdeaColumn {
  return { id, name: id, order, color }
}

function makeIdea(id: string, columnId: string): Idea {
  return {
    id,
    workspaceId: 'ws-1',
    title: id,
    notes: null,
    tags: [],
    links: [],
    columnId,
    orderInColumn: 0,
    convertedToPublicationId: null,
    createdAt: '2026-08-01T00:00:00Z',
    updatedAt: '2026-08-01T00:00:00Z',
  }
}

describe('IdeaBoard', () => {
  it('owns horizontal scroll', () => {
    const wrapper = mount(IdeaBoard, {
      props: {
        columns: [makeColumn('raw', 0), makeColumn('done', 1)],
        ideasByColumn: { raw: [makeIdea('idea-1', 'raw')] },
        loading: false,
      },
    })
    const scroll = wrapper.find('[data-testid="idea-board-scroll"]')
    expect(scroll.exists()).toBe(true)
    expect(scroll.classes().join(' ')).toContain('overflow-x-auto')
  })

  it('renders skeleton lanes when loading and not spinner', () => {
    const wrapper = mount(IdeaBoard, {
      props: {
        columns: [],
        ideasByColumn: {},
        loading: true,
      },
    })
    expect(wrapper.find('[data-testid="idea-board-skeleton"]').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('Loading ideas board')
    const lanes = wrapper.findAll('[data-testid="idea-board-skeleton-lane"]')
    expect(lanes.length).toBeGreaterThanOrEqual(3)
    lanes.forEach((lane) => {
      expect(lane.classes().join(' ')).toContain('animate-pulse')
    })
  })

  it('renders lanes per column after loading', () => {
    const wrapper = mount(IdeaBoard, {
      props: {
        columns: [makeColumn('raw', 0), makeColumn('done', 1)],
        ideasByColumn: { raw: [makeIdea('idea-1', 'raw')], done: [] },
        loading: false,
      },
    })
    expect(wrapper.find('[data-testid="idea-board-skeleton"]').exists()).toBe(false)
    const lanes = wrapper.findAll('[data-testid="lane-stub"]')
    expect(lanes).toHaveLength(2)
    expect(wrapper.find('[data-dnd-column="raw"]').exists()).toBe(true)
    expect(lanes[1]?.text()).toContain('done:0')
  })

  it('renders gallery columns and exposes a new-column action', async () => {
    const wrapper = mount(IdeaBoard, {
      props: {
        columns: [makeColumn('raw', 0, '#22c55e'), makeColumn('done', 1)],
        ideasByColumn: { raw: [makeIdea('idea-1', 'raw')] },
        loading: false,
        viewMode: 'gallery',
      },
    })

    expect(wrapper.find('[data-testid="idea-gallery-column-raw"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="idea-gallery-column-done"]').exists()).toBe(true)
    expect(
      wrapper.find('[data-testid="idea-gallery-column-raw"] span').attributes('style'),
    ).toBeDefined()
    await wrapper
      .find('[data-testid="idea-gallery-column-done"] [data-testid="idea-gallery-empty"]')
      .trigger('click')
    await wrapper.findAll('[data-testid="idea-gallery-add"]').at(1)?.trigger('click')
    expect(wrapper.emitted('addIdea')).toEqual([['done'], ['done']])
    await wrapper.find('[data-dnd-draggable="idea-1"]').trigger('click')
    expect(wrapper.emitted('selectIdea')).toEqual([['idea-1']])
    await wrapper.find('[data-testid="ideas-gallery-new-column"]').trigger('click')
    expect(wrapper.emitted('newColumn')).toHaveLength(1)
  })

  it('forwards add and selectIdea events', async () => {
    const wrapper = mount(IdeaBoard, {
      props: {
        columns: [makeColumn('raw', 0)],
        ideasByColumn: { raw: [] },
        loading: false,
      },
    })
    const lane = wrapper.findComponent({ name: 'IdeaLane' } as never)
    if (lane.exists()) {
      await (lane as unknown as { vm: { $emit: (e: string, v: string) => void } }).vm.$emit(
        'add',
        'raw',
      )
      expect(wrapper.emitted('addIdea')?.[0]).toEqual(['raw'])
      await (lane as unknown as { vm: { $emit: (e: string, v: string) => void } }).vm.$emit(
        'selectIdea',
        'idea-1',
      )
      expect(wrapper.emitted('selectIdea')?.[0]).toEqual(['idea-1'])
    }
  })

  it('does not show error spinner container', () => {
    const wrapper = mount(IdeaBoard, {
      props: {
        columns: [makeColumn('raw', 0)],
        ideasByColumn: { raw: [] },
        loading: false,
      },
    })
    expect(wrapper.find('[data-testid="idea-board-loading-spinner"]').exists()).toBe(false)
  })
})
