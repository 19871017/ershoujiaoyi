# 服务边界与基础服务骨架规划草案

> 适用阶段：MVP 冻结后、基础架构落地前
>
> 目标：先把服务边界、依赖方向、公共能力和目录骨架定死，后续子 agent 按边界并行实现，避免重复建表、重复造协议、重复写状态机。
>
> 约束前提：
> - MVP 范围、IM 协议、财务协议已冻结。
> - 钱相关路径必须账本优先。
> - 所有跨服务写入必须有幂等键。
> - 所有状态变更必须有单一归属服务。

---

## 1. 总体落地策略

### 1.1 推荐路线

**第一阶段：模块化单体优先。**

原因很直接：
- 当前核心边界已经冻结，但团队还在早期，先拆物理服务会带来过多部署、联调、鉴权、消息治理成本。
- 订单、支付、钱包、IM、审核之间依赖密，早期最容易踩踏的是表、事件和状态机，不是代码语言。
- 先用模块化单体把领域边界、接口、目录和依赖固定下来，后续可以按模块平滑拆服务。

**第二阶段：按风险和吞吐拆独立服务。**
- 先拆财务链路、IM 长连接、认证鉴权这种高耦合/高并发/高风险模块。
- 再拆用户、商品、订单、审核、后台这种典型业务模块。
- 最后再考虑推荐、榜单、关系链等读多写少模块。

### 1.2 不建议一开始就全微服务

不建议首期就把下面这些全部做成独立服务：
- auth-service
- user-service
- product-service
- order-service
- payment-service
- wallet-ledger-service
- chat-service
- ws-gateway
- gift-service
- audit-service
- rank-service
- relation-service
- admin-service

原因不是“以后不能拆”，而是“首期物理拆分收益不够，代价太高”。

首期应该是：
- **逻辑独立，物理合并**
- **模块独立，仓库统一**
- **接口先行，数据库归属明确**

---

## 2. 服务拆分建议

下面按“是否建议独立部署”来定边界。

### 2.1 必须优先独立的边界

#### 2.1.1 auth-service

**职责**
- 登录、注册、验证码、密码、第三方绑定
- token 签发、刷新、吊销
- 会话管理、设备管理、登录风控入口
- 账号禁用、冻结、踢下线

**边界**
- 只管“身份校验”和“会话生命周期”
- 不承载用户资料、商品、订单、钱包逻辑

**依赖**
- 依赖 user-service 的账号基础资料读接口
- 依赖公共风控能力、短信/邮件能力
- 写 auth_session、auth_credential

**建议形态**
- 第一阶段：模块化单体内独立模块
- 第二阶段：独立服务

---

#### 2.1.2 wallet-ledger-service

**职责**
- 账户总账、分户账、流水、冻结、解冻、冲正
- 资金归集、分润、提现申请/审核/打款状态
- 所有金额变更必须账本化

**边界**
- 不直接承担业务“下单成功”的判断
- 不直接负责商品或聊天状态
- 只对外提供“记账”和“查账”能力

**依赖**
- 依赖 payment-service 的支付回调结果
- 依赖 order-service / gift-service 的业务入账指令
- 依赖 audit-service 的风控结果

**建议形态**
- **尽早独立**，可作为首批物理拆分候选
- 原因：资金风险最高，且最需要单独幂等、审计和回放

---

#### 2.1.3 ws-gateway

**职责**
- WebSocket 长连接接入
- 在线状态、心跳、路由、连接管理
- IM 消息投递入口
- ACK、重试、离线补偿的接入层

**边界**
- 只做连接层，不做消息业务编排
- 不落消息主业务状态

**依赖**
- 依赖 chat-service 的消息存储和投递策略
- 依赖 auth-service 做连接鉴权

**建议形态**
- 第一阶段可独立部署，也可与 chat-service 逻辑拆分
- 如果团队人少，先逻辑拆分，后物理拆分

---

### 2.2 建议先做模块化单体、后续再拆的边界

#### 2.2.1 user-service

**职责**
- 用户主档、资料、标签、身份状态
- 用户认证结果展示
- 用户可见资料与隐私配置

**边界**
- 不负责登录校验，不负责交易
- 不管理会话
- 不承载风控判定

**依赖**
- 依赖 auth-service 提供登录态
- 依赖 audit-service 提供认证审核结果
- 依赖 relation-service 提供关注/好友关系读写

---

#### 2.2.2 product-service

**职责**
- 商品发布、编辑、上下架、审核快照
- 商品图片、商品快照、商品状态机
- 商品曝光读模型

**边界**
- 不负责订单支付
- 不负责聊天消息
- 不直接改钱包

**依赖**
- 依赖 audit-service 做商品审核
- 依赖 user-service 读取卖家身份/标签
- 依赖 relation-service / rank-service 提供部分推荐读数据

---

#### 2.2.3 order-service

**职责**
- 订单创建、锁单、取消、完成、售后状态机
- 商品单、礼物单的统一订单骨架
- 订单快照、状态流水、幂等控制

**边界**
- 不直接扣款
- 不直接记账
- 不负责商品库存（闲置场景一般无传统库存）

**依赖**
- 依赖 product-service 读取商品快照
- 依赖 payment-service 发起支付单
- 依赖 wallet-ledger-service 完成资金入账
- 依赖 audit-service 做风控/异常单处理

---

#### 2.2.4 payment-service

**职责**
- 第三方支付渠道接入
- 支付单管理、回调处理、对账、退款指令编排
- 外部支付状态与内部业务状态映射

**边界**
- 不保存业务余额
- 不做业务账本
- 不直接改变订单最终态

**依赖**
- 依赖 wallet-ledger-service 写账
- 依赖 order-service / gift-service 提供支付意图

---

#### 2.2.5 chat-service

**职责**
- 会话、消息、回执、已读游标、离线消息
- 消息敏感词/审核接入
- 群聊/私聊/系统通知的消息模型

**边界**
- 不负责连接层
- 不负责用户登录
- 不负责业务交易编排

**依赖**
- 依赖 ws-gateway 进行长连接投递
- 依赖 auth-service 鉴权
- 依赖 audit-service 审核消息内容
- 依赖 relation-service 判断好友/拉黑/互加关系

---

#### 2.2.6 gift-service

**职责**
- 礼物目录、赠送单、分成规则快照
- 礼物赠送场景统一建模
- 礼物收益计算结果输出

**边界**
- 不直接做资金入账
- 不直接写钱包余额
- 不负责礼物展示页面以外的通用交易逻辑

**依赖**
- 依赖 wallet-ledger-service 记账
- 依赖 payment-service 支付
- 依赖 user-service 读取收礼对象信息

---

#### 2.2.7 audit-service

**职责**
- 人工审核、自动审核、复审、申诉处理
- 审核任务流、审核结果、规则命中
- 商品、卖家认证、消息、礼物等审核入口

**边界**
- 不直接修改业务主表状态，最多发出审核结果事件
- 不承担具体业务流程

**依赖**
- 依赖规则引擎/风控能力
- 向 user-service、product-service、chat-service、gift-service 提供审核结果

---

#### 2.2.8 rank-service

**职责**
- 榜单计算、缓存、快照、周期榜
- 魅力值、人气值、积分榜等排行读模型

**边界**
- 只做读模型和定时计算
- 不参与交易主链路

**依赖**
- 依赖 wallet-ledger-service、gift-service、order-service 的事件
- 依赖 user-service 提供用户基础信息

---

#### 2.2.9 relation-service

**职责**
- 关注、好友、拉黑、陌生人关系、互动关系
- 社交关系读写与状态机

**边界**
- 不处理消息本体
- 不处理订单或支付

**依赖**
- 依赖 auth-service 鉴权
- 为 chat-service、rank-service、product-service 提供关系判断

---

#### 2.2.10 admin-service

**职责**
- 后台账号、角色、权限、审计日志
- 运营配置、内容配置、审核后台、风控处置入口
- 人工审核工作台统一入口

**边界**
- 不存业务主数据
- 不直接写核心业务状态，只通过受控接口操作

**依赖**
- 依赖 audit-service、user-service、product-service、order-service、wallet-ledger-service 的后台能力接口

---

### 2.3 服务独立优先级建议

按“先拆谁”排序：

1. **wallet-ledger-service**：资金风险最高，必须最先边界清楚
2. **auth-service**：统一登录、会话、鉴权
3. **ws-gateway**：长连接接入层
4. **chat-service**：IM 业务与消息状态
5. **audit-service**：审核流和风控入口
6. **payment-service**：第三方支付接入
7. **order-service**：交易编排主线
8. **product-service**：商品域
9. **user-service**：用户域
10. **gift-service**：可作为交易附属域
11. **rank-service**：读模型服务
12. **relation-service**：社交关系服务
13. **admin-service**：后台能力服务

---

## 3. 依赖关系原则

### 3.1 依赖方向

统一采用：**入口层 -> 应用层 -> 领域层 -> 基础设施层**。

服务间依赖只允许单向，不允许互相直接调用领域内部实现。

### 3.2 推荐依赖图

- auth-service -> user-service（读基础资料）
- user-service -> audit-service（认证结果回写/查询）
- product-service -> user-service、audit-service
- order-service -> product-service、payment-service、wallet-ledger-service、audit-service
- payment-service -> wallet-ledger-service
- gift-service -> payment-service、wallet-ledger-service、user-service
- chat-service -> ws-gateway、auth-service、relation-service、audit-service
- rank-service -> wallet-ledger-service、gift-service、order-service、user-service
- relation-service -> auth-service、user-service
- admin-service -> audit-service、user-service、product-service、order-service、wallet-ledger-service

### 3.3 禁止依赖

下面这些依赖要明确禁止：
- product-service 直接改 wallet-ledger-service
- chat-service 直接改 order-service
- admin-service 直接写核心业务表
- payment-service 直接改订单最终态
- rank-service 反向依赖交易主链路
- 任意服务绕过审核直接改“已审核/已通过”类状态

---

## 4. 公共能力层

下面这些能力不属于任何一个业务服务，应抽成公共基础能力，避免重复建设。

### 4.1 必须共享的基础能力

- **统一鉴权**：token 校验、权限校验、登录态识别
- **统一幂等**：幂等键生成、幂等结果缓存、重复请求识别
- **统一事件总线**：领域事件发布/订阅、失败重试、死信处理
- **统一审计日志**：谁在什么时间对什么对象做了什么操作
- **统一配置中心**：活动费率、审核规则、开关配置
- **统一文件/媒体能力**：图片上传、裁剪、压缩、审核标记、CDN 地址管理
- **统一通知能力**：短信、站内信、系统消息、推送
- **统一搜索索引适配层**：商品、用户、榜单、关系读模型的索引同步
- **统一监控与追踪**：日志、指标、链路追踪、告警
- **统一错误码体系**：全局错误码、业务错误码、回调错误码

### 4.2 不建议公共化的内容

以下内容不要急着抽公共库，否则会把边界搞糊：
- 各业务自己的状态机
- 各业务自己的请求 DTO
- 各业务自己的查询聚合
- 各业务自己的审核规则

公共库只放“基础设施级别”的东西，不放业务规则。

---

## 5. 首批需要冻结的目录结构

以下目录结构适合作为首批骨架，适用于模块化单体，也适用于后续拆分。

```text
repo/
├── docs/
│   ├── service-boundaries.md
│   ├── database-design.md
│   ├── im-protocol.md
│   ├── financial-protocol.md
│   └── api-contracts.md
├── apps/
│   ├── api-gateway/
│   ├── admin-web/
│   └── web-app/
├── services/
│   ├── auth-service/
│   ├── user-service/
│   ├── product-service/
│   ├── order-service/
│   ├── payment-service/
│   ├── wallet-ledger-service/
│   ├── chat-service/
│   ├── ws-gateway/
│   ├── gift-service/
│   ├── audit-service/
│   ├── rank-service/
│   ├── relation-service/
│   └── admin-service/
├── modules/
│   ├── common-auth/
│   ├── common-idempotency/
│   ├── common-audit/
│   ├── common-eventbus/
│   ├── common-storage/
│   ├── common-notify/
│   ├── common-observability/
│   └── common-error-codes/
├── contracts/
│   ├── events/
│   ├── dto/
│   └── enums/
├── infra/
│   ├── db/
│   ├── mq/
│   ├── redis/
│   ├── object-storage/
│   └── observability/
└── scripts/
    ├── migrate/
    ├── seed/
    └── ops/
```

---

## 6. 首批模块清单

### 6.1 每个业务服务建议统一拆成这些内部模块

每个服务初期都尽量保持一致的内部结构：

```text
service-x/
├── api/           # 对外接口层：controller、handler、rpc
├── application/   # 用例编排、事务边界、DTO 转换
├── domain/        # 实体、聚合、领域服务、状态机
├── repository/    # 仓储接口与实现
├── infrastructure/# mq、cache、第三方客户端、持久化适配
├── jobs/          # 定时任务、补偿任务、对账任务
├── events/        # 领域事件定义与发布
└── tests/         # 单测、集成测试
```

### 6.2 第一批要先补齐的模块

建议首批只做下面这些“骨架模块”，不要一口气铺全业务：

- `auth-service`
  - 登录/注册
  - session/token
  - credential
- `user-service`
  - account
  - profile
  - identity
  - tag
- `product-service`
  - spu
  - image
  - snapshot
  - audit record
- `order-service`
  - trade order
  - order state machine
  - order snapshot
  - idempotency
- `payment-service`
  - payment intent
  - channel adapter
  - callback handler
  - reconciliation
- `wallet-ledger-service`
  - account
  - ledger entry
  - freeze/unfreeze
  - settlement
- `chat-service`
  - conversation
  - message
  - receipt
  - unread cursor
- `ws-gateway`
  - connect auth
  - connection registry
  - push routing
- `gift-service`
  - gift catalog
  - gift order
  - split snapshot
- `audit-service`
  - audit task
  - audit decision
  - review queue
- `rank-service`
  - rank snapshot
  - rank job
- `relation-service`
  - follow
  - friend
  - block
- `admin-service`
  - role
  - permission
  - audit log
  - config

---

## 7. 第一批必须先定的接口和契约

为了让后续子 agent 并行不打架，下面这些接口必须先冻结：

### 7.1 认证与会话
- 登录/登出
- token 刷新
- 会话吊销
- 设备列表

### 7.2 用户主档
- 获取当前用户
- 获取公开资料
- 更新资料
- 更新身份标签

### 7.3 商品
- 发布商品
- 编辑商品
- 上下架
- 获取商品快照
- 商品审核结果回写

### 7.4 订单
- 创建订单
- 锁单/取消
- 支付中/已支付/已完成/已关闭
- 订单状态流水查询

### 7.5 支付与账本
- 创建支付意图
- 支付回调
- 记账入账
- 冻结/解冻
- 对账与冲正

### 7.6 IM
- 会话创建
- 消息发送
- 回执确认
- 未读游标同步
- 离线补偿

### 7.7 审核
- 提交审核
- 审核通过/拒绝
- 人工复审
- 审核结果查询

### 7.8 后台
- 账号权限
- 审核工作台
- 配置管理
- 审计日志查询

---

## 8. 推荐的实施顺序

### 阶段 A：先冻结边界

产出物：
- 服务边界图
- 依赖图
- 事件清单
- 状态机清单
- 目录骨架
- 核心接口草案

### 阶段 B：先实现公共底座

优先做：
- auth
- idempotency
- audit log
- event bus
- config
- storage
- observability
- error code

### 阶段 C：先做交易主链路

建议顺序：
1. user-service
2. auth-service
3. product-service
4. order-service
5. payment-service
6. wallet-ledger-service
7. audit-service

### 阶段 D：再做社交和增长模块

- chat-service
- ws-gateway
- gift-service
- relation-service
- rank-service
- admin-service

---

## 9. 最小可执行边界结论

如果现在就要定一句话版本：

- **平台首期采用“模块化单体 + 清晰领域边界”落地。**
- **wallet-ledger-service、auth-service、ws-gateway 是最优先边界化的模块。**
- **订单、支付、账本、审核必须按单向依赖设计，禁止交叉写主表。**
- **所有跨服务写操作先过契约、幂等和事件，不允许直接跨库乱写。**
- **先把目录、接口、状态机、事件冻结，再让子 agent 分模块并行实现。**
