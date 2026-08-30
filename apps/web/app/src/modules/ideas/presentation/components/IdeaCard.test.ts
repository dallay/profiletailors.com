import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import IdeaCard from './IdeaCard.vue'
import type { Idea } from '@modules/ideas/domain'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string, params?: Record<string, unknown>) => `${key}:${JSON.stringify(params ?? {})}` }),
}))

function makeIdea(overrides: Partial<Idea> = {}): Idea {
  return {
    id: 'idea-1',
    workspaceId: 'ws-1',
    title: 'Test title',
    notes: null,
    tags: [],
    links: [],
    columnId: 'raw',
    orderInColumn: 0,
    convertedToPublicationId: null,
    createdAt: '2026-08-01T00:00:00Z',
    updatedAt: '2026-08-01T00:00:00Z',
    ...overrides,
  }
}

describe('IdeaCard', () => {
  it('renders title primary and clamped', () => {
    const wrapper = mount(IdeaCard, {
      props: { idea: makeIdea({ title: 'Hello world' }) },
      global: { stubs: { Badge: { template: '<span><slot /></span>' } } },
    })
    const title = wrapper.find('[data-testid="idea-card-title"]')
    expect(title.exists()).toBe(true)
    expect(title.text()).toBe('Hello world')
    expect(title.classes().join(' ')).toContain('line-clamp-2')
  })

  it('shows bounded excerpt only when notes exist', () => {
    const withNotes = mount(IdeaCard, {
      props: { idea: makeIdea({ notes: 'Some notes' }) },
      global: { stubs: { Badge: { template: '<span><slot /></span>' } } },
    })
    const noteEl = withNotes.find('[data-testid="idea-card-notes"]')
    expect(noteEl.exists()).toBe(true)
    expect(noteEl.text()).toBe('Some notes')
    expect(noteEl.classes().join(' ')).toContain('line-clamp-2')

    const withoutNotes = mount(IdeaCard, {
      props: { idea: makeIdea({ notes: null }) },
      global: { stubs: { Badge: { template: '<span><slot /></span>' } } },
    })
    expect(withoutNotes.find('[data-testid="idea-card-notes"]').exists()).toBe(false)
  })

  it('shows bounded tags with +N overflow', () => {
    const wrapper = mount(IdeaCard, {
      props: { idea: makeIdea({ tags: ['a', 'b', 'c', 'd', 'e'] }) },
      global: { stubs: { Badge: { template: '<span><slot /></span>' } } },
    })
    const tags = wrapper.findAll('[data-testid="idea-card-tag"]')
    expect(tags).toHaveLength(3)
    expect(tags[0]?.text()).toContain('a')
    expect(wrapper.text()).toContain('+2')
  })

  it('shows links count only if present', () => {
    const withLinks = mount(IdeaCard, {
      props: { idea: makeIdea({ links: [{ url: 'https://a.com', label: null }, { url: 'https://b.com', label: null }] }) },
      global: { stubs: { Badge: { template: '<span><slot /></span>' } } },
    })
    expect(withLinks.find('[data-testid="idea-card-links"]').exists()).toBe(true)
    expect(withLinks.text()).toContain('🔗 2')

    const withoutLinks = mount(IdeaCard, {
      props: { idea: makeIdea({ links: [] }) },
      global: { stubs: { Badge: { template: '<span><slot /></span>' } } },
    })
    expect(withoutLinks.find('[data-testid="idea-card-links"]').exists()).toBe(false)
  })

  it('shows converted badge secondary when converted', () => {
    const wrapper = mount(IdeaCard, {
      props: { idea: makeIdea({ convertedToPublicationId: 'pub-1' }) },
      global: { stubs: { Badge: { template: '<span><slot /></span>' } } },
    })
    expect(wrapper.find('[data-testid="idea-card-converted"]').exists()).toBe(true)
    const empty = mount(IdeaCard, {
      props: { idea: makeIdea({ convertedToPublicationId: null }) },
      global: { stubs: { Badge: { template: '<span><slot /></span>' } } },
    })
    expect(empty.find('[data-testid="idea-card-converted"]').exists()).toBe(false)
  })

  it('exposes data-dnd-draggable and remains clickable', async () => {
    const wrapper = mount(IdeaCard, {
      props: { idea: makeIdea({ id: 'idea-42' }) },
      global: { stubs: { Badge: { template: '<span><slot /></span>' } } },
    })
    const card = wrapper.find('[data-dnd-draggable="idea-42"]')
    expect(card.exists()).toBe(true)
    expect(card.attributes('draggable')).toBe('true')
    await card.trigger('click')
    expect(wrapper.emitted('select')).toBeTruthy()
    expect(wrapper.emitted('select')?.[0]).toEqual(['idea-42'])
  })
})
