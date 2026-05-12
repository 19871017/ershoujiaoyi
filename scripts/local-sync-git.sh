#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

msg="${1:-}"
if [[ -z "$msg" ]]; then
  msg="chore: sync local xiaoyuanquan updates"
fi

echo "== 小原圈本地同步 =="
echo "项目目录: $ROOT"

git fetch origin
current_branch="$(git branch --show-current)"
if [[ "$current_branch" != "main" ]]; then
  echo "当前分支是 $current_branch，先切回 main"
  git checkout main
fi

echo "== 拉取 GitHub 最新 main =="
git pull --ff-only origin main

echo "== 当前改动 =="
git status --short

if git diff --quiet && git diff --cached --quiet; then
  echo "没有需要提交的本地改动。"
else
  echo "== 提交并推送到 GitHub =="
  git add -A
  git commit -m "$msg"
  git push origin main
fi

echo "== 完成 =="
git status --short --branch
