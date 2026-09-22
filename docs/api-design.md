# 接口契约设计（一期）

## 1. 通用约定

- 对外接口使用 HTTPS + REST/JSON，路径前缀为 `/api/v1`。
- JWT 必须包含账户 ID 与角色；Gateway 和业务服务均执行角色与资源归属校验。
- 金额使用最小货币单位整数，时间使用 ISO-8601/RFC 3339。
- 写请求返回 `trace_id`；日志、MQ 事件、Outbox、审计记录都关联该 Trace ID。
- 统一响应结构：成功 `{code, message, data, trace_id}`；失败 `{code, message, trace_id, details}`。
- 服务端从 JWT 获取当前账户；不信任请求体中的 `user_id`、`operator_id` 或角色。

## 2. 消费端接口（CUSTOMER）

| 方法 | 路径 | 角色 | 说明 | 幂等要求 |
|---|---|---|---|---|
| POST | `/auth/register` | 游客 | 注册账户；服务端固定赋予 `CUSTOMER`，拒绝角色字段 | 用户名唯一 |
| POST | `/auth/login` | 游客 | 登录并签发 JWT | 可重试，不创建业务资源 |
| GET | `/products`、`/products/{id}` | 游客/CUSTOMER | 浏览商品 | 无 |
| GET | `/activities`、`/activities/{id}` | 游客/CUSTOMER | 浏览活动及库存展示 | 无 |
| GET | `/coupon-templates/claimable` | CUSTOMER | 查询当前可领取的优惠券模板 | 无 |
| POST | `/coupons/{templateId}/claims` | CUSTOMER | 领取优惠券 | `Idempotency-Key` 必填 |
| GET | `/coupons/me` | CUSTOMER | 查询自己的优惠券与状态 | 无 |
| POST | `/orders/preview` | CUSTOMER | 校验商品、活动和优惠券，返回当前金额试算；不预占资源、不创建订单 | 无 |
| POST | `/orders` | CUSTOMER | 创建普通或活动订单并预占库存 | `Idempotency-Key` 必填 |
| GET | `/orders`、`/orders/{orderNumber}` | CUSTOMER | 查询自己的订单 | 无 |
| POST | `/orders/{orderNumber}/cancel` | CUSTOMER | 取消自己的待支付订单 | 状态 CAS 保证重复取消安全 |
| POST | `/orders/{orderNumber}/payments` | CUSTOMER | 发起模拟支付 | `Idempotency-Key` 必填；同订单只允许一次成功支付 |
| POST | `/payments/callback` | 支付渠道/模拟客户端 | 接收支付结果通知 | 渠道流水号和事件号双重幂等 |

## 3. 角色权限隔离

| 角色 | 允许操作 | 禁止操作 |
|---|---|---|
| `CUSTOMER` | 浏览、领券、下单、查询/取消/支付自己的订单 | 运营配置、账户管理、访问他人订单或优惠券 |
| `OPERATOR` | 管理全量商品、活动、优惠券；查看活动基础数据 | 账户与角色管理；消费者领券、下单、支付 |
| `ADMIN` | 管理账户状态与角色；查看账户审计 | 商品、活动、优惠券配置修改；消费者领券、下单、支付 |

角色校验失败返回 `FORBIDDEN`；资源归属校验失败返回 `RESOURCE_ACCESS_DENIED`。运营和管理员接口不因 URL 中含有 `/admin` 而互相授权，必须精确检查角色。

## 4. 运营端接口（OPERATOR）

路径前缀为 `/api/v1/admin`，全部要求 `OPERATOR`。成功写操作在同一 PostgreSQL 事务更新领域数据、`updated_by` 并写入 `operator_audit_log`。

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/admin/products` | 创建商品。 |
| GET | `/admin/products`、`/admin/products/{id}` | 查询商品列表与详情，包含上下架状态。 |
| PUT | `/admin/products/{id}` | 修改商品资料、价格或库存。 |
| POST | `/admin/products/{id}/on-sale` | 上架商品。 |
| POST | `/admin/products/{id}/off-sale` | 下架商品。 |
| GET | `/admin/products/{id}/inventory` | 查询普通库存及保留情况。 |
| POST | `/admin/activities` | 创建未开始活动。 |
| GET | `/admin/activities`、`/admin/activities/{id}` | 查询活动列表、详情与状态。 |
| POST | `/admin/activities/{id}/cancel` | 仅取消未开始活动。 |
| POST | `/admin/activities/{id}/pause` | 仅暂停进行中活动。 |
| POST | `/admin/activities/{id}/resume` | 仅为已暂停活动创建异步恢复任务，返回 `202 Accepted`；任务成功前活动始终为 `PAUSED`。 |
| GET | `/admin/activities/{id}/recovery` | 查询最近恢复任务及 `PENDING/RUNNING/FAILED/SUCCEEDED` 状态、屏障和失败原因。 |
| GET | `/admin/activities/{id}/metrics` | 查询活动状态、库存、订单及限购基础数据。 |
| POST | `/admin/coupon-templates` | 创建优惠券模板。 |
| GET | `/admin/coupon-templates`、`/admin/coupon-templates/{id}` | 查询模板列表、详情与领取状态。 |
| PUT | `/admin/coupon-templates/{id}` | 仅修改尚未开始领取的模板核心规则。 |
| POST | `/admin/coupon-templates/{id}/pause` | 暂停领取。 |
| POST | `/admin/coupon-templates/{id}/resume` | 恢复领取。 |
| GET | `/admin/coupon-templates/{id}/metrics` | 查询发行、领取、锁定、核销和过期数量。 |

## 5. 账户管理接口（ADMIN）

路径前缀为 `/api/v1/admin`，仅 `ADMIN` 可访问。公开注册只能创建 `CUSTOMER`；只有 ADMIN 能创建或提升 `OPERATOR`/`ADMIN`。

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/admin/users` | 创建 CUSTOMER、OPERATOR 或 ADMIN 账户。 |
| GET | `/admin/users`、`/admin/users/{id}` | 查询账户、角色与状态。 |
| GET | `/admin/users/audit-logs` | 查询账户创建、角色变更和启停审计；只返回 `USER_ACCOUNT` 目标。 |
| PATCH | `/admin/users/{id}/role` | 修改目标角色；禁止修改自己的角色，禁止移除最后一个启用 ADMIN。 |
| POST | `/admin/users/{id}/enable` | 启用账户。 |
| POST | `/admin/users/{id}/disable` | 禁用账户；禁止禁用自己或最后一个启用 ADMIN。 |

账户管理操作与 `operator_audit_log` 中 `USER_ACCOUNT` 审计记录同一事务提交。部署时必须以受控方式预置至少一个启用的 ADMIN，公开注册不具备管理员提权能力。

## 6. 关键请求字段

### 注册、分页与可见性

- `POST /auth/register` 只接收 `username`、`password`；角色、账户状态和审计字段均由服务端控制。
- 列表接口统一支持 `page`、`page_size`；响应包含 `items`、`page`、`page_size`、`total`。
- 游客/CUSTOMER 的商品列表只返回 `ON_SALE` 商品，活动列表只返回数据库当前时间内的 `ACTIVE` 活动；运营端列表返回全状态数据。
- `GET /coupons/me` 仅按 JWT 中用户 ID 查询，可按 `status` 过滤；`GET /coupon-templates/claimable` 只返回当前可领取的 `ACTIVE` 模板。

### 运营配置

- 商品写入字段：`sku`、`name`、`description`、`list_price_minor`、`available_stock`、`status`。创建时由服务端将 JWT 的 OPERATOR 写入 `created_by/updated_by`。
- 活动创建字段：`name`、`product_id`、`sale_price_minor`、`initial_stock`、`purchase_limit_per_user`、`starts_at`、`ends_at`。一期不提供活动更新接口；创建后仅允许取消未开始活动、暂停进行中活动和请求异步恢复已暂停活动。
- `POST /admin/activities/{id}/resume` 返回恢复任务 ID、状态 `PENDING` 和 `trace_id`；若该活动已有 `PENDING` 或 `RUNNING` 任务则返回该任务，不创建并发恢复。运营端通过恢复查询接口观察结果；任务失败时活动仍为 `PAUSED`。
- 优惠券模板写入字段：`name`、`threshold_minor`、`discount_minor`、`issue_limit`、`claim_limit_per_user`、`claim_starts_at`、`claim_ends_at`、`use_starts_at`、`use_ends_at`。开始领取后禁止修改核心规则。
- 运营状态接口不接受操作者或审计字段；服务端生成 `updated_by`、前后快照和 Trace ID。

### 创建订单

`POST /api/v1/orders`，请求头为 `Idempotency-Key: ORDER_SUBMIT_<客户端唯一值>`。

```json
{
  "kind": "DIRECT",
  "activity_id": null,
  "items": [{"product_id": 101, "quantity": 2}],
  "user_coupon_id": 9001
}
```

活动订单必须为 `ACTIVITY`、只含一个订单项并提供 `activity_id`；普通订单必须为 `DIRECT`，可包含多个商品，不能混用活动订单项。

`POST /orders/preview` 使用相同请求体，只返回 `item_subtotal_minor`、`activity_discount_minor`、`coupon_discount_minor`、`payable_amount_minor` 和校验结果；试算不承诺库存、活动资格或优惠券在随后提交时仍可用。

### 领取优惠券

`POST /api/v1/coupons/{templateId}/claims`，请求头为 `Idempotency-Key: COUPON_CLAIM_<客户端唯一值>`。服务先执行 Redis 原子预扣，再以 PostgreSQL 本地事务写入用户券和 Coupon Outbox；失败由对账任务补偿 Redis。

### 支付回调

`POST /payments/callback` 接收 `order_number`、`provider_transaction_id`、`payment_status`、`paid_at` 和支付事件号。模拟支付渠道以约定的回调签名/服务凭证鉴别调用方；相同渠道流水号或支付事件号只能成功处理一次。

## 7. 内部同步接口

- 订单服务调用商品/活动服务：校验商品状态、活动时间边界、活动商品匹配和价格快照。
- 订单服务调用优惠券服务：校验用户券归属、使用窗口、订单门槛并锁定优惠券。
- 订单服务调用库存服务：预占或释放直接库存/活动库存；库存服务返回保留记录号。
- 支付服务调用订单服务或发布支付事件：订单服务以数据库状态 CAS 处理 `PENDING_PAYMENT → PAID`。
- 服务间调用使用 OpenFeign，透传服务身份和 Trace ID；不得跨服务直接写对方领域表。

## 8. 异步事件信封

RocketMQ 事件统一包含：`event_id`、`event_type`、`event_version`、`idempotency_key`、`producer`、`aggregate_type`、`aggregate_id`、`occurred_at`、`trace_id`、`payload`。

事件幂等号使用业务前缀，例如 `ORDER_PAID_<UUID>`、`STOCK_RELEASE_<UUID>`、`COUPON_RESTORE_<UUID>`。消费者以自身幂等表记录 `PROCESSING/SUCCEEDED/FAILED`，成功确认消息必须发生在业务事务提交之后。

## 9. 错误码与状态约定

| 类别 | 典型错误码 |
|---|---|
| 认证与权限 | `UNAUTHENTICATED`、`FORBIDDEN`、`RESOURCE_ACCESS_DENIED`、`ACCOUNT_DISABLED` |
| 参数与幂等 | `VALIDATION_ERROR`、`USERNAME_ALREADY_EXISTS`、`ORDER_IDEMPOTENCY_CONFLICT`、`REQUEST_IN_PROGRESS` |
| 商品/活动 | `PRODUCT_NOT_ON_SALE`、`ACTIVITY_NOT_ACTIVE`、`ACTIVITY_PURCHASE_LIMIT_EXCEEDED`、`STOCK_NOT_ENOUGH` |
| 优惠券 | `COUPON_NOT_CLAIMABLE`、`COUPON_CLAIM_LIMIT_EXCEEDED`、`COUPON_NOT_AVAILABLE`、`COUPON_THRESHOLD_NOT_MET` |
| 订单与支付 | `ORDER_NOT_FOUND`、`ORDER_EXPIRED`、`ORDER_NOT_PAYABLE`、`ORDER_ALREADY_CANCELLED`、`PAYMENT_ALREADY_SUCCEEDED` |
| 账户管理 | `LAST_ACTIVE_ADMIN_PROTECTED`、`SELF_ROLE_CHANGE_FORBIDDEN`、`SELF_DISABLE_FORBIDDEN` |

## 10. PRD 覆盖核对

| PRD 功能 | 对应接口 | 覆盖结论 |
|---|---|---|
| 商品浏览与详情 | `GET /products`、`GET /products/{id}` | 已覆盖 |
| 活动列表与详情 | `GET /activities`、`GET /activities/{id}` | 已覆盖 |
| 可领券、领券、我的券 | `GET /coupon-templates/claimable`、`POST /coupons/{templateId}/claims`、`GET /coupons/me` | 已覆盖 |
| 金额展示与下单 | `POST /orders/preview`、`POST /orders` | 已覆盖 |
| 订单列表、详情、取消 | `GET /orders`、`GET /orders/{orderNumber}`、`POST /orders/{orderNumber}/cancel` | 已覆盖 |
| 模拟支付与回调 | `POST /orders/{orderNumber}/payments`、`POST /payments/callback` | 已覆盖 |
| 商品运营管理 | `/admin/products` 及库存、上架、下架子资源 | 已覆盖 |
| 活动运营管理与数据 | `/admin/activities` 及取消、暂停、恢复、指标子资源 | 已覆盖 |
| 优惠券运营管理与数据 | `/admin/coupon-templates` 及暂停、恢复、指标子资源 | 已覆盖 |
| 消费者注册/登录 | `POST /auth/register`、`POST /auth/login` | 已覆盖 |
| 运营与管理员账户管理 | `/admin/users` 及角色、启停、审计子资源 | 已覆盖 |

订单超时取消、履约完成、Outbox 投递、Redis/数据库对账和消费幂等均为后台异步职责，不暴露给客户端 API；其触发方式和可观测性要求见 `sequence-design.md` 与 `cross-cutting-design.md`。
