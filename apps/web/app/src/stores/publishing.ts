import { ref } from 'vue'
import { defineStore } from 'pinia'
import { useAuthStore } from './auth'

export interface Channel {
  id: string
  name: string
  provider: 'twitter' | 'linkedin' | 'instagram' | 'facebook'
  avatar: string
  handle: string
  status: 'ACTIVE' | 'INACTIVE'
  accountId: string // Maps to backend socialAccountId if available
}

export interface Publication {
  id: string
  content: string
  title?: string
  channels: ('twitter' | 'linkedin' | 'instagram' | 'facebook')[]
  scheduledAt: string // ISO string
  status: 'DRAFT' | 'QUEUED' | 'SCHEDULED' | 'PROCESSING' | 'PUBLISHED' | 'FAILED' | 'CANCELLED'
  priority: boolean
  thumbnail?: string
  mediaFiles?: File[] // Local file list for previewing uploads
}

export const usePublishingStore = defineStore('publishing', () => {
  const auth = useAuthStore()

  // Seeding initial mock channels
  const channels = ref<Channel[]>([
    {
      id: 'ch-twitter',
      name: 'yacosta738',
      provider: 'twitter',
      avatar:
        'https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=150&q=80',
      handle: '@yacosta738',
      status: 'ACTIVE',
      accountId: 'account-twitter-mock',
    },
    {
      id: 'ch-linkedin',
      name: 'Yuniel Acosta Pérez',
      provider: 'linkedin',
      avatar:
        'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=150&q=80',
      handle: 'Yuniel Acosta Pérez',
      status: 'ACTIVE',
      accountId: 'account-linkedin-mock',
    },
    {
      id: 'ch-instagram',
      name: 'yacosta738',
      provider: 'instagram',
      avatar:
        'https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=150&q=80',
      handle: '@yacosta738',
      status: 'ACTIVE',
      accountId: 'account-instagram-mock',
    },
  ])

  // Seeding initial mock publications
  const initialPublications: Publication[] = [
    {
      id: 'pub-1',
      content:
        'El error más común con DDD: crear modelos anémicos y tratar la base de datos como el centro del diseño. ¡Concéntrate en el comportamiento primero!',
      title: 'Common DDD Mistake',
      channels: ['linkedin', 'twitter'],
      scheduledAt: '2026-06-09T22:00:00Z', // Today 10:00 PM in UTC (fits Madrid timezone)
      status: 'QUEUED',
      priority: false,
      thumbnail:
        'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=200&q=80',
    },
    {
      id: 'pub-2',
      content:
        '¿Por qué tu Arquitectura debería seguir principios de diseño suizos? Minimalismo visual, tipografía clara y cero ruido ornamental.',
      title: 'Swiss Design Architecture',
      channels: ['linkedin'],
      scheduledAt: '2026-06-09T22:00:00Z', // Today 10:00 PM in UTC
      status: 'QUEUED',
      priority: false,
      thumbnail:
        'https://images.unsplash.com/photo-1507238691740-187a5b1d37b8?auto=format&fit=crop&w=200&q=80',
    },
  ]

  // Persisted publications list
  const publications = ref<Publication[]>([])
  // Track object URLs for memory cleanup
  const objectUrls = new Map<string, string>()

  // Load from localStorage or seed
  const stored = localStorage.getItem('pt_publications')
  if (stored) {
    try {
      publications.value = JSON.parse(stored)
    } catch {
      publications.value = initialPublications
    }
  } else {
    publications.value = initialPublications
  }

  // Active filters and settings
  const timezone = ref('Madrid')
  const filterTag = ref('')
  const filterChannel = ref('')
  const filterPostType = ref('all')
  const viewMode = ref<'calendar' | 'list'>('calendar')

  // Save changes helper
  function saveToStorage() {
    localStorage.setItem('pt_publications', JSON.stringify(publications.value))
  }

  // Actions
  async function schedulePost(post: {
    content: string
    title?: string
    channels: ('twitter' | 'linkedin' | 'instagram' | 'facebook')[]
    scheduledAt: string
    priority: boolean
    mediaFiles?: File[]
  }) {
    const publicationId = `pub-${Date.now()}`

    // Create new publication object
    const newPub: Publication = {
      id: publicationId,
      content: post.content,
      title: post.title || undefined,
      channels: post.channels,
      scheduledAt: post.scheduledAt,
      status: 'QUEUED',
      priority: post.priority,
      mediaFiles: post.mediaFiles,
    }

    // Generate static image thumbnail for file preview if uploaded
    if (post.mediaFiles && post.mediaFiles.length > 0) {
      const file = post.mediaFiles[0]
      if (file?.type?.startsWith('image/')) {
        const objectUrl = URL.createObjectURL(file)
        newPub.thumbnail = objectUrl
        objectUrls.set(publicationId, objectUrl)
      }
    }

    // Try backend integration if authenticated
    if (auth.isAuthenticated) {
      try {
        // LinkedIn is the only active integration on the backend
        const hasLinkedIn = post.channels.includes('linkedin')
        if (hasLinkedIn) {
          // Find the active account ID. In production we map workspace connections
          const linkedInChannel = channels.value.find((c) => c.provider === 'linkedin')
          const accountId = linkedInChannel?.accountId || 'account-linkedin-mock'

          // Call the Spring Boot API
          await auth.apiFetch<unknown>('/api/publishing/publications', {
            method: 'POST',
            body: JSON.stringify({
              socialAccountId: accountId,
              title: post.title || 'Post via Web App',
              bodyText: post.content,
              assetIds: [], // MVP media upload is backend-signaled; assets map later
              scheduleMode: 'SCHEDULED_AT',
              scheduledFor: post.scheduledAt,
              priority: post.priority,
            }),
          })
          console.log('Successfully synced publication with backend API!')
        }
      } catch (err) {
        console.warn('Backend API unavailable. Saving to local storage mock queue instead.', err)
      }
    }

    // Push local state
    publications.value.unshift(newPub)
    saveToStorage()
    return newPub
  }

  function deletePost(id: string) {
    // Revoke object URL if tracked
    const url = objectUrls.get(id)
    if (url) {
      URL.revokeObjectURL(url)
      objectUrls.delete(id)
    }
    publications.value = publications.value.filter((p) => p.id !== id)
    saveToStorage()
  }

  function cancelPost(id: string) {
    const post = publications.value.find((p) => p.id === id)
    if (post) {
      post.status = 'CANCELLED'
      saveToStorage()
    }
  }

  function updatePost(id: string, updates: Partial<Publication>) {
    const post = publications.value.find((p) => p.id === id)
    if (post) {
      // If thumbnail is being replaced, revoke old object URL
      if (updates.thumbnail && post.thumbnail && objectUrls.has(id)) {
        URL.revokeObjectURL(objectUrls.get(id)!)
        objectUrls.delete(id)
      }
      Object.assign(post, updates)
      saveToStorage()
    }
  }

  return {
    channels,
    publications,
    timezone,
    filterTag,
    filterChannel,
    filterPostType,
    viewMode,
    schedulePost,
    deletePost,
    cancelPost,
    updatePost,
  }
})
