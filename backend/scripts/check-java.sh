#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
if command -v javac >/dev/null 2>&1; then
  tmpfile="$(mktemp)"
  find src/main/java -name '*.java' > "$tmpfile"
  javac -cp "src/main/java" @"$tmpfile"
  rm -f "$tmpfile"
  find src/main/java -name '*.class' -delete
  echo "java syntax ok"
else
  echo "javac not installed; skipped java syntax check"
fi
