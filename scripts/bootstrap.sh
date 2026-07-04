#!/usr/bin/env bash
set -e

# Profile Tailors — Non-root Linux Bootstrap
# This script installs 'just' and triggers the repository setup.

BIN_DIR="$HOME/.local/bin"
mkdir -p "$BIN_DIR"

if ! command -v just >/dev/null 2>&1; then
  echo "▸ Installing 'just' to $BIN_DIR..."
  curl --proto '=https' --tlsv1.2 -sSf https://just.systems/install.sh | bash -s -- --to "$BIN_DIR"
  export PATH="$BIN_DIR:$PATH"
fi

echo "▸ Running 'just setup'..."
"$BIN_DIR/just" setup
