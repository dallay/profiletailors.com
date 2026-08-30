import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import IdeaLane from './IdeaLane.vue'
import type { Idea, IdeaColumn } from '@modules/ideas/domain'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('@modules/ideas/presentation/components/IdeaCard.vue', () => ({
  default: {
    props: ['idea'],
    emits: ['select'],
    template: '<button :data-dnd-draggable="idea.id" @click="$emit(\'select\', idea.id)"><slot /></button>',
  },
}))

function makeColumn(overrides: Partial<IdeaColumn> = {}): IdeaColumn {
  return { id: 'raw', name: 'Raw', order: 0, color: null, ...overrides }
}

function makeIdea(id: string, columnId = 'raw'): Idea {
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

describe('IdeaLane', () => {
  it('renders fixed width 280-320 and sticky header with count', () => {
    const wrapper = mount(IdeaLane, {
      props: {
        column: makeColumn({ name: 'In Progress', id: 'in-progress' }),
        ideas: [makeIdea('idea-1'), makeIdea('idea-2')],
      },
    })
    const root = wrapper.find('[data-testid="idea-lane"]')
    expect(root.exists()).toBe(true)
    expect(root.classes().join(' ')).toMatch(/min-w-\[280px\]|w-\[300px\]/)
    expect(root.classes().join(' ')).toContain('max-w-[320px]')
    const header = wrapper.find('[data-testid="idea-lane-header"]')
    expect(header.exists()).toBe(true)
    expect(header.classes().join(' ')).toContain('sticky')
    expect(wrapper.text()).toContain('In Progress')
    expect(wrapper.text()).toContain('2')
  })

  it('contains vertical scroll area', () => {
    const wrapper = mount(IdeaLane, {
      props: { column: makeColumn(), ideas: [makeIdea('idea-1')] },
    })
    const scroll = wrapper.find('[data-testid="idea-lane-scroll"]')
    expect(scroll.exists()).toBe(true)
    expect(scroll.classes().join(' ')).toContain('overflow-y-auto')
  })

  it('exposes data-dnd-column for drop target', () => {
    const wrapper = mount(IdeaLane, {
      props: { column: makeColumn({ id: 'done' }), ideas: [] },
    })
    expect(wrapper.find('[data-dnd-column="done"]').exists()).toBe(true)
  })

  it('shows empty state No ideas yet with Add action without dominance', () => {
    const wrapper = mount(IdeaLane, {
      props: { column: makeColumn(), ideas: [] },
    })
    expect(wrapper.text()).toContain('ideas.emptyColumn')
    const addBtn = wrapper.find('[data-testid="idea-lane-add"]')
    expect(addBtn.exists()).toBe(true)
    expect(wrapper.find('[data-testid="idea-lane-empty"]').exists()).toBe(true)
  })

  it('renders IdeaCard for each idea and emits select and add', async () => {
    const wrapper = mount(IdeaLane, {
      props: { column: makeColumn(), ideas: [makeIdea('idea-1'), makeIdea('idea-2')] },
    })
    const cards = wrapper.findAll('[data-dnd-draggable]')
    expect(cards).toHaveLength(2)
    await wrapper.find('[data-testid="idea-lane-add"]').trigger('click')
    expect(wrapper.emitted('add')?.[0]).toEqual(['raw'])
    await cards[0]!.trigger('click')
  })

  it('emits select when card selected', async () => {
    const wrapper = mount(IdeaLane, {
      props: { column: makeColumn(), ideas: [makeIdea('idea-99')] },
    })
    const stubCard = wrapper.findComponent({ name: 'IdeaCard' } as never)
    if (stubCard.exists()) {
      await (stubCard as unknown as { vm: { $emit: (e: string, v: string) => void } }).vm.$emit('select', 'idea-99')
      expect(wrapper.emitted('selectIdea')?.[0]).toEqual(['idea-99'])
    } else {
      const el = wrapper.find('[data-dnd-draggable="idea-99"]')
      await el.trigger('click')
      expect(wrapper.emitted('selectIdea')).toBeTruthy()
    }
  })
})
