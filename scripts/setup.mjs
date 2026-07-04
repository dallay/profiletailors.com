import { execSync } from 'node:child_process';
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import process from 'node:process';

const colors = {
  reset: '\x1b[0m',
  green: '\x1b[32m',
  blue: '\x1b[34m',
  bold: '\x1b[1m',
  yellow: '\x1b[33m',
};

console.log(`${colors.bold}Profile Tailors — Setup${colors.reset}\n`);

// 1. .env bootstrap
const envFile = join(process.cwd(), '.env');
const envExample = join(process.cwd(), '.env.example');

if (!existsSync(envFile)) {
  if (existsSync(envExample)) {
    console.log('▸ Creating .env from .env.example');
    writeFileSync(envFile, readFileSync(envExample));
  } else {
    console.log(`${colors.yellow}⚠️  .env.example not found, skipping .env creation${colors.reset}`);
  }
} else {
  console.log('▸ .env already exists, skipping creation');
}

// 2. Install dependencies
console.log('\n▸ Installing dependencies...');
if (process.env.SKIP_PNPM_INSTALL !== "true") {
  execSync('pnpm install --frozen-lockfile', { stdio: 'inherit' });
} else {
  console.log('○ Skipping pnpm install (SKIP_PNPM_INSTALL=true)');
}

// 3. Install hooks
console.log('\n▸ Installing Git hooks...');
try {
  execSync('node scripts/install-hooks.mjs', { stdio: 'inherit' });
} catch (e) {
  console.error(`${colors.yellow}⚠️  Failed to install hooks (non-fatal)${colors.reset}`);
}

// 4. AI Agents Sync
console.log('\n▸ Syncing AI agents...');
try {
  execSync('pnpm dlx @dallay/agentsync apply', { stdio: 'inherit' });
} catch (e) {
  console.error(`${colors.yellow}⚠️  Failed to sync AI agents (non-fatal)${colors.reset}`);
}

// 5. Codegraph init (optional)
console.log('\n▸ Initializing Codegraph (if available)...');
try {
  execSync('codegraph --version', { stdio: 'ignore' });
  execSync('codegraph init', { stdio: 'inherit' });
} catch (e) {
  console.log('○ Codegraph not found, skipping');
}

console.log(`\n${colors.green}${colors.bold}✓ Setup complete!${colors.reset}`);
