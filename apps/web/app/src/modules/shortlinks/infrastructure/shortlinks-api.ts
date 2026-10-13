import { useAuthStore } from '@modules/auth'

export type WorkspaceLinkMetric = {
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

export type WorkspaceLinkMetricsPage = {
  links: WorkspaceLinkMetric[]
  nextCursor: string | null
}

export type CreatedShortlink = {
  id: string
  shortUrl: string
  destinationUrl: string
}

export async function createShortlink(
  destinationUrl: string,
  options?: Pick<RequestInit, 'signal'>,
): Promise<CreatedShortlink> {
  const auth = useAuthStore()
  const response = await auth.apiFetch<CreatedShortlink>('/api/v1/links', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/vnd.api.v1+json' },
    body: JSON.stringify({ destinationUrl }),
    workspaceScoped: true,
    ...options,
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
    workspaceScoped: true,
  })
}
