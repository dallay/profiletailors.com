import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import ContentPipeline from './ContentPipeline.vue'
import type { PipelineColumn, PipelineCard } from '@modules/dashboard/domain/dashboard.types'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

function makeCard(id: string, overrides: Partial<PipelineCard> = {}): PipelineCard {
  return {
    id,
    title: 'Test card',
    content: 'Test content',
    platform: 'linkedin',
    author: 'Yuniel',
    tags: ['test'],
    ...overrides,
  }
}

function makeColumn(id: string, title: string, cards: PipelineCard[] = []): PipelineColumn {
  return { id, title, cards }
}

// The component requires exactly 4 columns (ideas, drafting, review, scheduled)
function defaultColumns(overrides?: Partial<Record<string, PipelineCard[]>>): PipelineColumn[] {
  return [
    makeColumn('ideas', 'dashboard.pipeline.column.ideas', overrides?.ideas ?? []),
    makeColumn('drafting', 'dashboard.pipeline.column.drafting', overrides?.drafting ?? []),
    makeColumn('review', 'dashboard.pipeline.column.review', overrides?.review ?? []),
    makeColumn('scheduled', 'dashboard.pipeline.column.scheduled', overrides?.scheduled ?? []),
  ]
}

describe('ContentPipeline', () => {
  it('renders the title and subtitle', () => {
    const wrapper = mount(ContentPipeline, {
      props: { columns: defaultColumns() },
    })
    expect(wrapper.text()).toContain('dashboard.pipeline.title')
    expect(wrapper.text()).toContain('dashboard.pipeline.subtitle')
  })

  it('renders all 4 column headers', () => {
    const wrapper = mount(ContentPipeline, {
      props: { columns: defaultColumns({ ideas: [makeCard('c1')] }) },
    })
    expect(wrapper.text()).toContain('dashboard.pipeline.column.ideas')
    expect(wrapper.text()).toContain('dashboard.pipeline.column.drafting')
    expect(wrapper.text()).toContain('dashboard.pipeline.column.review')
    expect(wrapper.text()).toContain('dashboard.pipeline.column.scheduled')
  })

  it('renders card titles within columns', () => {
    const wrapper = mount(ContentPipeline, {
      props: {
        columns: defaultColumns({ ideas: [makeCard('c1', { title: 'Thread on hex arch' })] }),
      },
    })
    expect(wrapper.text()).toContain('Thread on hex arch')
  })

  it('disables Move Left on first column', () => {
    const wrapper = mount(ContentPipeline, {
      props: {
        columns: defaultColumns({ ideas: [makeCard('c1')] }),
      },
    })
    const moveLeftButtons = wrapper.findAll('button[aria-label="Move left"]')
    expect(moveLeftButtons.length).toBeGreaterThan(0)
    expect(moveLeftButtons[0]?.attributes('disabled')).toBeDefined()
  })

  it('enables Move Right on non-last columns', () => {
    const wrapper = mount(ContentPipeline, {
      props: {
        columns: defaultColumns({ ideas: [makeCard('c1')] }),
      },
    })
    const moveRightButtons = wrapper.findAll('button[aria-label="Move right"]')
    expect(moveRightButtons.length).toBeGreaterThan(0)
    expect(moveRightButtons[0]?.attributes('disabled')).toBeUndefined()
  })

  it('emits moveCard event when move right is clicked', async () => {
    const wrapper = mount(ContentPipeline, {
      props: {
        columns: defaultColumns({ ideas: [makeCard('c1')] }),
      },
    })
    const moveRightButton = wrapper.find('button[aria-label="Move right"]')
    await moveRightButton.trigger('click')
    expect(wrapper.emitted('moveCard')).toBeTruthy()
    const emitArgs = wrapper.emitted('moveCard')?.[0]
    expect(emitArgs?.[0]).toBe('c1')
    expect(emitArgs?.[1]).toBe('ideas')
    expect(emitArgs?.[2]).toBe('drafting')
  })

  it('shows empty state for columns with no cards', () => {
    const wrapper = mount(ContentPipeline, {
      props: { columns: defaultColumns() },
    })
    expect(wrapper.text()).toContain('dashboard.pipeline.noCards')
  })

  it('renders card tags (up to 3)', () => {
    const wrapper = mount(ContentPipeline, {
      props: {
        columns: defaultColumns({
          ideas: [makeCard('c1', { tags: ['architecture', 'thread', 'hexagonal'] })],
        }),
      },
    })
    expect(wrapper.text()).toContain('architecture')
    expect(wrapper.text()).toContain('thread')
    expect(wrapper.text()).toContain('hexagonal')
  })

  it('renders author name on cards', () => {
    const wrapper = mount(ContentPipeline, {
      props: {
        columns: defaultColumns({
          ideas: [makeCard('c1', { author: 'Yuniel' })],
        }),
      },
    })
    expect(wrapper.text()).toContain('Yuniel')
  })

  // ---------------------------------------------------------------------------
  // Drag & Drop tests
  // ---------------------------------------------------------------------------

  it('card containers have drag-related attributes', () => {
    const wrapper = mount(ContentPipeline, {
      props: {
        columns: defaultColumns({ ideas: [makeCard('c1')] }),
      },
    })
    const cards = wrapper.findAll('[data-dnd-draggable]')
    const columns = wrapper.findAll('[data-dnd-column]')
    expect(cards).toHaveLength(1)
    expect(columns).toHaveLength(4)
    expect(cards[0]?.attributes('data-dnd-draggable')).toBe('c1')
    expect(cards[0]?.attributes('draggable')).toBe('true')
  })

  it('card containers have min-h-[48px] for drop targets', () => {
    const wrapper = mount(ContentPipeline, {
      props: { columns: defaultColumns() },
    })
    // Each column card container should have min-h for drop zone
    const dropZones = wrapper.findAll('.min-h-\\[48px\\]')
    expect(dropZones).toHaveLength(4)
  })

  it('renders platform badges on cards', () => {
    const wrapper = mount(ContentPipeline, {
      props: {
        columns: defaultColumns({
          ideas: [makeCard('c1', { platform: 'linkedin' })],
        }),
      },
    })
    expect(wrapper.text()).toContain('LinkedIn')
  })
})
