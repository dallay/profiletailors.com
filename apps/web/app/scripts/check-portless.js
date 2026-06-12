/**
 * Preflight check that ensures `portless` CLI is available before starting the dev server.
 *
 * Exits with code 1 and prints an error message if portless is not found.
 */
import { execSync } from 'node:child_process'
import process from 'node:process'

try {
  execSync('portless --version', { stdio: 'ignore' })
} catch {
  console.error('\n❌ ERROR: portless not found. See docs/portless-setup.md for installation.\n')
  process.exit(1)
}
