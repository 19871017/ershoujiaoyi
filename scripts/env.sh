#!/usr/bin/env bash
set -euo pipefail
SCRIPT_PATH="${BASH_SOURCE[0]:-${(%):-%x}}"
ROOT="$(cd "$(dirname "$SCRIPT_PATH")/.." && pwd)"
TOOLS_DIR="$ROOT/.tools"

if [[ -d "$TOOLS_DIR/maven/bin" ]]; then
  export PATH="$TOOLS_DIR/maven/bin:$PATH"
fi

if [[ -d "$TOOLS_DIR/jdk-21" ]]; then
  export JAVA_HOME="$TOOLS_DIR/jdk-21"
elif [[ -n "${JAVA_HOME:-}" && ! -x "$JAVA_HOME/bin/java" ]]; then
  unset JAVA_HOME
fi

if [[ -z "${JAVA_HOME:-}" ]] && [[ -x /usr/libexec/java_home ]]; then
  DETECTED_JAVA_HOME="$(/usr/libexec/java_home 2>/dev/null || true)"
  if [[ -n "$DETECTED_JAVA_HOME" && -x "$DETECTED_JAVA_HOME/bin/java" ]]; then
    export JAVA_HOME="$DETECTED_JAVA_HOME"
  fi
fi

if [[ -n "${JAVA_HOME:-}" ]]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi

export NO_PROXY="127.0.0.1,localhost,::1"
export no_proxy="$NO_PROXY"
