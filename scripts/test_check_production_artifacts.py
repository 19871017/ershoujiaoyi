import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / 'scripts' / 'check-production-artifacts.py'


def run_check(h5_dist: Path, admin_dist: Path):
    env = {
        **os.environ,
        'XYQ_H5_DIST': str(h5_dist),
        'XYQ_ADMIN_DIST': str(admin_dist),
    }
    return subprocess.run(
        [sys.executable, str(SCRIPT)],
        cwd=ROOT,
        env=env,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )


def write_minimal_dist(root: Path, *, asset_prefix: str, body: str = ''):
    assets = root / 'assets'
    assets.mkdir(parents=True)
    (root / 'index.html').write_text(
        f'<script type="module" src="{asset_prefix}index.js"></script>',
        encoding='utf-8',
    )
    (assets / 'index.js').write_text(body or 'console.log("ok")', encoding='utf-8')


class ProductionArtifactsCheckTest(unittest.TestCase):
    def test_accepts_clean_dist(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            h5 = root / 'h5'
            admin = root / 'admin'
            write_minimal_dist(h5, asset_prefix='/assets/')
            write_minimal_dist(admin, asset_prefix='/admin/assets/')

            result = run_check(h5, admin)

            self.assertEqual(result.returncode, 0, result.stdout)
            self.assertIn('issues=0', result.stdout)

    def test_rejects_frontend_dev_headers(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            h5 = root / 'h5'
            admin = root / 'admin'
            write_minimal_dist(h5, asset_prefix='/assets/', body='fetch("/api/products",{headers:{"X-Dev-Mode":"enabled","X-User-Id":"1"}})')
            write_minimal_dist(admin, asset_prefix='/admin/assets/')

            result = run_check(h5, admin)

            self.assertEqual(result.returncode, 1)
            self.assertIn('frontend dev header leaked', result.stdout)

    def test_allows_admin_session_headers(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            h5 = root / 'h5'
            admin = root / 'admin'
            write_minimal_dist(h5, asset_prefix='/assets/')
            write_minimal_dist(admin, asset_prefix='/admin/assets/', body='fetch("/api/admin/session/me",{headers:{"X-User-Id":"7","X-Admin-Session":"adm_x"}})')

            result = run_check(h5, admin)

            self.assertEqual(result.returncode, 0, result.stdout)

    def test_rejects_bad_admin_endpoint(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            h5 = root / 'h5'
            admin = root / 'admin'
            write_minimal_dist(h5, asset_prefix='/assets/')
            write_minimal_dist(admin, asset_prefix='/admin/assets/', body='fetch("http://old.tiklxd09.club:18080/api/admin/session/login")')

            result = run_check(h5, admin)

            self.assertEqual(result.returncode, 1)
            self.assertIn('local backend endpoint', result.stdout)


if __name__ == '__main__':
    unittest.main()
