import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

function getDocFiles(dir) {
  let results = [];
  if (!fs.existsSync(dir)) return results;
  const list = fs.readdirSync(dir);
  for (const file of list) {
    const filePath = path.join(dir, file);
    const stat = fs.statSync(filePath);
    if (stat && stat.isDirectory()) {
      results = results.concat(getDocFiles(filePath));
    } else if (file.endsWith('.md')) {
      results.push(filePath);
    }
  }
  return results;
}

const docFiles = getDocFiles('docs');
let hasErrors = false;

for (const file of docFiles) {
  const content = fs.readFileSync(file, 'utf-8');
  const match = content.match(/Last Updated:?\s*\*?\*?\s*(\d{4}-\d{2}-\d{2})/i);
  if (match) {
    const docDate = match[1];
    const parsedDocDate = new Date(`${docDate}T00:00:00Z`);
    if (
      Number.isNaN(parsedDocDate.getTime()) ||
      parsedDocDate.toISOString().slice(0, 10) !== docDate
    ) {
      console.error(`❌ Invalid 'Last Updated' date in ${file}: ${docDate}`);
      hasErrors = true;
      continue;
    }
    let gitDate = '';
    try {
      gitDate = execFileSync('git', ['log', '-1', '--format=%cd', '--date=short', '--', file], {
        stdio: ['pipe', 'pipe', 'ignore'],
      }).toString().trim();
    } catch {
      // Untracked or git error
    }
    if (gitDate && docDate < gitDate) {
      console.error(
        `❌ Outdated 'Last Updated' date in ${file}: header date is ${docDate}, but last git commit was ${gitDate}`
      );
      hasErrors = true;
    }
  }
}

if (hasErrors) {
  console.error('\nDocumentation date validation failed. Please update the "Last Updated" header in the affected documents.');
  process.exit(1);
} else {
  console.log('✅ All documentation Last Updated dates are valid and up to date.');
}
