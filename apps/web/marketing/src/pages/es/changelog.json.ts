import type { APIRoute } from 'astro'
import { handleChangelogJsonRequest } from '@/lib/changelog-feed'

export const GET: APIRoute = ({ site }) => handleChangelogJsonRequest(site, 'es')
