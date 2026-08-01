#!/usr/bin/env node
import { spawnSync } from 'node:child_process'

const scriptPath = process.argv[2]
const scriptArgs = process.argv.slice(3)

if (!scriptPath) {
  console.error('Usage: node scripts/run-shell-script.mjs <script-path> [...args]')
  process.exit(2)
}

const isWin = process.platform === 'win32'
const command = isWin ? 'bash' : scriptPath
const args = isWin ? [scriptPath, ...scriptArgs] : scriptArgs

const result = spawnSync(command, args, {
  stdio: 'inherit',
  shell: isWin,
})

if (result.error) {
  if (isWin) {
    console.error(
      'Unable to run shell script. Install Git Bash or WSL and ensure `bash` is in PATH.',
    )
  } else {
    console.error(result.error.message)
  }
  process.exit(1)
}

process.exit(result.status ?? 1)
