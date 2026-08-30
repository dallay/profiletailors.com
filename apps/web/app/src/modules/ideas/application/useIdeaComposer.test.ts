import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ref } from 'vue'
import { buildPublishingPrefill, useIdeaComposer } from './useIdeaComposer'
import type { Idea, IdeaColumn } from '@modules/ideas/domain'

function makeIdea(overrides: Partial<Idea> = {}): Idea {
  return {
    id: 'idea-1',
    workspaceId: 'ws-1',
    title: 'Original title',
    notes: 'Some notes',
    tags: ['kotlin', 'testing'],
    links: [{ label: 'Docs', url: 'https://example.com' }],
    columnId: 'raw',
    orderInColumn: 0,
    convertedToPublicationId: null,
    createdAt: '2026-08-01T00:00:00Z',
    updatedAt: '2026-08-01T00:00:00Z',
    ...overrides,
  }
}

function makeColumns(): IdeaColumn[] {
  return [
    { id: 'raw', name: 'Raw', order: 0 },
    { id: 'done', name: 'Done', order: 1 },
  ]
}

describe('useIdeaComposer', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('initializes draft from idea for edit mode', () => {
    const idea = makeIdea({ title: 'Edit me', notes: 'hello **bold**', tags: ['Vue'], links: [{ url: 'https://vuejs.org', label: null }], columnId: 'done' })
    const composer = useIdeaComposer({ idea, columns: ref(makeColumns()), store: { createIdea: vi.fn(), updateIdea: vi.fn(), saving: ref(false) } })
    expect(composer.mode.value).toBe('edit')
    expect(composer.title.value).toBe('Edit me')
    expect(composer.notes.value).toBe('hello **bold**')
    expect(composer.tags.value).toEqual(['Vue'])
    expect(composer.columnId.value).toBe('done')
    expect(composer.isDirty.value).toBe(false)
  })

  it('initializes create mode with first column fallback', () => {
    const composer = useIdeaComposer({ idea: null, columns: ref(makeColumns()), store: { createIdea: vi.fn(), updateIdea: vi.fn(), saving: ref(false) } })
    expect(composer.mode.value).toBe('create')
    expect(composer.title.value).toBe('')
    expect(composer.columnId.value).toBe('raw')
  })

  it('initializes with provided initialColumnId', () => {
    const composer = useIdeaComposer({ idea: null, columns: ref(makeColumns()), initialColumnId: 'done', store: { createIdea: vi.fn(), updateIdea: vi.fn(), saving: ref(false) } })
    expect(composer.columnId.value).toBe('done')
  })

  it('tracks isDirty when title changes', () => {
    const idea = makeIdea()
    const composer = useIdeaComposer({ idea, columns: ref(makeColumns()), store: { createIdea: vi.fn(), updateIdea: vi.fn(), saving: ref(false) } })
    expect(composer.isDirty.value).toBe(false)
    composer.title.value = 'Changed'
    expect(composer.isDirty.value).toBe(true)
  })

  it('validates title required', () => {
    const composer = useIdeaComposer({ idea: null, columns: ref(makeColumns()), store: { createIdea: vi.fn(), updateIdea: vi.fn(), saving: ref(false) } })
    expect(composer.titleError.value).toBeTruthy()
    expect(composer.isValid.value).toBe(false)
    composer.title.value = '  hi  '
    expect(composer.titleError.value).toBeNull()
    expect(composer.isValid.value).toBe(true)
  })

  it('blocks save when title empty and does not call store', async () => {
    const createIdea = vi.fn()
    const composer = useIdeaComposer({ idea: null, columns: ref(makeColumns()), store: { createIdea, updateIdea: vi.fn(), saving: ref(false) } })
    composer.title.value = '   '
    const result = await composer.save()
    expect(result).toBeNull()
    expect(createIdea).not.toHaveBeenCalled()
    expect(composer.titleError.value).toBeTruthy()
  })

  it('deduplicates tags case-insensitive trimmed and lowercases', () => {
    const composer = useIdeaComposer({ idea: null, columns: ref(makeColumns()), store: { createIdea: vi.fn(), updateIdea: vi.fn(), saving: ref(false) } })
    composer.addTag(' kotlin ')
    composer.addTag('KOTLIN')
    composer.addTag('testing')
    composer.addTag(' Testing ')
    expect(composer.tags.value).toEqual(['kotlin', 'testing'])
  })

  it('addTag trims and ignores empty', () => {
    const composer = useIdeaComposer({ idea: null, columns: ref(makeColumns()), store: { createIdea: vi.fn(), updateIdea: vi.fn(), saving: ref(false) } })
    composer.addTag('   ')
    expect(composer.tags.value).toEqual([])
    composer.addTag('vue')
    expect(composer.tags.value).toEqual(['vue'])
  })

  it('removeTag removes by case-insensitive', () => {
    const idea = makeIdea({ tags: ['kotlin', 'testing'] })
    const composer = useIdeaComposer({ idea, columns: ref(makeColumns()), store: { createIdea: vi.fn(), updateIdea: vi.fn(), saving: ref(false) } })
    composer.removeTag('KOTLIN')
    expect(composer.tags.value).toEqual(['testing'])
  })

  it('validates https guard for links', () => {
    const composer = useIdeaComposer({ idea: null, columns: ref(makeColumns()), store: { createIdea: vi.fn(), updateIdea: vi.fn(), saving: ref(false) } })
    const ok = composer.addLink({ label: 'Docs', url: 'https://example.com' })
    expect(ok).toBe(true)
    expect(composer.links.value).toEqual([{ label: 'Docs', url: 'https://example.com' }])
    expect(composer.linkError.value).toBeNull()
    const bad = composer.addLink({ label: '', url: 'http://example.com' })
    expect(bad).toBe(false)
    expect(composer.linkError.value).toBeTruthy()
    expect(composer.links.value).toHaveLength(1)
    const bad2 = composer.addLink({ label: null, url: 'ftp://example.com' })
    expect(bad2).toBe(false)
    expect(composer.links.value).toHaveLength(1)
  })

  it('addLink dedupes by url case-insensitive trimmed', () => {
    const composer = useIdeaComposer({ idea: null, columns: ref(makeColumns()), store: { createIdea: vi.fn(), updateIdea: vi.fn(), saving: ref(false) } })
    composer.addLink({ label: 'A', url: 'https://example.com' })
    const dup = composer.addLink({ label: 'B', url: 'https://EXAMPLE.com ' })
    expect(dup).toBe(false)
    expect(composer.links.value).toHaveLength(1)
  })

  it('removeLink removes by url', () => {
    const idea = makeIdea({ links: [{ url: 'https://example.com', label: 'Docs' }] })
    const composer = useIdeaComposer({ idea, columns: ref(makeColumns()), store: { createIdea: vi.fn(), updateIdea: vi.fn(), saving: ref(false) } })
    composer.removeLink('https://example.com')
    expect(composer.links.value).toEqual([])
  })

  it('uses normalizeForSubmission for notes on save and dedupes tags', async () => {
    const createIdea = vi.fn().mockResolvedValue(makeIdea())
    const composer = useIdeaComposer({ idea: null, columns: ref(makeColumns()), store: { createIdea, updateIdea: vi.fn(), saving: ref(false) } })
    composer.title.value = 'My idea'
    composer.notes.value = '**bold** #HELLO'
    composer.tags.value = ['HELLO', 'hello', 'world']
    composer.columnId.value = 'raw'
    await composer.save()
    expect(createIdea).toHaveBeenCalledWith(expect.objectContaining({
      title: 'My idea',
      tags: ['hello', 'world'],
      columnId: 'raw',
    }))
    const notesArg = createIdea.mock.calls[0]?.[0]?.notes
    expect(notesArg).toBe('bold #hello')
  })

  it('duplicate-save guard prevents second call while saving', async () => {
    let resolveFirst: (v: unknown) => void = () => {}
    const createIdea = vi.fn().mockImplementation(() => new Promise((res) => { resolveFirst = res as never }))
    const saving = ref(false)
    const storeSaving = ref(false)
    const composer = useIdeaComposer({ idea: null, columns: ref(makeColumns()), store: { createIdea, updateIdea: vi.fn(), saving: storeSaving } })
    composer.title.value = 'Title'
    const first = composer.save()
    expect(composer.isSaving.value).toBe(true)
    const second = await composer.save()
    expect(second).toBeNull()
    expect(createIdea).toHaveBeenCalledTimes(1)
    resolveFirst(makeIdea())
    await first
    expect(composer.isSaving.value).toBe(false)
    void saving
  })

  it('exposes markdownEditor with applyBold', () => {
    const composer = useIdeaComposer({ idea: null, columns: ref(makeColumns()), store: { createIdea: vi.fn(), updateIdea: vi.fn(), saving: ref(false) } })
    expect(composer.markdownEditor).toBeDefined()
    expect(typeof composer.markdownEditor.applyBold).toBe('function')
    composer.notes.value = 'hello'
    expect(composer.markdownEditor.plainTextForPreview.value).toBeDefined()
  })

  it('column change persists via save', async () => {
    const idea = makeIdea({ columnId: 'raw' })
    const updateIdea = vi.fn().mockResolvedValue({ ...idea, columnId: 'done' })
    const composer = useIdeaComposer({ idea, columns: ref(makeColumns()), store: { createIdea: vi.fn(), updateIdea, saving: ref(false) } })
    composer.columnId.value = 'done'
    composer.title.value = 'Original title'
    await composer.save()
    expect(updateIdea).toHaveBeenCalledWith('idea-1', expect.objectContaining({ columnId: 'done' }))
  })

  it('reset restores initial draft', () => {
    const idea = makeIdea({ title: 'Keep' })
    const composer = useIdeaComposer({ idea, columns: ref(makeColumns()), store: { createIdea: vi.fn(), updateIdea: vi.fn(), saving: ref(false) } })
    composer.title.value = 'Changed'
    composer.tags.value = ['a']
    composer.reset()
    expect(composer.title.value).toBe('Keep')
    expect(composer.isDirty.value).toBe(false)
  })
})

describe('buildPublishingPrefill', () => {
  it('joins title, notes and hashtags with dedupe', () => {
    const prefill = buildPublishingPrefill({ title: 'Launch idea', notes: 'Focus on ROI', tags: ['launch', 'roi'] } as Idea)
    expect(prefill).toBe('Launch idea\n\nFocus on ROI\n\n#launch #roi')
  })

  it('avoids duplicating tags already present as hashtags in notes', () => {
    const prefill = buildPublishingPrefill({ title: 'T', notes: 'Notes about #kafka and streams', tags: ['kafka', 'testing'] } as Idea)
    expect(prefill).toBe('T\n\nNotes about #kafka and streams\n\n#testing')
  })

  it('dedupes hashtags case-insensitive and trims', () => {
    const prefill = buildPublishingPrefill({ title: 'T', notes: '#Kafka', tags: ['kafka', 'Kafka', 'testing'] } as Idea)
    expect(prefill.toLowerCase().match(/#kafka/g)?.length).toBe(1)
    expect(prefill).toContain('#testing')
  })

  it('handles empty notes and tags', () => {
    expect(buildPublishingPrefill({ title: 'Only title', notes: null, tags: [] } as unknown as Idea)).toBe('Only title')
    expect(buildPublishingPrefill({ title: 'T', notes: 'N', tags: [] } as unknown as Idea)).toBe('T\n\nN')
    expect(buildPublishingPrefill({ title: 'T', notes: null, tags: ['vue'] } as unknown as Idea)).toBe('T\n\n#vue')
  })

  it('trims title and notes and ignores empty tag strings', () => {
    const prefill = buildPublishingPrefill({ title: '  T  ', notes: '  N  ', tags: [' ', 'vue ', ''] } as unknown as Idea)
    expect(prefill).toBe('T\n\nN\n\n#vue')
  })
})
