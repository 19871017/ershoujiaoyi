# 二手闲置社交交易平台
# 第一版后端模块划分草案（模块化单体）

> 目标：先定代码层边界、目录和依赖方向，保证后续子 agent 可并行开工。
>
> 适用前提：
> - 总体执行计划已冻结
> - 数据库设计已冻结
> - 服务边界、IM 协议、财务协议已冻结
> - 当前阶段不追求物理微服务拆分，先做**模块化单体**

---

## 1. 总体原则

### 1.1 组织方式

推荐采用：**一个仓库、一个应用入口、多个领域模块**。

核心原则：
- **模块独立**：每个业务边界一个模块目录
- **分层清晰**：接口层、应用层、领域层、基础设施层分离
- **依赖单向**：只能从外层依赖内层，不允许反向依赖
- **跨模块通过契约**：通过 application service、facade、event、read model 交互
- **钱相关必须账本优先**：payment 只管支付单和回调，wallet-ledger 才是资金事实源

### 1.2 推荐技术结构

不限定语言，但建议目录语义统一成下面四层：
- `interface`：HTTP / RPC / 消息消费 / 定时任务入口
- `application`：用例编排、事务边界、幂等、调用领域能力
- `domain`：实体、值对象、领域服务、仓储接口、领域事件
- `infrastructure`：DB 实现、MQ 实现、缓存、外部 API、对象存储、搜索等

### 1.3 依赖规则

必须遵守：
- `interface -> application -> domain`
- `infrastructure` 只能实现 `domain` 定义的接口
- 模块之间禁止直接访问对方的 `repository`、`entity`、`mapper`
- 跨模块读：优先通过 `query facade` / `read api`
- 跨模块写：优先通过 `application service` / `command facade` / `event` 驱动
- 所有重试写路径必须带 `idempotency_key`

---

## 2. 仓库目录建议

```text
backend/
├── apps/
│   ├── api/                      # 统一 HTTP/RPC 入口
│   ├── ws-gateway/               # WebSocket 长连接入口
│   └── admin-console/            # 可选：后台独立前端服务入口
├── modules/
│   ├── auth/
│   ├── user/
│   ├── product/
│   ├── order/
│   ├── payment/
│   ├── wallet-ledger/
│   ├── chat/
│   ├── gift/
│   ├── audit/
│   ├── rank/
│   ├── relation/
│   └── admin/
├── shared/
│   ├── kernel/
│   ├── infra/
│   ├── contracts/
│   ├── events/
│   └── observability/
├── docs/
├── scripts/
└── deploy/
```

### 2.1 命名建议

- 模块名用业务域名：`wallet-ledger`、`ws-gateway`
- 代码包名建议用下划线或驼峰统一一种风格，避免混用
- 对外接口命名以“能力”而不是“表名”命名，例如：
  - `AuthFacade`
  - `OrderAppService`
  - `LedgerCommandService`
  - `ChatMessageQueryService`

---

## 3. 每个模块的标准目录模板

建议每个模块都采用统一骨架：

```text
modules/{module-name}/
├── interface/
│   ├── http/
│   ├── rpc/
│   ├── mq/
│   └── job/
├── application/
│   ├── command/
│   ├── query/
│   ├── dto/
│   └── facade/
├── domain/
│   ├── aggregate/
│   ├── entity/
│   ├── value-object/
│   ├── service/
│   ├── repository/
│   ├── event/
│   └── policy/
├── infrastructure/
│   ├── persistence/
│   ├── cache/
│   ├── client/
│   ├── mq/
│   ├── converter/
│   └── config/
└── test/
```

### 3.1 模块内职责边界

- `interface`：只做协议适配、参数校验、鉴权接入、响应组装
- `application`：事务、流程编排、幂等、权限校验调用、事件发布
- `domain`：业务规则、状态机、领域约束、核心模型
- `infrastructure`：只处理技术细节，不写业务规则

---

## 4. 统一包结构草案

下面给出第一版建议。可直接作为子 agent 开工目录依据。

---

## 5. 模块划分说明

### 5.1 auth 模块

**职责**
- 登录、注册、验证码、密码、第三方绑定
- token 签发、刷新、吊销
- 会话管理、设备管理、踢下线
- 登录风控入口

**不做什么**
- 不承载用户资料
- 不承载商品、订单、钱包逻辑

**目录建议**
```text
modules/auth/
├── interface/
│   ├── http/
│   ├── mq/
│   └── job/
├── application/
│   ├── command/
│   ├── query/
│   ├── facade/
│   └── dto/
├── domain/
│   ├── aggregate/
│   ├── entity/
│   ├── repository/
│   ├── service/
│   └── event/
└── infrastructure/
    ├── persistence/
    ├── crypto/
    ├── token/
    ├── cache/
    └── config/
```

**典型依赖**
- 读 `user` 的账号基础资料
- 依赖 `shared/infra` 的验证码、token、风控、审计日志

---

### 5.2 user 模块

**职责**
- 用户主档、资料、标签、身份状态
- 认证展示信息
- 隐私配置、可见性控制

**不做什么**
- 不负责登录校验
- 不管理会话
- 不做风控判定

**目录建议**
```text
modules/user/
├── interface/
│   ├── http/
│   ├── mq/
│   └── query-api/
├── application/
│   ├── command/
│   ├── query/
│   ├── facade/
│   └── dto/
├── domain/
│   ├── aggregate/
│   ├── entity/
│   ├── value-object/
│   ├── repository/
│   ├── service/
│   └── event/
└── infrastructure/
    ├── persistence/
    ├── cache/
    ├── converter/
    └── config/
```

**典型依赖**
- 从 `auth` 获取登录态
- 从 `audit` 获取认证审核结果
- 从 `relation` 获取关注/好友/黑名单视图

---

### 5.3 product 模块

**职责**
- 商品发布、编辑、上下架、审核快照
- 商品图片、商品状态机
- 商品曝光和列表读模型

**不做什么**
- 不负责订单支付
- 不直接改钱包
- 不直接处理聊天业务

**目录建议**
```text
modules/product/
├── interface/
│   ├── http/
│   ├── mq/
│   └── query-api/
├── application/
│   ├── command/
│   ├── query/
│   ├── facade/
│   └── dto/
├── domain/
│   ├── aggregate/
│   ├── entity/
│   ├── state-machine/
│   ├── repository/
│   ├── service/
│   └── event/
└── infrastructure/
    ├── persistence/
    ├── cache/
    ├── search/
    ├── oss/
    └── config/
```

**典型依赖**
- `audit` 负责审核结果
- `user` 提供卖家身份/标签
- `rank` 和 `relation` 提供部分推荐读数据

---

### 5.4 order 模块

**职责**
- 订单创建、锁单、取消、完成、售后状态机
- 商品单、礼物单的统一订单骨架
- 订单快照、状态流水、幂等控制

**不做什么**
- 不直接扣款
- 不直接记账
- 不负责商品库存

**目录建议**
```text
modules/order/
├── interface/
│   ├── http/
│   ├── mq/
│   └── job/
├── application/
│   ├── command/
│   ├── query/
│   ├── facade/
│   └── dto/
├── domain/
│   ├── aggregate/
│   ├── entity/
│   ├── state-machine/
│   ├── repository/
│   ├── service/
│   ├── event/
│   └── policy/
└── infrastructure/
    ├── persistence/
    ├── mq/
    ├── cache/
    └── config/
```

**典型依赖**
- 读 `product` 的商品快照
- 调 `payment` 创建支付单
- 调 `wallet-ledger` 完成入账/冻结/解冻
- 调 `audit` 做异常单处理

**建议注意**
- 订单必须维护状态流水
- 订单状态是单一归属，其他模块不得直接改

---

### 5.5 payment 模块

**职责**
- 第三方支付渠道接入
- 支付单管理、回调处理、退款编排、对账
- 外部支付状态与内部业务状态映射

**不做什么**
- 不保存业务余额
- 不做业务账本
- 不直接改订单最终态

**目录建议**
```text
modules/payment/
├── interface/
│   ├── http/
│   ├── callback/
│   ├── mq/
│   └── job/
├── application/
│   ├── command/
│   ├── query/
│   ├── facade/
│   └── dto/
├── domain/
│   ├── aggregate/
│   ├── entity/
│   ├── repository/
│   ├── service/
│   ├── event/
│   └── policy/
└── infrastructure/
    ├── persistence/
    ├── client/
    ├── pay-channel/
    ├── mq/
    └── config/
```

**典型依赖**
- 调 `wallet-ledger` 写账
- 接收 `order` / `gift` 的支付意图

---

### 5.6 wallet-ledger 模块

**职责**
- 账户总账、分户账、流水、冻结、解冻、冲正
- 资金归集、分润、提现申请/审核/打款状态
- 所有金额变更必须账本化

**边界最重要**
- 这是资金事实源
- 任何业务模块都不能直接改余额
- 其他模块只能发“记账指令”或“账务请求”

**目录建议**
```text
modules/wallet-ledger/
├── interface/
│   ├── http/
│   ├── rpc/
│   ├── mq/
│   └── job/
├── application/
│   ├── command/
│   ├── query/
│   ├── facade/
│   └── dto/
├── domain/
│   ├── aggregate/
│   ├── entity/
│   ├── value-object/
│   ├── repository/
│   ├── service/
│   ├── event/
│   └── policy/
└── infrastructure/
    ├── persistence/
    ├── mq/
    ├── bank-client/
    ├── settlement/
    └── config/
```

**典型依赖**
- 接收 `payment` 回调结果
- 接收 `order` / `gift` 的入账指令
- 接收 `audit` 的风控结果

**强约束**
- 必须有幂等键
- 必须有可回放流水
- 必须保留冻结/解冻/冲正能力

---

### 5.7 chat 模块

**职责**
- 会话、消息、回执、已读游标、离线消息
- 消息敏感词/审核接入
- 私聊、群聊、系统消息的统一消息模型

**不做什么**
- 不负责连接层
- 不负责用户登录
- 不负责交易编排

**目录建议**
```text
modules/chat/
├── interface/
│   ├── http/
│   ├── mq/
│   └── query-api/
├── application/
│   ├── command/
│   ├── query/
│   ├── facade/
│   └── dto/
├── domain/
│   ├── aggregate/
│   ├── entity/
│   ├── repository/
│   ├── service/
│   ├── event/
│   └── policy/
└── infrastructure/
    ├── persistence/
    ├── mq/
    ├── cache/
    ├── content-filter/
    └── config/
```

**典型依赖**
- `ws-gateway` 做长连接投递
- `auth` 做鉴权
- `audit` 审核内容
- `relation` 判断好友、拉黑、互加关系

---

### 5.8 ws-gateway 模块

**职责**
- WebSocket 长连接接入
- 在线状态、心跳、路由、连接管理
- IM 消息投递入口
- ACK、重试、离线补偿的接入层

**边界**
- 只做连接层，不做消息业务编排
- 不落主业务状态

**目录建议**
```text
apps/ws-gateway/
├── interface/
│   ├── ws/
│   ├── http/
│   └── health/
├── application/
│   ├── connection/
│   ├── route/
│   ├── delivery/
│   └── dto/
├── domain/
│   ├── connection/
│   ├── route-policy/
│   └── event/
└── infrastructure/
    ├── registry/
    ├── cache/
    ├── mq/
    └── config/
```

**典型依赖**
- 依赖 `auth` 鉴权连接
- 依赖 `chat` 做消息投递和回执编排

> 如果人手有限，可以先把 `ws-gateway` 作为 `chat` 的一个独立运行入口，逻辑上拆分、物理上共仓。

---

### 5.9 gift 模块

**职责**
- 礼物目录、赠送单、分成规则快照
- 礼物赠送场景统一建模
- 礼物收益计算结果输出

**不做什么**
- 不直接做资金入账
- 不直接写钱包余额

**目录建议**
```text
modules/gift/
├── interface/
│   ├── http/
│   ├── mq/
│   └── query-api/
├── application/
│   ├── command/
│   ├── query/
│   ├── facade/
│   └── dto/
├── domain/
│   ├── aggregate/
│   ├── entity/
│   ├── repository/
│   ├── service/
│   ├── event/
│   └── policy/
└── infrastructure/
    ├── persistence/
    ├── mq/
    ├── cache/
    └── config/
```

**典型依赖**
- 调 `payment` 支付
- 调 `wallet-ledger` 记账
- 调 `user` 读取收礼对象信息

---

### 5.10 audit 模块

**职责**
- 人工审核、自动审核、复审、申诉处理
- 审核任务流、审核结果、规则命中
- 商品、卖家认证、消息、礼物等审核入口

**不做什么**
- 不直接修改业务主表状态
- 不承担具体业务流程

**目录建议**
```text
modules/audit/
├── interface/
│   ├── http/
│   ├── mq/
│   └── job/
├── application/
│   ├── command/
│   ├── query/
│   ├── facade/
│   └── dto/
├── domain/
│   ├── aggregate/
│   ├── entity/
│   ├── repository/
│   ├── service/
│   ├── event/
│   └── policy/
└── infrastructure/
    ├── persistence/
    ├── rule-engine/
    ├── mq/
    ├── workflow/
    └── config/
```

**典型依赖**
- 读取各模块的审核申请/快照
- 通过事件把结果发回 `user`、`product`、`chat`、`gift` 等模块

---

### 5.11 rank 模块

**职责**
- 榜单、热度、曝光分、综合排序、快照生成
- 提供推荐/排序读模型
- 读多写少，适合独立读模型

**不做什么**
- 不直接改业务实体
- 不承担主交易状态

**目录建议**
```text
modules/rank/
├── interface/
│   ├── http/
│   ├── mq/
│   └── query-api/
├── application/
│   ├── command/
│   ├── query/
│   ├── facade/
│   └── dto/
├── domain/
│   ├── aggregate/
│   ├── entity/
│   ├── service/
│   ├── repository/
│   └── event/
└── infrastructure/
    ├── persistence/
    ├── cache/
    ├── scoring/
    └── config/
```

**典型依赖**
- 读取 `product`、`relation`、`user` 的行为数据
- 消费事件生成热度和榜单快照

---

### 5.12 relation 模块

**职责**
- 关注、好友、黑名单、互相关系、粉丝关系
- 关系链状态查询与写入
- 给 chat、product、rank 提供关系视图

**不做什么**
- 不承载用户主档
- 不承担消息发送

**目录建议**
```text
modules/relation/
├── interface/
│   ├── http/
│   ├── mq/
│   └── query-api/
├── application/
│   ├── command/
│   ├── query/
│   ├── facade/
│   └── dto/
├── domain/
│   ├── aggregate/
│   ├── entity/
│   ├── repository/
│   ├── service/
│   ├── event/
│   └── policy/
└── infrastructure/
    ├── persistence/
    ├── cache/
    ├── mq/
    └── config/
```

**典型依赖**
- 被 `chat` 查询拉黑/好友关系
- 被 `user` 用作关系视图
- 被 `rank` 用作推荐特征

---

### 5.13 admin 模块

**职责**
- 后台账号、角色、权限、审计日志、配置管理
- 运营配置、人工处理入口、后台操作留痕
- 对业务模块提供管理操作，不直接替代业务模块

**不做什么**
- 不直接写业务主状态
- 不绕过业务模块规则

**目录建议**
```text
modules/admin/
├── interface/
│   ├── http/
│   ├── mq/
│   └── job/
├── application/
│   ├── command/
│   ├── query/
│   ├── facade/
│   └── dto/
├── domain/
│   ├── aggregate/
│   ├── entity/
│   ├── repository/
│   ├── service/
│   ├── event/
│   └── policy/
└── infrastructure/
    ├── persistence/
    ├── permission/
    ├── audit-log/
    └── config/
```

**典型依赖**
- 可读取 `audit`、`user`、`product`、`order`、`payment` 的管理视图
- 负责后台审计日志和权限校验

---

## 6. 共享基础设施层建议

共享层要控制范围，不要变成“万能工具箱”。建议只放真正通用、稳定、跨模块复用的东西。

### 6.1 `shared/kernel`

放领域共性基础类型：
- `Entity` / `AggregateRoot`
- `ValueObject`
- `DomainEvent`
- `Repository` 接口基类
- `Result` / `PageResult`
- `ErrorCode`
- `BusinessException`
- `IdGenerator`
- `Clock`
- `Tenant` / `Operator` / `TraceContext`

### 6.2 `shared/infra`

放技术基础设施：
- 数据库连接、事务管理
- ORM / Mapper 基础封装
- Redis / 缓存封装
- 消息队列生产消费封装
- 分布式锁
- 幂等键处理
- 对象存储
- 文件上传下载
- 第三方 HTTP Client
- 配置中心接入
- 任务调度
- 本地/测试环境桩

### 6.3 `shared/contracts`

放稳定的跨模块契约：
- DTO 定义
- Command / Query 定义
- Event Schema
- 枚举值
- 错误码映射
- 外部集成协议对象

**原则**：这里放“契约”，不放“业务实现”。

### 6.4 `shared/events`

放统一事件总线相关能力：
- Outbox 模型
- Event Publisher
- Event Consumer 基类
- 去重表
- 重试策略
- 死信处理

### 6.5 `shared/observability`

放可观测性能力：
- 日志格式规范
- Trace / Span 封装
- 指标埋点
- 审计上下文
- 慢查询记录
- 失败告警钩子

### 6.6 `shared/security`

放安全基础能力：
- token 工具
- 密码哈希
- 敏感字段脱敏
- 签名验签
- 请求重放防护
- 风控上下文

### 6.7 `shared/job`

放定时任务基础设施：
- 任务注册
- 分片执行
- 锁控制
- 重试
- 失败告警

---

## 7. 推荐的依赖方向

### 7.1 模块间依赖建议

建议按下面方向设计：
- `auth -> user`
- `user -> relation, audit`
- `product -> user, audit, rank, relation`
- `order -> product, payment, wallet-ledger, audit`
- `payment -> wallet-ledger`
- `wallet-ledger -> audit`
- `chat -> auth, relation, audit`
- `ws-gateway -> auth, chat`
- `gift -> user, payment, wallet-ledger, audit`
- `rank -> product, user, relation`
- `relation -> user`
- `admin -> audit, user, product, order, payment, wallet-ledger`

### 7.2 禁止的依赖

禁止：
- 直接跨模块引用对方 ORM 实体
- 直接跨模块调用对方 repository
- 业务模块直接依赖另一个业务模块的数据库表结构
- `payment` 绕过 `wallet-ledger` 直接改钱
- `admin` 直接写业务主表状态

---

## 8. 事件与消息建议

建议从第一版就统一事件模式：
- 领域事件只在模块内生成
- 集成事件用于跨模块通信
- 消费端必须幂等
- 必须支持重放
- 关键链路必须有 `event_no` / `request_id` / `idempotency_key`

### 8.1 建议事件流

- `product_published` -> `audit` / `rank`
- `audit_approved` -> `product` / `user`
- `order_created` -> `payment`
- `payment_succeeded` -> `order` / `wallet-ledger`
- `ledger_posted` -> `order` / `gift`
- `chat_message_sent` -> `ws-gateway`
- `relation_changed` -> `chat` / `rank`

---

## 9. 第一版落地优先级

建议按下面顺序开工：

1. `shared/kernel`、`shared/infra`、统一错误码、统一响应体
2. `auth`、`user`
3. `product`、`audit`
4. `order`、`payment`、`wallet-ledger`
5. `relation`、`chat`、`ws-gateway`
6. `gift`
7. `rank`
8. `admin`

### 9.1 原因

- 先把身份、商品、订单、资金链跑通
- 再接 IM 和关系链
- 最后补榜单和后台

---

## 10. 子 agent 开工建议

如果后续要拆给子 agent，建议按模块拆，不要按技术层拆。

### 10.1 适合单独派发的任务包
- `auth` 任务包
- `user` 任务包
- `product` 任务包
- `order + payment + wallet-ledger` 财务交易任务包
- `chat + ws-gateway` IM 任务包
- `audit` 任务包
- `relation + rank` 推荐关系任务包
- `admin` 任务包

### 10.2 每个任务包必须交付
- 模块目录
- 核心实体/聚合
- 应用服务接口
- 仓储接口
- 事件定义
- 测试骨架
- 依赖清单

---

## 11. 最终建议

这版代码结构的核心判断是：

- **模块化单体优先**，先把边界和依赖钉死
- **wallet-ledger、ws-gateway、auth** 是最容易独立演进的高优先级模块
- **order/payment/wallet-ledger** 必须作为强一致链路设计
- **shared** 只放真正复用的稳定能力，避免污染业务模块
- **跨模块通信尽量事件化、契约化、幂等化**

如果后续需要，我可以继续补：
- 每个模块的类图草案
- 每个模块的接口清单
- `shared/contracts` 的 DTO / Event 命名规范
- 一版可直接建仓的目录树清单
