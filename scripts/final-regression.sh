#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT/scripts/env.sh"
cd "$ROOT/backend"
mvn test
mvn package
cd "$ROOT/frontend"
npm run typecheck
npm run build:h5
python3 "$ROOT/scripts/smoke-api.py"
