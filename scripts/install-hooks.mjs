import { execSync } from 'node:child_process';
import process from 'node:process';

const colors = {
  reset: '\x1b[0m',
  green: '\x1b[32m',
  yellow: '\x1b[33m',
  bold: '\x1b[1m',
};

console.log(`${colors.bold}Lefthook — Hook Installation${colors.reset}`);

try {
  let hooksPath = '';
  try {
    hooksPath = execSync('git config --get core.hooksPath', { stdio: ['pipe', 'pipe', 'ignore'] }).toString().trim();
  } catch (e) {
    // ignore
  }

  if (hooksPath === '/dev/null') {
    console.log('○ Skipping Lefthook install: core.hooksPath=/dev/null (hooks are disabled)');
    process.exit(0);
  }

  console.log('▸ Running lefthook install...');
  execSync('pnpm exec lefthook install', { stdio: 'inherit' });
  console.log(`${colors.green}✓ Lefthook hooks installed successfully${colors.reset}`);
} catch (e) {
  console.log(`${colors.yellow}⚠️  Lefthook installation failed or skipped. This is normal in some CI/restricted environments.${colors.reset}`);
  // We don't exit with non-zero here because setup should continue
}
