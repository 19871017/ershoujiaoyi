#!/usr/bin/env python3
import json
import sys
import time
import urllib.error
import urllib.request

BASE = 'http://127.0.0.1:18080'
OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))
HEADERS = {
    'Content-Type': 'application/json',
    'X-User-Id': '1',
    'X-Dev-Mode': 'enabled',
    'X-Admin-Mode': 'enabled',
}

def call(name, method, path, body=None, expected=200):
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode('utf-8')
    req = urllib.request.Request(BASE + path, data=data, headers=HEADERS, method=method)
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
    if status != expected:
        raise SystemExit(f'{name} expected {expected}, got {status}')
    try:
        return json.loads(raw) if raw else None
    except json.JSONDecodeError:
        return raw

def require_api(result, name):
    if not isinstance(result, dict) or result.get('success') is not True:
        raise SystemExit(f'{name} not success: {result}')
    return result.get('data')

health = call('health', 'GET', '/actuator/health')
if not isinstance(health, dict) or health.get('status') != 'UP':
    raise SystemExit('health not UP')

require_api(call('login', 'POST', '/api/auth/login', {'mobile': '13800138000', 'password': 'dev-password'}), 'login')
product = require_api(call('create product', 'POST', '/api/products', {
    'title': 'Phase5测试商品',
    'description': 'full smoke product',
    'price': '12.34',
    'imageUrls': ['https://example.com/a.jpg']
}), 'create product')
call('list products', 'GET', '/api/products')
recharge = require_api(call('create recharge', 'POST', '/api/payments/recharge', {'amount': '100.00', 'channel': 'DEV'}), 'create recharge')
require_api(call('simulate recharge', 'POST', '/api/payments/recharge/simulate-success', {'rechargeNo': recharge['rechargeNo']}), 'simulate recharge')
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
call('admin dashboard', 'GET', '/api/admin/dashboard')
print('SMOKE_PASS')
