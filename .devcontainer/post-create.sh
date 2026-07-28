#!/usr/bin/env bash
set -euo pipefail

echo "⟳ Installing system packages..."
sudo apt-get update -qq
sudo apt-get install -y -qq --no-install-recommends just postgresql-client 2>/dev/null || {
  echo "just not in apt — downloading binary..."
  ARCH=$(uname -m)
  case "$ARCH" in
    x86_64)  ARCH="x86_64" ;;
    aarch64) ARCH="arm64"   ;;
  esac
  curl -fsSL "https://github.com/casey/just/releases/latest/download/just-${ARCH}-unknown-linux-musl.tar.gz" \
    | sudo tar -xz -C /usr/local/bin just
}
sudo apt-get clean
rm -rf /tmp/*

echo "⟳ Enabling corepack..."
corepack enable
corepack prepare pnpm@11.11.0 --activate

echo "⟳ Setting up environment..."
if [ ! -f .env ]; then
  cp .env.example .env
  echo "   ✅ Created .env from .env.example"
fi

echo "⟳ Installing project dependencies..."
pnpm install --frozen-lockfile

echo "✅ Dev container setup complete"
just -l 2>/dev/null | head -5
