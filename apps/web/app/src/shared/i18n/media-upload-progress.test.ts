import { describe, expect, it } from 'vitest'
import { createI18n } from 'vue-i18n'
import en from './locales/en/media'
import es from './locales/es/media'

describe('media upload progress translations', () => {
  const cases = [
    {
      locale: 'en',
      messages: en,
      uploading: ['Uploading 1 file', 'Uploading 2 files'],
      uploaded: ['1 file uploaded', '2 files uploaded'],
      failed: ['1 file failed', '2 files failed'],
    },
    {
      locale: 'es',
      messages: es,
      uploading: ['Subiendo 1 archivo', 'Subiendo 2 archivos'],
      uploaded: ['1 archivo subido', '2 archivos subidos'],
      failed: ['1 archivo fallido', '2 archivos fallidos'],
    },
  ]

  for (const entry of cases) {
    for (const state of ['uploading', 'uploaded', 'failed'] as const) {
      it(`${entry.locale} renders singular and plural ${state} progress`, () => {
        const i18n = createI18n({
          legacy: false,
          locale: entry.locale,
          messages: { [entry.locale]: { media: entry.messages } },
        })
        expect(i18n.global.t(`media.uploadProgress.${state}`, { count: 1 })).toBe(entry[state][0])
        expect(i18n.global.t(`media.uploadProgress.${state}`, { count: 2 })).toBe(entry[state][1])
      })
    }
  }
})
