/**
 * Provides media asset utility functions for formatting and user interactions
 */
export function formatFileSize(bytes: number | null | undefined): string | null {
  if (bytes == null || Number.isNaN(bytes)) return null
  if (bytes < 1024) return `${bytes} B`
  const units = ['KB', 'MB', 'GB', 'TB']
  let size = bytes / 1024
  let unitIndex = 0
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024
    unitIndex += 1
  }
  return `${size.toFixed(size >= 10 ? 0 : 1)} ${units[unitIndex]}`
}

/**
 * Triggers download of media asset by creating temporary link and clicking it
 */
export function triggerAssetDownload(
  downloadUrl: string | null,
  filename: string = 'media-asset',
): void {
  if (!downloadUrl) return

  const link = document.createElement('a')
  link.href = downloadUrl
  link.download = filename
  link.target = '_blank'
  link.rel = 'noopener noreferrer'
  document.body.appendChild(link)
  link.click()
  link.remove()
}
