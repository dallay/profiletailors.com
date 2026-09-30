import { execSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

export const HEADER_GRACE_DAYS = 3
export const LAST_UPDATED_PATTERN = /Last Updated:?\s*\*?\*?\s*(\d{4}-\d{2}-\d{2})/i
const MS_PER_DAY = 24 * 60 * 60 * 1000

export function findDocFiles(dir) {
  let results = []
  if (!fs.existsSync(dir)) return results
  const list = fs.readdirSync(dir)
  for (const file of list) {
    const filePath = path.join(dir, file)
    const stat = fs.statSync(filePath)
    if (stat?.isDirectory()) {
      results = results.concat(findDocFiles(filePath))
    } else if (file.endsWith('.md')) {
      results.push(filePath)
    }
  }
  return results
}

export function readHeaderDate(file) {
  const content = fs.readFileSync(file, 'utf-8')
  return content.match(LAST_UPDATED_PATTERN)?.[1] ?? null
}

export function readAuthorDate(file) {
  try {
    return execSync(`git log -1 --format="%ad" --date=short "${file}"`, {
      stdio: ['pipe', 'pipe', 'ignore'],
    })
      .toString()
      .trim()
  } catch {
    return ''
  }
}

export function wholeDaysBetween(earlierDay, laterDay) {
  return Math.round((Date.parse(laterDay) - Date.parse(earlierDay)) / MS_PER_DAY)
}

export function isHeaderStale(headerDate, authorDate, graceDays = HEADER_GRACE_DAYS) {
  return wholeDaysBetween(headerDate, authorDate) > graceDays
}

export function checkFiles(files, dependencies = {}) {
  const {
    getHeaderDate = readHeaderDate,
    getAuthorDate = readAuthorDate,
    graceDays = HEADER_GRACE_DAYS,
  } = dependencies
  const stale = []
  for (const file of files) {
    const headerDate = getHeaderDate(file)
    if (!headerDate) continue
    const authorDate = getAuthorDate(file)
    if (authorDate && isHeaderStale(headerDate, authorDate, graceDays)) {
      stale.push({ file, headerDate, authorDate })
    }
  }
  return stale
}

function runCli() {
  const stale = checkFiles(findDocFiles('docs'))
  for (const { file, headerDate, authorDate } of stale) {
    console.error(
      `❌ Stale 'Last Updated' date in ${file}: header is ${headerDate}, but the file was last changed ${authorDate} (allowed lag: ${HEADER_GRACE_DAYS} days). Update the header to today.`,
    )
  }
  if (stale.length > 0) {
    console.error(
      '\nDocumentation date validation failed. Update the "Last Updated" header in the files above.',
    )
    process.exit(1)
  } else {
    console.log('✅ All documentation Last Updated dates are valid and up to date.')
  }
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  runCli()
}
