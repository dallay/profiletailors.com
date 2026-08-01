#!/usr/bin/env node
import { spawnSync, execSync } from 'node:child_process'

const force = process.argv[2] || ''
const isWin = process.platform === 'win32'

if (force && force !== '--force') {
  console.error(`Unknown option: ${force}`)
  console.error('Usage: just dev-frontend [--force]')
  process.exit(2)
}

if (force === '--force') {
  console.log('Killing frontend dev servers...')
  try {
    if (isWin) {
      execSync('taskkill /F /IM node.exe /T', { stdio: 'ignore' })
    } else {
      execSync('pkill -f vite', { stdio: 'ignore' })
    }
  } catch {}
}

console.log('Starting frontend dev servers (marketing + app)...')
const result = spawnSync(
  'pnpm',
  ['--parallel', '--filter', 'marketing', '--filter', 'app', 'dev'],
  {
    stdio: 'inherit',
    shell: isWin,
  },
)

process.exit(result.status ?? 1)
