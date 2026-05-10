#!/usr/bin/env python3
import json
import sys
import time
import urllib.error
import urllib.request

BASE = 'http://127.0.0.1:18080'
OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))
JSON_HEADERS = {'Content-Type': 'application/json'}
USER_HEADERS = {**JSON_HEADERS, 'X-User-Id': '1'}
ADMIN_HEADERS = dict(JSON_HEADERS)
ADMIN_MOBILE = '13800138000'
ADMIN_PASSWORD = 'dev-password'

def call(name, method, path, body=None, expected=200, headers=None):
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode('utf-8')
    req = urllib.request.Request(BASE + path, data=data, headers=headers or USER_HEADERS, method=method)
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

def admin_login():
    session = require_api(call(
        'admin session login',
        'POST',
        '/api/admin/session/login',
        {'mobile': ADMIN_MOBILE, 'password': ADMIN_PASSWORD},
        headers=JSON_HEADERS
    ), 'admin session login')
    user_id = str(session.get('userId') or '').strip()
    permissions = session.get('permissions') or []
    if not user_id or 'audit:read' not in permissions:
        raise SystemExit('admin session missing explicit audit:read permission')
    ADMIN_HEADERS['X-User-Id'] = user_id
    session_id = str(session.get('sessionId') or '').strip()
    if not session_id.startswith('adm_'):
        raise SystemExit('admin session missing server-issued sessionId')
    ADMIN_HEADERS['X-Admin-Session'] = session_id

def issue_product_image():
    ticket = require_api(call(
        'issue product image ticket',
        'POST',
        '/api/media/upload-tickets',
        {
            'scene': 'PRODUCT_IMAGE',
            'contentType': 'image/jpeg',
            'fileSize': 300000,
            'filename': 'phase5-product.jpg'
        }
    ), 'issue product image ticket')
    storage_url = str(ticket.get('storageUrl') or '').strip()
    if not storage_url.startswith('/uploads/product-image/'):
        raise SystemExit('product image ticket missing storageUrl')
    return storage_url

health = call('health', 'GET', '/actuator/health', headers=JSON_HEADERS)
if not isinstance(health, dict) or health.get('status') != 'UP':
    raise SystemExit('health not UP')

require_api(call('login', 'POST', '/api/auth/login', {'mobile': '13800138000', 'password': 'dev-password'}, headers=JSON_HEADERS), 'login')
admin_login()
product_image_url = issue_product_image()
product = require_api(call('create product', 'POST', '/api/products', {
    'title': 'Phase5测试商品',
    'description': 'full smoke product',
    'price': '12.34',
    'imageUrls': [product_image_url]
}), 'create product')
product_id = product['productId']
require_api(call('admin approve product for smoke', 'POST', f'/api/admin/products/{product_id}/approve', headers=ADMIN_HEADERS), 'admin approve product for smoke')
call('list products', 'GET', '/api/products')
recharge = require_api(call('create recharge', 'POST', '/api/payments/recharge', {'amount': '100.00', 'channel': 'WECHAT'}), 'create recharge')
require_api(call('simulate recharge', 'POST', '/api/payments/recharge/simulate-success', {'rechargeNo': recharge['rechargeNo']}, headers={**USER_HEADERS, 'X-Dev-Mode': 'enabled'}), 'simulate recharge')
call('wallet balance', 'GET', '/api/wallet/balance')
call('wallet ledger', 'GET', '/api/wallet/ledger')
order = require_api(call('create order', 'POST', '/api/orders', {'goodsId': product['productId'], 'acceptedTradeRule': True}), 'create order')
require_api(call('pay order', 'POST', f"/api/orders/{order['orderNo']}/pay", {}), 'pay order')
msg = require_api(call('send chat', 'POST', '/api/chat/messages', {'receiverId': 2, 'clientMsgId': 'phase5-msg-' + str(int(time.time())), 'msgType': 'TEXT', 'contentJson': '{"text":"hello phase5"}'}), 'send chat')
ack = msg.get('ack') or msg.get('message') or msg
cid = ack.get('conversationId')
if not cid:
    raise SystemExit('missing conversationId')
call('chat conversations', 'GET', '/api/chat/conversations')
call('chat sync', 'GET', f'/api/chat/conversations/{cid}/messages?afterSeq=0&limit=20')
call('chat read', 'POST', f'/api/chat/conversations/{cid}/read', {'readSeq': ack.get('serverSeq', 1)})
call('admin dashboard', 'GET', '/api/admin/dashboard', headers=ADMIN_HEADERS)
print('SMOKE_PASS')
