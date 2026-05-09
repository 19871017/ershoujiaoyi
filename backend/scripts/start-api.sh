#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "$ROOT/scripts/env.sh"
cd "$ROOT/backend"
exec mvn -q spring-boot:run   -Dspring-boot.run.main-class=com.secondhand.platform.apps.api.ApiApplication   -Dspring-boot.run.arguments="--server.port=${PORT:-18080}"
