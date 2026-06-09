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
chat_controller_test = ROOT / 'backend' / 'src' / 'test' / 'java' / 'com' / 'secondhand' / 'platform' / 'modules' / 'chat' / 'ChatControllerTest.java'
community_related_product_migration = ROOT / 'db' / 'migrations' / '0007_community_related_product.sql'
user_identity_migration = ROOT / 'db' / 'migrations' / '0002_user_identity.sql'
community_report_schema_migration = ROOT / 'db' / 'migrations' / '0009_community_report_schema_compatibility.sql'
video_identity_schema_migration = ROOT / 'db' / 'migrations' / '0010_video_identity_schema_compatibility.sql'
core_domains_migration = ROOT / 'db' / 'migrations' / '0001_core_domains.sql'
product_application_service = BACKEND_SRC / 'com' / 'secondhand' / 'platform' / 'modules' / 'product' / 'application' / 'ProductApplicationService.java'
community_application_service = BACKEND_SRC / 'com' / 'secondhand' / 'platform' / 'modules' / 'community' / 'application' / 'CommunityApplicationService.java'
media_upload_ticket_service = BACKEND_SRC / 'com' / 'secondhand' / 'platform' / 'modules' / 'media' / 'application' / 'MediaUploadTicketService.java'
video_identity_media_inspector = BACKEND_SRC / 'com' / 'secondhand' / 'platform' / 'modules' / 'media' / 'application' / 'VideoIdentityMediaInspector.java'
video_identity_media_controller = BACKEND_SRC / 'com' / 'secondhand' / 'platform' / 'modules' / 'user' / 'VideoIdentityMediaController.java'
admin_controller = BACKEND_SRC / 'com' / 'secondhand' / 'platform' / 'modules' / 'admin' / 'AdminController.java'
admin_session_controller = BACKEND_SRC / 'com' / 'secondhand' / 'platform' / 'modules' / 'admin' / 'AdminSessionController.java'
admin_auth_store = ROOT / 'admin' / 'src' / 'store' / 'modules' / 'auth.ts'
admin_api_module = ROOT / 'admin' / 'src' / 'api' / 'modules' / 'admin.ts'
admin_operators_page = ROOT / 'admin' / 'src' / 'pages' / 'operators' / 'index.vue'
data_sql = ROOT / 'backend' / 'src' / 'main' / 'resources' / 'db' / 'data.sql'

def java_method_chunk(source: str, marker: str) -> str:
    start = source.find(marker)
    if start < 0:
        return ''
    next_mapping = source.find('\n    @', start + len(marker))
    if next_mapping < 0:
        return source[start:]
    return source[start:next_mapping]

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
    if 'const ENABLE_DEV_RUNTIME = ENABLE_DEV_HEADERS && isLocalDevRuntimeHost()' not in s:
        issues.append('frontend dev headers must also require localhost/private-LAN runtime')
    if "VITE_ENABLE_MOCK_DATA === 'true'" not in s:
        issues.append('frontend mock data is not controlled by VITE_ENABLE_MOCK_DATA')
    if "const ENABLE_MOCK_DATA = import.meta.env.VITE_ENABLE_MOCK_DATA === 'true' && ENABLE_DEV_RUNTIME" not in s:
        issues.append('frontend mock data must require runtime dev host, not env flags alone')
    mock_body_markers = ('function mockProducts', 'function mockProfiles', 'function mockConversations')
    for marker in mock_body_markers:
        if marker in s:
            issues.append(f'frontend api/http.ts contains mock data body: {marker}')

if payment_ts.exists():
    s = read(payment_ts)
    if 'isDevRuntimeEnabled()' not in s:
        issues.append('frontend simulateRechargeSuccess is not gated by runtime dev flag')
else:
    issues.append('frontend payment module missing')

if payment_java.exists():
    s = read(payment_java)
    if 'requireDevelopmentProfile();' not in s:
        issues.append('backend simulate recharge does not call development profile guard')
    if 'getActiveProfiles' not in s or '"dev"' not in s or '"local"' not in s:
        issues.append('backend simulate recharge guard does not require explicit dev/local profile')
else:
    issues.append('backend PaymentController missing')

if smoke_py.exists():
    s = read(smoke_py)
    admin_legacy_auth = [
        legacy for legacy in ('X-Admin-Mode', 'X-Dev-Mode')
        if re.search(rf"ADMIN_HEADERS[^\n]*{legacy}|headers=ADMIN_HEADERS[^\n]*{legacy}|X-Admin-Mode", s)
    ]
    if admin_legacy_auth:
        issues.append('smoke-api still sends legacy dev/admin authorization headers')
    for marker in (
        "'CHAT_VOICE'",
        "'CHAT_IMAGE'",
        'revoke voice chat',
        'peer clear chat',
        '/api/chat/messages',
        '/api/community/posts',
        "'COMMUNITY_IMAGE'",
        'SMOKE_PASS'
    ):
        if marker not in s:
            issues.append(f'smoke-api missing user smoke marker: {marker}')
else:
    issues.append('scripts/smoke-api.py missing')

if chat_controller_test.exists():
    s = read(chat_controller_test)
    for marker in (
        'void imageEndpointShouldRequireUploadedChatImageTicketAndSyncPayload()',
        "'CHAT_IMAGE'",
        'lastMessageSummary", is("[图片]")',
        'flow-image-unissued'
    ):
        if marker not in s:
            issues.append(f'ChatControllerTest missing chat image controller marker: {marker}')
else:
    issues.append('ChatControllerTest missing')

if community_related_product_migration.exists():
    s = read(community_related_product_migration)
    for marker in (
        'CREATE TABLE IF NOT EXISTS community_post',
        'CREATE TABLE IF NOT EXISTS community_comment',
        'CREATE TABLE IF NOT EXISTS community_like',
        'ALTER TABLE community_post ADD COLUMN related_product_id',
        'idx_community_post_related_product',
        'INFORMATION_SCHEMA.COLUMNS',
        'INFORMATION_SCHEMA.STATISTICS'
    ):
        if marker not in s:
            issues.append(f'community related-product migration missing marker: {marker}')
else:
    issues.append('db/migrations/0007_community_related_product.sql missing')

if user_identity_migration.exists():
    s = read(user_identity_migration)
    if 'followee_id' in s:
        issues.append('user identity migration must use followed_id, not legacy followee_id')
    for marker in (
        'followed_id BIGINT NOT NULL',
        'UNIQUE KEY uk_user_follow_pair (follower_id, followed_id)',
        'idx_user_follow_followed'
    ):
        if marker not in s:
            issues.append(f'user identity migration missing user_follow marker: {marker}')
else:
    issues.append('db/migrations/0002_user_identity.sql missing')

if community_report_schema_migration.exists():
    s = read(community_report_schema_migration)
    for marker in (
        'CHANGE COLUMN followee_id followed_id',
        'idx_user_follow_followed',
        'MODIFY COLUMN target_id VARCHAR(128) NOT NULL',
        'INFORMATION_SCHEMA.COLUMNS',
        'INFORMATION_SCHEMA.STATISTICS'
    ):
        if marker not in s:
            issues.append(f'community/report schema compatibility migration missing marker: {marker}')
else:
    issues.append('db/migrations/0009_community_report_schema_compatibility.sql missing')

if core_domains_migration.exists():
    s = read(core_domains_migration)
    report_match = re.search(r'CREATE TABLE IF NOT EXISTS report_record \(([\s\S]*?)\n\) ENGINE=', s)
    if not report_match or 'target_id VARCHAR(128) NOT NULL' not in report_match.group(1):
        issues.append('core domains migration report_record.target_id must be VARCHAR(128)')
    if not report_match or 'id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY' not in report_match.group(1):
        issues.append('core domains migration report_record.id must be AUTO_INCREMENT because reports insert without id')
else:
    issues.append('db/migrations/0001_core_domains.sql missing')

if video_identity_schema_migration.exists():
    s = read(video_identity_schema_migration)
    for marker in (
        'CREATE TABLE IF NOT EXISTS media_upload_ticket',
        'CREATE TABLE IF NOT EXISTS audit_record',
        'CREATE TABLE IF NOT EXISTS admin_audit_log',
        'video_identity_status',
        'video_verified',
        'VIDEO_IDENTITY',
        'INFORMATION_SCHEMA.COLUMNS'
    ):
        if marker not in s:
            issues.append(f'video identity schema compatibility migration missing marker: {marker}')
else:
    issues.append('db/migrations/0010_video_identity_schema_compatibility.sql missing')

if product_application_service.exists():
    s = read(product_application_service)
    if re.search(r'\bup\.video_verified\s+as\s+seller_video_verified\b', s, re.IGNORECASE):
        issues.append('product sellerVideoVerified must be strict approved-video derived, not raw user_profile.video_verified')
    for marker in (
        'CERTIFIED_SELLER_VERIFIED_SELECT',
        "up.video_identity_status = 'APPROVED'",
        "t.scene = 'VIDEO_IDENTITY'",
        "ar.audit_type = 'VIDEO_IDENTITY'",
        'AS seller_video_verified'
    ):
        if marker not in s:
            issues.append(f'product strict seller video projection missing marker: {marker}')
else:
    issues.append('ProductApplicationService missing')

if community_application_service.exists():
    s = read(community_application_service)
    if 'DATEADD(' in s:
        issues.append('CommunityApplicationService must not use H2-only DATEADD in production SQL')
    for marker in (
        'duplicateSubmitWindowStart()',
        'Timestamp.valueOf(LocalDateTime.now().minusSeconds(DUPLICATE_SUBMIT_WINDOW_SECONDS))',
        'AND created_at >= ?',
        'AND c.created_at >= ?',
    ):
        if marker not in s:
            issues.append(f'CommunityApplicationService duplicate-submit SQL compatibility marker missing: {marker}')
else:
    issues.append('CommunityApplicationService missing')

if media_upload_ticket_service.exists():
    s = read(media_upload_ticket_service)
    for marker in (
        'requireValidImageMedia(target, uploadedContentType)',
        'ImageIO.getImageReaders(stream)',
        'requireValidWebpMedia(mediaPath)',
        'imageFormatMatches(contentType, formatName)',
    ):
        if marker not in s:
            issues.append(f'MediaUploadTicketService missing image content validation marker: {marker}')
else:
    issues.append('MediaUploadTicketService missing')

if video_identity_media_inspector.exists():
    s = read(video_identity_media_inspector)
    for marker in (
        'hasVideoTrack',
        'hasMediaData',
        '"hdlr"',
        '"mdat"',
        'isVideoHandler',
    ):
        if marker not in s:
            issues.append(f'VideoIdentityMediaInspector missing strict video validation marker: {marker}')
else:
    issues.append('VideoIdentityMediaInspector missing')

if video_identity_media_controller.exists():
    s = read(video_identity_media_controller)
    for marker in (
        'ALLOWED_VIDEO_CONTENT_TYPES',
        'video/mp4',
        'video/quicktime',
        'video/x-m4v',
        '!ALLOWED_VIDEO_CONTENT_TYPES.contains(uploadedContentType)',
        'video identity contentType unsupported',
        'isPublicApprovedVideo(ownerUserId, storageUrl)',
        'isOwnPendingVideo(ownerUserId, viewerId, storageUrl)',
        'VideoIdentityMediaInspector.requireValidVideoIdentityMedia(mediaPath)',
        'X-Content-Type-Options',
        'noStore()',
    ):
        if marker not in s:
            issues.append(f'VideoIdentityMediaController missing secure read marker: {marker}')
    if 'video/webm' in s:
        issues.append('VideoIdentityMediaController must not expose unsupported video/webm MIME for video identity media')
else:
    issues.append('VideoIdentityMediaController missing')

if admin_controller.exists():
    s = read(admin_controller)
    for marker, name in (
        ('@GetMapping("/chat/conversations")', 'admin chat conversation list'),
        ('@GetMapping("/chat/conversations/{conversationId}/messages")', 'admin chat message trace'),
        ('@GetMapping("/chat/media")', 'admin chat media trace'),
    ):
        chunk = java_method_chunk(s, marker)
        if not chunk:
            issues.append(f'{name} endpoint missing')
            continue
        if 'requireAdmin(request, "chat:trace")' not in chunk:
            issues.append(f'{name} endpoint must require chat:trace')
        if 'requireAdmin(request, "audit:read")' in chunk:
            issues.append(f'{name} endpoint must not fall back to audit:read')
    for marker in (
        '"chat:trace"',
        "CHAT_TRACE_VIEW",
        "CHAT_TRACE_MEDIA_VIEW",
    ):
        if marker not in s:
            issues.append(f'AdminController missing chat trace marker: {marker}')
else:
    issues.append('AdminController missing')

if admin_session_controller.exists():
    s = read(admin_session_controller)
    if '"chat:trace"' not in s:
        issues.append('AdminSessionController allowed permissions missing chat:trace')
else:
    issues.append('AdminSessionController missing')

if data_sql.exists():
    s = read(data_sql)
    if "UNION ALL SELECT 'chat:trace'" not in s:
        issues.append('admin seed permissions missing chat:trace')
else:
    issues.append('backend db/data.sql missing')

if admin_auth_store.exists():
    s = read(admin_auth_store)
    for marker in (
        "'chat:trace'",
        "{ pattern: /^\\/chat-trace(?:\\/|$)/, permission: 'chat:trace' }",
        "{ path: '/chat-trace', label: '私聊追溯', permission: 'chat:trace' }",
    ):
        if marker not in s:
            issues.append(f'admin auth store missing chat trace marker: {marker}')
    if re.search(r"/chat-trace[^\\n]+permission:\s*'audit:read'", s):
        issues.append('admin chat-trace route/menu must not use audit:read')
else:
    issues.append('admin auth store missing')

if admin_api_module.exists():
    s = read(admin_api_module)
    if "'chat:trace'" not in s:
        issues.append('admin api permission type missing chat:trace')
else:
    issues.append('admin api module missing')

if admin_operators_page.exists():
    s = read(admin_operators_page)
    if "{ code: 'chat:trace', label: '私聊追溯' }" not in s:
        issues.append('admin operator permissions UI missing chat:trace option')
else:
    issues.append('admin operators page missing')

admin_backoffice_check = ROOT / 'admin' / 'scripts' / 'check-real-backoffice.cjs'
if admin_backoffice_check.exists():
    s = read(admin_backoffice_check)
    if 'sessionAllowsPermission' not in s or 'X-Admin-Mode|X-Dev-Mode' not in s:
        issues.append('admin real-backoffice check must cover RBAC session and reject legacy dev admin auth')
else:
    issues.append('admin/scripts/check-real-backoffice.cjs missing')

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
    class_match = re.search(r'public\s+class\s+([A-Za-z_][A-Za-z0-9_]*)', s)
    if class_match and ('@RestController' in s or '@Controller' in s):
        class_name = class_match.group(1)
        constructors = list(re.finditer(rf'(?m)^\s*public\s+{re.escape(class_name)}\s*\(', s))
        if len(constructors) > 1:
            has_injected_constructor = False
            for constructor in constructors:
                prefix = s[max(0, constructor.start() - 240):constructor.start()]
                lines = [line.strip() for line in prefix.splitlines() if line.strip()]
                annotation_block = []
                for line in reversed(lines):
                    if line.startswith('@'):
                        annotation_block.append(line)
                        continue
                    break
                if any(line.startswith('@Autowired') for line in annotation_block):
                    has_injected_constructor = True
                    break
            if not has_injected_constructor:
                issues.append(
                    'controller has multiple constructors without explicit @Autowired '
                    f'{path.relative_to(ROOT)}'
                )

print('production_readiness_static_check')
print(f'java_files={len(java_files)}')
print(f'frontend_files={len(list(FRONTEND_SRC.rglob("*.vue"))) + len(list(FRONTEND_SRC.rglob("*.ts")))}')
print(f'issues={len(issues)}')
for item in issues:
    print(f'ISSUE: {item}')
for item in warnings:
    print(f'WARN: {item}')

sys.exit(1 if issues else 0)
