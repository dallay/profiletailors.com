import fs from 'node:fs'
import path from 'node:path'

/**
 * Normalizes LCOV report source paths (SF: entries) so SonarQube executing from the repo root
 * can resolve source files accurately.
 *
 * Usage: node scripts/normalize-lcov.mjs <package-relative-prefix> <lcov-file-path>
 * Example: node scripts/normalize-lcov.mjs apps/web/app apps/web/app/coverage/lcov.info
 */

const [packagePrefix, lcovPath] = process.argv.slice(2)

if (!packagePrefix || !lcovPath) {
  console.error('Usage: node scripts/normalize-lcov.mjs <package-relative-prefix> <lcov-file-path>')
  process.exit(1)
}

const absoluteLcovPath = path.resolve(process.cwd(), lcovPath)

if (!fs.existsSync(absoluteLcovPath)) {
  console.error(`LCOV file not found: ${absoluteLcovPath}`)
  process.exit(1)
}

const content = fs.readFileSync(absoluteLcovPath, 'utf8')

const normalizedContent = content.replace(/^SF:(.+)$/gm, (match, filePath) => {
  const cleanPath = filePath.trim()

  // If already prefixed or absolute matching process.cwd(), normalize
  if (cleanPath.startsWith(packagePrefix)) {
    return `SF:${cleanPath}`
  }

  if (path.isAbsolute(cleanPath)) {
    const relativeToRoot = path.relative(process.cwd(), cleanPath)
    return `SF:${relativeToRoot}`
  }

  // Prepend package relative directory (e.g. src/App.vue -> apps/web/app/src/App.vue)
  const normalized = path.join(packagePrefix, cleanPath)
  return `SF:${normalized}`
})

fs.writeFileSync(absoluteLcovPath, normalizedContent, 'utf8')
console.log(`✅ Normalized LCOV paths in ${lcovPath} with prefix '${packagePrefix}'`)
