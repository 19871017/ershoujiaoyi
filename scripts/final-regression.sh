#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT/scripts/env.sh"

run() {
  echo
  echo "==> $*"
  "$@"
}

assert_backend_manifest() {
  local jar="$ROOT/backend/target/backend-0.1.0-SNAPSHOT.jar"
  local manifest
  manifest="$(unzip -p "$jar" META-INF/MANIFEST.MF)"
  grep -q "Main-Class: org.springframework.boot.loader.launch.JarLauncher" <<<"$manifest"
  grep -q "Start-Class: com.secondhand.platform.apps.api.ApiApplication" <<<"$manifest"
}

cd "$ROOT/backend"
run mvn test
run mvn package spring-boot:repackage -DskipTests
run assert_backend_manifest

cd "$ROOT/frontend"
run npm run typecheck
run npm run check:home-real-products
run npm run check:category-real-products
run npm run check:community-feed-real-data
run npm run check:community-compose-real-post
run npm run check:community-comment
run npm run check:community-detail-follow
run npm run check:chat-no-demo
run npm run check:public-profile-real-data
run npm run check:seller-video-identity-display
run npm run check:upload-evidence
run npm run check:gift-module
run npm run check:global-ticker-priority
run npm run check:global-bottom-nav-chat
run npm run check:lan-api-base
run npm run build:h5:prod
MOBILE_UI_PORT="${MOBILE_UI_PORT:-4187}"
PORT="$MOBILE_UI_PORT" bash "$ROOT/frontend/scripts/preview-h5.sh" >/tmp/xiaoyuanquan-mobile-ui-preview.log 2>&1 &
MOBILE_UI_SERVER_PID=$!
trap 'kill "$MOBILE_UI_SERVER_PID" 2>/dev/null || true' EXIT
sleep 2
run env MOBILE_UI_BASE_URL="http://127.0.0.1:$MOBILE_UI_PORT" npm run check:mobile-ui-overlap
kill "$MOBILE_UI_SERVER_PID" 2>/dev/null || true
trap - EXIT

cd "$ROOT/admin"
run npm run check:real-backoffice
run npm test
run npm run build

cd "$ROOT"
run python3 -m unittest "$ROOT/scripts/test_check_production_artifacts.py" "$ROOT/scripts/test_check_production_readiness.py"
run python3 "$ROOT/scripts/check-production-artifacts.py"
run python3 "$ROOT/scripts/check-production-readiness.py"

if [[ "${RUN_SMOKE_API:-false}" == "true" ]]; then
  run python3 "$ROOT/scripts/smoke-api.py"
fi
