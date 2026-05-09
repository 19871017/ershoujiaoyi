# 二手闲置社交交易平台本地运行说明

项目主目录：`/mnt/c/Users/WangYu/Desktop/esxz-sj-jypt`

## 1. 本地工具链

本项目当前使用项目内本地工具链，避免依赖系统 sudo 安装：

- JDK：`.tools/jdk-21`
- Maven：`.tools/maven`

加载环境：

```bash
source scripts/env.sh
```

## 2. 后端启动

```bash
cd /mnt/c/Users/WangYu/Desktop/esxz-sj-jypt
backend/scripts/start-api.sh
```

默认端口：`18080`

健康检查：

```bash
curl --noproxy '*' http://127.0.0.1:18080/actuator/health
```

预期：

```json
{"status":"UP"}
```

## 3. 前端构建与预览

构建 H5：

```bash
frontend/scripts/build-h5.sh
```

预览 H5：

```bash
frontend/scripts/preview-h5.sh
```

浏览器访问：

```text
http://127.0.0.1:4173/h5/
```

## 4. 后端测试/打包

```bash
backend/scripts/test.sh
backend/scripts/package.sh
```

## 5. 全量回归

确保后端已启动在 `127.0.0.1:18080`，然后执行：

```bash
scripts/final-regression.sh
```

该脚本会执行：

1. 后端 `mvn test`
2. 后端 `mvn package`
3. 前端 `npm run typecheck`
4. 前端 `npm run build:h5`
5. 核心 API 冒烟：`scripts/smoke-api.py`

冒烟覆盖：

- health
- login
- product create/list
- recharge create/simulate-success
- wallet balance/ledger
- order create/pay
- chat send/list/sync/read
- admin dashboard

成功标志：

```text
SMOKE_PASS
```

## 6. 开发态请求头

当前 MVP 联调阶段使用开发态请求头：

```text
X-User-Id: 1
X-Dev-Mode: enabled
X-Admin-Mode: enabled
```

注意：这些只能用于开发/联调。生产前必须替换为真实登录态、权限体系，并禁用模拟充值接口。

## 7. 当前限制

- 业务数据当前主要为内存态，重启后会丢失。
- 模拟充值是开发态能力，不能上线。
- 前端全局 dev header 不能进入生产构建。
- npm audit 仍存在高危依赖告警，后续上线前需专项治理。
- 真实支付、实名、IM WebSocket、数据库持久化、风控策略仍需后续生产化。
