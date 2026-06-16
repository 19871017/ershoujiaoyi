#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
failures: list[str] = []

frontend_build = (ROOT / "frontend/scripts/build-h5.sh").read_text()
backend_package = (ROOT / "backend/scripts/package.sh").read_text()

if "npm run build:h5:prod" not in frontend_build:
    failures.append("frontend/scripts/build-h5.sh must build the production H5 bundle")
if "npm run build:h5\n" in frontend_build or "npm run build:h5\r\n" in frontend_build:
    failures.append("frontend/scripts/build-h5.sh must not call build:h5 because it enables preview flags")

required_backend_snippets = [
    "mvn package spring-boot:repackage -DskipTests",
    "Main-Class: org.springframework.boot.loader.launch.JarLauncher",
    "Start-Class: com.secondhand.platform.apps.api.ApiApplication",
]
for snippet in required_backend_snippets:
    if snippet not in backend_package:
        failures.append(f"backend/scripts/package.sh missing required production guard: {snippet}")
if "\nmvn package\n" in backend_package:
    failures.append("backend/scripts/package.sh must not stop at plain mvn package")

if failures:
    print("\n".join(failures), file=sys.stderr)
    sys.exit(1)

print("production build scripts are guarded")
