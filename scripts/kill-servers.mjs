#!/usr/bin/env node
import { execSync } from 'node:child_process'

const isWin = process.platform === 'win32'
console.log('Stopping dev servers...')

try {
  if (isWin) {
    execSync('taskkill /F /IM java.exe /T', { stdio: 'ignore' })
    execSync('taskkill /F /IM node.exe /T', { stdio: 'ignore' })
  } else {
    execSync('pkill -f bootRun', { stdio: 'ignore' })
    execSync('pkill -f vite', { stdio: 'ignore' })
    execSync('pkill -f GradleDaemon', { stdio: 'ignore' })
  }
} catch {}

console.log('Servers stopped')
