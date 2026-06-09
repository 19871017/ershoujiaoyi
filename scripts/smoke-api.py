#!/usr/bin/env python3
"""API smoke checks for the current backend.

By default this script targets the local dev backend and uses the dev-only
X-User-Id header. For a deployed backend, set SMOKE_API_BASE_URL plus real test
account credentials and explicitly allow remote mutations. It exercises the
user-facing chains most likely to regress during pre-launch hardening: IM text,
IM image/voice upload, revoke, clear, community publish/list/detail/comment,
and media upload tickets.
"""

import json
import os
import sys
import time
import uuid
import base64
import urllib.error
import urllib.parse
import urllib.request

DEFAULT_BASE = 'http://127.0.0.1:18080'
BASE = os.environ.get('SMOKE_API_BASE_URL', DEFAULT_BASE).strip().rstrip('/') or DEFAULT_BASE
OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))
JSON_HEADERS = {'Content-Type': 'application/json'}
USER_1_HEADERS = {}
USER_2_HEADERS = {}
USER_1_ID = 1
USER_2_ID = 2
SMOKE_PNG_BYTES = base64.b64decode(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGNgaGAAAAEEAMHh+XGQAAAAAElFTkSuQmCC'
)


def webm_voice_bytes():
    return bytes([0x1A, 0x45, 0xDF, 0xA3]) + b'xiaoyuanquan-smoke-voice'


def call(name, method, path, body=None, expected=200, headers=None, redact_raw=False):
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode('utf-8')
    req = urllib.request.Request(BASE + path, data=data, headers=headers or JSON_HEADERS, method=method)
    try:
        with OPENER.open(req, timeout=15) as resp:
            raw = resp.read().decode('utf-8')
            status = resp.status
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode('utf-8')
        status = exc.code
    print(f'## {name}: HTTP {status}')
    if raw:
        print('<redacted>' if redact_raw else raw[:900])
    if expected is not None and status != expected:
        raise SystemExit(f'{name} expected {expected}, got {status}')
    try:
        return json.loads(raw) if raw else None
    except json.JSONDecodeError:
        return raw


def require_api(result, name):
    if not isinstance(result, dict) or result.get('success') is not True:
        raise SystemExit(f'{name} not success: {result}')
    return result.get('data')


def is_loopback_base_url(value):
    parsed = urllib.parse.urlparse(value)
    return parsed.hostname in {'127.0.0.1', 'localhost', '::1'}


def auth_headers_for_token(access_token):
    if not isinstance(access_token, str) or not access_token.startswith('usr_'):
        raise SystemExit('login did not return a valid access token')
    return {**JSON_HEADERS, 'Authorization': 'Bearer ' + access_token}


def dev_headers_for_user(user_id):
    return {**JSON_HEADERS, 'X-User-Id': str(user_id), 'X-Dev-Mode': 'enabled'}


def require_env(name):
    value = os.environ.get(name, '').strip()
    if not value:
        raise SystemExit(f'{name} is required for login-mode smoke checks')
    return value


def login_smoke_user(label, mobile_env, password_env):
    token = require_api(call(
        f'login {label}',
        'POST',
        '/api/auth/login',
        {'mobile': require_env(mobile_env), 'password': require_env(password_env)},
        headers=JSON_HEADERS,
        redact_raw=True
    ), f'login {label}')
    headers = auth_headers_for_token(token.get('accessToken'))
    profile = require_api(call(
        f'profile {label}',
        'GET',
        '/api/user/me',
        headers=headers,
        redact_raw=True
    ), f'profile {label}')
    user_id = profile.get('userId')
    if not isinstance(user_id, int) or user_id <= 0:
        raise SystemExit(f'{label} profile userId invalid')
    return headers, user_id


def configure_auth():
    global USER_1_HEADERS, USER_2_HEADERS, USER_1_ID, USER_2_ID
    local_target = is_loopback_base_url(BASE)
    requested_mode = os.environ.get('SMOKE_API_AUTH_MODE', '').strip().lower()
    auth_mode = requested_mode or ('dev-header' if local_target else 'login')
    if auth_mode not in {'dev-header', 'login'}:
        raise SystemExit('SMOKE_API_AUTH_MODE must be dev-header or login')
    if not local_target and os.environ.get('SMOKE_API_ALLOW_REMOTE_MUTATION') != 'true':
        raise SystemExit('Remote smoke checks create chat/community test data; set SMOKE_API_ALLOW_REMOTE_MUTATION=true to continue')
    if auth_mode == 'dev-header':
        if not local_target:
            raise SystemExit('dev-header smoke auth is only allowed for localhost/127.0.0.1 targets')
        USER_1_ID = int(os.environ.get('SMOKE_API_DEV_USER1_ID', '1'))
        USER_2_ID = int(os.environ.get('SMOKE_API_DEV_USER2_ID', '2'))
        USER_1_HEADERS = dev_headers_for_user(USER_1_ID)
        USER_2_HEADERS = dev_headers_for_user(USER_2_ID)
        print(f'smoke target={BASE} auth=dev-header users={USER_1_ID},{USER_2_ID}')
        return
    USER_1_HEADERS, USER_1_ID = login_smoke_user('user1', 'SMOKE_API_USER1_MOBILE', 'SMOKE_API_USER1_PASSWORD')
    USER_2_HEADERS, USER_2_ID = login_smoke_user('user2', 'SMOKE_API_USER2_MOBILE', 'SMOKE_API_USER2_PASSWORD')
    if USER_1_ID == USER_2_ID:
        raise SystemExit('smoke accounts must be two different users')
    print(f'smoke target={BASE} auth=login users={USER_1_ID},{USER_2_ID}')


def ensure_peer_user():
    if 'Authorization' in USER_2_HEADERS:
        return
    probe = call(
        'probe peer user',
        'GET',
        '/api/chat/conversations',
        headers=USER_2_HEADERS,
        expected=None
    )
    if isinstance(probe, dict) and probe.get('success') is True:
        return
    register = call(
        'register peer user',
        'POST',
        '/api/auth/register',
        {'mobile': '13900000002', 'password': 'dev-password', 'gender': 'god'},
        expected=None,
        headers=JSON_HEADERS
    )
    if isinstance(register, dict) and register.get('success') is True:
        return
    message = str(register.get('message') if isinstance(register, dict) else register)
    if 'daily registration limit exceeded' in message or 'mobile already registered' in message:
        raise SystemExit('peer user id=2 is unavailable and registration cannot create it; restart the dev H2 backend')
    raise SystemExit(f'peer user setup failed: {register}')


def upload_blob(name, ticket, content, content_type):
    boundary = '----XQY' + uuid.uuid4().hex
    filename = ticket['storageUrl'].split('/')[-1] or 'smoke-upload.bin'
    body = (
        f'--{boundary}\r\n'
        f'Content-Disposition: form-data; name="file"; filename="{filename}"\r\n'
        f'Content-Type: {content_type}\r\n\r\n'
    ).encode('utf-8') + content + f'\r\n--{boundary}--\r\n'.encode('utf-8')
    headers = {
        'Content-Type': 'multipart/form-data; boundary=' + boundary,
        'X-Upload-Token': ticket['uploadToken'],
        **{key: value for key, value in USER_1_HEADERS.items() if key != 'Content-Type'},
    }
    req = urllib.request.Request(
        BASE + f"/api/media/upload-tickets/{ticket['ticketNo']}/file",
        data=body,
        headers=headers,
        method='POST'
    )
    try:
        with OPENER.open(req, timeout=15) as resp:
            raw = resp.read().decode('utf-8')
            status = resp.status
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode('utf-8')
        status = exc.code
    print(f'## {name}: HTTP {status}')
    if raw:
        print(raw[:900])
    if status != 200:
        raise SystemExit(f'{name} expected 200, got {status}')
    return require_api(json.loads(raw), name)


def issue_ticket(scene, content_type, content, filename):
    ticket = require_api(call(
        f'issue {scene} ticket',
        'POST',
        '/api/media/upload-tickets',
        {
            'scene': scene,
            'contentType': content_type,
            'fileSize': len(content),
            'filename': filename,
        },
        headers=USER_1_HEADERS
    , redact_raw=True), f'issue {scene} ticket')
    if ticket.get('scene') != scene or ticket.get('status') != 'ISSUED':
        raise SystemExit(f'{scene} ticket invalid: {ticket}')
    return ticket


def smoke_im():
    text_response = require_api(call(
        'send text chat',
        'POST',
        '/api/chat/messages',
        {
            'receiverId': USER_2_ID,
            'clientMsgId': 'smoke-text-' + str(int(time.time())),
            'msgType': 'TEXT',
            'contentJson': '{"text":"smoke IM text"}',
        },
        headers=USER_1_HEADERS
    ), 'send text chat')
    text_ack = text_response.get('ack') or text_response.get('message') or text_response
    conversation_id = text_ack.get('conversationId')
    if not conversation_id or text_ack.get('msgType') != 'TEXT':
        raise SystemExit(f'text ack invalid: {text_ack}')

    audio = webm_voice_bytes()
    ticket = issue_ticket('CHAT_VOICE', 'audio/webm', audio, 'chat-voice.webm')
    uploaded = upload_blob('upload chat voice', ticket, audio, 'audio/webm')
    if not str(uploaded.get('storageUrl') or '').startswith('/uploads/chat-voice/'):
        raise SystemExit(f'chat voice upload invalid: {uploaded}')
    voice_response = require_api(call(
        'send voice chat',
        'POST',
        '/api/chat/messages',
        {
            'conversationId': conversation_id,
            'receiverId': USER_2_ID,
            'clientMsgId': 'smoke-voice-' + str(int(time.time())),
            'msgType': 'VOICE',
            'contentJson': json.dumps({
                'url': uploaded['storageUrl'],
                'durationMs': 1600,
                'sizeBytes': len(audio),
                'mimeType': 'audio/webm',
            }, ensure_ascii=False),
        },
        headers=USER_1_HEADERS
    ), 'send voice chat')
    voice_ack = voice_response.get('ack') or voice_response
    if voice_ack.get('conversationId') != conversation_id or voice_ack.get('msgType') != 'VOICE':
        raise SystemExit(f'voice ack invalid: {voice_ack}')

    image = SMOKE_PNG_BYTES
    image_ticket = issue_ticket('CHAT_IMAGE', 'image/png', image, 'chat-image.png')
    image_uploaded = upload_blob('upload chat image', image_ticket, image, 'image/png')
    if not str(image_uploaded.get('storageUrl') or '').startswith('/uploads/chat-image/'):
        raise SystemExit(f'chat image upload invalid: {image_uploaded}')
    image_response = require_api(call(
        'send image chat',
        'POST',
        '/api/chat/messages',
        {
            'conversationId': conversation_id,
            'receiverId': USER_2_ID,
            'clientMsgId': 'smoke-image-' + str(int(time.time())),
            'msgType': 'IMAGE',
            'contentJson': json.dumps({
                'url': image_uploaded['storageUrl'],
                'width': 720,
                'height': 720,
                'sizeBytes': len(image),
                'mimeType': 'image/png',
            }, ensure_ascii=False),
        },
        headers=USER_1_HEADERS
    ), 'send image chat')
    image_ack = image_response.get('ack') or image_response
    if image_ack.get('conversationId') != conversation_id or image_ack.get('msgType') != 'IMAGE':
        raise SystemExit(f'image ack invalid: {image_ack}')

    conversations = require_api(call('peer chat conversations', 'GET', '/api/chat/conversations', headers=USER_2_HEADERS), 'peer chat conversations')
    conversation_rows = conversations.get('conversations') or []
    if not any(row.get('conversationId') == conversation_id and row.get('peerUserId') == USER_1_ID for row in conversation_rows):
        raise SystemExit(f'peer conversation missing: {conversations}')
    sync = require_api(call(
        'peer chat sync',
        'GET',
        f'/api/chat/conversations/{conversation_id}/messages?latest=true&limit=20',
        headers=USER_2_HEADERS
    ), 'peer chat sync')
    types = [message.get('msgType') for message in sync.get('messages') or []]
    if 'TEXT' not in types or 'VOICE' not in types or 'IMAGE' not in types:
        raise SystemExit(f'peer sync missing text, voice, or image: {sync}')
    voice_server_msg_id = voice_ack.get('serverMsgId')
    if not voice_server_msg_id:
        raise SystemExit(f'voice server message id missing: {voice_ack}')
    revoked = require_api(call(
        'revoke voice chat',
        'POST',
        f'/api/chat/messages/{voice_server_msg_id}/revoke',
        headers=USER_1_HEADERS
    ), 'revoke voice chat')
    if revoked.get('serverMsgId') != voice_server_msg_id or revoked.get('revoked') is not True:
        raise SystemExit(f'voice revoke invalid: {revoked}')
    call(
        'peer chat read',
        'POST',
        f'/api/chat/conversations/{conversation_id}/read',
        {'readSeq': image_ack.get('serverSeq')},
        headers=USER_2_HEADERS
    )
    cleared = require_api(call(
        'peer clear chat',
        'POST',
        f'/api/chat/conversations/{conversation_id}/clear',
        headers=USER_2_HEADERS
    ), 'peer clear chat')
    if cleared.get('conversationId') != conversation_id or cleared.get('clearedSeq') != image_ack.get('serverSeq'):
        raise SystemExit(f'peer clear invalid: {cleared}')
    cleared_sync = require_api(call(
        'peer chat sync after clear',
        'GET',
        f'/api/chat/conversations/{conversation_id}/messages?afterSeq=0&limit=20',
        headers=USER_2_HEADERS
    ), 'peer chat sync after clear')
    if cleared_sync.get('messages'):
        raise SystemExit(f'peer clear did not hide messages: {cleared_sync}')


def smoke_community():
    image = SMOKE_PNG_BYTES
    ticket = issue_ticket('COMMUNITY_IMAGE', 'image/png', image, 'community-smoke.png')
    uploaded = upload_blob('upload community image', ticket, image, 'image/png')
    if not str(uploaded.get('storageUrl') or '').startswith('/uploads/community-image/'):
        raise SystemExit(f'community image upload invalid: {uploaded}')
    post = require_api(call(
        'create community post',
        'POST',
        '/api/community/posts',
        {
            'title': '本地冒烟社区帖',
            'topic': '生活日常',
            'content': '本地冒烟验证社区发帖、列表、详情、评论闭环。',
            'imageUrls': [uploaded['storageUrl']],
        },
        headers=USER_1_HEADERS
    ), 'create community post')
    post_id = post.get('postId')
    post_no = post.get('postNo')
    if not post_id or not post_no or post.get('topic') != '生活日常' or post.get('status') != 'PUBLISHED':
        raise SystemExit(f'community post invalid: {post}')
    feed = require_api(call(
        'community feed',
        'GET',
        '/api/community/posts?limit=10&topic=%E7%94%9F%E6%B4%BB%E6%97%A5%E5%B8%B8',
        headers=USER_1_HEADERS
    ), 'community feed')
    if not any(row.get('postId') == post_id for row in feed):
        raise SystemExit(f'community feed missing post: {feed}')
    detail = require_api(call('community detail', 'GET', f'/api/community/posts/{post_id}', headers=USER_1_HEADERS), 'community detail')
    if detail.get('postNo') != post_no:
        raise SystemExit(f'community detail mismatch: {detail}')
    comment = require_api(call(
        'community comment',
        'POST',
        f'/api/community/posts/{post_id}/comments',
        {'content': '冒烟评论闭环验证'},
        headers=USER_2_HEADERS
    ), 'community comment')
    if comment.get('content') != '冒烟评论闭环验证':
        raise SystemExit(f'community comment invalid: {comment}')


def main():
    configure_auth()
    health = call('health', 'GET', '/actuator/health')
    if not isinstance(health, dict) or health.get('status') != 'UP':
        raise SystemExit('health not UP')
    ensure_peer_user()
    smoke_im()
    smoke_community()
    print('SMOKE_PASS')


if __name__ == '__main__':
    try:
        main()
    except KeyboardInterrupt:
        sys.exit(130)
