#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "$ROOT/scripts/env.sh"
cd "$ROOT/backend"
mvn package spring-boot:repackage -DskipTests
manifest="$(unzip -p target/backend-0.1.0-SNAPSHOT.jar META-INF/MANIFEST.MF)"
grep -q "Main-Class: org.springframework.boot.loader.launch.JarLauncher" <<<"$manifest"
grep -q "Start-Class: com.secondhand.platform.apps.api.ApiApplication" <<<"$manifest"
echo "backend executable jar manifest ok"
