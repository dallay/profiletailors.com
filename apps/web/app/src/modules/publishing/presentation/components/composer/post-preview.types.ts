export const PREVIEW_PROVIDERS = {
  LINKEDIN: 'linkedin',
} as const

export type PreviewProvider = (typeof PREVIEW_PROVIDERS)[keyof typeof PREVIEW_PROVIDERS]

export type PostPreviewMedia = {
  kind: 'image' | 'video'
  url?: string | null
  alt: string
  name?: string | null
}

export type LinkedInPreviewModel = {
  authorName: string
  authorHandle: string
  authorAvatarUrl?: string | null
  authorInitials: string
  text: string
  placeholderText: string
  media?: PostPreviewMedia | null
}
