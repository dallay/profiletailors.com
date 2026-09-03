import type { APIRoute } from 'astro'
import { readFileSync, existsSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

function getDesignPath(): string {
  const currentDir = dirname(fileURLToPath(import.meta.url))
  const candidates = [
    resolve(process.cwd(), '.agents/DESIGN.md'),
    resolve(process.cwd(), '../../.agents/DESIGN.md'),
    resolve(process.cwd(), '../../../.agents/DESIGN.md'),
    resolve(currentDir, '../../../../../.agents/DESIGN.md'),
    resolve(currentDir, '../../../../.agents/DESIGN.md'),
    resolve(currentDir, '../../../.agents/DESIGN.md'),
    resolve(currentDir, '../../.agents/DESIGN.md'),
  ]
  for (const candidate of candidates) {
    if (existsSync(candidate)) {
      return candidate
    }
  }
  return candidates[0]
}

export const GET: APIRoute = () => {
  const filePath = getDesignPath()
  const content = readFileSync(filePath, 'utf-8')

  return new Response(content, {
    headers: {
      'Content-Type': 'text/markdown; charset=utf-8',
      'Cache-Control': 'public, max-age=3600',
    },
  })
}
