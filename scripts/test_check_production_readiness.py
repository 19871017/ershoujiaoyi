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
        self.assertNotIn('smoke-api must obtain an admin session through persisted RBAC login', result.stdout)
        self.assertNotIn('smoke-api must send server-issued X-Admin-Session on admin smoke calls', result.stdout)

    def test_controller_with_multiple_constructors_requires_explicit_autowired(self):
        target = ROOT / 'backend' / 'src' / 'main' / 'java' / 'com' / 'secondhand' / 'platform' / 'modules' / 'admin' / 'ReadinessProbeController.java'
        original = target.read_text(encoding='utf-8') if target.exists() else None
        target.write_text(
            '''
package com.secondhand.platform.modules.admin;

import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReadinessProbeController {
    public ReadinessProbeController(String value) {
    }

    public ReadinessProbeController() {
    }
}
''',
            encoding='utf-8',
        )
        try:
            result = subprocess.run(
                [sys.executable, str(ROOT / 'scripts' / 'check-production-readiness.py')],
                cwd=ROOT,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
        finally:
            if original is None:
                target.unlink(missing_ok=True)
            else:
                target.write_text(original, encoding='utf-8')

        self.assertNotEqual(result.returncode, 0)
        self.assertIn(
            'controller has multiple constructors without explicit @Autowired',
            result.stdout,
        )

    def test_admin_chat_trace_endpoints_must_require_dedicated_permission(self):
        target = ROOT / 'backend' / 'src' / 'main' / 'java' / 'com' / 'secondhand' / 'platform' / 'modules' / 'admin' / 'AdminController.java'
        original = target.read_text(encoding='utf-8')
        modified = original.replace(
            '@GetMapping("/chat/conversations")\n'
            '    public Result<List<AdminChatConversationTraceResponse>> chatConversationList(@RequestParam(required = false) Long conversationId,\n'
            '                                                                                @RequestParam(required = false) Long userId,\n'
            '                                                                                @RequestParam(required = false) String keyword,\n'
            '                                                                                @RequestParam(defaultValue = "20") Integer limit,\n'
            '                                                                                HttpServletRequest request) {\n'
            '        adminAccessGuard.requireAdmin(request, "chat:trace");',
            '@GetMapping("/chat/conversations")\n'
            '    public Result<List<AdminChatConversationTraceResponse>> chatConversationList(@RequestParam(required = false) Long conversationId,\n'
            '                                                                                @RequestParam(required = false) Long userId,\n'
            '                                                                                @RequestParam(required = false) String keyword,\n'
            '                                                                                @RequestParam(defaultValue = "20") Integer limit,\n'
            '                                                                                HttpServletRequest request) {\n'
            '        adminAccessGuard.requireAdmin(request, "audit:read");',
            1,
        )
        self.assertNotEqual(original, modified)
        target.write_text(modified, encoding='utf-8')
        try:
            result = subprocess.run(
                [sys.executable, str(ROOT / 'scripts' / 'check-production-readiness.py')],
                cwd=ROOT,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
        finally:
            target.write_text(original, encoding='utf-8')

        self.assertNotEqual(result.returncode, 0)
        self.assertIn(
            'admin chat conversation list endpoint must require chat:trace',
            result.stdout,
        )
        self.assertIn(
            'admin chat conversation list endpoint must not fall back to audit:read',
            result.stdout,
        )

    def test_report_record_migration_must_auto_increment_id(self):
        target = ROOT / 'db' / 'migrations' / '0001_core_domains.sql'
        original = target.read_text(encoding='utf-8')
        modified = original.replace(
            'id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,\n  report_no VARCHAR(128) NOT NULL,',
            'id BIGINT PRIMARY KEY,\n  report_no VARCHAR(128) NOT NULL,',
            1,
        )
        self.assertNotEqual(original, modified)
        target.write_text(modified, encoding='utf-8')
        try:
            result = subprocess.run(
                [sys.executable, str(ROOT / 'scripts' / 'check-production-readiness.py')],
                cwd=ROOT,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
        finally:
            target.write_text(original, encoding='utf-8')

        self.assertNotEqual(result.returncode, 0)
        self.assertIn(
            'core domains migration report_record.id must be AUTO_INCREMENT',
            result.stdout,
        )

    def test_video_identity_migration_must_create_media_ticket_table(self):
        target = ROOT / 'db' / 'migrations' / '0010_video_identity_schema_compatibility.sql'
        original = target.read_text(encoding='utf-8')
        modified = original.replace(
            'CREATE TABLE IF NOT EXISTS media_upload_ticket',
            'CREATE TABLE IF NOT EXISTS missing_media_upload_ticket',
            1,
        )
        self.assertNotEqual(original, modified)
        target.write_text(modified, encoding='utf-8')
        try:
            result = subprocess.run(
                [sys.executable, str(ROOT / 'scripts' / 'check-production-readiness.py')],
                cwd=ROOT,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
        finally:
            target.write_text(original, encoding='utf-8')

        self.assertNotEqual(result.returncode, 0)
        self.assertIn(
            'video identity schema compatibility migration missing marker: CREATE TABLE IF NOT EXISTS media_upload_ticket',
            result.stdout,
        )

    def test_video_identity_controller_must_not_expose_unsupported_mime(self):
        target = ROOT / 'backend' / 'src' / 'main' / 'java' / 'com' / 'secondhand' / 'platform' / 'modules' / 'user' / 'VideoIdentityMediaController.java'
        original = target.read_text(encoding='utf-8')
        modified = original.replace(
            'private static final List<String> ALLOWED_VIDEO_CONTENT_TYPES = List.of("video/mp4", "video/quicktime", "video/x-m4v");',
            'private static final List<String> ALLOWED_VIDEO_CONTENT_TYPES = List.of("video/mp4", "video/quicktime", "video/x-m4v", "video/webm");',
            1,
        )
        self.assertNotEqual(original, modified)
        target.write_text(modified, encoding='utf-8')
        try:
            result = subprocess.run(
                [sys.executable, str(ROOT / 'scripts' / 'check-production-readiness.py')],
                cwd=ROOT,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
        finally:
            target.write_text(original, encoding='utf-8')

        self.assertNotEqual(result.returncode, 0)
        self.assertIn(
            'VideoIdentityMediaController must not expose unsupported video/webm MIME',
            result.stdout,
        )


if __name__ == '__main__':
    unittest.main()
