# IM 协议草案（冻结版）

> 适用范围：二手闲置社交交易平台 MVP 阶段的 IM 子系统。
>
> 冻结目标：让后续多个子 agent 统一遵守同一套消息协议、状态机、游标、ACK、离线补拉、去重、存储分工与前端约束。
>
> 强制原则：
> - 消息协议先冻结，再写实现。
> - 页面不直接管理 socket。
> - 媒体消息只传 URL 元信息，不传二进制内容。
> - 未读数必须由服务端游标与消息序号推导，不允许前端自行累加为准。

## 1. 设计目标

IM 子系统在 MVP 中只做以下事情：

- 支持单聊会话。
- 支持文本消息与图片消息。
- 支持发送 ACK、离线补拉、去重、已读游标。
- 支持基础审核接入。
- 支持会话列表、消息列表、未读数。

IM 子系统不做以下事情：

- 不做群聊。
- 不做音视频。
- 不做撤回、编辑、转发、引用回复。
- 不做 typing、在线状态展示、已读回执以外的复杂协作能力。
- 不做页面层直连 socket 管理。
- 不把图片二进制、视频二进制、文件二进制塞进消息体。

## 2. 核心术语

### 2.1 会话 Conversation

会话是两个用户之间的单聊容器，唯一标识为 `conversation_id`。

- 单聊会话采用稳定 ID，保证同一对用户永远映射到同一会话。
- 会话主数据归 `chat-service` 管理。
- 会话列表、最后一条消息摘要、最后活跃时间、双方游标都从会话维度维护。

### 2.2 消息 Message

消息是会话中的一次业务事件，拥有全局唯一的 `message_id`，同时拥有会话内单调递增的 `server_seq`。

- `message_id`：全局唯一 ID，服务端生成。
- `client_msg_id`：客户端幂等 ID，由客户端生成。
- `server_seq`：会话内顺序号，由服务端分配，只能递增，不能回退。

### 2.3 游标 Cursor

游标用于同步、未读与补拉。

冻结两个游标：

- `sync_cursor`：客户端已成功拉取并落地的最大 `server_seq`。
- `read_cursor`：用户已实际读到的最大 `server_seq`。

定义：

- `sync_cursor <= read_cursor` 不成立时，说明客户端只收到未读内容但尚未进入已读状态。
- 服务端推导未读数时，只认 `read_cursor`。
- 离线补拉只认 `sync_cursor`。

## 3. 消息协议

### 3.1 基础字段

所有消息在服务端持久化时必须包含以下字段。

#### 必填字段

- `message_id`：服务端全局唯一 ID。
- `conversation_id`：会话 ID。
- `server_seq`：会话内顺序号。
- `sender_id`：发送者用户 ID。
- `receiver_id`：接收者用户 ID。
- `client_msg_id`：客户端幂等 ID。
- `msg_type`：消息类型。
- `content`：消息内容体。
- `send_state`：发送状态。
- `audit_state`：审核状态。
- `created_at`：服务端创建时间。
- `server_ts`：服务端业务时间戳。

#### 可选字段

- `media_meta`：媒体元信息，仅在图片消息中出现。
- `client_ts`：客户端发起时间，仅用于排障与排序辅助，不作为最终顺序依据。
- `read_at`：对方已读时间。
- `delivered_at`：对方设备送达时间。
- `ext`：扩展字段，必须是可忽略结构，禁止承载核心协议语义。

### 3.2 消息类型 msg_type

MVP 仅允许以下类型：

- `text`：纯文本消息。
- `image`：图片消息。

禁止在第一批实现中加入其他类型，除非文档先行更新并完成冻结。

### 3.3 content 结构

#### 3.3.1 文本消息

```json
{
  "text": "你好，想问一下还在吗？"
}
```

约束：

- 文本内容必须经过长度限制。
- 文本内容必须经过审核接入。
- 文本内容不得承载结构化业务指令。

#### 3.3.2 图片消息

```json
{
  "text": "",
  "media_meta": {
    "url": "https://cdn.example.com/im/2026/05/abc.jpg",
    "mime_type": "image/jpeg",
    "width": 1080,
    "height": 1440,
    "size_bytes": 345678,
    "sha256": "可选",
    "preview_url": "可选"
  }
}
```

强制约束：

- 只传 URL 与元信息，不传图片二进制。
- 消息协议只负责引用文件，不负责上传流程本身。
- `url` 必须是可直接访问或可通过统一鉴权访问的资源地址。
- `mime_type` 必须是图片类型。
- 前端渲染仅依据元信息，不得再把图片内容塞回消息体。

### 3.4 发送状态 send_state

`send_state` 是发送链路状态，不等于审核状态，不等于已读状态。

冻结状态：

- `local_pending`：客户端已生成消息，未提交服务端。
- `sending`：已发往服务端，等待服务端处理。
- `sent`：服务端已受理并持久化。
- `failed`：本次发送失败，可重试。

说明：

- `sent` 只表示“服务端已接收并落库”，不表示对方已读。
- `failed` 必须保留 `client_msg_id` 供原样重试。
- 失败重试不得生成新的业务消息，只能复用原 `client_msg_id`。

### 3.5 审核状态 audit_state

`audit_state` 与发送状态独立存在。

冻结状态：

- `pending`：待审核。
- `passed`：审核通过。
- `rejected`：审核拒绝。
- `masked`：内容已隐藏，仅管理员可见。

规则：

- 发送成功不代表审核通过。
- 审核通过不回写为新的消息，仅更新同一条消息的审核状态。
- 如果审核拒绝，前端必须按统一规则展示，不允许自行猜测。

## 4. 消息状态机

### 4.1 发送侧状态机

发送状态流转必须遵守以下顺序：

`local_pending -> sending -> sent`

异常流转：

`local_pending -> failed`

`sending -> failed`

约束：

- `sent` 后不得回退到 `sending`。
- `failed` 只允许通过“重试发送”重新进入 `sending`。
- 重试必须复用原 `client_msg_id`。

### 4.2 服务端处理状态机

服务端内部处理可以拆为：

- `received`：收到请求。
- `dedup_checked`：完成幂等校验。
- `persisted`：完成入库并分配 `server_seq`。
- `dispatched`：完成投递或投递队列入列。
- `ack_ready`：可返回发送 ACK。

约束：

- 对客户端返回 ACK 的前提是消息已完成持久化。
- 不能先 ACK 后落库。
- 不能先给出最终 `server_seq` 再事后补写数据库。

### 4.3 读状态

读状态不混入发送状态。

冻结规则：

- 已读只通过 `read_cursor` 体现。
- 已读不要求逐条更新为“已读”。
- 会话中的已读判定以 `server_seq <= read_cursor` 为准。

## 5. ACK 规则

### 5.1 发送 ACK（send_ack）

发送 ACK 是服务端对客户端发送请求的确认。

返回条件：

- 服务端已完成幂等检查。
- 服务端已持久化消息。
- 服务端已分配 `message_id` 与 `server_seq`。

返回内容必须至少包含：

- `message_id`
- `conversation_id`
- `server_seq`
- `client_msg_id`
- `send_state=sent`
- `server_ts`

语义约束：

- send_ack 只代表“服务端已收”。
- send_ack 不代表对方已收。
- send_ack 不代表对方已读。

### 5.2 送达 ACK（delivery_ack）

送达 ACK 表示接收方设备已收到该消息并完成本地展示。

是否纳入第一批：

- 第一批可实现，但不是强制用户可见能力。
- 如果实现，必须独立于已读状态。

语义：

- 送达 ACK 不是已读 ACK。
- 送达 ACK 只能向服务端回写 `delivered_at` 或等价字段。

### 5.3 已读 ACK（read_ack）

已读 ACK 由接收方在会话打开、消息内容进入可见区域后触发。

规则：

- 已读 ACK 不按条逐个频繁上报，优先上报最大 `read_cursor`。
- 服务端以“最大连续已读消息序号”更新。
- read_ack 只能单调递增，不能回退。

返回内容：

- `conversation_id`
- `read_cursor`
- `read_at`

约束：

- read_ack 不能伪装成发送 ACK。
- read_ack 不能由前端拍脑袋写本地状态后直接当真。
- 服务端是最终裁决者。

## 6. 离线补拉协议

### 6.1 补拉触发时机

客户端在以下场景必须触发离线补拉：

- 登录成功后。
- WebSocket / 长连接重连后。
- 进入会话详情页后。
- 前台恢复、网络恢复后。
- 本地 `sync_cursor` 落后服务端 `server_seq` 时。

### 6.2 补拉请求参数

补拉只允许使用游标驱动，不允许用“页码 + 偏移”替代核心同步逻辑。

请求参数建议如下：

- `conversation_id`
- `sync_cursor`
- `limit`
- `direction=forward`

说明：

- `sync_cursor` 表示客户端已确认落地的最大序号。
- 服务端只返回 `server_seq > sync_cursor` 的消息。
- `limit` 仅用于分页控制，不是业务主语义。

### 6.3 补拉返回结构

返回内容必须包含：

- 消息列表
- `next_sync_cursor`
- `has_more`
- 当前会话最新 `server_seq`
- 会话级未读数

### 6.4 补拉规则

- 补拉结果按 `server_seq` 升序返回。
- 补拉必须支持断点续拉。
- 同一批消息重复拉取必须可被客户端幂等去重。
- 补拉返回与 socket 推送可以并存，但最终以 `server_seq` 去重合并。

## 7. 去重规则

### 7.1 客户端幂等 ID

客户端发消息前必须生成 `client_msg_id`。

规则：

- 同一次用户点击发送对应一个 `client_msg_id`。
- 发送失败重试必须复用同一个 `client_msg_id`。
- 不得因网络重试、页面刷新、长连接抖动而生成新的业务消息。

### 7.2 服务端幂等规则

服务端必须对以下组合做幂等保障：

- `sender_id + client_msg_id`

要求：

- 第一次请求：正常落库并返回 ACK。
- 重复请求：直接返回首次生成的消息记录与 ACK 信息。
- 不得插入第二条业务消息。

### 7.3 去重层级

去重必须同时覆盖三层：

- **客户端本地去重**：避免界面重复渲染。
- **Redis 预去重**：提升高并发重试下的幂等性能。
- **MySQL 唯一约束**：最终保证数据不重复。

推荐唯一键：

- `uniq_sender_client_msg_id(sender_id, client_msg_id)`

### 7.4 拉取去重

客户端收到推送与补拉结果同时包含同一条消息时：

- 以 `message_id` 或 `server_seq` 去重。
- 以 `server_seq` 作为会话内排序主依据。
- 不允许仅凭时间戳排序。

## 8. 未读数口径

### 8.1 会话未读数

单个会话的未读数定义为：

- 该会话中 `server_seq > read_cursor` 的、且发送者不是当前用户的消息数量。

冻结口径：

- 未读数由服务端计算。
- 前端显示未读数只能读取服务端结果。
- 前端本地自增只能作为临时 UI 状态，不得作为最终口径。

### 8.2 全局未读数

全局未读数为所有会话未读数之和。

规则：

- 全局未读数必须可从会话未读数汇总得到。
- 全局未读数与会话未读数必须一致，不允许两套算法。

### 8.3 已读与未读更新规则

- 当用户进入会话并触发 read_ack，服务端更新 `read_cursor`。
- `read_cursor` 更新后，未读数按新游标重新计算。
- 未读数不能因本地手动清零而改变服务端真值。

## 9. Redis / MySQL 分工

### 9.1 MySQL 职责

MySQL 是 IM 的**最终事实源**，保存以下内容：

- 消息主表。
- 会话主表。
- 会话成员表。
- 已读游标持久化。
- 送达记录或已读记录的持久化快照。
- 审核状态与审核结果引用。
- 会话最后消息摘要。

原则：

- 只要涉及可追溯、可恢复、可审计，最终都必须落 MySQL。
- MySQL 决定消息是否真实存在。
- MySQL 决定消息最终顺序。

### 9.2 Redis 职责

Redis 只承担高频、短时、可重建数据：

- WebSocket 在线连接映射。
- 用户在线状态的短期缓存。
- 发送幂等预热键。
- 会话游标热缓存。
- 未读数热缓存。
- 消息分发队列或发布订阅中间态。
- 限流计数器。

原则：

- Redis 不是事实源。
- Redis 可丢失，丢失后必须可由 MySQL 重新计算或恢复。
- Redis 里的未读数只能做加速缓存，不能作为最终账本。

### 9.3 推荐键设计

建议使用以下键前缀：

- `im:dedup:{sender_id}:{client_msg_id}`
- `im:cursor:sync:{user_id}:{conversation_id}`
- `im:cursor:read:{user_id}:{conversation_id}`
- `im:unread:{user_id}`
- `im:conn:{user_id}`
- `im:conv:last_seq:{conversation_id}`

说明：

- 键名统一使用 `im:` 前缀。
- 所有热缓存键必须能从 MySQL 重建。

## 10. 前端状态约束

### 10.1 页面不得直接管理 socket

页面层禁止直接实现以下逻辑：

- 直接创建、关闭、重连 WebSocket。
- 直接维护 socket 事件分发为业务真值。
- 在页面组件内散落消息收发协议。

正确做法：

- 由 IM SDK 或 IM domain service 统一封装 socket 生命周期。
- 页面只调用 `sendMessage`、`pullMessages`、`markRead`、`syncConversation` 等稳定接口。
- 页面只订阅状态，不直接控制底层连接细节。

### 10.2 页面状态来源

页面展示的消息列表、会话列表、未读数，必须来自统一状态源：

- IM store
- 会话 store
- 消息 store

禁止：

- 页面自己拼消息状态。
- 页面自己决定消息是否已读。
- 页面自己用本地计数替代服务端未读数。

### 10.3 前端发送约束

前端发送消息时必须：

- 先生成 `client_msg_id`。
- 先写入本地临时状态。
- 再调用发送接口。
- 接收到 send_ack 后，用 `message_id` 与 `server_seq` 覆盖本地临时记录。

禁止：

- 发送成功后再重新创建一条“新消息”。
- 发送失败后丢弃原记录而让用户无从重试。

### 10.4 前端补拉约束

- 进入会话页时必须先补拉，再渲染增量状态。
- 补拉与推送并发时，以 `server_seq` 去重合并。
- 未读角标只能由服务端口径刷新。

## 11. 服务端接口边界

### 11.1 发送消息接口

职责：

- 校验身份。
- 校验会话归属。
- 执行幂等去重。
- 落库。
- 分配 `message_id` 与 `server_seq`。
- 返回 send_ack。

### 11.2 拉取消息接口

职责：

- 按 `sync_cursor` 返回增量消息。
- 支持断点续拉。
- 保证返回顺序稳定。

### 11.3 已读回执接口

职责：

- 接收 `read_cursor`。
- 只允许单调递增。
- 更新会话读取状态与未读数。

### 11.4 会话列表接口

职责：

- 返回会话摘要。
- 返回最后消息。
- 返回未读数。
- 返回读游标与同步游标。

## 12. 数据模型建议

### 12.1 message 表

建议字段：

- `id`
- `message_id`
- `conversation_id`
- `server_seq`
- `sender_id`
- `receiver_id`
- `client_msg_id`
- `msg_type`
- `content_json`
- `media_json`
- `send_state`
- `audit_state`
- `delivered_at`
- `read_at`
- `created_at`
- `updated_at`

### 12.2 conversation 表

建议字段：

- `conversation_id`
- `user_a_id`
- `user_b_id`
- `last_message_id`
- `last_server_seq`
- `last_message_preview`
- `last_message_at`
- `created_at`
- `updated_at`

### 12.3 conversation_member 表

建议字段：

- `conversation_id`
- `user_id`
- `sync_cursor`
- `read_cursor`
- `unread_count_snapshot`
- `joined_at`
- `updated_at`

## 13. 第一批实现边界

第一批只做以下能力：

- 单聊会话创建与获取。
- 文本消息发送与接收。
- 图片消息发送与接收（仅 URL 元信息）。
- send_ack。
- read_ack。
- 离线补拉。
- 消息去重。
- 会话列表。
- 未读数。
- 基础审核接入。

第一批明确不做：

- 群聊。
- 撤回。
- 编辑消息。
- 消息引用回复。
- 表情包消息类型扩展。
- 文件、音频、视频消息。
- 语音转文字。
- 在线状态展示。
- typing 状态。
- 消息搜索。
- 消息置顶。
- 消息转发。
- 复杂端到端加密协议。

## 14. 第一批任务拆分

### 14.1 后端任务

1. 冻结消息表、会话表、成员表结构。
2. 实现单聊会话生成与查询。
3. 实现消息发送接口与幂等去重。
4. 实现 server_seq 分配规则。
5. 实现补拉接口与 cursor 体系。
6. 实现 read_ack 接口与未读数计算。
7. 接入审核状态流转。
8. 实现 Redis 热缓存与 MySQL 落库一致性策略。

### 14.2 前端任务

1. 封装 IM SDK，不允许页面直连 socket。
2. 建立会话列表页与消息列表页。
3. 建立本地临时消息状态与 send_ack 回写机制。
4. 建立离线补拉与重连恢复机制。
5. 建立已读上报机制。
6. 建立未读角标展示与刷新机制。
7. 建立图片消息渲染组件。

### 14.3 联调任务

1. 验证发送幂等。
2. 验证重复拉取去重。
3. 验证断网重连补拉。
4. 验证 send_ack、read_ack、未读数一致性。
5. 验证审核状态对前端展示的影响。

### 14.4 测试任务

1. 覆盖重复发送。
2. 覆盖重复补拉。
3. 覆盖乱序推送。
4. 覆盖离线重连。
5. 覆盖未读数回归。
6. 覆盖图片 URL 元信息解析。

## 15. 严格约束

以下约束属于冻结要求，后续实现不得违反：

- 消息顺序以 `server_seq` 为准。
- 幂等以 `sender_id + client_msg_id` 为准。
- 未读数以 `read_cursor` 为准。
- 离线补拉以 `sync_cursor` 为准。
- 图片消息只传 URL 元信息。
- 页面不直接管理 socket。
- Redis 只做热数据与中间态，不做事实源。
- MySQL 是最终事实源。
- send_ack 只表示服务端已收，不表示对方已读。
- 审核状态与发送状态分离。

## 16. 冻结结论

MVP 阶段的 IM 协议应当遵循：

- **协议先行**：先冻结字段、状态机、ACK、游标与去重。
- **存储分层**：MySQL 保真，Redis 加速。
- **前后端解耦**：页面只消费 IM 域能力，不直控 socket。
- **同步可恢复**：离线补拉必须可重试、可断点续传。
- **口径唯一**：未读数、顺序、已读、去重都必须有唯一口径。

该文档可作为后续 IM 子 agent 的统一契约基线。
