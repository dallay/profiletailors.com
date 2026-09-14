/// <reference types="vitest" />

import { beforeEach, afterEach, vi } from 'vitest'

// Reset process.env between tests to avoid pollution
beforeEach(() => {
  // Restore VITE_API_BASE_URL to undefined between tests
  delete process.env.VITE_API_BASE_URL
})

afterEach(() => {
  vi.restoreAllMocks()
  // Clean up any env overrides
  delete process.env.VITE_API_BASE_URL
})

if (
  typeof window.HTMLDialogElement !== 'undefined' &&
  !window.HTMLDialogElement.prototype.showModal
) {
  window.HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
    this.setAttribute('open', '')
  }
  window.HTMLDialogElement.prototype.close = function (this: HTMLDialogElement) {
    this.removeAttribute('open')
  }
}
