# 项目骨架与目录结构草案（V1）

适用范围：二手闲置社交交易平台 MVP 第一版。

目标：先冻结目录边界，再让前后端、后台、IM、财务、文档、测试各自按目录开工，避免后续反复搬家。

## 一、推荐仓库总结构

```text
二手闲置社交交易平台/
├── apps/
│   ├── web/                     # 前台用户端（买家/卖家）
│   ├── admin/                   # 后台管理端
│   ├── im/                      # IM 前端或 IM 专用页面/组件
│   └── docs-site/               # 文档站点（如后续需要）
├── services/
│   ├── auth-service/            # 登录、注册、Token、验证码
│   ├── user-service/            # 用户资料、身份、关注关系
│   ├── goods-service/           # 商品发布、列表、详情、上下架
│   ├── order-service/           # 下单、订单状态流转、订单快照
│   ├── payment-service/         # 支付、充值、退款、回调
│   ├── wallet-service/          # 钱包、账本、冻结、解冻、流水
│   ├── chat-service/            # IM 会话、消息、游标、ACK
│   ├── gift-service/            # 礼物、赠送、收礼、分账
│   ├── audit-service/           # 审核任务、审核记录、内容审核
│   └── admin-service/           # 后台权限、审核调度、配置管理
├── packages/
│   ├── shared-types/             # 前后端共享类型、DTO、枚举
│   ├── shared-utils/             # 通用工具、校验、日期、加密封装
│   ├── shared-ui/                # 通用 UI 组件
│   └── sdk/                      # 接口 SDK、API Client
├── infra/
│   ├── docker/                   # 容器化配置
│   ├── k8s/                      # 部署编排（如后续需要）
│   ├── nginx/                    # 反向代理、静态资源配置
│   └── scripts/                  # 启动、初始化、迁移脚本
├── db/
│   ├── migrations/               # 数据库迁移脚本
│   ├── seeds/                    # 初始数据、字典数据
│   └── schema/                   # 核心表结构草案或导出文件
├── docs/
│   ├── 00-execution-plan.md
│   ├── 01-project-structure-v1.md
│   ├── im-protocol.md
│   ├── finance-ledger.md
│   ├── service-boundaries.md
│   └── database-design.md
├── tests/
│   ├── unit/                     # 单元测试
│   ├── integration/              # 集成测试
│   ├── e2e/                      # 端到端测试
│   └── fixtures/                 # 测试数据、Mock 数据
├── tools/
│   ├── codegen/                  # 代码生成
│   ├── lint/                     # 规范检查脚本
│   └── verify/                   # 校验脚本、冻结检查
├── .env.example
├── README.md
└── package.json / pnpm-workspace.yaml / turbo.json（按技术栈选用）
```

## 二、目录职责划分

### 1）前端：`apps/web/`
面向买家、卖家使用的主站。

建议拆分：

```text
apps/web/
├── src/
│   ├── pages/ 或 app/           # 路由入口
│   ├── components/              # 页面组件
│   ├── features/                # 按业务域拆分：商品、订单、聊天、钱包
│   ├── hooks/                   # 复用 Hook
│   ├── services/                # 前端 API 调用
│   ├── store/                   # 状态管理
│   ├── styles/                  # 样式
│   └── utils/                   # 本地工具
└── tests/
```

### 2）后台：`apps/admin/`
后台管理端，只服务审核、配置、运营动作。

建议拆分：

```text
apps/admin/
├── src/
│   ├── pages/ 或 app/
│   ├── components/
│   ├── features/                # 审核、配置、用户管理、商品审核、提现审核
│   ├── services/
│   ├── store/
│   └── utils/
└── tests/
```

### 3）IM：`apps/im/` + `services/chat-service/`
IM 建议前后端分开看：

- `apps/im/`：IM 页面、会话列表、消息列表、输入框、上传组件
- `services/chat-service/`：会话、消息、ACK、游标、离线补拉、审核接入

IM 子系统目录建议：

```text
services/chat-service/
├── src/
│   ├── api/
│   ├── domain/
│   ├── application/
│   ├── infrastructure/
│   ├── websocket/
│   ├── repository/
│   └── tests/
```

### 4）财务：`services/payment-service/` + `services/wallet-service/`
财务域必须单独成块，不和订单、商品混写。

- `payment-service/`：充值、支付、退款、渠道回调、对账入口
- `wallet-service/`：余额、流水、冻结、解冻、账本、分录

建议目录：

```text
services/wallet-service/
├── src/
│   ├── domain/
│   ├── application/
│   ├── infrastructure/
│   ├── repository/
│   └── tests/
```

### 5）文档：`docs/`
文档只放冻结内容和执行基线，不放临时讨论稿。

首期文档建议固定为：

- `00-execution-plan.md`：总执行计划
- `01-project-structure-v1.md`：目录结构草案
- `service-boundaries.md`：服务边界冻结
- `database-design.md`：数据库设计冻结
- `im-protocol.md`：IM 协议冻结
- `finance-ledger.md`：财务账本协议冻结

### 6）测试：`tests/`
测试不要散落到各处，统一收口，便于自动化执行。

建议按层次划分：

- `tests/unit/`：纯函数、领域规则、状态机
- `tests/integration/`：服务间接口、数据库、消息流转
- `tests/e2e/`：关键主流程
- `tests/fixtures/`：Mock 数据、样例 payload、初始化数据

## 三、首期必须存在的目录

以下目录属于 **MVP 第一阶段必须存在**，建议先建空目录也要先占位：

### 必须存在
- `apps/web/`
- `apps/admin/`
- `services/auth-service/`
- `services/user-service/`
- `services/goods-service/`
- `services/order-service/`
- `services/payment-service/`
- `services/wallet-service/`
- `services/chat-service/`
- `services/gift-service/`
- `services/audit-service/`
- `services/admin-service/`
- `packages/shared-types/`
- `packages/shared-utils/`
- `docs/`
- `tests/`
- `infra/scripts/`
- `db/schema/`

### 建议首期就存在，但可先空置
- `apps/im/`
- `packages/shared-ui/`
- `packages/sdk/`
- `db/migrations/`
- `db/seeds/`
- `tests/unit/`
- `tests/integration/`
- `tests/e2e/`
- `tests/fixtures/`

### 可后置
- `apps/docs-site/`
- `infra/k8s/`
- `infra/nginx/`
- `tools/codegen/`
- `tools/verify/`

## 四、首期冻结原则

1. **前后端分离，但共享类型集中管理。**
2. **后台单独目录，不混入前台代码。**
3. **IM 的前端和服务端分目录，不要放在一个大目录里。**
4. **财务必须独立成域，禁止和订单/商品逻辑混写。**
5. **文档只放冻结后的基线，不放实现草稿。**
6. **测试目录必须预留，避免后面补测试时结构失控。**
7. **跨域共享内容优先放 `packages/`，不要复制粘贴。**

## 五、建议的落地顺序

1. 先建顶层目录：`apps/`、`services/`、`packages/`、`docs/`、`tests/`、`infra/`、`db/`。
2. 再补 MVP 必须存在的服务目录。
3. 再补共享包和测试目录。
4. 最后补各目录下的 `README.md` 或占位文件，防止空目录丢失。

## 六、执行建议

如果现在要直接落到桌面项目文件夹，建议按这个顺序创建：

- 顶层目录先冻结
- 服务目录先建空壳
- 文档目录补齐冻结文件
- 测试目录先放占位结构
- 再由各子 agent 进入各自目录实现

这个版本的目录结构原则很简单：**先按业务域切开，再在域内按实现层次拆分。**
