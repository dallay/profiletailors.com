export { createShortlink, listWorkspaceShortlinkMetrics } from './infrastructure/shortlinks-api'
export type {
  CreatedShortlink,
  WorkspaceLinkMetric,
  WorkspaceLinkMetricsPage,
} from './infrastructure/shortlinks-api'
export { extractDistinctUrls, replaceShortenedUrl } from './domain/shortlink-post-content'
