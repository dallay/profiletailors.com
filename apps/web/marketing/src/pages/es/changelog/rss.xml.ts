import type { APIRoute } from 'astro'
import { handleChangelogRssRequest } from '@/lib/changelog-feed'

export const GET: APIRoute = ({ site }) => handleChangelogRssRequest(site, 'es')
