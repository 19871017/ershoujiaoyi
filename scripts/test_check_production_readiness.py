#!/usr/bin/env python3
import subprocess
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


class ProductionReadinessCheckTest(unittest.TestCase):
    def test_dev_payment_simulation_header_is_not_classified_as_legacy_admin_auth(self):
        result = subprocess.run(
            [sys.executable, str(ROOT / 'scripts' / 'check-production-readiness.py')],
            cwd=ROOT,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            check=False,
        )

        self.assertNotIn('smoke-api still sends legacy dev/admin authorization headers', result.stdout)


if __name__ == '__main__':
    unittest.main()
