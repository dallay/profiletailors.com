#!/usr/bin/env node
// Checks that no frontend dependency uses a licence blocked under the project's AGPL-3.0 policy.
// Reads pnpm's JSON licence output from stdin or a file path given as first argument.
// Exits with code 1 if a blocked licence is found; 0 otherwise.
//
// Usage:
//   pnpm licenses list --json | node scripts/check-frontend-licences.mjs
//   node scripts/check-frontend-licences.mjs frontend-licences.json

import { readFileSync, readSync } from "node:fs";

const BLOCKED = [
  "GPL-2.0-only",
  "GPL-2.0",
  "GNU General Public License, version 2",
  "GNU General Public License v2.0 only",
];

function readInput() {
  const arg = process.argv[2];
  if (arg) {
    return readFileSync(arg, "utf8");
  }
  // Read from stdin if no file argument provided
  let data = "";
  try {
    data = readFileSync("/dev/stdin", "utf8");
  } catch {
    // On Windows /dev/stdin is unavailable; fall back to process.stdin sync read
    const buf = [];
    const fd = 0; // stdin
    const chunk = Buffer.alloc(4096);
    let bytesRead;
    try {
      while ((bytesRead = readSync(fd, chunk, 0, chunk.length, null)) > 0) {
        buf.push(chunk.subarray(0, bytesRead).toString());
      }
      data = buf.join("");
    } catch {
      console.error("❌  Could not read licence data from stdin or file argument.");
      process.exit(1);
    }
  }
  return data;
}

const raw = readInput();

// pnpm licenses list --json outputs an object keyed by licence name.
// Each value is an array of package records.
let licenceMap;
try {
  licenceMap = JSON.parse(raw);
} catch {
  console.error("❌  Could not parse licence JSON. Ensure pnpm licenses list --json succeeded.");
  process.exit(1);
}

const violations = [];
for (const [licence, packages] of Object.entries(licenceMap)) {
  if (BLOCKED.some((b) => licence.includes(b))) {
    const names = (packages ?? []).map((p) => `${p.name}@${p.version}`).join(", ");
    violations.push(`  ${licence}: ${names}`);
  }
}

if (violations.length > 0) {
  console.error("❌  Frontend dependency licence check FAILED.");
  console.error("    The following blocked licences were found:\n");
  for (const v of violations) {
    console.error(v);
  }
  console.error(
    "\n    Replace the offending dependency or obtain a legal exception before merging.",
  );
  process.exit(1);
}

console.log("✅  Frontend dependency licence check passed.");
