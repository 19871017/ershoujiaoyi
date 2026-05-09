# 数据库总设计草案

> 适用阶段：基础架构冻结期
>
> 目标：先冻结共享数据模型、主键、唯一键、索引、幂等键和状态机边界，避免后续用户、商品、订单、钱包、IM、审核、风控、后台服务反复改表。
>
> 原则：**先定边界，再定表；先定写路径，再定读模型；钱相关必须账本优先，所有跨服务写入必须具备幂等键。**

## 1. 总体建模原则

### 1.1 主键策略

- 所有核心业务表统一使用 `bigint` 雪花 ID 作为主键，便于分库分表、日志追踪和跨服务引用。
- 少量强约束字典表可使用短整型或自增 ID，但不建议用于核心交易表。
- 所有表统一保留：`id`、`created_at`、`updated_at`，必要时保留 `deleted_at` 或 `is_deleted`。

### 1.2 业务唯一键

- 除主键外，必须为关键业务对象定义业务唯一键，避免重复创建。
- 所有会重试的写路径都要有幂等键，且必须建立唯一索引。
- 任何“回调、支付、发货、审核、IM 送达、提现处理”类动作，都不能只靠主键防重。

### 1.3 状态字段规则

- 状态字段统一使用枚举型字符串或小整型，避免前期业务频繁加列。
- 每个状态机只允许单一服务写入状态变更。
- 所有状态变更必须落一张状态流水表，便于排障和审计。

### 1.4 服务边界原则

- **用户/身份/认证**：用户服务负责账号、资料、认证、标签。
- **商品**：商品服务负责发布、审核快照、上下架。
- **订单**：订单服务负责交易编排、状态机、快照。
- **钱包/账本**：财务服务负责账户、流水、冻结、解冻、结算、提现。
- **IM**：IM 服务负责会话、消息、回执、已读游标。
- **礼物**：礼物服务负责礼物目录、赠送单、分成结果，最终入账走财务服务。
- **审核/风控**：审核与风控服务负责审核任务、规则命中、处置结果。
- **后台**：后台服务负责运营账号、角色权限、审计日志、配置管理。

---

## 2. 第一批必须定义的核心表

以下表必须在基础架构阶段先冻结，任何下游服务不得自行新增替代表。

### 2.1 用户 / 身份 / 认证

#### `user_account`
用户主账号表，平台唯一身份入口。

- 主键：`id`
- 业务唯一键：`mobile`、`email`、`union_id`（如有第三方登录）
- 核心字段：
  - `account_status`：`active / frozen / banned / deleted`
  - `main_role`：`buyer / seller / unknown`
  - `nickname`
  - `avatar_url`
  - `gender`
  - `region_code`
  - `region_level`
  - `last_login_at`
  - `auth_level`：认证等级或认证完成度
- 索引建议：
  - `uk_mobile`
  - `uk_email`
  - `idx_status_created_at`
  - `idx_region_code`

#### `user_profile`
用户展示资料扩展表，和账号核心信息解耦。

- 主键：`id`
- 业务唯一键：`user_id`
- 核心字段：
  - `bio`
  - `birthday`
  - `city_code`
  - `province_code`
  - `occupation`
  - `tags_json`
  - `visibility_level`
- 索引建议：
  - `uk_user_id`
  - `idx_city_code`
  - `idx_province_code`

#### `user_identity`
身份与认证结果表，一个账号可有多次认证记录，但只保留当前生效版本。

- 主键：`id`
- 业务唯一键：`user_id` + `identity_type` + `version`
- `identity_type`：`buyer / seller`
- `identity_status`：
  - 买家：`pending / verified / rejected / expired`
  - 卖家：`draft / pending_review / reviewing / approved / rejected / expired`
- 核心字段：
  - `review_task_id`
  - `review_result_id`
  - `label_codes`
  - `valid_from`
  - `valid_to`
  - `reviewed_by`
  - `reviewed_at`
- 索引建议：
  - `idx_user_id_type_status`
  - `idx_identity_status_updated_at`
  - `idx_review_task_id`

#### `user_tag`
用户标签表，用于认证卖家、真实买家、优质卖家等标签。

- 主键：`id`
- 业务唯一键：`user_id` + `tag_code`
- 核心字段：
  - `tag_code`
  - `tag_source`
  - `tag_status`：`active / revoked / expired`
  - `start_at`
  - `end_at`
- 索引建议：
  - `uk_user_tag`
  - `idx_tag_code_status`

#### `auth_credential`
认证凭据表，承载手机号验证码、第三方绑定、密码哈希等。

- 主键：`id`
- 业务唯一键：`user_id` + `credential_type` + `identifier`
- 核心字段：
  - `credential_type`：`mobile / email / password / oauth`
  - `identifier`
  - `secret_hash`
  - `verified_at`
  - `bind_status`
- 索引建议：
  - `uk_identifier_type`
  - `idx_user_id_type`

#### `auth_session`
登录会话表，支持踢下线和设备管理。

- 主键：`id`
- 业务唯一键：`session_token_hash`
- 核心字段：
  - `user_id`
  - `device_id`
  - `device_type`
  - `ip`
  - `user_agent`
  - `session_status`：`active / revoked / expired`
  - `expires_at`
- 索引建议：
  - `uk_session_token_hash`
  - `idx_user_id_status`
  - `idx_expires_at`

---

### 2.2 商品

#### `product_spu`
商品主表，承载闲置商品基础信息。

- 主键：`id`
- 业务唯一键：`seller_id` + `product_no`
- 核心字段：
  - `seller_id`
  - `title`
  - `category_id`
  - `brand_name`
  - `condition_level`
  - `list_price`
  - `shipping_rule_json`
  - `trade_rule_json`
  - `product_status`：`draft / pending_review / active / off_shelf / sold / closed / rejected`
  - `audit_status`
  - `published_at`
  - `shelf_at`
- 索引建议：
  - `uk_seller_product_no`
  - `idx_seller_status_updated_at`
  - `idx_category_status_created_at`
  - `idx_price_status`

#### `product_image`
商品图片表。

- 主键：`id`
- 业务唯一键：`product_id` + `sort_no`
- 核心字段：
  - `product_id`
  - `image_url`
  - `sort_no`
  - `image_status`
  - `audit_status`
- 索引建议：
  - `uk_product_sort_no`
  - `idx_product_id_status`

#### `product_snapshot`
商品快照表，订单和审核必须引用快照，不能直接读实时商品。

- 主键：`id`
- 业务唯一键：`product_id` + `version`
- 核心字段：
  - `product_id`
  - `version`
  - `title_snapshot`
  - `price_snapshot`
  - `trade_rule_snapshot`
  - `image_snapshot_json`
  - `seller_snapshot_json`
- 索引建议：
  - `uk_product_version`
  - `idx_product_id_version`

#### `product_audit`
商品审核记录表。

- 主键：`id`
- 业务唯一键：`audit_batch_no`
- 核心字段：
  - `product_id`
  - `audit_type`：`create / update / republish`
  - `audit_status`：`pending / reviewing / approved / rejected / escalated`
  - `risk_level`
  - `reviewer_id`
  - `review_reason`
  - `audit_snapshot_id`
- 索引建议：
  - `uk_audit_batch_no`
  - `idx_product_id_status_created_at`
  - `idx_audit_status_created_at`

---

### 2.3 订单

#### `trade_order`
交易订单主表，统一承载商品单、礼物单的订单骨架。

- 主键：`id`
- 业务唯一键：`order_no`
- 核心字段：
  - `buyer_id`
  - `seller_id`
  - `order_type`：`product / gift / other`
  - `origin_scene`
  - `order_status`：`created / pending_pay / paid / confirmed / shipped / completed / canceled / refunded / closed`
  - `pay_status`
  - `refund_status`
  - `total_amount`
  - `service_fee_amount`
  - `settle_amount`
  - `currency`
  - `fee_snapshot_json`
  - `source_id`：关联商品单或礼物单
  - `expires_at`
- 索引建议：
  - `uk_order_no`
  - `idx_buyer_id_created_at`
  - `idx_seller_id_created_at`
  - `idx_status_updated_at`
  - `idx_source_id_order_type`

#### `trade_order_item`
订单明细表。

- 主键：`id`
- 业务唯一键：`order_id` + `item_no`
- 核心字段：
  - `order_id`
  - `source_product_id`
  - `source_gift_id`
  - `sku_name_snapshot`
  - `item_amount`
  - `quantity`
  - `item_status`
- 索引建议：
  - `uk_order_item_no`
  - `idx_order_id`

#### `trade_order_snapshot`
订单快照表，记录下单时的商品、费率、交易规则、地址/地区等关键上下文。

- 主键：`id`
- 业务唯一键：`order_id`
- 核心字段：
  - `order_id`
  - `buyer_snapshot_json`
  - `seller_snapshot_json`
  - `product_snapshot_json`
  - `trade_rule_snapshot_json`
  - `pricing_snapshot_json`
  - `risk_snapshot_json`
- 索引建议：
  - `uk_order_id`

#### `trade_order_status_log`
订单状态流水表，所有状态机必须留痕。

- 主键：`id`
- 业务唯一键：`order_id` + `from_status` + `to_status` + `event_no`
- 核心字段：
  - `order_id`
  - `from_status`
  - `to_status`
  - `event_no`
  - `operator_type`：`system / user / admin / job`
  - `operator_id`
  - `reason_code`
  - `remark`
- 索引建议：
  - `idx_order_id_created_at`
  - `idx_event_no`

---

### 2.4 钱包 / 账本 / 结算

> 说明：财务域必须以账本为真相源，余额表只做加速缓存，不得作为最终凭证。

#### `wallet_account`
钱包账户表，一人可有多个账户类型。

- 主键：`id`
- 业务唯一键：`user_id` + `wallet_type`
- 核心字段：
  - `user_id`
  - `wallet_type`：`cash / bonus / frozen / commission`
  - `account_status`：`active / frozen / closed`
  - `available_balance`
  - `frozen_balance`
  - `total_in_amount`
  - `total_out_amount`
- 索引建议：
  - `uk_user_wallet_type`
  - `idx_user_id_status`

#### `wallet_ledger_entry`
账本流水表，财务核心真相表。

- 主键：`id`
- 业务唯一键：`ledger_no`
- 幂等键：`idempotency_key`
- 核心字段：
  - `wallet_account_id`
  - `direction`：`in / out / freeze / unfreeze`
  - `entry_type`：`recharge / consume / gift_income / gift_payout / order_settlement / withdrawal / refund / adjustment / risk_hold`
  - `amount`
  - `balance_before`
  - `balance_after`
  - `biz_type`
  - `biz_id`
  - `related_txn_no`
  - `ledger_status`：`pending / success / failed / reversed`
  - `occurred_at`
- 索引建议：
  - `uk_ledger_no`
  - `uk_idempotency_key`
  - `idx_wallet_account_id_created_at`
  - `idx_biz_type_biz_id`
  - `idx_related_txn_no`

#### `wallet_transaction`
资金交易单表，承载充值、支付、退款、提现、赠送等财务动作的编排单。

- 主键：`id`
- 业务唯一键：`txn_no`
- 幂等键：`idempotency_key`
- 核心字段：
  - `txn_type`：`recharge / pay / refund / settlement / withdraw / gift / adjust`
  - `txn_status`：`created / processing / success / failed / canceled / reversed`
  - `payer_user_id`
  - `payee_user_id`
  - `amount`
  - `currency`
  - `biz_type`
  - `biz_id`
  - `request_source`
  - `fee_snapshot_json`
  - `expire_at`
- 索引建议：
  - `uk_txn_no`
  - `uk_idempotency_key`
  - `idx_biz_type_biz_id`
  - `idx_payer_user_id_created_at`
  - `idx_payee_user_id_created_at`

#### `wallet_freeze_record`
冻结/解冻记录表，用于风控、售后、提现审核。

- 主键：`id`
- 业务唯一键：`freeze_no`
- 核心字段：
  - `wallet_account_id`
  - `freeze_type`
  - `freeze_amount`
  - `freeze_status`：`active / released / consumed / expired`
  - `biz_type`
  - `biz_id`
  - `reason_code`
- 索引建议：
  - `uk_freeze_no`
  - `idx_wallet_account_id_status`
  - `idx_biz_type_biz_id`

#### `withdrawal_request`
提现申请表。

- 主键：`id`
- 业务唯一键：`withdrawal_no`
- 幂等键：`idempotency_key`
- 核心字段：
  - `user_id`
  - `wallet_account_id`
  - `amount`
  - `withdrawal_status`：`submitted / reviewing / approved / rejected / paying / paid / failed / canceled`
  - `review_task_id`
  - `payout_channel`
  - `channel_txn_no`
  - `fee_snapshot_json`
- 索引建议：
  - `uk_withdrawal_no`
  - `uk_idempotency_key`
  - `idx_user_id_created_at`
  - `idx_withdrawal_status_created_at`

---

### 2.5 IM 会话 / 消息

#### `im_conversation`
会话主表。

- 主键：`id`
- 业务唯一键：`conversation_key`
- 核心字段：
  - `conversation_type`：`single / group / system`
  - `owner_user_id`
  - `target_id`
  - `conversation_status`：`active / muted / blocked / closed`
  - `last_message_id`
  - `last_message_at`
  - `unread_count_snapshot`
- 索引建议：
  - `uk_conversation_key`
  - `idx_owner_user_id_last_message_at`
  - `idx_target_id`
  - `idx_last_message_at`

#### `im_conversation_member`
会话成员表。

- 主键：`id`
- 业务唯一键：`conversation_id` + `user_id`
- 核心字段：
  - `conversation_id`
  - `user_id`
  - `member_role`
  - `member_status`：`active / left / blocked`
  - `last_read_msg_id`
  - `last_read_at`
  - `mute_status`
- 索引建议：
  - `uk_conv_user`
  - `idx_user_id_last_read_at`
  - `idx_conversation_id`

#### `im_message`
消息主表。

- 主键：`id`
- 业务唯一键：`message_no`
- 幂等键：`client_msg_id`
- 核心字段：
  - `conversation_id`
  - `sender_id`
  - `msg_type`：`text / image / gift / order_card / system / audio`
  - `msg_status`：`created / sent / delivered / read / revoked / deleted`
  - `content_json`
  - `reply_to_msg_id`
  - `biz_type`
  - `biz_id`
  - `sent_at`
- 索引建议：
  - `uk_message_no`
  - `uk_client_msg_id`
  - `idx_conversation_id_id_desc`
  - `idx_sender_id_created_at`
  - `idx_biz_type_biz_id`

#### `im_message_receipt`
消息回执表，后置但建议预留。

- 主键：`id`
- 业务唯一键：`message_id` + `user_id` + `receipt_type`
- 核心字段：
  - `message_id`
  - `user_id`
  - `receipt_type`：`delivered / read`
  - `receipt_at`
- 索引建议：
  - `uk_message_user_receipt`
  - `idx_user_id_receipt_at`

---

### 2.6 礼物

#### `gift_catalog`
礼物配置表。

- 主键：`id`
- 业务唯一键：`gift_code`
- 核心字段：
  - `gift_name`
  - `gift_price`
  - `gift_icon_url`
  - `gift_status`：`enabled / disabled`
  - `charisma_value`
  - `platform_fee_rate`
  - `recipient_share_rate`
- 索引建议：
  - `uk_gift_code`
  - `idx_status_sort_no`

#### `gift_order`
礼物赠送单。

- 主键：`id`
- 业务唯一键：`gift_order_no`
- 幂等键：`idempotency_key`
- 核心字段：
  - `sender_user_id`
  - `receiver_user_id`
  - `gift_id`
  - `gift_count`
  - `gift_amount`
  - `gift_order_status`：`created / paid / sent / failed / canceled / reversed`
  - `scene_type`
  - `scene_id`
  - `fee_snapshot_json`
  - `wallet_txn_no`
- 索引建议：
  - `uk_gift_order_no`
  - `uk_idempotency_key`
  - `idx_sender_user_id_created_at`
  - `idx_receiver_user_id_created_at`
  - `idx_scene_type_scene_id`

#### `gift_ledger_link`
礼物与账本关联表。

- 主键：`id`
- 业务唯一键：`gift_order_id`
- 核心字段：
  - `gift_order_id`
  - `wallet_txn_no`
  - `ledger_no`
  - `link_status`
- 索引建议：
  - `uk_gift_order_id`
  - `idx_wallet_txn_no`

---

### 2.7 审核 / 风控

#### `review_task`
统一审核任务表，覆盖商品、身份、提现、风控处置等。

- 主键：`id`
- 业务唯一键：`review_task_no`
- 核心字段：
  - `biz_type`
  - `biz_id`
  - `review_type`：`identity / product / withdrawal / content / risk`
  - `review_status`：`pending / assigned / reviewing / approved / rejected / escalated / closed`
  - `priority`
  - `assignee_id`
  - `due_at`
  - `risk_score`
- 索引建议：
  - `uk_review_task_no`
  - `idx_biz_type_biz_id`
  - `idx_review_status_priority_due_at`
  - `idx_assignee_id_status`

#### `review_record`
审核结果表。

- 主键：`id`
- 业务唯一键：`review_record_no`
- 核心字段：
  - `review_task_id`
  - `review_result`：`approved / rejected / need_more_info / escalated`
  - `review_reason_code`
  - `review_comment`
  - `reviewer_id`
  - `reviewed_at`
- 索引建议：
  - `uk_review_record_no`
  - `idx_review_task_id`
  - `idx_reviewer_id_reviewed_at`

#### `risk_event`
风控事件表。

- 主键：`id`
- 业务唯一键：`risk_event_no`
- 幂等键：`event_key`
- 核心字段：
  - `event_type`
  - `biz_type`
  - `biz_id`
  - `risk_level`：`low / medium / high / critical`
  - `risk_status`：`open / handling / blocked / resolved / ignored`
  - `rule_code`
  - `hit_detail_json`
  - `action_taken`
- 索引建议：
  - `uk_risk_event_no`
  - `uk_event_key`
  - `idx_biz_type_biz_id`
  - `idx_risk_status_risk_level_created_at`

#### `abuse_report`
举报表，建议第一批即定义。

- 主键：`id`
- 业务唯一键：`report_no`
- 幂等键：`idempotency_key`
- 核心字段：
  - `reporter_user_id`
  - `target_user_id`
  - `target_type`
  - `target_id`
  - `report_reason`
  - `report_status`：`submitted / triaged / reviewing / resolved / dismissed`
  - `linked_review_task_id`
- 索引建议：
  - `uk_report_no`
  - `uk_idempotency_key`
  - `idx_target_type_target_id`
  - `idx_report_status_created_at`

---

### 2.8 后台 / 配置 / 审计

#### `admin_account`
后台账号表。

- 主键：`id`
- 业务唯一键：`username`
- 核心字段：
  - `password_hash`
  - `admin_status`：`active / frozen / disabled`
  - `last_login_at`
  - `last_login_ip`
- 索引建议：
  - `uk_username`
  - `idx_admin_status`

#### `admin_role`
后台角色表。

- 主键：`id`
- 业务唯一键：`role_code`
- 核心字段：
  - `role_name`
  - `role_status`
  - `role_desc`
- 索引建议：
  - `uk_role_code`

#### `admin_permission`
后台权限点表。

- 主键：`id`
- 业务唯一键：`permission_code`
- 核心字段：
  - `permission_name`
  - `module_code`
  - `permission_status`
- 索引建议：
  - `uk_permission_code`

#### `admin_role_permission`
角色权限关联表。

- 主键：`id`
- 业务唯一键：`role_id` + `permission_id`
- 索引建议：
  - `uk_role_permission`
  - `idx_permission_id`

#### `admin_audit_log`
后台操作审计表。

- 主键：`id`
- 业务唯一键：`audit_no`
- 核心字段：
  - `admin_id`
  - `action_type`
  - `biz_type`
  - `biz_id`
  - `before_json`
  - `after_json`
  - `request_id`
  - `ip`
- 索引建议：
  - `uk_audit_no`
  - `idx_admin_id_created_at`
  - `idx_biz_type_biz_id`
  - `idx_request_id`

#### `system_config`
系统配置表，承载费率、开关、风控阈值等。

- 主键：`id`
- 业务唯一键：`config_key`
- 核心字段：
  - `config_value`
  - `config_type`
  - `config_status`
  - `remark`
  - `version`
- 索引建议：
  - `uk_config_key`
  - `idx_config_type_status`

---

## 3. 幂等键设计规范

所有具备重试风险的接口必须在数据库层冻结幂等键，不能只依赖应用内去重。

### 3.1 必须具备幂等键的场景

- 登录/绑定/注册写入
- 商品发布、编辑提交、上架动作
- 下单、支付、退款、取消、关闭
- 礼物赠送、礼物撤销、礼物分成入账
- 提现申请、审核通过、打款回调
- IM 发送消息、撤回消息、已读回执写入
- 举报、审核派单、审核结果回写
- 风控处置动作

### 3.2 幂等键建议

- 外部请求类：`idempotency_key`
- 渠道回调类：`channel_txn_no` + `channel_code`
- 客户端消息类：`client_msg_id`
- 订单类：`order_no`
- 交易类：`txn_no`
- 业务事件类：`event_key`

### 3.3 幂等索引原则

- 幂等键必须建立唯一索引。
- 幂等键应与业务域绑定，不要全局共用一个模糊字段。
- 对于高并发表，幂等键字段长度要稳定、可比较、可索引。

---

## 4. 状态字段总约束

### 4.1 用户域

- `account_status`
- `identity_status`
- `tag_status`
- `session_status`

### 4.2 商品域

- `product_status`
- `audit_status`
- `image_status`

### 4.3 订单域

- `order_status`
- `pay_status`
- `refund_status`
- `item_status`

### 4.4 财务域

- `wallet_account.account_status`
- `wallet_ledger_entry.ledger_status`
- `wallet_transaction.txn_status`
- `wallet_freeze_record.freeze_status`
- `withdrawal_request.withdrawal_status`

### 4.5 IM 域

- `conversation_status`
- `member_status`
- `msg_status`

### 4.6 审核/风控域

- `review_status`
- `review_result`
- `risk_status`
- `report_status`

### 4.7 状态设计要求

- 状态只允许单向推进，允许回退的必须显式定义。
- 所有状态机必须有事件日志表。
- 状态枚举必须在文档中冻结，不允许各服务自行新增同义状态。

---

## 5. 索引设计共识

### 5.1 通用索引原则

- 所有查询主路径都要有联合索引，禁止“先上线再补索引”。
- 列表页优先按 `owner_id + status + created_at` 建索引。
- 审核队列优先按 `status + priority + due_at` 建索引。
- 财务流水优先按 `biz_type + biz_id`、`wallet_account_id + created_at` 建索引。
- IM 消息优先按 `conversation_id + id desc` 建索引。

### 5.2 禁止原则

- 禁止在高频交易表上滥建大字段索引。
- 禁止把 JSON 内容作为唯一查询路径。
- 禁止只保留主键索引而没有业务查询索引。

---

## 6. 服务边界冻结建议

### 6.1 用户与认证服务

负责：`user_account`、`user_profile`、`user_identity`、`user_tag`、`auth_credential`、`auth_session`

不负责：商品发布、订单、IM 消息、财务流水。

### 6.2 商品服务

负责：`product_spu`、`product_image`、`product_snapshot`、`product_audit`

不负责：下单支付、结算、提现。

### 6.3 订单服务

负责：`trade_order`、`trade_order_item`、`trade_order_snapshot`、`trade_order_status_log`

不负责：余额扣减、钱包记账、实际转账。

### 6.4 财务服务

负责：`wallet_account`、`wallet_ledger_entry`、`wallet_transaction`、`wallet_freeze_record`、`withdrawal_request`

不负责：商品审核、IM 内容审核、后台权限管理。

### 6.5 IM 服务

负责：`im_conversation`、`im_conversation_member`、`im_message`、`im_message_receipt`

不负责：商品详情真实状态、订单结算、钱包入账。

### 6.6 礼物服务

负责：`gift_catalog`、`gift_order`、`gift_ledger_link`

不负责：钱包记账底层规则，入账必须调用财务服务完成。

### 6.7 审核 / 风控服务

负责：`review_task`、`review_record`、`risk_event`、`abuse_report`

不负责：业务主表状态计算逻辑的最终落库权。

### 6.8 后台服务

负责：`admin_account`、`admin_role`、`admin_permission`、`admin_role_permission`、`admin_audit_log`、`system_config`

不负责：直接跳过业务服务修改核心交易表。

---

## 7. 必须先定义 vs 可后置

### 7.1 第一批必须先定义

这些表一旦晚定义，会直接拖慢服务开发或导致多次返工：

- `user_account`
- `user_profile`
- `user_identity`
- `user_tag`
- `auth_credential`
- `auth_session`
- `product_spu`
- `product_snapshot`
- `trade_order`
- `trade_order_snapshot`
- `trade_order_status_log`
- `wallet_account`
- `wallet_ledger_entry`
- `wallet_transaction`
- `wallet_freeze_record`
- `im_conversation`
- `im_conversation_member`
- `im_message`
- `gift_catalog`
- `gift_order`
- `review_task`
- `review_record`
- `risk_event`
- `abuse_report`
- `admin_account`
- `admin_audit_log`
- `system_config`

### 7.2 建议后置

以下表可以在第一阶段只预留字段和接口，先不深度实现：

- `im_message_receipt`
- `gift_ledger_link`
- `trade_order_item` 的复杂多品类扩展
- `withdrawal_request` 的多渠道打款扩展字段
- `oauth_binding` / 第三方登录扩展表
- `recommendation_feed` / 推荐流缓存表
- `rank_snapshot` / 榜单快照表
- `analytics_event` / 埋点明细表
- `search_index_sync_log` / 搜索同步日志表

### 7.3 不建议第一阶段抢先做的内容

- 榜单统计宽表
- 推荐算法结果表
- 全量报表聚合表
- 复杂的多币种、多钱包、多结算中心设计
- 大而全的商品属性模板体系

这些内容可以在核心交易链路稳定后再补。

---

## 8. 冻结结论

基础架构阶段只冻结三类东西：

1. **共享主表**：账号、商品、订单、钱包、IM、审核、后台
2. **状态机**：每个核心对象的状态枚举和流转规则
3. **写入保护**：唯一键、幂等键、状态流水、费率快照、账本流水

只要这三类冻结，后续各服务就可以并行开发；否则任何一边先写代码，最后都大概率要回头改表。
