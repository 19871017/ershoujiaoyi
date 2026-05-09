#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT/frontend"
if [ ! -f dist/build/h5/index.html ]; then
  npm run build:h5
fi
cd dist/build
exec python3 -m http.server "${PORT:-4173}" --bind 127.0.0.1
