import { computed, ref, isRef, watch, type Ref } from 'vue'
import { useMarkdownEditor } from '@modules/publishing/application/useMarkdownEditor'
import { normalizeForSubmission } from '@modules/publishing/application/markdown'
import { useComposerScheduling } from '@modules/publishing/application/useComposerScheduling'
import type { Idea, IdeaColumn, CreateIdeaInput, UpdateIdeaInput, IdeaLink } from '@modules/ideas/domain'

export type UseIdeaComposerOptions = {
  idea?: Idea | null | Ref<Idea | null>
  columns: Ref<IdeaColumn[]>
  store: {
    createIdea: (input: CreateIdeaInput) => Promise<Idea>
    updateIdea: (id: string, input: UpdateIdeaInput) => Promise<Idea>
    saving: Ref<boolean>
  }
  initialColumnId?: string | null | Ref<string | null>
}

function normalizeTagValue(raw: string): string | null {
  const trimmed = raw.trim().toLowerCase()
  return trimmed.length ? trimmed : null
}

function isHttpsUrl(raw: string): boolean {
  const trimmed = raw.trim()
  if (!trimmed.toLowerCase().startsWith('https://')) return false
  try {
    const url = new URL(trimmed)
    return url.protocol === 'https:'
  } catch {
    return false
  }
}

function dedupeTags(input: string[]): string[] {
  const seen = new Set<string>()
  const out: string[] = []
  for (const raw of input) {
    const normalized = normalizeTagValue(raw)
    if (!normalized) continue
    if (seen.has(normalized)) continue
    seen.add(normalized)
    out.push(normalized)
  }
  return out
}

export function buildPublishingPrefill(idea: Pick<Idea, 'title' | 'notes' | 'tags'>): string {
  const title = idea.title?.trim() ?? ''
  const notes = typeof idea.notes === 'string' ? idea.notes.trim() : ''
  const tags = Array.isArray(idea.tags) ? idea.tags : []
  const deduped = dedupeTags(tags)
  const hashtagsInNotes = new Set<string>()
  if (notes) {
    const re = /#([a-z0-9_]+)/gi
    let m: RegExpExecArray | null
    while ((m = re.exec(notes)) !== null) {
      const body = (m[1] ?? '').toLowerCase().replace(/[^a-z0-9_]/g, '')
      if (body) hashtagsInNotes.add(body)
    }
  }
  const filtered = deduped.filter((t) => {
    const norm = t.toLowerCase().replace(/[^a-z0-9_]/g, '')
    return norm ? !hashtagsInNotes.has(norm) : false
  })
  const tagsBlock = filtered.map((t) => `#${t}`).join(' ')
  return [title, notes || null, tagsBlock || null].filter(Boolean).join('\n\n')
}

export function useIdeaComposer(options: UseIdeaComposerOptions) {
  const ideaRef: Ref<Idea | null> = isRef(options.idea)
    ? (options.idea as Ref<Idea | null>)
    : ref((options.idea as Idea | null) ?? null)
  const initialColumnIdRef: Ref<string | null> = isRef(options.initialColumnId)
    ? (options.initialColumnId as Ref<string | null>)
    : ref((options.initialColumnId as string | null) ?? null)

  const mode = computed<'create' | 'edit'>(() => (ideaRef.value ? 'edit' : 'create'))

  const title = ref(ideaRef.value?.title ?? '')
  const notes = ref(ideaRef.value?.notes ?? '')
  const tags = ref<string[]>(ideaRef.value ? [...(ideaRef.value.tags ?? [])] : [])
  const links = ref<IdeaLink[]>(ideaRef.value ? ideaRef.value.links.map((l) => ({ ...l })) : [])
  const columnId = ref(
    ideaRef.value?.columnId ?? initialColumnIdRef.value ?? options.columns.value[0]?.id ?? '',
  )
  const linkError = ref<string | null>(null)
  const isSaving = ref(false)

  const markdownEditor = useMarkdownEditor({ postText: notes })
  const scheduling = useComposerScheduling()

  const initialSnapshot = ref({
    title: title.value,
    notes: notes.value,
    tags: [...tags.value],
    links: links.value.map((l) => ({ ...l })),
    columnId: columnId.value,
  })

  watch(
    () => ideaRef.value,
    (next) => {
      if (next) {
        title.value = next.title
        notes.value = next.notes ?? ''
        tags.value = [...(next.tags ?? [])]
        links.value = next.links.map((l) => ({ ...l }))
        columnId.value = next.columnId
      } else {
        title.value = ''
        notes.value = ''
        tags.value = []
        links.value = []
        columnId.value = initialColumnIdRef.value ?? options.columns.value[0]?.id ?? ''
      }
      initialSnapshot.value = {
        title: title.value,
        notes: notes.value,
        tags: [...tags.value],
        links: links.value.map((l) => ({ ...l })),
        columnId: columnId.value,
      }
      linkError.value = null
      isSaving.value = false
    },
  )

  watch(
    () => initialColumnIdRef.value,
    (next) => {
      if (!ideaRef.value && next) {
        columnId.value = next
        initialSnapshot.value.columnId = next
      }
    },
  )

  const titleError = computed<string | null>(() => {
    if (!title.value.trim()) return 'Title is required'
    return null
  })

  const isValid = computed(() => !titleError.value)

  const isDirty = computed(() => {
    const snap = initialSnapshot.value
    if (title.value !== snap.title) return true
    if (notes.value !== snap.notes) return true
    if (columnId.value !== snap.columnId) return true
    if (tags.value.length !== snap.tags.length) return true
    if (tags.value.some((t, i) => t !== snap.tags[i])) return true
    if (links.value.length !== snap.links.length) return true
    for (let i = 0; i < links.value.length; i++) {
      const a = links.value[i]
      const b = snap.links[i]
      if (!b) return true
      if ((a?.label ?? null) !== (b.label ?? null)) return true
      if (a?.url !== b.url) return true
    }
    return false
  })

  function addTag(raw: string): void {
    const normalized = normalizeTagValue(raw)
    if (!normalized) return
    const exists = tags.value.some((t) => t.toLowerCase() === normalized)
    if (exists) return
    tags.value = [...tags.value, normalized]
  }

  function removeTag(raw: string): void {
    const normalized = normalizeTagValue(raw)
    if (!normalized) return
    tags.value = tags.value.filter((t) => t.toLowerCase() !== normalized)
  }

  function addLink(input: { label?: string | null; url: string }): boolean {
    const urlTrimmed = input.url.trim()
    if (!urlTrimmed) {
      linkError.value = 'URL is required'
      return false
    }
    if (!isHttpsUrl(urlTrimmed)) {
      linkError.value = 'URL must start with https://'
      return false
    }
    const normalizedUrl = urlTrimmed
    const exists = links.value.some((l) => l.url.trim().toLowerCase() === normalizedUrl.toLowerCase())
    if (exists) {
      linkError.value = 'Link already added'
      return false
    }
    const labelTrimmed = input.label?.trim() || null
    links.value = [...links.value, { label: labelTrimmed, url: normalizedUrl }]
    linkError.value = null
    return true
  }

  function removeLink(url: string): void {
    const trimmed = url.trim().toLowerCase()
    links.value = links.value.filter((l) => l.url.trim().toLowerCase() !== trimmed)
    linkError.value = null
  }

  function reset(): void {
    const snap = initialSnapshot.value
    title.value = snap.title
    notes.value = snap.notes
    tags.value = [...snap.tags]
    links.value = snap.links.map((l) => ({ ...l }))
    columnId.value = snap.columnId
    linkError.value = null
    isSaving.value = false
  }

  function buildSubmission(): { title: string; notes: string | null; tags: string[]; links: IdeaLink[]; columnId: string } {
    const deduped = dedupeTags(tags.value)
    const normalizedNotesRaw = notes.value ? normalizeForSubmission(notes.value) : ''
    const normalizedNotes = normalizedNotesRaw.trim() ? normalizedNotesRaw : null
    const dedupedLinks = (() => {
      const seen = new Set<string>()
      const out: IdeaLink[] = []
      for (const link of links.value) {
        const key = link.url.trim().toLowerCase()
        if (seen.has(key)) continue
        if (!isHttpsUrl(link.url)) continue
        seen.add(key)
        out.push({ label: link.label?.trim() || null, url: link.url.trim() })
      }
      return out
    })()
    return {
      title: title.value.trim(),
      notes: normalizedNotes,
      tags: deduped,
      links: dedupedLinks,
      columnId: columnId.value,
    }
  }

  async function save(): Promise<Idea | null> {
    if (isSaving.value) return null
    if (!isValid.value) return null
    const submission = buildSubmission()
    if (!submission.title) return null
    isSaving.value = true
    try {
      const currentIdea = ideaRef.value
      if (mode.value === 'create') {
        const created = await options.store.createIdea({
          title: submission.title,
          notes: submission.notes,
          tags: submission.tags,
          links: submission.links,
          columnId: submission.columnId,
        })
        return created
      }
      if (!currentIdea) return null
      const updated = await options.store.updateIdea(currentIdea.id, {
        title: submission.title,
        notes: submission.notes,
        tags: submission.tags,
        links: submission.links,
        columnId: submission.columnId,
      })
      return updated
    } finally {
      isSaving.value = false
    }
  }

  return {
    mode,
    title,
    notes,
    tags,
    links,
    columnId,
    linkError,
    isSaving,
    titleError,
    isValid,
    isDirty,
    markdownEditor,
    scheduling,
    addTag,
    removeTag,
    addLink,
    removeLink,
    reset,
    buildSubmission,
    save,
  }
}
