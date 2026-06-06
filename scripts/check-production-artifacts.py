#!/usr/bin/env python3
from __future__ import annotations

import os
import re
import sys
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
H5_DIST = Path(os.environ.get('XYQ_H5_DIST', ROOT / 'frontend' / 'dist' / 'build' / 'h5'))
ADMIN_DIST = Path(os.environ.get('XYQ_ADMIN_DIST', ROOT / 'admin' / 'dist'))

TEXT_SUFFIXES = {
    '.css',
    '.html',
    '.js',
    '.json',
    '.map',
    '.mjs',
    '.svg',
    '.txt',
    '.webmanifest',
    '.xml',
}


@dataclass(frozen=True)
class Rule:
    name: str
    pattern: re.Pattern[str]


COMMON_FORBIDDEN = [
    Rule('duplicated api prefix', re.compile(r'/api/api')),
    Rule('local backend endpoint', re.compile(r'localhost:18080|127\.0\.0\.1:18080|old\.tiklxd09\.club:18080')),
    Rule('vite env flag leaked', re.compile(r'VITE_ENABLE')),
    Rule('gift mock marker leaked', re.compile(r'GIFT-MOCK')),
    Rule('mock data function leaked', re.compile(r'mockRecentGiftFeed|mockResponse|mockProducts|mockProfiles|mockConversations')),
    Rule('mock asset path leaked', re.compile(r'/assets/mock')),
    Rule('mock product number leaked', re.compile(r'MOCK-(DRESS|SHOES|SOCKS|BAG|CAMERA|PERFUME)-')),
    Rule('demo announcement leaked', re.compile(r'演示公告')),
]

H5_FORBIDDEN = [
    *COMMON_FORBIDDEN,
    Rule('frontend dev header leaked', re.compile(r'X-Dev-Mode|X-User-Id')),
]

ADMIN_FORBIDDEN = [
    *COMMON_FORBIDDEN,
    Rule('admin dev header leaked', re.compile(r'X-Dev-Mode|X-Admin-Mode')),
]


def read_text_file(path: Path) -> str | None:
    if path.suffix not in TEXT_SUFFIXES:
        return None
    try:
        data = path.read_bytes()
    except OSError as exc:
        raise RuntimeError(f'cannot read {path}: {exc}') from exc
    if b'\x00' in data:
        return None
    try:
        return data.decode('utf-8')
    except UnicodeDecodeError:
        return data.decode('utf-8', errors='ignore')


def scan_dist(label: str, root: Path, rules: list[Rule]) -> list[str]:
    issues: list[str] = []
    if not root.exists():
        return [f'{label} dist missing: {root}']
    if not root.is_dir():
        return [f'{label} dist is not a directory: {root}']

    for path in sorted(root.rglob('*')):
        if not path.is_file():
            continue
        text = read_text_file(path)
        if text is None:
            continue
        try:
            rel = path.relative_to(ROOT)
        except ValueError:
            rel = path
        for rule in rules:
            match = rule.pattern.search(text)
            if match:
                issues.append(f'{label}: {rule.name} in {rel} -> {match.group(0)}')
    return issues


def assert_index_paths(label: str, index: Path, required_asset_prefix: str) -> list[str]:
    if not index.exists():
        return [f'{label} index missing: {index}']
    text = index.read_text(encoding='utf-8', errors='ignore')
    if required_asset_prefix not in text:
        return [f'{label} index does not reference {required_asset_prefix} assets']
    if 'localhost' in text or ':18080' in text:
        return [f'{label} index references a local backend endpoint']
    return []


def main() -> int:
    issues: list[str] = []
    issues.extend(assert_index_paths('h5', H5_DIST / 'index.html', '/assets/'))
    issues.extend(assert_index_paths('admin', ADMIN_DIST / 'index.html', '/admin/assets/'))
    issues.extend(scan_dist('h5', H5_DIST, H5_FORBIDDEN))
    issues.extend(scan_dist('admin', ADMIN_DIST, ADMIN_FORBIDDEN))

    print('production_artifacts_check')
    print(f'h5_dist={H5_DIST}')
    print(f'admin_dist={ADMIN_DIST}')
    print(f'issues={len(issues)}')
    for issue in issues:
        print(f'ISSUE: {issue}')
    return 1 if issues else 0


if __name__ == '__main__':
    sys.exit(main())
