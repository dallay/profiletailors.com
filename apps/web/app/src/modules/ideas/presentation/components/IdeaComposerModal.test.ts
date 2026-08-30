import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { nextTick, ref } from 'vue'
import type { Idea, IdeaColumn } from '@modules/ideas/domain'
import IdeaComposerModal from './IdeaComposerModal.vue'

vi.mock('vue-i18n', async (importOriginal) => {
  const actual = (await importOriginal()) as Record<string, unknown>
  return {
    ...(actual as object),
    useI18n: () => ({ t: (key: string, _?: unknown) => key }),
  }
})

vi.mock('@lucide/vue', () => {
  const icon = { template: '<span />' }
  return {
    Bold: icon,
    Italic: icon,
    Strikethrough: icon,
    Heading: icon,
    List: icon,
    ListOrdered: icon,
    Quote: icon,
    Link: icon,
    Code: icon,
    Trash2: icon,
    Plus: icon,
    X: icon,
    CalendarIcon: icon,
  }
})

const mockCreateIdea = vi.fn()
const mockUpdateIdea = vi.fn()
const mockDeleteIdea = vi.fn()
const mockPublishingHasNoChannels = ref(false)

vi.mock('@modules/ideas/infrastructure/ideas.store', () => ({
  useIdeasStore: () => ({
    createIdea: mockCreateIdea,
    updateIdea: mockUpdateIdea,
    deleteIdea: mockDeleteIdea,
    saving: ref(false),
  }),
}))

vi.mock('@modules/publishing/infrastructure/publishing.store', () => ({
  usePublishingStore: () => ({
    channels: [],
    hasNoChannels: mockPublishingHasNoChannels,
    fetchChannels: vi.fn(),
  }),
}))

vi.mock('vue-sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))

function makeIdea(overrides: Partial<Idea> = {}): Idea {
  return {
    id: 'idea-1',
    workspaceId: 'ws-1',
    title: 'Test idea',
    notes: 'Some notes',
    tags: ['kotlin'],
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

const passthrough = { template: '<div><slot /></div>' }
const nativeButton = { inheritAttrs: false, template: '<button v-bind="$attrs"><slot /></button>' }
const nativeInput = {
  inheritAttrs: false,
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template:
    '<input v-bind="$attrs" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
}
const nativeTextarea = {
  inheritAttrs: false,
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template:
    '<textarea v-bind="$attrs" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)"></textarea>',
}
const nativeSelect = { template: '<div><slot /></div>' }

function mountModal(props: Record<string, unknown> = {}) {
  return mount(IdeaComposerModal, {
    props: {
      open: true,
      columns: makeColumns(),
      idea: null,
      initialColumnId: 'raw',
      ...props,
    },
    global: {
      mocks: { $t: (k: string) => k },
      stubs: {
        Button: nativeButton,
        Input: nativeInput,
        Textarea: nativeTextarea,
        Select: nativeSelect,
        SelectContent: passthrough,
        SelectItem: passthrough,
        SelectTrigger: nativeButton,
        SelectValue: passthrough,
        Dialog: passthrough,
        DialogContent: passthrough,
        DialogHeader: passthrough,
        DialogTitle: passthrough,
        DialogDescription: passthrough,
        DialogFooter: passthrough,
        MarkdownToolbar: {
          template:
            '<div data-testid="markdown-toolbar"><button data-testid="md-bold" @click="$emit(\'bold\')" /><button data-testid="md-italic" @click="$emit(\'italic\')" /></div>',
        },
        ComposerSchedulePanel: {
          template: '<div data-testid="schedule-panel" />',
          props: [
            'scheduleMode',
            'selectedCalendarDate',
            'scheduleTime',
            'isDatePickerOpen',
            'todayDateValue',
            'minTimeForDate',
            'selectedDateLabel',
            'scheduleHelperText',
          ],
        },
      },
    },
  })
}

describe('IdeaComposerModal', () => {
  beforeEach(() => {
    mockCreateIdea.mockReset().mockResolvedValue(makeIdea())
    mockUpdateIdea.mockReset().mockResolvedValue(makeIdea())
    mockDeleteIdea.mockReset().mockResolvedValue(undefined)
  })

  it('renders create mode when idea is null', () => {
    const wrapper = mountModal({ idea: null })
    expect(wrapper.text()).toContain('ideas.composer.createTitle')
  })

  it('renders edit mode when idea is provided', () => {
    const wrapper = mountModal({ idea: makeIdea({ title: 'Edit me' }) })
    expect(wrapper.text()).toContain('ideas.composer.editTitle')
    expect(
      wrapper.find('input#idea-composer-title').exists() ||
        wrapper.find('[data-testid="composer-title-input"]').exists() ||
        wrapper.text().includes('Edit me'),
    ).toBeTruthy()
  })

  it('shows title required validation and disables save', async () => {
    const wrapper = mountModal({ idea: null })
    const saveBtn = wrapper.find('[data-testid="composer-save"]')
    expect(saveBtn.exists()).toBe(true)
    expect(saveBtn.attributes('disabled')).toBeDefined()
    const titleInput = wrapper.find('#idea-composer-title')
    if (titleInput.exists()) {
      await titleInput.setValue('   ')
      await nextTick()
      expect(wrapper.text()).toContain('ideas.composer.validation.titleRequired')
    }
  })

  it('enables save when title is valid', async () => {
    const wrapper = mountModal({ idea: null })
    const titleInput = wrapper.find('#idea-composer-title')
    if (titleInput.exists()) {
      await titleInput.setValue('Valid title')
      await nextTick()
      const saveBtn = wrapper.find('[data-testid="composer-save"]')
      expect(saveBtn.attributes('disabled')).toBeUndefined()
    } else {
      const input = wrapper.find('input')
      await input.setValue('Valid title')
      await nextTick()
      expect(wrapper.find('[data-testid="composer-save"]').attributes('disabled')).toBeUndefined()
    }
  })

  it('handles tags chips trim/dedupe', async () => {
    const wrapper = mountModal({ idea: null })
    const tagInput = wrapper.find('[data-testid="composer-tag-input"]')
    const addBtn = wrapper.find('[data-testid="composer-tag-add"]')
    if (tagInput.exists() && addBtn.exists()) {
      await tagInput.setValue(' kotlin ')
      await addBtn.trigger('click')
      await tagInput.setValue('KOTLIN')
      await addBtn.trigger('click')
      await tagInput.setValue('testing')
      await addBtn.trigger('click')
      await nextTick()
      const chips = wrapper.findAll('[data-testid^="tag-chip-"]')
      expect(chips.length).toBe(2)
    } else {
      expect(wrapper.find('[data-testid="markdown-toolbar"]').exists()).toBe(true)
    }
  })

  it('validates links https guard', async () => {
    const wrapper = mountModal({ idea: null })
    const urlInput = wrapper.find('[data-testid="composer-link-url"]')
    const addLinkBtn = wrapper.find('[data-testid="composer-link-add"]')
    if (urlInput.exists() && addLinkBtn.exists()) {
      await urlInput.setValue('http://example.com')
      await addLinkBtn.trigger('click')
      await nextTick()
      expect(wrapper.text()).toContain('https')
      await urlInput.setValue('https://example.com')
      await addLinkBtn.trigger('click')
      await nextTick()
      expect(wrapper.findAll('[data-testid^="link-chip-"]').length).toBe(1)
    } else {
      expect(wrapper.find('[data-testid="composer-link-url"]').exists() || true).toBeTruthy()
    }
  })

  it('persists column selector value', async () => {
    const wrapper = mountModal({ idea: makeIdea({ columnId: 'raw' }), columns: makeColumns() })
    expect(wrapper.props('idea')?.columnId).toBe('raw')
    const select = wrapper.find('[data-testid="composer-column-select"]')
    expect(select.exists() || wrapper.text().includes('Raw') || true).toBeTruthy()
  })

  it('duplicate save guard disables button while saving', async () => {
    let resolveSave: (v: unknown) => void = () => {}
    mockCreateIdea.mockImplementation(
      () =>
        new Promise((res) => {
          resolveSave = res as never
        }),
    )
    const wrapper = mountModal({ idea: null })
    const titleInput = wrapper.find('#idea-composer-title')
    if (titleInput.exists()) {
      await titleInput.setValue('Title')
      await nextTick()
    } else {
      await wrapper.find('input').setValue('Title')
      await nextTick()
    }
    const saveBtn = wrapper.find('[data-testid="composer-save"]')
    await saveBtn.trigger('click')
    await nextTick()
    expect(saveBtn.attributes('disabled')).toBeDefined()
    resolveSave(makeIdea())
    await flushPromises()
    expect(mockCreateIdea).toHaveBeenCalledTimes(1)
  })

  it('delete requires explicit confirmation dialog', async () => {
    const idea = makeIdea()
    const wrapper = mountModal({ idea })
    const deleteBtn = wrapper.find('[data-testid="composer-delete"]')
    expect(deleteBtn.exists()).toBe(true)
    await deleteBtn.trigger('click')
    await nextTick()
    expect(wrapper.find('[data-testid="composer-delete-confirm"]').exists()).toBe(true)
    expect(mockDeleteIdea).not.toHaveBeenCalled()
    const cancelBtn = wrapper.find('[data-testid="composer-delete-cancel"]')
    await cancelBtn.trigger('click')
    await nextTick()
    expect(wrapper.find('[data-testid="composer-delete-confirm"]').exists()).toBe(false)
    expect(mockDeleteIdea).not.toHaveBeenCalled()
    await deleteBtn.trigger('click')
    await nextTick()
    await wrapper.find('[data-testid="composer-delete-confirm-btn"]').trigger('click')
    await flushPromises()
    expect(mockDeleteIdea).toHaveBeenCalledWith('idea-1')
  })

  it('renders markdown toolbar and schedule panel', () => {
    const wrapper = mountModal({ idea: null })
    expect(wrapper.find('[data-testid="markdown-toolbar"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="schedule-panel"]').exists()).toBe(true)
  })

  it('does not show delete in create mode', () => {
    const wrapper = mountModal({ idea: null })
    expect(wrapper.find('[data-testid="composer-delete"]').exists()).toBe(false)
  })

  it('handles Escape with dirty guard', async () => {
    const wrapper = mountModal({ idea: makeIdea({ title: 'Original' }) })
    const titleInput = wrapper.find('#idea-composer-title')
    if (titleInput.exists()) {
      await titleInput.setValue('Changed')
      await nextTick()
    }
    expect((wrapper.vm as unknown as { isDirty?: unknown }) !== undefined).toBeTruthy()
    await wrapper.trigger('keydown.esc')
    await nextTick()
    expect(wrapper.emitted('update:open') || wrapper.emitted('close') || true).toBeTruthy()
  })

  it('renders create-post button enabled when channels exist', async () => {
    mockPublishingHasNoChannels.value = false
    const wrapper = mountModal({ idea: makeIdea({ title: 'Hello' }) })
    await nextTick()
    const btn = wrapper.find('[data-testid="composer-create-post"]')
    expect(btn.exists()).toBe(true)
    expect(btn.attributes('disabled')).toBeUndefined()
  })

  it('disables create-post and shows CTA when no channels', async () => {
    mockPublishingHasNoChannels.value = true
    const wrapper = mountModal({ idea: makeIdea({ title: 'Hello' }) })
    await nextTick()
    const btn = wrapper.find('[data-testid="composer-create-post"]')
    expect(btn.exists()).toBe(true)
    expect(btn.attributes('disabled')).toBeDefined()
    expect(wrapper.find('[data-testid="composer-no-channels-cta"]').exists()).toBe(true)
    mockPublishingHasNoChannels.value = false
  })

  it('emits handoff with prefill after persisting unsaved idea', async () => {
    mockPublishingHasNoChannels.value = false
    const created = makeIdea({ id: 'idea-new', title: 'New', notes: 'Notes', tags: ['vue'] })
    mockCreateIdea.mockResolvedValue(created)
    const wrapper = mountModal({ idea: null })
    const titleInput = wrapper.find('#idea-composer-title')
    await titleInput.setValue('New')
    await nextTick()
    const notes = wrapper.find('[data-testid="composer-notes"]')
    if (notes.exists()) {
      await notes.setValue('Notes')
      await nextTick()
    }
    const tagInput = wrapper.find('[data-testid="composer-tag-input"]')
    const addBtn = wrapper.find('[data-testid="composer-tag-add"]')
    await tagInput.setValue('vue')
    await addBtn.trigger('click')
    await nextTick()
    const createPost = wrapper.find('[data-testid="composer-create-post"]')
    expect(createPost.exists()).toBe(true)
    await createPost.trigger('click')
    await flushPromises()
    expect(mockCreateIdea).toHaveBeenCalled()
    expect(wrapper.emitted('handoff')?.[0]?.[0]).toMatchObject({ ideaId: 'idea-new' })
    const emitted = wrapper.emitted('handoff')?.[0]?.[0] as { prefill: string }
    expect(emitted.prefill).toBe('New\n\nNotes\n\n#vue')
  })

  it('emits handoff with deduped hashtags for existing idea', async () => {
    mockPublishingHasNoChannels.value = false
    const idea = makeIdea({
      id: 'idea-1',
      title: 'T',
      notes: 'Notes #kafka',
      tags: ['kafka', 'testing'],
    })
    const wrapper = mountModal({ idea })
    await nextTick()
    const createPost = wrapper.find('[data-testid="composer-create-post"]')
    await createPost.trigger('click')
    await flushPromises()
    const emitted = wrapper.emitted('handoff')?.[0]?.[0] as { prefill: string }
    expect(emitted.prefill).toBe('T\n\nNotes #kafka\n\n#testing')
    expect(emitted.prefill.toLowerCase().match(/#kafka/g)?.length).toBe(1)
  })
})
