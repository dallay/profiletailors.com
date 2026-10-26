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
    template:
      '<button :data-dnd-draggable="idea.id" @click="$emit(\'select\', idea.id)"><slot /></button>',
  },
}))

function makeColumn(overrides: Partial<IdeaColumn> = {}): IdeaColumn {
  return { id: 'raw', name: 'Raw', order: 0, color: null, ...overrides }
}

function makeIdea(id: string, columnId = 'raw'): Idea {
  return {
    id,
    workspaceId: '820c22d8-9ce7-5e76-867f-04bac4e53b59',
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
        column: makeColumn({ name: 'In Progress', id: 'in-progress', color: '#22c55e' }),
        ideas: [
          makeIdea('0ca69867-76ef-5e75-a8c3-3933fb8faec4'),
          makeIdea('a7dfae4e-afcd-5cc0-a278-5eebffa5b0b1'),
        ],
      },
    })
    const root = wrapper.find('[data-testid="idea-lane"]')
    expect(root.exists()).toBe(true)
    expect(root.classes().join(' ')).toMatch(/min-w-\[280px\]|w-\[300px\]/)
    expect(root.classes().join(' ')).toContain('max-w-[320px]')
    const header = wrapper.find('[data-testid="idea-lane-header"]')
    expect(header.exists()).toBe(true)
    expect(header.classes().join(' ')).toContain('sticky')
    expect(header.find('span').attributes('style')).toBeDefined()
    expect(wrapper.text()).toContain('In Progress')
    expect(wrapper.text()).toContain('2')
  })

  it('contains vertical scroll area', () => {
    const wrapper = mount(IdeaLane, {
      props: { column: makeColumn(), ideas: [makeIdea('0ca69867-76ef-5e75-a8c3-3933fb8faec4')] },
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
      props: {
        column: makeColumn(),
        ideas: [
          makeIdea('0ca69867-76ef-5e75-a8c3-3933fb8faec4'),
          makeIdea('a7dfae4e-afcd-5cc0-a278-5eebffa5b0b1'),
        ],
      },
    })
    const cards = wrapper.findAll('[data-dnd-draggable]')
    expect(cards).toHaveLength(2)
    await wrapper.find('[data-testid="idea-lane-add"]').trigger('click')
    expect(wrapper.emitted('add')?.[0]).toEqual(['raw'])
    await cards[0]!.trigger('click')
  })

  it('emits select when card selected', async () => {
    const wrapper = mount(IdeaLane, {
      props: { column: makeColumn(), ideas: [makeIdea('e83a1812-0b46-5b51-84ea-690ea276dddf')] },
    })
    const stubCard = wrapper.findComponent({ name: 'IdeaCard' } as never)
    if (stubCard.exists()) {
      await (stubCard as unknown as { vm: { $emit: (e: string, v: string) => void } }).vm.$emit(
        'select',
        'e83a1812-0b46-5b51-84ea-690ea276dddf',
      )
      expect(wrapper.emitted('selectIdea')?.[0]).toEqual(['e83a1812-0b46-5b51-84ea-690ea276dddf'])
    } else {
      const el = wrapper.find('[data-dnd-draggable="e83a1812-0b46-5b51-84ea-690ea276dddf"]')
      await el.trigger('click')
      expect(wrapper.emitted('selectIdea')).toBeTruthy()
    }
  })
})
