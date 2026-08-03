#!/usr/bin/env node
import { spawnSync } from 'node:child_process'

const isWin = process.platform === 'win32'

const hooksPathResult = spawnSync('git', ['config', '--global', 'core.hooksPath'], {
  encoding: 'utf8',
  shell: isWin,
})

const hooksPath = (hooksPathResult.stdout || '').trim()
if (hooksPath === '/dev/null') {
  console.log('Skipping Lefthook install: core.hooksPath=/dev/null')
  process.exit(0)
}

const installResult = spawnSync('pnpm', ['exec', 'lefthook', 'install'], {
  stdio: 'inherit',
  shell: isWin,
})

process.exit(installResult.status ?? 1)
