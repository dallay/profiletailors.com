#!/usr/bin/env bash
set -euo pipefail

JUST_VERSION="1.57.0"

# Source nvm so corepack uses the correct Node
NVM_DIR="${NVM_DIR:-/usr/local/share/nvm}"
if [ -s "${NVM_DIR}/nvm.sh" ]; then
  # shellcheck source=/dev/null
  . "${NVM_DIR}/nvm.sh"
fi

echo "⟳ Installing system packages..."
sudo apt-get update -qq

# Install just from apt when available
if sudo apt-get install -y -qq --no-install-recommends just 2>/dev/null; then
  echo "   ✅ just installed from apt"
else
  echo "   just not in apt — downloading binary..."
  ARCH=$(uname -m)
  case "$ARCH" in
    x86_64)  ARCH="x86_64" ;;
    aarch64) ARCH="arm64"  ;;
  esac
  TARBALL="just-${JUST_VERSION}-${ARCH}-unknown-linux-musl.tar.gz"
  TMPDIR=$(mktemp -d)
  curl -fsSL "https://github.com/casey/just/releases/download/${JUST_VERSION}/${TARBALL}" \
    -o "${TMPDIR}/${TARBALL}"
  curl -fsSL "https://github.com/casey/just/releases/download/${JUST_VERSION}/SHA256SUMS" \
    -o "${TMPDIR}/SHA256SUMS"
  (cd "${TMPDIR}" && sha256sum -c --ignore-missing --status "SHA256SUMS" 2>/dev/null) || {
    echo "   ⚠️ SHA-256 mismatch for just binary — aborting download" >&2
    rm -rf "${TMPDIR}"
    exit 1
  }
  sudo tar -xzf "${TMPDIR}/${TARBALL}" -C /usr/local/bin just
  rm -rf "${TMPDIR}"
  echo "   ✅ just ${JUST_VERSION} downloaded and verified"
fi

# Install postgresql-client separately so failures are visible
sudo apt-get install -y -qq --no-install-recommends postgresql-client || {
  echo "   ⚠️ postgresql-client not available (non-fatal)" >&2
}
sudo apt-get clean

echo "⟳ Enabling corepack..."
corepack enable
corepack prepare pnpm@11.11.0 --activate

echo "⟳ Setting up environment..."
if [ ! -f .env ]; then
  cp .env.example .env
  echo "   ✅ Created .env from .env.example"
fi

echo "⟳ Installing project dependencies..."
just install

echo "✅ Dev container setup complete"
just -l 2>/dev/null | head -5
