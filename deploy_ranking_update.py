#!/usr/bin/env python3
import os, pexpect, shlex, sys
repo = os.environ.get('XQY_REPO') or os.path.dirname(os.path.abspath(__file__))
password = os.environ.get('XQY_DEPLOY_PASSWORD', '')
server = os.environ.get('XQY_DEPLOY_SERVER', 'root@23.138.12.9')
remote_root = os.environ.get('XQY_REMOTE_ROOT', '/www/wwwroot/esxz-old/current')
known_hosts = os.environ.get('XQY_KNOWN_HOSTS', '/tmp/xqy_known_hosts')
ssh = f"ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile={known_hosts}"
scp = f"scp -o StrictHostKeyChecking=no -o UserKnownHostsFile={known_hosts}"
commands = [
    f"rsync -az --delete -e {shlex.quote(ssh)} {shlex.quote(os.path.join(repo, 'frontend/dist/build/h5/'))} {server}:{shlex.quote(remote_root + '/h5/')}",
    f"{ssh} {server} \"mkdir -p {shlex.quote(remote_root + '/assets')} && rsync -a --delete {shlex.quote(remote_root + '/h5/assets/')} {shlex.quote(remote_root + '/assets/')}\"",
    f"{scp} {shlex.quote(os.path.join(repo, 'backend/target/backend-0.1.0-SNAPSHOT.jar'))} {server}:{shlex.quote(remote_root + '/backend.jar')}",
    f"{ssh} {server} \"systemctl restart esxz-old && sleep 5 && systemctl is-active esxz-old && curl -fsS http://127.0.0.1:18080/actuator/health\"",
    f"{ssh} {server} \"grep -R '女神收礼榜\\|男神消费榜\\|日榜\\|周榜\\|总榜' -n {shlex.quote(remote_root + '/h5')} {shlex.quote(remote_root + '/assets')} 2>/dev/null | head -20; ! grep -R '礼物积分榜\\|1 元 = 1 分\\|男神女神礼物榜' -n {shlex.quote(remote_root + '/h5')} {shlex.quote(remote_root + '/assets')} 2>/dev/null\"",
]
for i, cmd in enumerate(commands, 1):
    print(f'--- command {i} ---')
    child = pexpect.spawn('/bin/bash', ['-lc', cmd], encoding='utf-8', timeout=600)
    child.logfile_read = sys.stdout
    while True:
        idx = child.expect([r'(?i)password:', r'(?i)yes/no', pexpect.EOF, pexpect.TIMEOUT])
        if idx == 0:
            if not password:
                print('XQY_DEPLOY_PASSWORD is required for password-based SSH; prefer SSH key auth.', file=sys.stderr)
                child.close(force=True)
                sys.exit(2)
            child.sendline(password)
        elif idx == 1:
            child.sendline('yes')
        elif idx == 2:
            break
        else:
            print('TIMEOUT', file=sys.stderr)
            child.close(force=True)
            sys.exit(124)
    child.close()
    if child.exitstatus not in (0, None):
        print(f'command {i} failed: {child.exitstatus}', file=sys.stderr)
        sys.exit(child.exitstatus)
print('DEPLOY_DONE')
