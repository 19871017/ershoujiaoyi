# 后端实施脚手架规划（V1）

> 适用范围：二手闲置社交交易平台 MVP 第一阶段的后端代码落地。
>
> 目标：先固定**模块位置、包名规则、入口文件、共享基础设施**，再由子任务按边界创建代码骨架。
>
> 适用前提：
> - 已冻结 MVP 范围
> - 已冻结服务边界、IM 协议、财务账本协议
> - 当前阶段采用**模块化单体**，先逻辑拆分，后物理拆分

---

## 1. 设计原则

1. **一个后端仓库，一个统一运行基座，多个业务模块。**
2. **模块按领域放，不按技术层乱放。**
3. **接口层只做协议适配，不写业务规则。**
4. **application 负责编排，domain 负责规则，infrastructure 负责技术实现。**
5. **跨模块读通过 facade / query service，跨模块写通过 application service / event。**
6. **钱相关只认 wallet-ledger 为事实源。**
7. **ws-gateway 只做连接与投递，不承载消息主业务状态。**

---

## 2. 推荐后端仓库结构

> 下面是建议的后端独立树。若当前仓库采用单仓多端结构，可把该目录挂到 `backend/` 或 `services/backend/` 下。

```text
backend/
├── apps/
│   ├── api/                      # 统一 HTTP / RPC 入口
│   └── ws-gateway/               # WebSocket 连接网关
├── modules/
│   ├── auth/                     # 登录、注册、Token、会话
│   ├── user/                     # 用户资料、身份、可见性
│   ├── wallet-ledger/            # 钱包、账本、流水、冻结、解冻
│   └── chat/                     # 会话、消息、ACK、游标、离线补拉
├── shared/
│   ├── kernel/                   # 通用基类、错误、结果对象、领域事件基类
│   ├── infra/                    # ID、时间、幂等、事务、配置、审计、消息总线
│   ├── contracts/                # 模块间稳定契约 DTO / Command / Query / Event
│   ├── events/                   # 事件定义与事件名常量
│   └── observability/            # 日志、指标、链路追踪
├── tests/
│   ├── unit/
│   ├── integration/
│   └── e2e/
├── docs/
├── scripts/
└── deploy/
```

---

## 3. 包名 / 命名规则

### 3.1 目录名

- 模块目录保留业务域原名：
  - `auth`
  - `user`
  - `wallet-ledger`
  - `chat`
  - `ws-gateway`
- `wallet-ledger` 这种带连字符的目录名只用于文件夹；代码包名统一改成下划线形式。

### 3.2 代码包名建议

推荐统一为以下形式：

- `backend.apps.api`
- `backend.apps.ws_gateway`
- `backend.modules.auth`
- `backend.modules.user`
- `backend.modules.wallet_ledger`
- `backend.modules.chat`
- `backend.shared.kernel`
- `backend.shared.infra`
- `backend.shared.contracts`
- `backend.shared.events`
- `backend.shared.observability`

### 3.3 文件命名规则

- 入口：`main.*`、`bootstrap.*`、`module.*`
- 控制器：`*.controller.*`
- 网关：`*.gateway.*`
- 应用服务：`*.service.*`、`*.facade.*`
- 命令：`*.command.*`
- 查询：`*.query.*`
- 领域对象：`*.aggregate.*`、`*.entity.*`、`*.value-object.*`
- 仓储接口：`*.repository.*`
- 仓储实现：`*.repository.impl.*` 或 `*.repository.adapter.*`

> 具体后缀由技术栈决定，本文只冻结命名语义。

---

## 4. 第一批必须创建的文件骨架

### 4.1 仓库级基础文件

建议优先创建：

```text
backend/
├── README.md
├── .env.example
├── .gitignore
├── docs/
│   └── 02-backend-implementation-scaffold-v1.md
└── tests/
    ├── unit/
    ├── integration/
    └── e2e/
```

### 4.2 应用入口文件

```text
backend/apps/api/
├── src/
│   ├── main.*
│   ├── app.bootstrap.*
│   ├── app.module.*
│   └── config/
│       ├── env.config.*
│       ├── database.config.*
│       └── auth.config.*
└── README.md

backend/apps/ws-gateway/
├── src/
│   ├── main.*
│   ├── gateway.bootstrap.*
│   ├── gateway.module.*
│   └── config/
│       ├── env.config.*
│       ├── websocket.config.*
│       └── auth.config.*
└── README.md
```

### 4.3 shared 基础设施先建文件

```text
backend/shared/
├── kernel/
│   ├── base.entity.*
│   ├── base.aggregate.*
│   ├── domain-event.*
│   ├── result.*
│   ├── error.*
│   └── pagination.*
├── infra/
│   ├── clock.*
│   ├── id-generator.*
│   ├── idempotency-key.*
│   ├── transaction-manager.*
│   ├── event-bus.*
│   ├── cache.*
│   ├── message-bus.*
│   └── config-loader.*
├── contracts/
│   ├── auth/
│   ├── user/
│   ├── wallet_ledger/
│   └── chat/
├── events/
│   ├── event-names.*
│   └── event-envelope.*
└── observability/
    ├── logger.*
    ├── tracer.*
    └── metrics.*
```

> shared 先只放“全局公共且稳定”的东西，禁止塞业务模型。

---

## 5. 重点模块骨架

## 5.1 auth 模块

### 职责
- 登录、注册、验证码、密码、第三方绑定预留
- Token 签发、刷新、吊销
- 会话管理、设备管理、踢下线
- 登录风控入口

### 不做什么
- 不承载用户资料
- 不承载商品、订单、钱包逻辑

### 第一批文件

```text
backend/modules/auth/
├── src/
│   ├── auth.module.*
│   ├── interface/
│   │   ├── http/
│   │   │   ├── auth.controller.*
│   │   │   ├── auth.dto.*
│   │   │   └── auth.guard.*
│   │   └── job/
│   ├── application/
│   │   ├── command/
│   │   │   ├── login.command.*
│   │   │   ├── register.command.*
│   │   │   ├── refresh-token.command.*
│   │   │   └── revoke-session.command.*
│   │   ├── query/
│   │   └── facade/
│   │       └── auth.facade.*
│   ├── domain/
│   │   ├── aggregate/
│   │   │   ├── auth-session.aggregate.*
│   │   │   └── auth-credential.aggregate.*
│   │   ├── entity/
│   │   ├── repository/
│   │   │   ├── auth-session.repository.*
│   │   │   └── auth-credential.repository.*
│   │   ├── service/
│   │   └── event/
│   └── infrastructure/
│       ├── persistence/
│       │   ├── auth-session.repository.impl.*
│       │   └── auth-credential.repository.impl.*
│       ├── token/
│       │   ├── jwt-token.service.*
│       │   └── token-config.*
│       ├── crypto/
│       ├── cache/
│       └── config/
└── README.md
```

### 依赖方向
- 允许读 `user` 的账号基础资料
- 允许用 `shared/infra` 的验证码、token、审计、风控封装
- 不允许直接写 `user` 主表

---

## 5.2 user 模块

### 职责
- 用户主档、资料、标签、身份状态
- 认证展示信息
- 隐私配置、可见性控制

### 不做什么
- 不负责登录校验
- 不管理会话
- 不做风控判定

### 第一批文件

```text
backend/modules/user/
├── src/
│   ├── user.module.*
│   ├── interface/
│   │   ├── http/
│   │   │   ├── user.controller.*
│   │   │   ├── profile.dto.*
│   │   │   └── privacy.dto.*
│   │   └── query-api/
│   ├── application/
│   │   ├── command/
│   │   │   ├── update-profile.command.*
│   │   │   ├── update-privacy.command.*
│   │   │   └── change-identity.command.*
│   │   ├── query/
│   │   │   ├── get-profile.query.*
│   │   │   └── get-public-profile.query.*
│   │   └── facade/
│   │       └── user.facade.*
│   ├── domain/
│   │   ├── aggregate/
│   │   │   └── user-profile.aggregate.*
│   │   ├── entity/
│   │   ├── value-object/
│   │   ├── repository/
│   │   │   └── user-profile.repository.*
│   │   ├── service/
│   │   └── event/
│   └── infrastructure/
│       ├── persistence/
│       │   └── user-profile.repository.impl.*
│       ├── cache/
│       ├── converter/
│       └── config/
└── README.md
```

### 依赖方向
- 读取 `auth` 的登录态
- 读取 `audit` 的认证审核结果（后续模块接入）
- 读取 `relation` 的关注/黑名单视图（后续模块接入）

---

## 5.3 wallet-ledger 模块

### 职责
- 钱包账户、账本、流水、冻结、解冻、冲正
- 收入入账、消费扣款、提现前冻结、退款冲正
- 余额汇总与对账基础能力

### 不做什么
- 不决定订单是否成交
- 不决定支付渠道
- 不承担内容审核

### 第一批文件

```text
backend/modules/wallet-ledger/
├── src/
│   ├── wallet-ledger.module.*
│   ├── interface/
│   │   ├── http/
│   │   │   ├── ledger.controller.*
│   │   │   ├── account.controller.*
│   │   │   └── ledger.dto.*
│   │   └── mq/
│   ├── application/
│   │   ├── command/
│   │   │   ├── create-ledger-entry.command.*
│   │   │   ├── freeze-balance.command.*
│   │   │   ├── unfreeze-balance.command.*
│   │   │   └── reverse-ledger-entry.command.*
│   │   ├── query/
│   │   │   ├── get-account-balance.query.*
│   │   │   └── list-ledger-entries.query.*
│   │   └── facade/
│   │       └── wallet-ledger.facade.*
│   ├── domain/
│   │   ├── aggregate/
│   │   │   ├── wallet-account.aggregate.*
│   │   │   └── ledger-book.aggregate.*
│   │   ├── entity/
│   │   │   └── ledger-entry.entity.*
│   │   ├── value-object/
│   │   ├── repository/
│   │   │   ├── wallet-account.repository.*
│   │   │   └── ledger-entry.repository.*
│   │   ├── service/
│   │   │   └── balance-calculator.service.*
│   │   └── event/
│   └── infrastructure/
│       ├── persistence/
│       │   ├── wallet-account.repository.impl.*
│       │   ├── ledger-entry.repository.impl.*
│       │   └── ledger-mapper.*
│       ├── converter/
│       ├── mq/
│       ├── config/
│       └── lock/
└── README.md
```

### 依赖方向
- 只依赖 `shared` 公共能力
- 接收 `payment` / `order` / `gift` 的记账指令时，必须通过幂等键
- 所有金额变更必须走账本服务，不允许直接改余额字段

---

## 5.4 chat 模块

### 职责
- 单聊会话、消息、ACK、离线补拉、已读游标
- 基础文本 / 图片消息
- 基础审核接入

### 不做什么
- 不负责长连接本身
- 不负责好友关系计算
- 不负责礼物账务

### 第一批文件

```text
backend/modules/chat/
├── src/
│   ├── chat.module.*
│   ├── interface/
│   │   ├── http/
│   │   │   ├── conversation.controller.*
│   │   │   ├── message.controller.*
│   │   │   └── chat.dto.*
│   │   └── mq/
│   │       └── message-consumer.*
│   ├── application/
│   │   ├── command/
│   │   │   ├── send-message.command.*
│   │   │   ├── ack-message.command.*
│   │   │   ├── mark-read.command.*
│   │   │   └── sync-offline.command.*
│   │   ├── query/
│   │   │   ├── list-conversations.query.*
│   │   │   ├── list-messages.query.*
│   │   │   └── get-unread-count.query.*
│   │   └── facade/
│   │       └── chat.facade.*
│   ├── domain/
│   │   ├── aggregate/
│   │   │   ├── conversation.aggregate.*
│   │   │   └── message.aggregate.*
│   │   ├── entity/
│   │   ├── value-object/
│   │   ├── repository/
│   │   │   ├── conversation.repository.*
│   │   │   └── message.repository.*
│   │   ├── service/
│   │   │   ├── sequence-generator.service.*
│   │   │   └── unread-calculator.service.*
│   │   └── event/
│   └── infrastructure/
│       ├── persistence/
│       │   ├── conversation.repository.impl.*
│       │   └── message.repository.impl.*
│       ├── converter/
│       ├── mq/
│       ├── cache/
│       └── config/
└── README.md
```

### 依赖方向
- 依赖 `auth` 做连接鉴权
- 依赖 `user` 做用户展示信息读取
- 依赖 `shared/contracts` 处理消息事件、ACK、游标契约

---

## 5.5 ws-gateway

### 职责
- WebSocket 长连接接入
- 在线连接管理、心跳、路由、消息投递
- ACK 转发、离线补偿接入

### 不做什么
- 不做消息业务主状态存储
- 不做会话规则判断
- 不做内容审核本体

### 第一批文件

```text
backend/apps/ws-gateway/
├── src/
│   ├── gateway.module.*
│   ├── gateway.bootstrap.*
│   ├── interface/
│   │   └── ws/
│   │       ├── chat.gateway.*
│   │       ├── connection.gateway.*
│   │       └── ws-message.dto.*
│   ├── application/
│   │   ├── command/
│   │   │   ├── route-message.command.*
│   │   │   ├── push-message.command.*
│   │   │   └── heartbeat.command.*
│   │   └── facade/
│   │       └── ws-routing.facade.*
│   ├── infrastructure/
│   │   ├── connection/
│   │   │   ├── connection-registry.*
│   │   │   ├── connection-session.*
│   │   │   └── online-state-store.*
│   │   ├── auth/
│   │   │   └── ws-auth.guard.*
│   │   ├── route/
│   │   └── config/
│   └── README.md
```

### 依赖方向
- 连接鉴权依赖 `auth`
- 消息投递策略依赖 `chat`
- 连接状态优先放在 gateway 内部，不下沉为业务主表

---

## 6. 第一阶段创建顺序建议

建议按下面顺序建骨架：

1. `backend/README.md`、`.env.example`、`.gitignore`
2. `backend/shared/kernel` 与 `backend/shared/infra`
3. `backend/apps/api` 与 `backend/apps/ws-gateway`
4. `backend/modules/auth`
5. `backend/modules/user`
6. `backend/modules/wallet-ledger`
7. `backend/modules/chat`
8. `backend/shared/contracts` 与 `backend/shared/events`
9. `backend/tests/unit`、`backend/tests/integration`、`backend/tests/e2e`

---

## 7. 模块间依赖速记

- `apps/api` -> `modules/*` + `shared/*`
- `apps/ws-gateway` -> `modules/auth`、`modules/chat`、`shared/*`
- `auth` -> `user` 读接口 + `shared/*`
- `user` -> `shared/*`
- `wallet-ledger` -> `shared/*`
- `chat` -> `auth`、`user`、`shared/contracts`、`shared/events`
- `shared` 不允许反向依赖任何业务模块

---

## 8. 这版脚手架的落地标准

当以下内容齐备时，说明脚手架可以进入代码骨架阶段：

- 模块目录已创建
- 入口文件已创建
- shared 基础设施已占位
- auth、user、wallet-ledger、chat、ws-gateway 的第一批接口文件已占位
- 模块依赖方向已冻结
- 后续子任务可以直接按目录开工，不需要再讨论结构
