#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export JAVA_HOME="$ROOT/.tools/jdk-21"
export PATH="$ROOT/.tools/maven/bin:$ROOT/.tools/jdk-21/bin:$PATH"
export NO_PROXY="127.0.0.1,localhost,::1"
export no_proxy="$NO_PROXY"
