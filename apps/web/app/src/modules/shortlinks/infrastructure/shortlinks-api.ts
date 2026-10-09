import { useAuthStore } from '@modules/auth'

export interface WorkspaceLinkMetric {
  id: string
  shortCode: string
  shortUrl: string
  destinationUrl: string
  status: string
  createdAt: string
  expiresAt: string | null
  version: number
  recordedRedirects: number
}

export interface WorkspaceLinkMetricsPage {
  links: WorkspaceLinkMetric[]
  nextCursor: string | null
}

export interface CreatedShortlink {
  id: string
  shortUrl: string
  destinationUrl: string
}

export async function createShortlink(destinationUrl: string): Promise<CreatedShortlink> {
  const auth = useAuthStore()
  const response = await auth.apiFetch<CreatedShortlink>('/api/v1/links', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/vnd.api.v1+json' },
    body: JSON.stringify({ destinationUrl }),
  })
  return response
}

export async function listWorkspaceShortlinkMetrics(
  cursor: string | null,
): Promise<WorkspaceLinkMetricsPage> {
  const auth = useAuthStore()
  const params = new URLSearchParams({ limit: '20' })
  if (cursor) params.set('cursor', cursor)
  return auth.apiFetch<WorkspaceLinkMetricsPage>(`/api/v1/links?${params}`, {
    headers: { Accept: 'application/vnd.api.v1+json' },
  })
}
