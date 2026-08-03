#!/usr/bin/env node
import { spawn, spawnSync } from 'node:child_process'

const force = process.argv[2] || ''
const isWin = process.platform === 'win32'

if (force && force !== '--force') {
  console.error(`Unknown option: ${force}`)
  console.error('Usage: just serve [--force]')
  process.exit(2)
}

if (force === '--force') {
  const kill = spawnSync('node', ['scripts/kill-servers.mjs'], { stdio: 'inherit', shell: isWin })
  if ((kill.status ?? 1) !== 0) {
    process.exit(kill.status ?? 1)
  }
}

console.log(
  'Ensure Portless proxy is running for https://pt-app.localhost (run `portless proxy start` if needed).',
)
console.log('Starting backend (Spring Boot) + frontend app (Vite)...')

const gradleWrapper = isWin ? 'gradlew.bat' : './gradlew'
const backend = spawn(
  gradleWrapper,
  [':server:smp:bootRun', '--args=--spring.profiles.active=dev'],
  {
    stdio: 'inherit',
    shell: isWin,
  },
)

const frontend = spawn('pnpm', ['dev'], {
  cwd: 'apps/web/app',
  stdio: 'inherit',
  shell: isWin,
})

const shutdown = () => {
  if (!backend.killed) {
    backend.kill('SIGTERM')
  }
}

process.on('SIGINT', shutdown)
process.on('SIGTERM', shutdown)

frontend.on('exit', (code) => {
  shutdown()
  process.exit(code ?? 1)
})
