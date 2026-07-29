#!/usr/bin/env bash
# ============================================================
# record-har.sh — Record Playwright HAR from live backend
#
# Usage:
#   ./scripts/record-har.sh                    # normal record
#   UPDATE_HAR=true ./scripts/record-har.sh    # explicit (same)
#
# Prerequisites:
#   1. Backend / API running
#   2. Frontend dev server can start (pnpm dev)
#   3. Optional: E2E_TEST_USER_EMAIL / E2E_TEST_USER_PASSWORD to record
#      with a different account than the HAR defaults
#
# What it does:
#   - Starts the frontend dev server (if not already running)
#   - Runs Playwright with UPDATE_HAR=true
#   - All **/api/** responses are captured into hars/auth-flow.har
#   - Stops the frontend dev server
#
# After recording:
#   Commit the updated hars/auth-flow.har to the repo.
#   From now on, tests replay from HAR — no backend needed.
# ============================================================

set -euo pipefail

HAR_FILE="$(cd "$(dirname "$0")/.." && pwd)/hars/auth-flow.har"

echo "🎙️  Recording HAR to: $HAR_FILE"
echo ""
echo "Make sure the backend is running!"
echo "Using E2E credentials: ${E2E_TEST_USER_EMAIL:-dev@profiletailors.com}"
echo ""

# Kill frontend on exit
cleanup() {
  if [ -n "${FRONTEND_PID:-}" ]; then
    echo "Shutting down frontend..."
    kill "$FRONTEND_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT

# Start frontend if not already running
if ! curl -sf http://localhost:5173 > /dev/null 2>&1; then
  echo "Starting frontend dev server..."
  pnpm dev &
  FRONTEND_PID=$!
  echo "Waiting for frontend..."
  for i in $(seq 1 30); do
    if curl -sf http://localhost:5173 > /dev/null 2>&1; then
      echo "Frontend ready."
      break
    fi
    sleep 1
  done
fi

# Run Playwright in record mode
UPDATE_HAR=true npx playwright test --grep @integration --reporter=list

echo ""
echo "✅ HAR recorded to: $HAR_FILE"
echo "   Commit this file to lock in the API responses."
echo ""
