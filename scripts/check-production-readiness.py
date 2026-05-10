#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
FRONTEND_SRC = ROOT / 'frontend' / 'src'
BACKEND_SRC = ROOT / 'backend' / 'src' / 'main' / 'java'

issues = []
warnings = []

def read(path: Path) -> str:
    return path.read_text(encoding='utf-8', errors='ignore')

http_ts = FRONTEND_SRC / 'api' / 'http.ts'
payment_ts = FRONTEND_SRC / 'api' / 'modules' / 'payment.ts'
payment_java = BACKEND_SRC / 'com' / 'secondhand' / 'platform' / 'modules' / 'payment' / 'PaymentController.java'
smoke_py = ROOT / 'scripts' / 'smoke-api.py'

if not http_ts.exists():
    issues.append('frontend api/http.ts missing')
else:
    s = read(http_ts)
    if "const DEV_HEADERS: Record<string, string> = {" in s:
        issues.append('frontend DEV_HEADERS is hardcoded object; must be env-gated')
    if "const USE_MOCK_DATA = true" in s or "const ENABLE_MOCK_DATA = true" in s:
        issues.append('frontend mock data is hardcoded enabled')
    if "VITE_ENABLE_DEV_HEADERS === 'true'" not in s:
        issues.append('frontend dev headers are not controlled by VITE_ENABLE_DEV_HEADERS')
    if "VITE_ENABLE_MOCK_DATA === 'true'" not in s:
        issues.append('frontend mock data is not controlled by VITE_ENABLE_MOCK_DATA')

if payment_ts.exists():
    s = read(payment_ts)
    if 'isDevRuntimeEnabled()' not in s:
        issues.append('frontend simulateRechargeSuccess is not gated by runtime dev flag')
else:
    issues.append('frontend payment module missing')

if payment_java.exists():
    s = read(payment_java)
    if 'requireNonProductionProfile();' not in s:
        issues.append('backend simulate recharge does not call non-production profile guard')
    if 'getActiveProfiles' not in s or 'production' not in s or 'prod' not in s:
        issues.append('backend simulate recharge guard does not inspect active profiles')
else:
    issues.append('backend PaymentController missing')

if smoke_py.exists():
    s = read(smoke_py)
    if 'X-Admin-Mode' in s or 'X-Dev-Mode' in s:
        issues.append('smoke-api still sends legacy dev/admin authorization headers')
    if '/api/admin/session/login' not in s:
        issues.append('smoke-api must obtain an admin session through persisted RBAC login')
    if 'X-Admin-Session' not in s:
        issues.append('smoke-api must send server-issued X-Admin-Session on admin smoke calls')
else:
    issues.append('scripts/smoke-api.py missing')

for path in FRONTEND_SRC.rglob('*'):
    if not path.is_file() or path.suffix not in {'.ts', '.vue'}:
        continue
    s = read(path)
    if 'TODO' in s or 'FIXME' in s or 'todo-' in s or '论坛' in s:
        issues.append(f'frontend forbidden marker in {path.relative_to(ROOT)}')

java_files = list(BACKEND_SRC.rglob('*.java'))
for path in java_files:
    s = read(path)
    bal = 0
    for ch in s:
        if ch == '{':
            bal += 1
        elif ch == '}':
            bal -= 1
        if bal < 0:
            issues.append(f'java brace underflow {path.relative_to(ROOT)}')
            break
    if bal != 0:
        issues.append(f'java brace imbalance {path.relative_to(ROOT)}: {bal}')
    if 'package ' not in s:
        issues.append(f'java missing package {path.relative_to(ROOT)}')

print('production_readiness_static_check')
print(f'java_files={len(java_files)}')
print(f'frontend_files={len(list(FRONTEND_SRC.rglob("*.vue"))) + len(list(FRONTEND_SRC.rglob("*.ts")))}')
print(f'issues={len(issues)}')
for item in issues:
    print(f'ISSUE: {item}')
for item in warnings:
    print(f'WARN: {item}')

sys.exit(1 if issues else 0)
